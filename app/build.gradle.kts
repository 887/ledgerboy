import org.jetbrains.kotlin.gradle.dsl.JvmTarget
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
// `git rev-parse --short HEAD` runs via `providers.exec` at configuration time so
// the value is baked into BuildConfig.GIT_SHA. The ISO-8601 build date
// is captured at the same time; both fall back to sentinels if the
// underlying command fails (e.g. building from a tarball without git).
val gitShortSha: String = runCatching {
  providers.exec {
    commandLine("git", "rev-parse", "--short", "HEAD")
    isIgnoreExitValue = true
  }.standardOutput.asText.get().trim().ifEmpty { "unknown" }
}.getOrDefault("unknown")

val buildDateUtc: String = DateTimeFormatter.ISO_LOCAL_DATE
  .format(LocalDate.now(ZoneOffset.UTC))

android {
    namespace = "com.eight87.ledgerboy"
    compileSdk {
        version = release(37) {
            minorApiLevel = 2
        }
    }
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
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
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

// oss-licenses A.4 — publish the per-variant Licensee `artifacts.json` as
// a generated asset (`licenses/artifacts.json`) so `LicensesScreen` reads
// it via AssetManager at runtime. Registered through the AGP Variant API
// (`sources.assets.addGeneratedSourceDirectory`), so every consumer of the
// variant's assets — mergeAssets, lint, Robolectric — gets the task
// dependency wired automatically and the asset stays self-consistent with
// the build's resolved classpath. Nothing is written into `src/`.
abstract class LicensesAssetTask : DefaultTask() {
    @get:InputFile
    abstract val licenseeReport: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun copy() {
        val target = outputDir.get().asFile.resolve("licenses")
        target.deleteRecursively()
        target.mkdirs()
        licenseeReport.get().asFile.copyTo(target.resolve("artifacts.json"))
    }
}

val licenseeReportDir = layout.buildDirectory.dir("reports/licensee")

androidComponents {
    onVariants { variant ->
        val variantName = variant.name.replaceFirstChar { it.uppercase() }
        val licenseeTask = tasks.named("licenseeAndroid$variantName")
        val copyTask = tasks.register<LicensesAssetTask>("copyLicensesAssetFor$variantName") {
            dependsOn(licenseeTask)
            licenseeReport.set(licenseeReportDir.map { it.file("android$variantName/artifacts.json") })
        }
        variant.sources.assets?.addGeneratedSourceDirectory(copyTask, LicensesAssetTask::outputDir)
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
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
  implementation(libs.androidx.documentfile)

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
