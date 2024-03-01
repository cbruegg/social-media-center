package util

import PlatformId
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.UriHandler

interface ContextualUriHandler {
    fun openPostUri(uri: String, platformOfPost: PlatformId)
    fun openUri(uri: String)
}

val LocalContextualUriHandler = staticCompositionLocalOf<ContextualUriHandler> {
    error("ContextualUriHandler is missing!")
}

fun UriHandler.toContextualUriHandler(): ContextualUriHandler = object : ContextualUriHandler {
    override fun openPostUri(uri: String, platformOfPost: PlatformId) {
        this@toContextualUriHandler.openUri(uri)
    }

    override fun openUri(uri: String) {
        this@toContextualUriHandler.openUri(uri)
    }
}