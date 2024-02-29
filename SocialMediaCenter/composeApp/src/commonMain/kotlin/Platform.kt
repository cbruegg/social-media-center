import kotlinx.datetime.Instant

interface Platform {
    fun formatFeedItemDate(instant: Instant): String
}

expect fun getPlatform(): Platform