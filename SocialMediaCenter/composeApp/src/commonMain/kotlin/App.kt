
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.AlertDialog
import androidx.compose.material.Card
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import components.FeedItemContentText
import components.LifecycleHandler
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.kodein.emoji.compose.LocalEmojiDownloader
import org.kodein.emoji.compose.WithPlatformEmoji
import persistence.rememberForeverLazyListState
import util.LocalContextualUriHandler
import util.toContextualUriHandler
import kotlin.time.Duration.Companion.minutes

// TODO: Configurable server
// TODO: Remember timeline state across devices
// TODO: (Configurable?) maximum post height (Mastodon posts can be very long)
// TODO: Move logic to some ViewModel
// TODO: Support mentions (maybe not for all platforms)

@OptIn(ExperimentalMaterialApi::class)
@Composable
@Preview
fun App() {
    MaterialTheme {
        val clipboardManager = LocalClipboardManager.current
        val localUriHandler = LocalUriHandler.current
        val uriHandler = remember(clipboardManager, localUriHandler) {
            getPlatform().createUriHandler(clipboardManager)
                ?: localUriHandler.toContextualUriHandler()
        }

        CompositionLocalProvider(
            LocalContextualUriHandler provides uriHandler,
            LocalEmojiDownloader provides ::downloadEmoji
        ) {
            val scope = rememberCoroutineScope()
            var feedItems: List<FeedItem>? by remember { mutableStateOf(null) }
            var lastLoadFailure: Throwable? by remember { mutableStateOf(null) }
            var isLoading by remember { mutableStateOf(false) }
            var shouldRefreshPeriodically by remember { mutableStateOf(true) }
            val refresh = suspend {
                if (!isLoading) {
                    println("Refreshing...")
                    isLoading = true
                    val result = feedLoader.fetch()
                    feedItems = result.getOrNull() ?: feedItems
                    lastLoadFailure = result.exceptionOrNull()
                    isLoading = false
                }
            }
            val pullRefreshState =
                rememberPullRefreshState(isLoading, { scope.launch { refresh() } })
            var showLastLoadFailure by remember { mutableStateOf(false) }

            LaunchedEffect(Unit) { refresh() } // initial load
            LaunchedEffect(Unit) {
                launch {
                    while (isActive) {
                        delay(1.minutes)
                        if (shouldRefreshPeriodically) {
                            refresh()
                        } else {
                            println("Skipping refresh")
                        }
                    }
                }
            }
            LifecycleHandler(
                onPause = { shouldRefreshPeriodically = false },
                onResume = {
                    shouldRefreshPeriodically = true
                    scope.launch { refresh() }
                }
            )

            if (showLastLoadFailure) {
                AlertDialog(
                    text = { Text(lastLoadFailure?.message ?: "No error!") },
                    onDismissRequest = { showLastLoadFailure = false },
                    dismissButton = {
                        TextButton(onClick = {
                            showLastLoadFailure = false
                        }) { Text("Dismiss") }
                    },
                    confirmButton = {}
                )
            }

            Box(modifier = Modifier.fillMaxSize().padding(8.dp).pullRefresh(pullRefreshState)) {
                Column(modifier = Modifier.widthIn(max = 1000.dp).align(Alignment.TopCenter)) {
                    if (lastLoadFailure != null) {
                        Card {
                            Row {
                                Text("Loading error!")
                                TextButton({ showLastLoadFailure = true }) { Text("Details") }
                                TextButton({ lastLoadFailure = null }) { Text("Dismiss") }
                            }
                        }
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

@Composable
private fun FeedItemRow(feedItem: FeedItem, modifier: Modifier = Modifier) {
    val uriHandler = LocalContextualUriHandler.current

    val formattedDate = remember(feedItem) { getPlatform().formatFeedItemDate(feedItem.published) }

    Card(modifier = modifier
        .fillMaxWidth()
        .clickable { uriHandler.openPostUri(feedItem.link, feedItem.platform) }
    ) {
        Row(Modifier.padding(8.dp)) {
            AsyncImage(
                model = feedItem.authorImageUrl?.let {
                    getPlatform().corsProxiedUrlToAbsoluteUrl(
                        socialMediaCenterBaseUrl,
                        it
                    )
                },
                contentDescription = feedItem.author,
                modifier = Modifier
                    .padding(8.dp)
                    .size(48.dp)
                    .clip(CircleShape)
                    .border(2.dp, Color.Gray, CircleShape)
            )
            Column {
                // Using WithPlatformEmoji for emoji support on WASM
                WithPlatformEmoji(feedItem.author) { text, content ->
                    Text(text, inlineContent = content, fontWeight = FontWeight.Bold)
                }
                FeedItemContentText(feedItem)
                Text(
                    text = formattedDate,
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }
        }
    }
}
