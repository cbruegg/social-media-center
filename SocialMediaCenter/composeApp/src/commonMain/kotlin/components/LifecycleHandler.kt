package components

import androidx.compose.runtime.Composable

@Composable
expect fun LifecycleHandler(
    onPause: () -> Unit,
    onResume: () -> Unit
)