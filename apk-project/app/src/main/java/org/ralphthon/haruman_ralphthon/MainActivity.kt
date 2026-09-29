package org.ralphthon.haruman_ralphthon

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import org.ralphthon.haruman_ralphthon.data.ThemePreference
import org.ralphthon.haruman_ralphthon.ui.HarumanApp
import org.ralphthon.haruman_ralphthon.ui.HarumanViewModel
import org.ralphthon.haruman_ralphthon.ui.theme.Haruman_RalphthonTheme

class MainActivity : ComponentActivity() {
    private val model: HarumanViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) receiveShare(intent)
        setContent {
            val dark = when (model.themePreference) {
                ThemePreference.SYSTEM -> isSystemInDarkTheme()
                ThemePreference.DARK -> true
                ThemePreference.LIGHT -> false
            }
            LaunchedEffect(dark) {
                val transparent = android.graphics.Color.TRANSPARENT
                enableEdgeToEdge(
                    statusBarStyle = if (dark) SystemBarStyle.dark(transparent) else SystemBarStyle.light(transparent, transparent),
                    navigationBarStyle = if (dark) SystemBarStyle.dark(transparent) else SystemBarStyle.light(transparent, transparent),
                )
            }
            Haruman_RalphthonTheme(darkTheme = dark) { HarumanApp(model) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        receiveShare(intent)
    }

    private fun receiveShare(shared: Intent?) {
        if (shared?.action != Intent.ACTION_SEND || shared.type != "text/plain") return
        val text = shared.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        // Consume the external event before any recreation; a new onNewIntent remains a new event.
        shared.removeExtra(Intent.EXTRA_TEXT)
        shared.action = Intent.ACTION_MAIN
        model.receiveShare(text)
    }
}
