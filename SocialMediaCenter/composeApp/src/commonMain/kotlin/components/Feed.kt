package components

import AppViewModel
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeGestures
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.PlatformContext
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import com.cbruegg.socialmediaserver.shared.FeedItem
import com.cbruegg.socialmediaserver.shared.MediaAttachment
import com.cbruegg.socialmediaserver.shared.MediaType
import com.cbruegg.socialmediaserver.shared.RepostMeta
import getPlatform
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import persistence.rememberForeverLazyListState
import security.ServerConfig
import security.tokenAsHttpHeader
import util.LocalContextualUriHandler
import util.currentTimeFlow
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
@Composable
fun Feed(
    feedItems: List<FeedItem>,
    serverConfig: ServerConfig,
    onConfigButtonClick: () -> Unit,
    firstVisibleItemFlowChanged: (Flow<FeedItem>) -> Unit = {},
    suggestedFeedPosition: Int?,
    revertSyncFeedPositionOption: AppViewModel.RevertSyncFeedPositionOption?,
    acceptSuggestedFeedPosition: (previousFeedPosition: Int) -> Unit,
    revertedSyncFeedPosition: () -> Unit
) {
    val scope = rememberCoroutineScope()
    Box {
        val listState = rememberForeverFeedItemsListState(
            feedItems,
            firstVisibleItemFlowChanged = firstVisibleItemFlowChanged
        )
        LazyColumn(state = listState, modifier = Modifier.fillMaxHeight()) {
            items(
                feedItems.size,
                key = { feedItems[it].id },
                itemContent = {
                    FeedItemRow(
                        feedItems[it],
                        tokenAsHttpHeader = serverConfig.tokenAsHttpHeader,
                        Modifier.padding(top = if (it == 0) 8.dp else 0.dp),
                        baseUrl = serverConfig.baseUrl.value ?: error("baseUrl not set")
                    )
                }
            )
        }

        // Don't consume touch events in the system gestures zone:
        Spacer(
            Modifier
                .fillMaxWidth()
                .windowInsetsBottomHeight(WindowInsets.safeGestures)
                .align(Alignment.BottomCenter)
                .draggable(rememberDraggableState { }, orientation = Orientation.Vertical)
        )

        val bottomStartButtonModifier = Modifier.align(Alignment.BottomStart).padding(bottom = 8.dp)
        JumpToTopButton(listState, bottomStartButtonModifier)
        ConfigButton(
            visible = listState.firstVisibleItemIndex == 0,
            onConfigButtonClick,
            bottomStartButtonModifier
        )

        val bottomEndButtonModifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 8.dp)
        val currentTime by currentTimeFlow.collectAsStateWithLifecycle(initialValue = Clock.System.now())
        val showSyncFeedPositionButton by remember(suggestedFeedPosition) {
            derivedStateOf { suggestedFeedPosition != null && suggestedFeedPosition < listState.firstVisibleItemIndex }
        }
        SyncFeedPositionButton(
            visible = showSyncFeedPositionButton,
            onClick = {
                suggestedFeedPosition ?: return@SyncFeedPositionButton

                acceptSuggestedFeedPosition(listState.firstVisibleItemIndex)
                scope.launch { listState.animateScrollToItem(suggestedFeedPosition) }
            },
            modifier = bottomEndButtonModifier
        )
        RevertSyncFeedPositionButton(
            visible = revertSyncFeedPositionOption != null && currentTime <= revertSyncFeedPositionOption.showUntil,
            onClick = {
                revertSyncFeedPositionOption ?: return@RevertSyncFeedPositionButton

                revertedSyncFeedPosition()
                scope.launch { listState.animateScrollToItem(index = revertSyncFeedPositionOption.previousFeedPosition) }
            },
            modifier = bottomEndButtonModifier
        )
    }
}

@Composable
private fun rememberForeverFeedItemsListState(
    feedItems: List<FeedItem>,
    firstVisibleItemFlowChanged: (Flow<FeedItem>) -> Unit = {}
): LazyListState {
    val persistence = getPlatform().persistence
    return rememberForeverLazyListState(
        "appScrollState",
        persistence,
        idOfItemAt = { feedItems.getOrNull(it)?.id ?: "___out-of-bounds___" },
        indexOfItem = { id -> feedItems.indexOfFirst { it.id == id }.takeIf { it != -1 } },
        firstVisibleItemIndexStateFlowChanged = { flow ->
            firstVisibleItemFlowChanged(flow.map { index -> feedItems[index] })
        }
    )
}

@OptIn(ExperimentalTime::class)
@Composable
private fun FeedItemRow(
    feedItem: FeedItem,
    tokenAsHttpHeader: Pair<String, String>?,
    modifier: Modifier = Modifier,
    baseUrl: String,
    showRepost: Boolean = true
) {
    val uriHandler = LocalContextualUriHandler.current

    val formattedDate = remember(feedItem) { getPlatform().formatFeedItemDate(feedItem.published) }
    val link = feedItem.link
    val repostMeta = feedItem.repostMeta

    Column(
        modifier = modifier
            .fillMaxWidth()
            .let {
                if (link != null)
                    it.clickable { uriHandler.openPostUri(link, feedItem.platform) }
                else
                    it
            }
            .padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 8.dp)
    ) {
        if (repostMeta != null) {
            RepostInfo(
                Modifier.padding(start = 64.dp),
                repostMeta,
                tokenAsHttpHeader,
                baseUrl
            )
        }
        Row {
            AuthorAvatar(
                feedItem.author,
                feedItem.authorImageUrl,
                tokenAsHttpHeader,
                baseUrl,
                Modifier.size(64.dp).padding(8.dp)
            )
            Column {
                AuthorName(feedItem)
                FeedItemContentText(feedItem)
                FeedItemMediaAttachments(feedItem, tokenAsHttpHeader, baseUrl)
                val repost = feedItem.quotedPost
                if (repost != null && showRepost) {
                    Card(
                        modifier = Modifier.padding(8.dp),
                        colors = CardDefaults.cardColors().copy(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        FeedItemRow(
                            repost,
                            tokenAsHttpHeader,
                            baseUrl = baseUrl,
                            showRepost = false // to avoid deep nesting
                        )
                    }
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

@Composable
fun RepostInfo(
    modifier: Modifier = Modifier,
    repostMeta: RepostMeta,
    tokenAsHttpHeader: Pair<String, String>?,
    baseUrl: String,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Repeat, "Repost Icon", modifier = Modifier.size(20.dp))
        AuthorAvatar(
            repostMeta.repostingAuthor,
            repostMeta.repostingAuthorImageUrl,
            tokenAsHttpHeader,
            baseUrl,
            Modifier.padding(start = 8.dp).size(20.dp)
        )
        Text(
            repostMeta.repostingAuthor,
            modifier = Modifier.padding(start = 8.dp)
                .align(Alignment.CenterVertically)
                .height(28.dp),
            color = if (isSystemInDarkTheme()) Color.LightGray else Color.DarkGray,
            fontSize = 14.sp
        )
    }
}

@Composable
private fun AuthorName(feedItem: FeedItem) {
    Text(feedItem.author, fontWeight = FontWeight.Bold)
}

@Composable
private fun AuthorAvatar(
    author: String,
    authorImageUrl: String?,
    tokenAsHttpHeader: Pair<String, String>?,
    baseUrl: String,
    modifier: Modifier = Modifier
) {
    AsyncImage(
        model = createProxiedImageRequest(
            LocalPlatformContext.current,
            authorImageUrl,
            tokenAsHttpHeader,
            baseUrl
        ),
        contentDescription = author,
        modifier = modifier
            .clip(CircleShape)
            .border(2.dp, Color.Gray, CircleShape)
    )
}

@Composable
private fun FeedItemMediaAttachments(
    feedItem: FeedItem,
    tokenAsHttpHeader: Pair<String, String>?,
    baseUrl: String
) {
    val attachments = feedItem.mediaAttachments
    LazyRow {
        items(
            attachments.size,
            key = { attachments[it].previewImageUrl },
            itemContent = { MediaAttachment(attachments[it], tokenAsHttpHeader, baseUrl) })
    }
}

@Composable
private fun MediaAttachment(
    attachment: MediaAttachment,
    tokenAsHttpHeader: Pair<String, String>?,
    baseUrl: String
) {
    Box {
        AsyncImage(
            model = createProxiedImageRequest(
                LocalPlatformContext.current,
                attachment.previewImageUrl,
                tokenAsHttpHeader,
                baseUrl
            ),
            contentDescription = "feed item media attachment",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .padding(8.dp)
                .size(128.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, Color.Black)
        )
        if (attachment.type == MediaType.Gifv || attachment.type == MediaType.Video) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f))
                    .align(Alignment.Center)
            )
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Play",
                modifier = Modifier.size(48.dp).align(Alignment.Center),
                tint = Color.White
            )
        }
    }
}

private fun createProxiedImageRequest(
    context: PlatformContext,
    url: String?,
    tokenAsHttpHeader: Pair<String, String>?,
    baseUrl: String
) = ImageRequest.Builder(context)
    .data(url?.let {
        getPlatform().corsProxiedUrlToAbsoluteUrl(baseUrl, it)
    })
    .also {
        if (tokenAsHttpHeader != null) {
            val (key, value) = tokenAsHttpHeader
            it.httpHeaders(NetworkHeaders.Builder().add(key, value).build())
        }
    }
    .build()
