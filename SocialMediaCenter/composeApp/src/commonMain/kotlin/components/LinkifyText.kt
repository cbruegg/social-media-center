package components

import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import com.cbruegg.socialmediaserver.shared.FeedItem
import com.cbruegg.socialmediaserver.shared.PlatformId
import com.mohamedrejeb.ksoup.entities.KsoupEntities
import parseHtml
import util.LocalContextualUriHandler

@Composable
fun FeedItemContentText(feedItem: FeedItem) {
    if (feedItem.text.isEmpty()) return // nothing to display

    val linkColor = MaterialTheme.colors.primary
    val uriHandler = LocalContextualUriHandler.current
    val linkInteractionListener = LinkInteractionListener { link ->
        println("Clicked $feedItem with $link")
        val url = (link as? LinkAnnotation.Url)?.url ?: return@LinkInteractionListener
        val feedItemLink = feedItem.link
        if (url.isNotEmpty())
            uriHandler.openUri(url)
        else if (feedItemLink != null)
            uriHandler.openPostUri(feedItemLink, feedItem.platform)
        else
            println("No clickable content, ignoring click")
    }

    val annotatedString = if (feedItem.platform.hasHtmlText) {
        remember(feedItem, linkColor) {
            feedItem.text.parseHtml(
                linkColor,
                maxLinkLength = 100,
                linkInteractionListener = linkInteractionListener
            )
        }
    } else {
        val decoded = remember(feedItem.text) { KsoupEntities.decodeHtml(feedItem.text) }
        remember(decoded) {
            buildAnnotatedString {
                append(text = decoded)
                val urls = extractUrls(text = decoded)
                urls.forEach {
                    addStyle(
                        style = SpanStyle(
                            color = Color.Blue,
                            textDecoration = TextDecoration.Underline
                        ),
                        start = it.start,
                        end = it.end
                    )
                    addLink(
                        LinkAnnotation.Url(
                            it.url,
                            linkInteractionListener = linkInteractionListener
                        ), it.start, it.end
                    )
                }
            }
        }
    }

    Text(text = annotatedString)
}

private val PlatformId.hasHtmlText get() = this == PlatformId.Mastodon

private val urlPattern = Regex(
    """https?://(www\.)?[-a-zA-Z0-9@:%._+~#=]{1,256}\.[a-zA-Z0-9()]{1,6}\b([-a-zA-Z0-9()@:%_+.~#?&/=]*)""",
    setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE)
)

private fun extractUrls(text: String): List<LinkInfo> {
    return urlPattern.findAll(text)
        .map { match ->
            LinkInfo(
                url = match.value,
                start = match.range.first,
                end = match.range.last + 1
            )
        }
        .toList()
}

private data class LinkInfo(
    val url: String,
    val start: Int,
    val end: Int
)