import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toLocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

object JVMPlatform : Platform {
    private val dateTimeFormatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)

    override fun formatFeedItemDate(instant: Instant): String {
        val localDateTime =
            instant.toLocalDateTime(TimeZone.currentSystemDefault()).toJavaLocalDateTime()
        return dateTimeFormatter.format(localDateTime)
    }
}

actual fun getPlatform(): Platform = JVMPlatform