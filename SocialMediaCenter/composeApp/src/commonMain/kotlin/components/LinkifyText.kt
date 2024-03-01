package components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import util.LocalContextualUriHandler

// Inspired by https://stackoverflow.com/a/66235329/1502352

@Composable
fun LinkifiedText(
    text: String,
    defaultClickHandler: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uriHandler = LocalContextualUriHandler.current
    val layoutResult = remember {
        mutableStateOf<TextLayoutResult?>(null)
    }
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
                addStringAnnotation(
                    tag = "URL",
                    annotation = it.url,
                    start = it.start,
                    end = it.end
                )
            }
        }
    }
    // TODO: Theoretically, this should also just be ClickableText instead of Text with pointerInput modifier. or not? maybe this can support ripple...
    Text(
        text = annotatedString,
        onTextLayout = { layoutResult.value = it },
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures { offsetPosition ->
                    layoutResult.value?.let {
                        val position = it.getOffsetForPosition(offsetPosition)
                        val annotation =
                            annotatedString.getStringAnnotations(position, position).firstOrNull()
                        if (annotation != null) {
                            if (annotation.tag == "URL") {
                                uriHandler.openUri(annotation.item)
                            }
                        } else {
                            defaultClickHandler()
                        }
                    }
                }
            }
    )
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