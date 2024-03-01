import androidx.compose.ui.platform.UriHandler
import kotlinx.datetime.Instant

interface Platform {
    val uriHandler: UriHandler? get() = null // TODO Implement for Android
    fun formatFeedItemDate(instant: Instant): String
}

expect fun getPlatform(): Platform