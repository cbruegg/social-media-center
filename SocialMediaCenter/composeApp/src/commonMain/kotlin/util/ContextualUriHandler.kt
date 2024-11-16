package util

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.UriHandler
import com.cbruegg.socialmediaserver.shared.PlatformId

interface ContextualUriHandler {
    fun openPostUri(uri: String, platformOfPost: PlatformId)
    fun openUri(uri: String, allowInApp: Boolean = true)
}

val LocalContextualUriHandler = staticCompositionLocalOf<ContextualUriHandler> {
    error("ContextualUriHandler is missing!")
}

fun UriHandler.toContextualUriHandler(inAppBrowserOpener: InAppBrowserOpener?): ContextualUriHandler =
    object : ContextualUriHandler {
        override fun openPostUri(uri: String, platformOfPost: PlatformId) {
            this@toContextualUriHandler.openUri(uri)
        }

        override fun openUri(uri: String, allowInApp: Boolean) {
            if (allowInApp && inAppBrowserOpener != null) {
                inAppBrowserOpener.openUriWithinInAppBrowser(uri)
            } else {
                this@toContextualUriHandler.openUri(uri)
            }
        }
    }