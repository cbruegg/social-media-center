import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.UrlAnnotation
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import com.mohamedrejeb.ksoup.entities.KsoupEntities
import com.mohamedrejeb.ksoup.html.parser.KsoupHtmlHandler
import com.mohamedrejeb.ksoup.html.parser.KsoupHtmlParser

@ExperimentalTextApi
fun String.parseHtml(
    linkColor: Color,
    maxLinkLength: Int = Int.MAX_VALUE,
    isSkyBridgePost: Boolean,
    requiresHtmlDecode: Boolean = true
): AnnotatedString {
    // Can probably be replaced with AnnotatedString.fromHtml once
    // Compose Multiplatform 1.7 is out: https://developer.android.com/reference/kotlin/androidx/compose/ui/text/AnnotatedString.Companion#(androidx.compose.ui.text.AnnotatedString.Companion).fromHtml(kotlin.String,androidx.compose.ui.text.SpanStyle,androidx.compose.ui.text.SpanStyle,androidx.compose.ui.text.SpanStyle,androidx.compose.ui.text.SpanStyle,androidx.compose.ui.text.LinkInteractionListener)
    // Supported HTML elements: https://developer.android.com/guide/topics/resources/string-resource#StylingWithHTML
    val string = AnnotatedString.Builder()

    var visitedLinkUrl: String? = null // set in onOpenTag, cleared in onCloseTag
    var visitedLinkText = "" // appended to in text handler if visitedLinkUrl != null

    val handler = KsoupHtmlHandler
        .Builder()
        .onOpenTag { name, attributes, _ ->
            when (name) {
                "p", "span" -> {}
                "br" -> string.append('\n')
                "a" -> {
                    val link = attributes["href"]?.let { href ->
                        if (!isSkyBridgePost) return@let href

                        val profile = href.substringAfter("@@")
                        if ('/' !in profile && profile.isNotEmpty()) {
                            // Turn https://skybridge.cbruegg.com/@@ntvde.bsky.social (SkyBridge bug)
                            // into https://bsky.app/profile/ntvde.bsky.social (fixed)
                            "https://bsky.app/profile/$profile"
                        } else {
                            href
                        }
                    }

                    visitedLinkUrl = link

                    // replaceable with pushStringAnnotation if API goes away due to experimental status
                    string.pushUrlAnnotation(UrlAnnotation(link ?: ""))
                    string.pushStyle(
                        SpanStyle(
                            color = linkColor,
                            textDecoration = TextDecoration.Underline
                        )
                    )
                }

                "b" -> string.pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                "u" -> string.pushStyle(SpanStyle(textDecoration = TextDecoration.Underline))
                "i", "em" -> string.pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                "s" -> string.pushStyle(SpanStyle(textDecoration = TextDecoration.LineThrough))
                "blockquote" -> string.pushStyle(SpanStyle(fontStyle = FontStyle.Italic, background = Color.LightGray))

                else -> println("onOpenTag: Unhandled span $name")
            }
        }
        .onCloseTag { name, _ ->
            when (name) {
                "p" -> string.append(' ')
                "span", "br" -> {}
                "b", "u", "i", "em", "s", "blockquote" -> string.pop()
                "a" -> {
                    // don't shorten links where the text is not the URL itself
                    val textIsUrl = visitedLinkUrl?.startsWith(visitedLinkText) == true
                    if (textIsUrl) {
                        string.append(visitedLinkText.ellipsize(maxLinkLength))
                    } else {
                        string.append(visitedLinkText)
                    }

                    string.pop() // corresponds to pushStyle
                    string.pop() // corresponds to pushUrlAnnotation
                    visitedLinkUrl = null // reset
                    visitedLinkText = "" // reset
                }

                else -> println("onCloseTag: Unhandled span $name")
            }
        }
        .onText { text ->
            if (visitedLinkUrl != null) {
                // we are currently visiting a link. Capture all parts of the link text that
                // may be split into multiple spans by Mastodon into one combined String
                visitedLinkText += text
            } else {
                string.append(text)
            }



        }
        .build()


    val ksoupHtmlParser = KsoupHtmlParser(handler)

    // Pass the HTML to the parser (It is going to parse the HTML and call the callbacks)
    val html = if (requiresHtmlDecode) KsoupEntities.decodeHtml(this) else this
    ksoupHtmlParser.write(html)
    ksoupHtmlParser.end()

    return string.toAnnotatedString()
}

/**
 * @param maxLength Must be >= 2 to display at least one character of the original string plus
 *                  an ellipsis character.
 */
private fun String.ellipsize(maxLength: Int): String {
    check(maxLength >= 2) { "maxLength must be >= 2" }
    if (length <= maxLength) return this

    return substring(0, maxLength - 1) + "…"
}
