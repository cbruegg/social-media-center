import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import org.kodein.emoji.compose.EmojiUrl

private val httpClient = HttpClient {
    install(ContentNegotiation) {
        json()
    }
    install(HttpRequestRetry)
    install(DefaultRequest) {
        // This might fix connectivity issues due to CloudFlare WAF
        headers.append(
            HttpHeaders.UserAgent,
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.2.1 Safari/605.1.15"
        )
    }
}

val socialMediaCenterBaseUrl = "https://socialmediacenter.cbruegg.com"
val feedLoader = FeedLoader(socialMediaCenterBaseUrl, httpClient)

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