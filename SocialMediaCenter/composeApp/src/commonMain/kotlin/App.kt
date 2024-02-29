import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.Card
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.ui.tooling.preview.Preview

// TODO: Configurable server
// TODO: Remember timeline state (across devices?)
// TODO: Open corresponding app instead of browser
// TODO: (Configurable?) maximum post height (Mastodon posts can be very long)
// TODO: Refresh button

@OptIn(ExperimentalResourceApi::class)
@Composable
@Preview
fun App() {
    MaterialTheme {
        var feedItemResult: Result<List<FeedItem>>? by remember { mutableStateOf(null) }

        LaunchedEffect(Unit) {
            launch {
                feedItemResult = feedLoader.fetch()
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            feedItemResult?.let { feedItemResult ->
                feedItemResult.fold(
                    onSuccess = { feedItems ->
                        LazyColumn {
                            items(
                                feedItems.size,
                                key = { feedItems[it].id },
                                itemContent = { FeedItemRow(feedItems[it]) }
                            )
                        }
                    },
                    onFailure = { loadError ->
                        Text("Loading error! ${loadError.message}")
                    }
                )
            } ?: run {
                CircularProgressIndicator()
            }
        }
    }
}

@OptIn(ExperimentalTextApi::class)
@Composable
private fun FeedItemRow(feedItem: FeedItem, modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    val linkColor = MaterialTheme.colors.primary

    val formattedDate = remember(feedItem) { getPlatform().formatFeedItemDate(feedItem.published) }
    val annotatedString = remember(feedItem, linkColor) {
        feedItem.text.parseHtml(linkColor, maxLinkLength = 100)
    }

    Card(modifier = modifier
        .fillMaxWidth()
        .clickable { uriHandler.openUri(feedItem.link) }
    ) {
        Row(Modifier.padding(8.dp)) {
            AsyncImage(
                model = feedItem.authorImageUrl,
                contentDescription = feedItem.author,
                modifier = Modifier
                    .padding(8.dp)
                    .size(48.dp)
                    .clip(CircleShape)
                    .border(2.dp, Color.Gray, CircleShape)
            )
            Column {
                Text(feedItem.author, fontWeight = FontWeight.Bold)
                if (feedItem.platform.hasHtmlText) {
                    ClickableText(annotatedString, style = LocalTextStyle.current) { offset ->
                        val url = annotatedString.getUrlAnnotations(start = offset, end = offset)
                            .firstOrNull()?.item?.url
                        if (!url.isNullOrEmpty())
                            uriHandler.openUri(url)
                        else
                            uriHandler.openUri(feedItem.link)
                    }
                } else {
                    Text(feedItem.text) // TODO Linkify
                }
                Text(
                    text = formattedDate,
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }
        }
    }
}

private val PlatformId.hasHtmlText get() = this == PlatformId.Mastodon