import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import persistence.Persistence
import java.io.File
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

object JVMPlatform : Platform {
    private val dateTimeFormatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)

    override val persistence: Persistence = JVMPersistence

    override fun formatFeedItemDate(instant: Instant): String {
        val localDateTime =
            instant.toLocalDateTime(TimeZone.currentSystemDefault()).toJavaLocalDateTime()
        return dateTimeFormatter.format(localDateTime)
    }
}

private object JVMPersistence : Persistence {
    private val dataDir = File(System.getProperty("user.home"), ".socialmediacenter")

    override fun <T : Any> save(key: String, value: T, serializer: KSerializer<T>) {
        dataDir.mkdirs()

        val serialized = Json.encodeToString(serializer, value)
        println("Storing $serialized for $key")
        File(dataDir, "$key.json").writeText(serialized)
    }

    override fun <T : Any> load(key: String, serializer: KSerializer<T>): T? {
        dataDir.mkdirs()

        val storedString = File(dataDir, "$key.json").takeIf { it.exists() }?.readText()
        val result = storedString?.let { Json.decodeFromString(serializer, it) }
        println("Got $result for $key")
        return result
    }
}

actual fun getPlatform(): Platform = JVMPlatform