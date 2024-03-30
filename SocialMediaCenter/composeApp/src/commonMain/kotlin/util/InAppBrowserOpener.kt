package util

import androidx.compose.runtime.compositionLocalOf

// TODO Implement on Android
fun interface InAppBrowserOpener {
    fun openUriWithinInAppBrowser(uri: String)
}

val LocalInAppBrowserOpener = compositionLocalOf<InAppBrowserOpener?> { null }