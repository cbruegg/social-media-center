import kotlinx.datetime.Instant

// TODO For web, we need to proxy images due to CORS
// TODO Max-width for feed, center i?

class WasmPlatform : Platform {
    override fun formatFeedItemDate(instant: Instant): String {
//        val localDateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
        return jsFormatDate(instant.toEpochMilliseconds().toString())
    }
}

private fun jsFormatDate(epochMillis: String): String =
    js("new Date(Number.parseInt(epochMillis)).toLocaleDateString(undefined, { year: 'numeric', month: 'long', day: 'numeric' })")

actual fun getPlatform(): Platform = WasmPlatform()