package org.ralphthon.haruman_ralphthon

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import android.content.Intent
import androidx.activity.SystemBarStyle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.ralphthon.haruman_ralphthon.ui.HarumanApp
import org.ralphthon.haruman_ralphthon.ui.theme.Haruman_RalphthonTheme

class MainActivity : ComponentActivity() {
    private var incomingShare by mutableStateOf<String?>(null)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.WHITE, android.graphics.Color.WHITE)
        )
        receiveShare(intent)
        setContent {
            Haruman_RalphthonTheme {
                HarumanApp(incomingShare) { incomingShare = null }
            }
        }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        receiveShare(intent)
    }
    private fun receiveShare(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type?.startsWith("text/") == true) {
            incomingShare = intent.getStringExtra(Intent.EXTRA_TEXT)
        }
    }
}
