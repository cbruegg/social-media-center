import androidx.compose.ui.platform.UriHandler
import kotlinx.datetime.Instant
import persistence.Persistence

interface Platform {
    val uriHandler: UriHandler? get() = null // TODO Implement for Android
    val persistence: Persistence? get() = null
    fun formatFeedItemDate(instant: Instant): String
}

expect fun getPlatform(): Platform