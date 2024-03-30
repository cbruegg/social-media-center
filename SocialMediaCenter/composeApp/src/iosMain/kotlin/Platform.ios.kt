import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.AnnotatedString
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
    override fun openUri(uri: String) {
        println("Opening with standard handler: $uri")
        if (inAppBrowserOpener != null) {
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

    override fun openPostUri(uri: String, platformOfPost: PlatformId) {
        when (platformOfPost) {
            PlatformId.BlueSky -> openUri(uri)

            PlatformId.Twitter -> {
                val postId = Url(uri).pathSegments.lastOrNull { it.isNumeric() }
                if (postId != null && tryOpenUri("twitter://status?id=${postId}")) return
                if (tryOpenUri("opener://x-callback-url/show-options?url=${uri.encodeURLPath()}")) return

                openUri(uri)
            }

            PlatformId.Mastodon -> {
                // For Mastodon, opening posts is not always reliable. Copy text to clipboard
                // to let user paste it into the search bar of their Mastodon app if neede
                clipboardManager.setText(AnnotatedString(uri))

                val postId = Url(uri).pathSegments.lastOrNull { it.isNumeric() }
                if (false && postId != null) {
                    // Disabled for now as the postId is not the postId as known by the user's
                    // own Mastodon server. This is because we don't use proper Mastodon APIs,
                    // but get post data from the RSS feeds of each followed user's server.
                    // The post IDs on those servers are not the same as the post IDs defined by
                    // the user's server.
                    // Unfortunately, the official Mastodon iOS app currently does not support
                    // opening arbitrary Mastodon post URLs either:
                    // https://github.com/mastodon/mastodon-ios/issues/968
                    // We could probably get the post ID by not getting post data through RSS,
                    // but always through the user's own server instead:
                    // https://stackoverflow.com/a/76288210/1502352

                    if (tryOpenUri("mastodon://status/${postId}")) return
                }

                // See SceneDelegate.swift in https://github.dev/TheBLVD/mammoth
                if (tryOpenUri("mammoth://" + uri.removePrefix("https://"))) return

                if (tryOpenUri("opener://x-callback-url/show-options?url=${uri.encodeURLPath()}")) return

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

