import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.window.ComposeViewport
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport {
        val fontFamilyResolver = LocalFontFamilyResolver.current
        var fontsLoaded by remember { mutableStateOf(false) }
        var fontLoadFailure by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            val jobs = mutableListOf<Job>()
            jobs += launch {
                val notoEmojisBytes = loadEmojisFontAsBytes()
                println("Loaded emojis font")
                val fontFamily = FontFamily(Font("NotoColorEmoji", notoEmojisBytes))
                fontFamilyResolver.preload(fontFamily)
                println("Preloaded emojis font")
            }
            jobs += launch {
                val scFontAsBytes = loadSimpleChineseFontAsBytes()
                println("Loaded SC font")
                val scFontFamily = FontFamily(Font("NotoSansSC", scFontAsBytes))
                fontFamilyResolver.preload(scFontFamily)
                println("Preloaded SC font")
            }
            jobs += launch {
                val tcFontAsBytes = loadTraditionalChineseFontAsBytes()
                println("Loaded TC font")
                val tcFontFamily = FontFamily(Font("NotoSansTC", tcFontAsBytes))
                fontFamilyResolver.preload(tcFontFamily)
                println("Preloaded TC font")
            }
            try {
                jobs.joinAll()
                fontsLoaded = true
            } catch (_: Exception) {
                fontLoadFailure = true
            }
        }

        println("fontsLoaded=$fontsLoaded, fontLoadFailure=$fontLoadFailure")
        if (fontsLoaded) {
            App()
        } else if (fontLoadFailure) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Text("Failed to load fonts!")
            }
        } else {
            Box(modifier = Modifier.fillMaxWidth()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.TopCenter))
            }
        }
    }
}

private suspend fun loadEmojisFontAsBytes(): ByteArray = HttpClient().use { client ->
    client.get("https://rawcdn.githack.com/googlefonts/noto-emoji/refs/tags/v2.051/fonts/NotoColorEmoji.ttf")
        .bodyAsBytes()
}

private suspend fun loadSimpleChineseFontAsBytes(): ByteArray = HttpClient().use { client ->
    client.get("https://cdn.jsdelivr.net/npm/@electron-fonts/noto-sans-sc@1.2.0/fonts/NotoSansSC-Regular.ttf")
        .bodyAsBytes()
}

private suspend fun loadTraditionalChineseFontAsBytes(): ByteArray = HttpClient().use { client ->
    client.get("https://cdn.jsdelivr.net/npm/@electron-fonts/noto-sans-tc@1.2.0/fonts/NotoSansTC-Regular.ttf")
        .bodyAsBytes()
}