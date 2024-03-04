
import io.ktor.http.URLBuilder
import io.ktor.http.appendEncodedPathSegments
import kotlinx.datetime.Instant

// TODO Emoji support

class WasmPlatform : Platform {
    override val isCorsRestricted: Boolean = true
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

private fun jsFormatDate(epochMillis: String): String =
    js("new Date(Number.parseInt(epochMillis)).toLocaleString(undefined, { year: 'numeric', month: 'long', day: 'numeric' })")

actual fun getPlatform(): Platform = WasmPlatform()