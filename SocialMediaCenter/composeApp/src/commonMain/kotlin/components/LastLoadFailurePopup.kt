package components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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