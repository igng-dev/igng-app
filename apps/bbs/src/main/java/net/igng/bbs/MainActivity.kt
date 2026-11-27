package net.igng.bbs

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import kotlinx.coroutines.flow.MutableSharedFlow

class MainActivity : ComponentActivity() {

    private val deepLinkEvents = MutableSharedFlow<BbsRoute>(extraBufferCapacity = 4)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as BbsApplication

        setContent {
            BbsAppRoot(
                container = app.container,
                incomingDeepLinks = deepLinkEvents,
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
        val uri = intent?.data ?: return
        val app = application as BbsApplication

        if (app.container.handleIncomingOAuthCallback(uri)) {
            return
        }

        AppRoutes.resolveDeepLink(uri)?.let { route ->
            deepLinkEvents.tryEmit(route)
        }
    }
}
