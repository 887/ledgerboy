package com.eight87.ledgerboy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.eight87.ledgerboy.theme.LedgerboyTheme
import com.eight87.ledgerboy.ui.nav.LedgerboyApp

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
        LedgerboyApp(graph)
      }
    }
  }
}
