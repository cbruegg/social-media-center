@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE" )
package components

import FeedItem
import PlatformId
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.UrlAnnotation
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.em
import com.mohamedrejeb.ksoup.entities.KsoupEntities
import kotlinx.coroutines.launch
import org.kodein.emoji.EmojiFinder
import org.kodein.emoji.FoundEmoji
import org.kodein.emoji.codePointCharLength
import org.kodein.emoji.compose.EmojiService
import org.kodein.emoji.compose.EmojiUrl
import org.kodein.emoji.compose.LocalEmojiDownloader
import org.kodein.emoji.compose.PlatformEmojiPlaceholder
import org.kodein.emoji.compose.SVGImage
import org.kodein.emoji.compose.platformSizeRatio
import org.kodein.emoji.isCodePointInOneChar
import parseHtml
import util.LocalContextualUriHandler

// Inspired by https://stackoverflow.com/a/66235329/1502352

// TODO Upstream some of this
// TODO Flag emojis become wayyy too large https://twitter.com/melissakchan/status/1764719267994362031 https://twitter.com/bueti/status/1764747214407008578

@OptIn(ExperimentalTextApi::class)
@Composable
fun FeedItemContentText(feedItem: FeedItem) {
    val uriHandler = LocalContextualUriHandler.current
    val linkColor = MaterialTheme.colors.primary

    val annotatedString = if (feedItem.platform.hasHtmlText) {
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

    ClickableEmojiText(text = annotatedString) { url ->
        println(feedItem)
        if (!url.isNullOrEmpty())
            uriHandler.openUri(url)
        else
            uriHandler.openPostUri(feedItem.link, feedItem.platform)
    }
}


@Composable
private fun ClickableEmojiText(
    modifier: Modifier = Modifier,
    text: AnnotatedString,
    download: suspend (EmojiUrl) -> ByteArray? = LocalEmojiDownloader.current,
    onClick: (url: String?) -> Unit
) {
    val service = EmojiService.get() ?: return
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

    val all = remember(text) {
        service.finder.findEmoji(text)
            .map { found ->
                val sizeRatio = platformSizeRatio(found.emoji, textMeasurer, density)
                found to mutableStateOf(InlineTextContent(
                    placeholder = Placeholder(sizeRatio.width.em, sizeRatio.height.em, PlaceholderVerticalAlign.Center),
                    children = { PlatformEmojiPlaceholder(found.emoji) }
                ))
            }
            .toList()
    }

    LaunchedEffect(all) {
        all.forEach { (found, inlineTextContent) ->
            launch {
                val newInlineContent = createInlineTextContent(found, download)
                if (newInlineContent != null) {
                    inlineTextContent.value = newInlineContent
                }
            }
        }
    }

    val inlineContent = HashMap<String, InlineTextContent>()
    val annotatedString = buildAnnotatedString {
        var start = 0
        all.forEach { (found, inlineTextContent) ->
            append(text.subSequence(start, found.start))
            val inlineContentID = "emoji:${found.emoji}"
            inlineContent[inlineContentID] = inlineTextContent.value
            appendInlineContent(inlineContentID)
            start = found.end
        }
        // TODO Build substring for AnnotatedString
        append(text.subSequence(start, text.length))
    }

    // Can't use ClickableText as it doesn't support inlineContent
    ClickableTextWithInlineContent(
        modifier = modifier,
        text = annotatedString,
        inlineContent = inlineContent,
        onClick = onClick
    )
}

private fun EmojiFinder.findEmoji(str: AnnotatedString): Sequence<FoundEmoji> =
    sequence {
        var index = 0
        while (index < str.length) {
            val found = follow(str, index, root, index)
            if (found != null) {
                yield(found)
                index += found.length
            } else {
                index += codePointCharLength(codePointAt(str, index))
            }
        }
    }

private tailrec fun follow(string: AnnotatedString, index: Int, node: EmojiFinder.Node, start: Int): FoundEmoji? {
    if (index >= string.length) return node.emoji?.let { FoundEmoji(start, index, it) }
    val branches = node.branches ?: return node.emoji?.let { FoundEmoji(start, index, it) }
    val code = codePointAt(string, index)
    val next = branches[code] ?: return node.emoji?.let { FoundEmoji(start, index, it) }
    return follow(
        string = string,
        index = index + codePointCharLength(code),
        node = next,
        start = start,
    )
}

private fun codePointAt(string: AnnotatedString, index: Int): Int {
    if (isCodePointInOneChar(string[index].code)) {
        return string[index].code
    }

    val highSurrogate = string[index].code
    val lowSurrogate = string[index + 1].code
    require(highSurrogate and 0xFC00 == 0xD800) { error("Invalid high surrogate at $index") }
    require(lowSurrogate and 0xFC00 == 0xDC00) { error("Invalid low surrogate at $index") }
    val highBits = highSurrogate and 0x3FF
    val lowBits = lowSurrogate and 0x3FF
    return ((highBits shl 10) or lowBits) + 0x10000
}

@Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE" )
private suspend fun createInlineTextContent(found: FoundEmoji, download: suspend (EmojiUrl) -> ByteArray?): InlineTextContent? {
    val bytes = download(EmojiUrl.from(found.emoji, EmojiUrl.Type.SVG)) ?: return null
    val svg = SVGImage.create(bytes)
    return InlineTextContent(
        placeholder = Placeholder(1.em, 1.em / svg.sizeRatio(), PlaceholderVerticalAlign.Center),
        children = {
            SVGImage(svg, "${found.emoji.details.description} emoji", Modifier.fillMaxSize())
        }
    )
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