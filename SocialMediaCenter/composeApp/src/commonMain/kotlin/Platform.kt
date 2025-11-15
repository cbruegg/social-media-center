import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.UriHandler
import persistence.Persistence
import util.ContextualUriHandler
import util.InAppBrowserOpener
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
interface Platform {
    fun createUriHandler(
        clipboardManager: ClipboardManager,
        defaultUriHandler: UriHandler,
        inAppBrowserOpener: InAppBrowserOpener?
    ): ContextualUriHandler? =
        null // TODO Implement for Android

    val persistence: Persistence
    val isCorsRestricted: Boolean get() = false
    fun formatFeedItemDate(instant: Instant): String
    fun corsProxiedUrlToAbsoluteUrl(socialMediaCenterBaseUrl: String, authorImageUrl: String): String {
        check(!isCorsRestricted) { "This method must be implemented if CORS is restricted" }
        return authorImageUrl
    }
}

expect fun getPlatform(): Platform