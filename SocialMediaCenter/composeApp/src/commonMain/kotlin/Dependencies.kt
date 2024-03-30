import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.kodein.emoji.compose.EmojiUrl

private val httpClient = HttpClient {
    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
        })
    }
    install(HttpRequestRetry)
}

val socialMediaCenterBaseUrl = "https://socialmediacenter.cbruegg.com"
val api = Api(socialMediaCenterBaseUrl, httpClient)

suspend fun downloadEmoji(emojiUrl: EmojiUrl): ByteArray {
    val bytes = httpClient.get(emojiUrl.url).body<ByteArray>()
    if (getPlatform().nativelySupportsEmojiRendering) {
        return bytes
    }

    // Some emoji SVGs have a width and height set on the root element.
    // We need to remove them as at least on web, they result in oversized flag emojis
    return try {
        val xmlStr = bytes.decodeToString()
        val svgRootStart = xmlStr.indexOf("<svg")
        val svgRootEndInclusive = xmlStr.indexOf('>', startIndex = svgRootStart)
        val svgRootStartTag = xmlStr.substring(svgRootStart, svgRootEndInclusive + 1)
        val fixedSvgRootStartTag = svgRootStartTag.replace(
            Regex("""(width|height)="\w+""""),
            ""
        )
        val fixedXmlStr =
            xmlStr.replaceRange(svgRootStart, svgRootEndInclusive + 1, fixedSvgRootStartTag)
        fixedXmlStr.encodeToByteArray()
    } catch (e: Exception) {
        bytes
    }
}