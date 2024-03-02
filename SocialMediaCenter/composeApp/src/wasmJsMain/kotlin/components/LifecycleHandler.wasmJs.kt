package components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

@Composable
actual fun LifecycleHandler(onPause: () -> Unit, onResume: () -> Unit) {
    // No lifecycle events on the web: We're always resumed
    LaunchedEffect(Unit) {
        onResume()
    }
}