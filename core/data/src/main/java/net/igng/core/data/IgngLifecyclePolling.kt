package net.igng.core.data

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay

/**
 * Lifecycle-aware foreground polling helper shared across IGNG apps.
 * Automatically suspends polling when the app moves to the background (below [Lifecycle.State.STARTED])
 * to preserve battery and Client API v1 rate-limit budget.
 */
@Composable
fun LaunchedForegroundPollingEffect(
    intervalMs: Long,
    key1: Any? = Unit,
    key2: Any? = Unit,
    enabled: Boolean = true,
    onPoll: suspend () -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestPoll by rememberUpdatedState(onPoll)

    LaunchedEffect(lifecycleOwner, intervalMs, key1, key2, enabled) {
        if (!enabled || intervalMs <= 0L) return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                delay(intervalMs)
                latestPoll()
            }
        }
    }
}
