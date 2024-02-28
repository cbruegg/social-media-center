import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

class FeedLoader(private val baseUrl: String, private val httpClient: HttpClient) {
    suspend fun fetch(): Result<List<FeedItem>> = runCatching {
        httpClient.get("$baseUrl/json").body<List<FeedItem>>()
    }
}

enum class PlatformId { Twitter, Mastodon, BlueSky }

@Serializable
data class FeedItem(
    val text: String,
    val author: String,
    val authorImageUrl: String?,
    val id: String,
    val published: Instant,
    val link: String,
    val platform: PlatformId
)
