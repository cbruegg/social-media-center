import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

class Api(private val baseUrl: String, private val httpClient: HttpClient) {
    suspend fun getFeed(): Result<List<FeedItem>> = runCatching {
        httpClient
            .get("$baseUrl/json?isCorsRestricted=${getPlatform().isCorsRestricted}")
            .body()
    }

    suspend fun getUnauthenticatedMastodonAccounts(): Result<List<MastodonUser>> = runCatching {
        httpClient
            .get("$baseUrl/unauthenticated-mastodon-accounts")
            .body()
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

@Serializable
data class MastodonUser(val server: String, val username: String)