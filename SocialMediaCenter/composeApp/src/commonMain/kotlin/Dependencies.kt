import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.AuthProvider
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.kodein.emoji.compose.EmojiUrl
import security.AuthTokenRepository

fun createHttpClient(authTokenRepository: AuthTokenRepository) = HttpClient {
    install(Auth) {
        providers += object : AuthProvider {
            @Deprecated(
                "Please use sendWithoutRequest function instead",
                level = DeprecationLevel.ERROR
            )
            override val sendWithoutRequest: Boolean
                get() = error("Deprecated")

            override suspend fun addRequestHeaders(
                request: HttpRequestBuilder,
                authHeader: HttpAuthHeader?
            ) {

                val token = authTokenRepository.token
                if (token != null) {
                    request.headers.append(HttpHeaders.Authorization, "Bearer $token")
                }
            }

            override fun isApplicable(auth: HttpAuthHeader): Boolean {
                return true
            }

            override fun sendWithoutRequest(request: HttpRequestBuilder): Boolean {
                return true // might cause header leaks, but this is a low security app
            }
        }
    }
    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
        })
    }
    install(HttpRequestRetry)
}

//val socialMediaCenterBaseUrl = "http://localhost:8000"
val socialMediaCenterBaseUrl = "https://socialmediacenter.cbruegg.com"

fun createAuthTokenRepository() = AuthTokenRepository(getPlatform().persistence)

fun createApi(httpClient: HttpClient) = Api(
    socialMediaCenterBaseUrl,
    httpClient
)

suspend fun downloadEmoji(httpClient: HttpClient, emojiUrl: EmojiUrl): ByteArray {
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