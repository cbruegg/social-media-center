import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

class Api(private val baseUrl: String, private val httpClient: HttpClient) {
    suspend fun getFeed(): ApiResponse<List<FeedItem>> = request {
        val isCorsRestricted = getPlatform().isCorsRestricted
        httpClient.get("$baseUrl/json?isCorsRestricted=$isCorsRestricted")
    }

    suspend fun getUnauthenticatedMastodonAccounts(): ApiResponse<List<MastodonUser>> = request {
        httpClient.get("$baseUrl/unauthenticated-mastodon-accounts")
    }

    private suspend inline fun <reified T> request(block: () -> HttpResponse): ApiResponse<T> {
        val response = try {
            block()
        } catch (e: Exception) {
            return ApiResponse.CaughtException(e)
        }

        return if (response.status == HttpStatusCode.Unauthorized) {
            ApiResponse.Unauthorized(response.bodyAsText())
        } else if (response.status.isSuccess()) {
            ApiResponse.Ok(response.body())
        } else {
            ApiResponse.ErrorStatus(response.bodyAsText(), response.status)
        }
    }
}

sealed interface ApiResponse<out T> {
    data class CaughtException(val exception: Exception) : ApiResponse<Nothing>
    data class ErrorStatus(val body: String, val statusCode: HttpStatusCode) : ApiResponse<Nothing>, RuntimeException()
    data class Unauthorized(val body: String) : ApiResponse<Nothing>
    data class Ok<T>(val body: T) : ApiResponse<T>
}

// TODO Move this stuff to common module

enum class PlatformId { Twitter, Mastodon, BlueSky }

@Serializable
data class FeedItem(
    val text: String,
    val author: String,
    val authorImageUrl: String?,
    val id: String,
    val published: Instant,
    val link: String?,
    val platform: PlatformId,
    val repost: FeedItem?
)

@Serializable
data class MastodonUser(val server: String, val username: String)

val MastodonUser.serverWithoutScheme get() = server.substringAfter("://")