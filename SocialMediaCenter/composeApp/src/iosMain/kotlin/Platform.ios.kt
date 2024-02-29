import kotlinx.datetime.Instant
import kotlinx.datetime.toNSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDateFormatterMediumStyle

object IOSPlatform: Platform {
    private val formatter = NSDateFormatter().apply {
        dateStyle = NSDateFormatterMediumStyle
        timeStyle = NSDateFormatterMediumStyle
    }
    override fun formatFeedItemDate(instant: Instant): String {
        return formatter.stringFromDate(instant.toNSDate())
    }
}

actual fun getPlatform(): Platform = IOSPlatform