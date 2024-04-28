import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.AnnotatedString
import com.cbruegg.socialmediaserver.shared.PlatformId
import io.ktor.http.Url
import io.ktor.http.encodeURLPath
import kotlinx.datetime.Instant
import kotlinx.datetime.toNSDate
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import persistence.Persistence
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDateFormatterMediumStyle
import platform.Foundation.NSURL
import platform.Foundation.NSUserDefaults
import platform.UIKit.UIApplication
import util.ContextualUriHandler
import util.InAppBrowserOpener
import util.isNumeric

object IOSPlatform : Platform {
    private val formatter = NSDateFormatter().apply {
        dateStyle = NSDateFormatterMediumStyle
        timeStyle = NSDateFormatterMediumStyle
    }

    override val persistence: Persistence = IOSPersistence

    override fun formatFeedItemDate(instant: Instant): String {
        return formatter.stringFromDate(instant.toNSDate())
    }

    override fun createUriHandler(
        clipboardManager: ClipboardManager,
        defaultUriHandler: UriHandler,
        inAppBrowserOpener: InAppBrowserOpener?,
        socialMediaCenterBaseUrl: String
    ): ContextualUriHandler =
        IOSUriHandler(clipboardManager, inAppBrowserOpener)
}

private class IOSUriHandler(
    private val clipboardManager: ClipboardManager,
    private val inAppBrowserOpener: InAppBrowserOpener?
) : ContextualUriHandler {
    override fun openUri(uri: String, allowInApp: Boolean) {
        println("Opening with standard handler: $uri")
        if (allowInApp && inAppBrowserOpener != null) {
            inAppBrowserOpener.openUriWithinInAppBrowser(uri)
        } else {
            UIApplication.sharedApplication.openURL(
                NSURL(string = uri),
                emptyMap<Any?, Any>(),
                null
            )
        }
    }

    private fun tryOpenUri(uri: String): Boolean {
        val nsUrl = NSURL(string = uri)
        if (!UIApplication.sharedApplication.canOpenURL(nsUrl)) return false

        println("Opening URL: ${nsUrl.absoluteString}")
        UIApplication.sharedApplication.openURL(nsUrl, emptyMap<Any?, Any>(), null)
        return true
    }

    override fun openPostUri(uri: String, platformOfPost: PlatformId, isSkyBridgePost: Boolean) {
        when (platformOfPost) {
            PlatformId.Twitter -> {
                val postId = Url(uri).pathSegments.lastOrNull { it.isNumeric() }
                if (postId != null && tryOpenUri("twitter://status?id=${postId}")) return
                if (tryOpenUri("opener://x-callback-url/show-options?url=${uri.encodeURLPath()}")) return

                openUri(uri)
            }

            PlatformId.Mastodon -> {
                if (isSkyBridgePost) {
                    println("isSkyBridgePost=true -> don't use Mastodon app to open Skybridge post")
                    openUri(uri, allowInApp = false)
                    return
                }

                // For Mastodon, opening posts is not always reliable. Copy text to clipboard
                // to let user paste it into the search bar of their Mastodon app if needed
                clipboardManager.setText(AnnotatedString(uri))

                // Try official Mastodon app first
                val postId = Url(uri).pathSegments.lastOrNull { it.isNotEmpty() && it.isNumeric() }
                if (postId != null && tryOpenUri("mastodon://status/${postId}")) return

                // Try Mammoth next
                // See SceneDelegate.swift in https://github.dev/TheBLVD/mammoth
                if (tryOpenUri("mammoth://" + uri.removePrefix("https://"))) return

                // Try Opener for any other Mastodon apps
                if (tryOpenUri("opener://x-callback-url/show-options?url=${uri.encodeURLPath()}")) return

                // If all else fails, delegate to system
                openUri(uri)
            }
        }
    }
}

private object IOSPersistence : Persistence {
    private const val KEY_PREFIX = "IOSPersistence_"

    override fun <T : Any> save(key: String, value: T, serializer: KSerializer<T>) {
        val serialized = Json.encodeToString(serializer, value)
        println("Storing $serialized for $key")
        NSUserDefaults.standardUserDefaults.setObject(serialized, KEY_PREFIX + key)
    }

    override fun <T : Any> load(key: String, serializer: KSerializer<T>): T? {
        val storedString = NSUserDefaults.standardUserDefaults.stringForKey(KEY_PREFIX + key)
        val result = storedString?.let { Json.decodeFromString(serializer, it) }
        println("Got $result for $key")
        return result
    }
}

actual fun getPlatform(): Platform = IOSPlatform

