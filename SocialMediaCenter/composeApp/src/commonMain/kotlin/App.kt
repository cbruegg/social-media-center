
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.Card
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.mohamedrejeb.ksoup.entities.KsoupEntities
import components.LinkifiedText
import kotlinx.coroutines.launch
import org.jetbrains.compose.ui.tooling.preview.Preview
import persistence.rememberForeverLazyListState
import util.LocalContextualUriHandler
import util.toContextualUriHandler

// TODO: Configurable server
// TODO: Remember timeline state across devices
// TODO: (Configurable?) maximum post height (Mastodon posts can be very long)
// TODO: Move logic to some ViewModel
// TODO: Auto-refresh (every minute + on app relaunch)
// TODO: Support mentions (maybe not for all platforms)

@OptIn(ExperimentalMaterialApi::class)
@Composable
@Preview
fun App() {
    MaterialTheme {
        val uriHandler =
            getPlatform().uriHandler ?: LocalUriHandler.current.toContextualUriHandler()
        CompositionLocalProvider(LocalContextualUriHandler provides uriHandler) {
            val scope = rememberCoroutineScope()
            var feedItems: List<FeedItem>? by remember { mutableStateOf(null) }
            var lastLoadFailure: Throwable? by remember { mutableStateOf(null) }
            var isLoading by remember { mutableStateOf(true) }
            val refresh = suspend {
                isLoading = true
                val result = feedLoader.fetch()
                feedItems = result.getOrNull() ?: feedItems
                lastLoadFailure = result.exceptionOrNull()
                isLoading = false
            }
            val pullRefreshState =
                rememberPullRefreshState(isLoading, { scope.launch { refresh() } })

            LaunchedEffect(Unit) {
                launch { refresh() }
            }

            Box(modifier = Modifier.fillMaxSize().padding(8.dp).pullRefresh(pullRefreshState)) {
                Column {
                    lastLoadFailure?.let { lastLoadFailure ->
                        Text("Loading error! ${lastLoadFailure.message}") // TODO: Nicer display
                    }
                    feedItems?.let { feedItems ->
                        LazyColumn(state = rememberForeverFeedItemsListState(feedItems)) {
                            items(
                                feedItems.size,
                                key = { feedItems[it].id },
                                itemContent = { FeedItemRow(feedItems[it]) }
                            )
                        }
                    }
                }
                PullRefreshIndicator(
                    refreshing = isLoading,
                    state = pullRefreshState,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }
        }
    }
}

@Composable
private fun rememberForeverFeedItemsListState(feedItems: List<FeedItem>): LazyListState {
    val persistence = getPlatform().persistence
    return if (persistence != null) {
        rememberForeverLazyListState(
            "appScrollState",
            persistence,
            idOfItemAt = { feedItems[it].id },
            indexOfItem = { id -> feedItems.indexOfFirst { it.id == id }.takeIf { it != -1 } }
        )
    } else {
        rememberLazyListState()
    }
}

@OptIn(ExperimentalTextApi::class)
@Composable
private fun FeedItemRow(feedItem: FeedItem, modifier: Modifier = Modifier) {
    val uriHandler = LocalContextualUriHandler.current
    val linkColor = MaterialTheme.colors.primary

    val formattedDate = remember(feedItem) { getPlatform().formatFeedItemDate(feedItem.published) }

    Card(modifier = modifier
        .fillMaxWidth()
        .clickable { uriHandler.openPostUri(feedItem.link, feedItem.platform) }
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
                    val annotatedString = remember(feedItem, linkColor) {
                        feedItem.text.parseHtml(linkColor, maxLinkLength = 100)
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
                } else {
                    val decoded = remember(feedItem) { KsoupEntities.decodeHtml(feedItem.text) }
                    LinkifiedText(
                        text = decoded,
                        defaultClickHandler = {
                            uriHandler.openPostUri(
                                feedItem.link,
                                feedItem.platform
                            )
                        })
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