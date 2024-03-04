import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.serialization.kotlinx.json.json
import org.kodein.emoji.compose.EmojiUrl

private val httpClient = HttpClient {
    install(ContentNegotiation) {
        json()
    }
    install(HttpRequestRetry)
}

val socialMediaCenterBaseUrl = "https://socialmediacenter.cbruegg.com"
val feedLoader = FeedLoader(socialMediaCenterBaseUrl, httpClient)

suspend fun downloadEmoji(emojiUrl: EmojiUrl): ByteArray = httpClient.get(emojiUrl.url).body()