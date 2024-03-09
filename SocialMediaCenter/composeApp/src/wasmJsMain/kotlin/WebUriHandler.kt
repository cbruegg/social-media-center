import androidx.compose.ui.platform.UriHandler
import util.ContextualUriHandler

class WebUriHandler(
    private val defaultUriHandler: UriHandler,
    private val socialMediaCenterBaseUrl: String
) : ContextualUriHandler {
    override fun openPostUri(uri: String, platformOfPost: PlatformId) {
        if (platformOfPost == PlatformId.Mastodon) {
            // Instead of opening the author's Mastodon server, let's open the user's.
            // SocialMediaServer has a feature for this.
            defaultUriHandler.openUri("$socialMediaCenterBaseUrl/mastodon-status?statusUrl=$uri")
        } else {
            openUri(uri)
        }
    }

    override fun openUri(uri: String) {
        defaultUriHandler.openUri(uri)
    }
}