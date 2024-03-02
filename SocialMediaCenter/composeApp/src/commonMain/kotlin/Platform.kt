
import androidx.compose.ui.platform.ClipboardManager
import kotlinx.datetime.Instant
import persistence.Persistence
import util.ContextualUriHandler

interface Platform {
    fun createUriHandler(clipboardManager: ClipboardManager): ContextualUriHandler? = null // TODO Implement for Android
    val persistence: Persistence? get() = null
    fun formatFeedItemDate(instant: Instant): String
}

expect fun getPlatform(): Platform