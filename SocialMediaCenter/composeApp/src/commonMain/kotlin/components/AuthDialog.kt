package components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import security.ServerConfig

@Composable
fun AuthDialog(
    serverConfig: ServerConfig,
    onServerConfigEntered: (token: String, baseUrl: String) -> Unit
) {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        var tokenInput by remember { mutableStateOf(serverConfig.token ?: "") }
        var baseUrlInput by remember { mutableStateOf(serverConfig.baseUrl.value ?: "") }

        Card {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "Please enter your authentication token. You can find it in token.txt inside the server's data directory.",
                    modifier = Modifier.padding(8.dp)
                )
                TextField(
                    value = tokenInput,
                    onValueChange = { tokenInput = it },
                    label = { Text("Token") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )
                TextField(
                    value = baseUrlInput,
                    onValueChange = { baseUrlInput = it },
                    label = { Text("Server URL") },
                    singleLine = true
                )
                TextButton(onClick = { onServerConfigEntered(tokenInput, baseUrlInput) }) {
                    Text("Save")
                }
            }
        }
    }
}