package components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import com.multiplatform.lifecycle.LifecycleEvent
import com.multiplatform.lifecycle.LifecycleObserver
import com.multiplatform.lifecycle.LocalLifecycleTracker

@Composable
actual fun LifecycleHandler(
    onPause: () -> Unit,
    onResume: () -> Unit
) {
    val lifecycleTracker = LocalLifecycleTracker.current
    DisposableEffect(Unit) {
        val listener = object : LifecycleObserver {
            override fun onEvent(event: LifecycleEvent) {
                when (event) {
                    LifecycleEvent.OnPauseEvent -> onPause()
                    LifecycleEvent.OnResumeEvent -> onResume()
                    else -> {}
                }
            }
        }
        lifecycleTracker.addObserver(listener)
        onDispose {
            lifecycleTracker.removeObserver(listener)
        }
    }
}