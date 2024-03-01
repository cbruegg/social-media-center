
import androidx.compose.ui.platform.UriHandler
import io.ktor.http.encodeURLPath
import kotlinx.datetime.Instant
import kotlinx.datetime.toNSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDateFormatterMediumStyle
import platform.Foundation.NSURL
import platform.UIKit.UIApplication

object IOSPlatform : Platform {
    private val formatter = NSDateFormatter().apply {
        dateStyle = NSDateFormatterMediumStyle
        timeStyle = NSDateFormatterMediumStyle
    }

    override fun formatFeedItemDate(instant: Instant): String {
        return formatter.stringFromDate(instant.toNSDate())
    }

    override val uriHandler = IOSUriHandler
}

object IOSUriHandler : UriHandler {
    override fun openUri(uri: String) {
        val sharedApplication = UIApplication.sharedApplication

        // TODO Optimize this implementation. Try apps in the following order of preference,
        //      then cache the result:
        //      Twitter (for Twitter posts); some Mastodon app (for Mastodon posts); Opener; Browser

        val openerUri = "opener://x-callback-url/show-options?url=${uri.encodeURLPath()}"
        val openerUrl = NSURL(string = openerUri)
        if (sharedApplication.canOpenURL(openerUrl)) {
            println("Opening with opener: $openerUri")
            sharedApplication.openURL(openerUrl)
        } else {
            println("Opening without opener: $uri")
            sharedApplication.openURL(NSURL(string = uri))
        }
    }
}

actual fun getPlatform(): Platform = IOSPlatform

