import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.ksp)
  alias(libs.plugins.room)
  alias(libs.plugins.licensee)
}

// A.7 — capture build-time metadata that the About screen renders.
// `git rev-parse --short HEAD` is shelled out at configuration time so
// the value is baked into BuildConfig.GIT_SHA. The ISO-8601 build date
// is captured at the same time; both fall back to sentinels if the
// underlying command fails (e.g. building from a tarball without git).
val gitShortSha: String = runCatching {
  val proc = ProcessBuilder("git", "rev-parse", "--short", "HEAD")
    .redirectErrorStream(true)
    .start()
  proc.waitFor()
  proc.inputStream.bufferedReader().readText().trim().ifEmpty { "unknown" }
}.getOrDefault("unknown")

val buildDateUtc: String = DateTimeFormatter.ISO_LOCAL_DATE
  .format(LocalDate.now(ZoneOffset.UTC))

android {
    namespace = "com.eight87.ledgerboy"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.eight87.ledgerboy"
        // A.3 — family floor. Strongbox / biometric / SAF behaviour is
        // verified on a real phone; the AVD covers the rest.
        minSdk = 28
        targetSdk = 36
        // Auto-bump per build: minutes since the Unix epoch. Stays in
        // Int range until ~year 6053. Every assemble produces a strictly
        // higher versionCode so Obtainium / PackageInstaller treat a new
        // APK as an upgrade rather than a duplicate-package reject.
        versionCode = (System.currentTimeMillis() / 60_000L).toInt()
        versionName = "1.0"

        buildConfigField("String", "GIT_SHA", "\"$gitShortSha\"")
        buildConfigField("String", "BUILD_DATE", "\"$buildDateUtc\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
      compose = true
      aidl = false
      buildConfig = true
      shaders = false
    }

    packaging {
      resources {
        excludes += "/META-INF/{AL2.0,LGPL2.1}"
      }
    }

    testOptions {
      unitTests {
        isIncludeAndroidResources = true
      }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

// oss-licenses A.3 — declare allowed licenses for shipped deps. v1 ships
// reporting-only (no failOnDisallowed); enforcement flips once the
// inventory is reviewed. The finance-lib GPL/LGPL gate is enforced by
// hand-review when adding connector deps (see CLAUDE.md). EPL-1.0 (junit)
// is test-scope only, never in the resolved release classpath.
licensee {
    allow("Apache-2.0")
    allow("MIT")
    allow("BSD-2-Clause")
    allow("BSD-3-Clause")
    allow("MPL-2.0")
}

// oss-licenses A.4 — copy the per-variant Licensee `artifacts.json` into
// `src/main/assets/licenses/` so `LicensesScreen` reads it via
// AssetManager at runtime. Wired as a dependency of the variant's
// mergeAssets task so the asset stays self-consistent with the build's
// resolved classpath.
val licenseeReportDir = layout.buildDirectory.dir("reports/licensee")
val licensesAssetDir = layout.projectDirectory.dir("src/main/assets/licenses")

androidComponents {
    onVariants { variant ->
        val variantName = variant.name.replaceFirstChar { it.uppercase() }
        val copyTask = tasks.register<Copy>("copyLicensesAssetFor$variantName") {
            dependsOn("licenseeAndroid$variantName")
            from(licenseeReportDir.map { it.dir("android$variantName") }) {
                include("artifacts.json")
            }
            into(licensesAssetDir)
        }
        afterEvaluate {
            tasks.named("merge${variantName}Assets").configure {
                dependsOn(copyTask)
            }
        }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  // Core Android
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)

  // Arch Components
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.material.icons.extended)
  debugImplementation(libs.androidx.compose.ui.tooling)
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Navigation 3
  implementation(libs.androidx.navigation3.ui)
  implementation(libs.androidx.navigation3.runtime)
  implementation(libs.androidx.lifecycle.viewmodel.navigation3)

  // Room — local ledger cache (Phase C).
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.room.ktx)
  ksp(libs.androidx.room.compiler)

  // DataStore — preferences (theme, base currency, plugin config).
  implementation(libs.androidx.datastore.preferences)

  // WorkManager — Phase X PluginScheduler.
  implementation(libs.androidx.work.runtime.ktx)

  // SplashScreen 1.x backport — dark cold-boot frame (M3E gotcha 4).
  implementation(libs.androidx.core.splashscreen)

  // SAF tree walking for import-file workflows.
  implementation("androidx.documentfile:documentfile:1.0.1")

  // kotlinx-serialization JSON — plugin config + licenses model.
  implementation(libs.kotlinx.serialization.json)

  // OkHttp — guarded connector transport (Phase X). Plugins call out
  // only through the shared guarded client.
  implementation(libs.okhttp)

  // Local tests
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.androidx.test.core)
  testImplementation(libs.androidx.test.ext.junit)
  testImplementation(libs.androidx.arch.core.testing)
  testImplementation(libs.androidx.room.testing)
  testImplementation(libs.androidx.work.testing)
  testImplementation(libs.okhttp.mockwebserver)

  // Compose UI testing on the JVM via Robolectric
  testImplementation(composeBom)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.compose.ui.test.manifest)

  // Instrumented tests
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)
  androidTestImplementation(libs.androidx.room.testing)
}
