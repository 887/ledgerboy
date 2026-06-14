package com.eight87.ledgerboy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import com.eight87.ledgerboy.theme.LedgerboyTheme
import com.eight87.ledgerboy.ui.nav.LedgerboyApp
import com.eight87.ledgerboy.ui.onboarding.OnboardingScreen

/**
 * A.5 / A.10 — single activity hosting the Compose UI. Installs the
 * SplashScreen so the cold-boot frame stays on the dark launcher
 * background (M3E gotcha 4), then mounts [LedgerboyApp] under the
 * [LedgerboyTheme] with the process-wide [AppGraph].
 */
class LedgerboyActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    installSplashScreen()
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)
    val graph = (application as LedgerboyApplication).graph
    setContent {
      LedgerboyTheme {
        // null = preference not loaded yet → keep the splash-coloured frame
        // (no flash) until we know whether to onboard.
        val onboarded by graph.appPreferences.onboardingComplete
          .collectAsStateWithLifecycle(initialValue = null as Boolean?)
        when (onboarded) {
          null -> Unit
          false -> OnboardingScreen(
            onFinish = { baseCurrency ->
              graph.applicationScope.launch {
                graph.appPreferences.setBaseCurrency(baseCurrency)
                graph.appPreferences.setOnboardingComplete(true)
              }
            },
          )
          true -> LedgerboyApp(graph)
        }
      }
    }
  }
}
