import com.cbruegg.socialmediaserver.shared.FeedItem
import com.cbruegg.socialmediaserver.shared.MastodonUser
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess

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
