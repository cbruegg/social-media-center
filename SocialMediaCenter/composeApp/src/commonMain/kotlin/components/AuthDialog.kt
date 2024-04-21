package components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Card
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import security.AuthTokenRepository

@Composable
fun AuthDialog(
    authTokenRepository: AuthTokenRepository,
    onTokenEntered: (token: String) -> Unit
) {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        var tokenInput by remember { mutableStateOf(authTokenRepository.token ?: "") }

        Card {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "Please enter your authentication token.",
                    modifier = Modifier.padding(8.dp)
                )
                TextField(
                    value = tokenInput,
                    onValueChange = { tokenInput = it },
                    label = { Text("Token") }
                )
                TextButton(onClick = { onTokenEntered(tokenInput) }) {
                    Text("Save")
                }
            }
        }
    }
}