package components

import FeedItem
import PlatformId
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.UrlAnnotation
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import com.mohamedrejeb.ksoup.entities.KsoupEntities
import parseHtml
import util.LocalContextualUriHandler

// Inspired by https://stackoverflow.com/a/66235329/1502352

@OptIn(ExperimentalTextApi::class)
@Composable
fun FeedItemContentText(feedItem: FeedItem) {
    val uriHandler = LocalContextualUriHandler.current
    val linkColor = MaterialTheme.colors.primary

    val annotatedString =  if (feedItem.platform.hasHtmlText) {
        remember(feedItem, linkColor) {
            feedItem.text.parseHtml(linkColor, maxLinkLength = 100)
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

    ClickableText(annotatedString, style = LocalTextStyle.current) { offset ->
        val url = annotatedString.getUrlAnnotations(start = offset, end = offset)
            .firstOrNull()?.item?.url
        println(feedItem)
        if (!url.isNullOrEmpty())
            uriHandler.openUri(url)
        else
            uriHandler.openPostUri(feedItem.link, feedItem.platform)
    }
}

private val PlatformId.hasHtmlText get() = this == PlatformId.Mastodon

@OptIn(ExperimentalTextApi::class)
@Composable
fun LinkifiedText(
    text: String,
    defaultClickHandler: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uriHandler = LocalContextualUriHandler.current
    val annotatedString = remember(text) {
        buildAnnotatedString {
            append(text)
            extractUrls(text).forEach {
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

    ClickableText(
        modifier = modifier,
        text = annotatedString,
        style = LocalTextStyle.current
    ) { offset ->
        val url = annotatedString.getUrlAnnotations(start = offset, end = offset)
            .firstOrNull()?.item?.url
        if (!url.isNullOrEmpty())
            uriHandler.openUri(url)
        else
            defaultClickHandler()
    }
}

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