
import android.content.Context
import com.cbruegg.socialmediacenter.frontend.app
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import persistence.Persistence
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import androidx.core.content.edit

@OptIn(ExperimentalTime::class)
object AndroidPlatform : Platform {
    private val dateTimeFormatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
    override val persistence: Persistence = AndroidPersistence(app)

    override fun formatFeedItemDate(instant: Instant): String {
        val localDateTime =
            instant.toLocalDateTime(TimeZone.currentSystemDefault()).toJavaLocalDateTime()
        return dateTimeFormatter.format(localDateTime)
    }
}

private class AndroidPersistence(context: Context): Persistence {
    private val prefs = context.getSharedPreferences("persistence", Context.MODE_PRIVATE)

    override fun <T : Any> save(key: String, value: T, serializer: KSerializer<T>) {
        val serialized = Json.encodeToString(serializer, value)
        prefs.edit { putString(key, serialized) }
    }

    override fun <T : Any> load(key: String, serializer: KSerializer<T>): T? {
        return prefs.getString(key, null)?.let { Json.decodeFromString(serializer, it) }
    }

}

actual fun getPlatform(): Platform = AndroidPlatform