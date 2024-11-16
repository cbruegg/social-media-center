
import io.ktor.http.URLBuilder
import io.ktor.http.appendEncodedPathSegments
import kotlinx.browser.localStorage
import kotlinx.datetime.Instant
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import org.w3c.dom.get
import org.w3c.dom.set
import persistence.Persistence

class WasmPlatform : Platform {
    override val isCorsRestricted: Boolean = true

    override val persistence: Persistence = WasmPersistence
    override fun formatFeedItemDate(instant: Instant): String =
        jsFormatDate(instant.toEpochMilliseconds().toString())

    override fun corsProxiedUrlToAbsoluteUrl(socialMediaCenterBaseUrl: String, authorImageUrl: String): String {
        return if (authorImageUrl.startsWith('/')) {
            URLBuilder(socialMediaCenterBaseUrl).appendEncodedPathSegments(authorImageUrl).buildString()
        } else {
            authorImageUrl
        }
    }
}

private object WasmPersistence : Persistence {
    private const val KEY_PREFIX = "WasmPersistence_"

    override fun <T : Any> save(key: String, value: T, serializer: KSerializer<T>) {
        val serialized = Json.encodeToString(serializer, value)
        println("Storing $serialized for $key")
        localStorage[KEY_PREFIX + key] = serialized
    }

    override fun <T : Any> load(key: String, serializer: KSerializer<T>): T? {
        val storedString = localStorage[KEY_PREFIX + key]
        val result = storedString?.let { Json.decodeFromString(serializer, it) }
        println("Got $result for $key")
        return result
    }
}

private fun jsFormatDate(epochMillis: String): String =
    js("new Date(Number.parseInt(epochMillis)).toLocaleString(undefined, { year: 'numeric', month: 'long', day: 'numeric', hour: 'numeric', minute: 'numeric' })")

actual fun getPlatform(): Platform = WasmPlatform()