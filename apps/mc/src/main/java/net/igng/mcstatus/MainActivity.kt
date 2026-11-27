package net.igng.mcstatus

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import kotlinx.coroutines.flow.MutableSharedFlow

class MainActivity : ComponentActivity() {
    private val shortcutEvents = MutableSharedFlow<String>(extraBufferCapacity = 4)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as IgngApplication
        setContent {
            IgngMcApp(
                container = app.container,
                incomingShortcuts = shortcutEvents,
            )
        }
        handleIncomingIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        val app = application as IgngApplication
        if (app.container.handleIncomingOAuthCallback(intent?.data)) {
            return
        }
        intent?.getStringExtra(EXTRA_SHORTCUT_ACTION)?.takeIf { it.isNotBlank() }?.let { action ->
            shortcutEvents.tryEmit(action)
        }
    }
}
