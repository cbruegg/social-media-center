
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.viewmodel.compose.viewModel
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.AuthProvider
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import org.kodein.emoji.compose.EmojiUrl
import security.ServerConfig
import security.tokenAsHttpHeader
import util.ContextualUriHandler
import util.LocalInAppBrowserOpener
import util.toContextualUriHandler

private fun createApiHttpClient(authTokenRepository: ServerConfig) = HttpClient {
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
                val tokenAsHttpHeader = authTokenRepository.tokenAsHttpHeader
                if (tokenAsHttpHeader != null) {
                    val (key, headerValue) = tokenAsHttpHeader
                    println("headerValue=$headerValue")
                    request.headers[key] = headerValue
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

private fun createGenericHttpClient() = HttpClient {
    install(HttpRequestRetry)
}

private fun createServerConfig() = ServerConfig(getPlatform().persistence)

private fun createApi(baseUrl: String, httpClient: HttpClient) = Api(
    baseUrl,
    httpClient
)

private suspend fun downloadEmoji(httpClient: HttpClient, emojiUrl: EmojiUrl): ByteArray {
    return httpClient.get(emojiUrl.url).body<ByteArray>()
}

private fun createEmojiDownloader(httpClient: HttpClient): suspend (EmojiUrl) -> ByteArray =
    { emojiUrl -> downloadEmoji(httpClient, emojiUrl) }

class AppDependencies(
    val uriHandler: ContextualUriHandler,
    val serverConfig: ServerConfig,
    val downloadEmojis: suspend (EmojiUrl) -> ByteArray,
    val viewModel: AppViewModel
)

@Composable
fun getAppDependencies(): AppDependencies {
    val clipboardManager = LocalClipboardManager.current
    val localUriHandler = LocalUriHandler.current
    val inAppBrowserOpener = LocalInAppBrowserOpener.current
    val uriHandler = remember(clipboardManager, localUriHandler, inAppBrowserOpener) {
        getPlatform().createUriHandler(
            clipboardManager,
            localUriHandler,
            inAppBrowserOpener,
        ) ?: localUriHandler.toContextualUriHandler(inAppBrowserOpener)
    }
    val serverConfig = remember { createServerConfig() }
    val downloadEmoji = remember { createEmojiDownloader(createGenericHttpClient()) }
    val viewModel = viewModel {
        val apiFlow = serverConfig.baseUrl.map { baseUrl ->
            baseUrl?.let { createApi(baseUrl = it, createApiHttpClient(serverConfig)) }
        }
        AppViewModel(apiFlow, serverConfig)
    }

    return AppDependencies(
        uriHandler, serverConfig, downloadEmoji, viewModel
    )
}
