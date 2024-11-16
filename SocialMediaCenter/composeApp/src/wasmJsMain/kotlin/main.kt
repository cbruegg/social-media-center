import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.window.CanvasBasedWindow
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import io.ktor.utils.io.core.use
import kotlinx.io.IOException

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    CanvasBasedWindow(canvasElementId = "ComposeTarget") {
        val fontFamilyResolver = LocalFontFamilyResolver.current
        var fontsLoaded by remember { mutableStateOf(false) }
        var fontLoadFailure by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            val notoEmojisBytes = try {
                loadEmojisFontAsBytes()
            } catch (e: IOException) {
                fontLoadFailure = true
                return@LaunchedEffect
            }
            println("Loaded emojis font")
            val fontFamily = FontFamily(Font("NotoColorEmoji", notoEmojisBytes))
            fontFamilyResolver.preload(fontFamily)
            println("Preloaded emojis font")
            fontsLoaded = true
        }

        println("fontsLoaded=$fontsLoaded, fontLoadFailure=$fontLoadFailure")
        if (fontsLoaded) {
            App()
        } else if (fontLoadFailure) {
            Text("Failed to load fonts!")
        } else {
            CircularProgressIndicator()
        }
    }
}

private suspend fun loadEmojisFontAsBytes(): ByteArray = HttpClient().use { client ->
    client.get("https://rawcdn.githack.com/googlefonts/noto-emoji/refs/tags/v2.047/fonts/NotoColorEmoji.ttf")
        .bodyAsBytes()
}
