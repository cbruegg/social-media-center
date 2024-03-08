import androidx.compose.ui.platform.ClipboardManager
import kotlinx.datetime.Instant
import persistence.Persistence
import util.ContextualUriHandler

interface Platform {
    fun createUriHandler(clipboardManager: ClipboardManager): ContextualUriHandler? =
        null // TODO Implement for Android

    val persistence: Persistence? get() = null
    val isCorsRestricted: Boolean get() = false
    val nativelySupportsEmojiRendering: Boolean get() = true
    fun formatFeedItemDate(instant: Instant): String
    fun corsProxiedUrlToAbsoluteUrl(socialMediaCenterBaseUrl: String, authorImageUrl: String): String {
        check(!isCorsRestricted) { "This method must be implemented if CORS is restricted" }
        return authorImageUrl
    }
}

expect fun getPlatform(): Platform