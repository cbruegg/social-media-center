package components

import androidx.compose.material.AlertDialog
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable

@Composable
fun LastLoadFailurePopup(
    lastLoadFailure: Throwable,
    dismissLastLoadFailurePopup: () -> Unit
) {
    AlertDialog(
        text = {
            Text(
                lastLoadFailure.message ?: lastLoadFailure.toString()
            )
        },
        onDismissRequest = dismissLastLoadFailurePopup,
        dismissButton = {
            TextButton(onClick = dismissLastLoadFailurePopup) { Text("Dismiss") }
        },
        confirmButton = {}
    )
}