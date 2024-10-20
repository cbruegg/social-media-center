package components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.UrlAnnotation
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import com.cbruegg.socialmediaserver.shared.FeedItem
import com.cbruegg.socialmediaserver.shared.PlatformId
import com.mohamedrejeb.ksoup.entities.KsoupEntities
import org.kodein.emoji.compose.WithPlatformEmoji
import parseHtml
import util.LocalContextualUriHandler

// Inspired by https://stackoverflow.com/a/66235329/1502352

// TODO Upstream some of this

@OptIn(ExperimentalTextApi::class)
@Composable
fun FeedItemContentText(feedItem: FeedItem) {
    if (feedItem.text.isEmpty()) return // nothing to display

    val uriHandler = LocalContextualUriHandler.current
    val linkColor = MaterialTheme.colors.primary

    val annotatedString = if (feedItem.platform.hasHtmlText) {
        remember(feedItem, linkColor) {
            feedItem.text.parseHtml(
                linkColor,
                maxLinkLength = 100,
                isSkyBridgePost = feedItem.isSkyBridgePost
            )
        }
    } else {
        val decoded = remember(feedItem.text) { KsoupEntities.decodeHtml(feedItem.text) }
        remember(decoded) {
            buildAnnotatedString {
                append(text = decoded)
                extractUrls(text = decoded).forEach {
                    addStyle(
                        style = SpanStyle(
                            color = Color.Blue,
                            textDecoration = TextDecoration.Underline
                        ),
                        start = it.start,
                        end = it.end
                    )
                    addUrlAnnotation(UrlAnnotation(it.url), it.start, it.end)
                }
            }
        }
    }

    ClickableEmojiText(text = annotatedString) { url ->
        println("Clicked $feedItem")
        val feedItemLink = feedItem.link
        if (!url.isNullOrEmpty())
            uriHandler.openUri(url)
        else if (feedItemLink != null)
            uriHandler.openPostUri(feedItemLink, feedItem.platform, feedItem.isSkyBridgePost)
        else
            println("No clickable content, ignoring click")
    }
}


@Composable
private fun ClickableEmojiText(
    modifier: Modifier = Modifier,
    text: AnnotatedString,
    onClick: (url: String?) -> Unit
) {
    WithPlatformEmoji(text) { annotatedString, inlineContent ->
        // Can't use ClickableText as it doesn't support inlineContent
        ClickableTextWithInlineContent(
            modifier = modifier,
            text = annotatedString,
            inlineContent = inlineContent,
            onClick = onClick
        )
    }
}

@OptIn(ExperimentalTextApi::class)
@Composable
private fun ClickableTextWithInlineContent(
    modifier: Modifier = Modifier,
    text: AnnotatedString,
    inlineContent: Map<String, InlineTextContent>,
    onClick: (url: String?) -> Unit
) {
    val layoutResult = remember {
        mutableStateOf<TextLayoutResult?>(null)
    }
    Text(
        text = text,
        onTextLayout = { layoutResult.value = it },
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures { offsetPosition ->
                    layoutResult.value?.let {
                        val position = it.getOffsetForPosition(offsetPosition)
                        val annotation =
                            text.getUrlAnnotations(position, position).firstOrNull()
                        onClick(annotation?.item?.url)
                    }
                }
            },
        inlineContent = inlineContent
    )
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