
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.UriHandler
import io.ktor.http.URLBuilder
import io.ktor.http.appendEncodedPathSegments
import kotlinx.datetime.Instant

class WasmPlatform : Platform {
    override val isCorsRestricted: Boolean = true
    override val nativelySupportsEmojiRendering: Boolean = false
    override fun formatFeedItemDate(instant: Instant): String =
        jsFormatDate(instant.toEpochMilliseconds().toString())

    override fun corsProxiedUrlToAbsoluteUrl(socialMediaCenterBaseUrl: String, authorImageUrl: String): String {
        return if (authorImageUrl.startsWith('/')) {
            URLBuilder(socialMediaCenterBaseUrl).appendEncodedPathSegments(authorImageUrl).buildString()
        } else {
            authorImageUrl
        }
    }

    override fun createUriHandler(
        clipboardManager: ClipboardManager,
        defaultUriHandler: UriHandler,
        socialMediaCenterBaseUrl: String,
    ) = WebUriHandler(defaultUriHandler, socialMediaCenterBaseUrl)
}

private fun jsFormatDate(epochMillis: String): String =
    js("new Date(Number.parseInt(epochMillis)).toLocaleString(undefined, { year: 'numeric', month: 'long', day: 'numeric', hour: 'numeric', minute: 'numeric' })")

actual fun getPlatform(): Platform = WasmPlatform()