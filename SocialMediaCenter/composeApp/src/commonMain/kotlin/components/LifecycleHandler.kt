package components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

@Composable
fun LifecycleHandler(
    onPause: () -> Unit,
    onResume: () -> Unit
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(Unit) {
        val listener = object : DefaultLifecycleObserver {

            override fun onResume(owner: LifecycleOwner) {
                onResume()
            }

            override fun onPause(owner: LifecycleOwner) {
                onPause()
            }
        }

        lifecycle.addObserver(listener)
        onDispose {
            lifecycle.removeObserver(listener)
        }
    }
}