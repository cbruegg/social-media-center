import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import com.mohamedrejeb.ksoup.entities.KsoupEntities
import com.mohamedrejeb.ksoup.html.parser.KsoupHtmlHandler
import com.mohamedrejeb.ksoup.html.parser.KsoupHtmlParser

// TODO Reply with this to stackoverflow post? https://stackoverflow.com/a/68241339/1502352

// TODO Add feature to automatically shorten links?
fun String.parseHtml(linkColor: Color, requiresHtmlDecode: Boolean = true): AnnotatedString {
    val string = AnnotatedString.Builder()

    val handler = KsoupHtmlHandler
        .Builder()
        .onOpenTag { name, attributes, isImplied ->
            when (name) {
                "p", "span" -> {}
                "br" -> string.append('\n')
                "a" -> {
                    string.pushStringAnnotation("link", attributes["href"] ?: "")
                    string.pushStyle(
                        SpanStyle(
                            color = linkColor,
                            textDecoration = TextDecoration.Underline
                        )
                    )
                }

                "b" -> string.pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                "u" -> string.pushStyle(SpanStyle(textDecoration = TextDecoration.Underline))
                "i" -> string.pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                "s" -> string.pushStyle(SpanStyle(textDecoration = TextDecoration.LineThrough))

                else -> println("onOpenTag: Unhandled span $name")
            }
        }
        .onCloseTag { name, isImplied ->
            when (name) {
                "p" -> string.append(' ')
                "span", "br" -> {}
                "b", "u", "i", "s" -> string.pop()
                "a" -> {
                    string.pop() // corresponds to pushStyle
                    string.pop() // corresponds to pushStringAnnotation
                }

                else -> println("onCloseTag: Unhandled span $name")
            }
        }
        .onText { text ->
            println("text=$text")
            string.append(text)

        }
        .build()


    val ksoupHtmlParser = KsoupHtmlParser(handler)

    // Pass the HTML to the parser (It is going to parse the HTML and call the callbacks)
    val html = if (requiresHtmlDecode) KsoupEntities.decodeHtml(this) else this
    ksoupHtmlParser.write(html)
    ksoupHtmlParser.end()

    return string.toAnnotatedString()
}