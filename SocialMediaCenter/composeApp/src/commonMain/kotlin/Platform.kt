
import kotlinx.datetime.Instant
import persistence.Persistence
import util.ContextualUriHandler

interface Platform {
    val uriHandler: ContextualUriHandler? get() = null // TODO Implement for Android
    val persistence: Persistence? get() = null
    fun formatFeedItemDate(instant: Instant): String
}

expect fun getPlatform(): Platform