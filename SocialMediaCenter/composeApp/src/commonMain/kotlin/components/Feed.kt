package components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.FloatingActionButton
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
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
import kotlinx.coroutines.launch
import persistence.rememberForeverLazyListState
import security.ServerConfig
import security.tokenAsHttpHeader
import util.LocalContextualUriHandler

@Composable
fun Feed(
    feedItems: List<FeedItem>,
    serverConfig: ServerConfig,
    onConfigButtonClick: () -> Unit
) {
    Box {
        val listState = rememberForeverFeedItemsListState(feedItems)
        LazyColumn(state = listState) {
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
        JumpToTopButton(listState, Modifier.align(Alignment.BottomStart))
        ConfigButton(listState, onConfigButtonClick, Modifier.align(Alignment.BottomStart))
    }
}

@Composable
private fun JumpToTopButton(listState: LazyListState, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    AnimatedVisibility(
        modifier = modifier,
        visible = listState.firstVisibleItemIndex != 0,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        FloatingActionButton(
            onClick = { scope.launch { listState.animateScrollToItem(0) } },
            modifier = Modifier
                .padding(16.dp)
                .size(48.dp)
        ) {
            Icon(Icons.Filled.KeyboardArrowUp, "Jump up")
        }
    }
}

@Composable
private fun ConfigButton(
    listState: LazyListState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        modifier = modifier,
        visible = listState.firstVisibleItemIndex == 0,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        FloatingActionButton(
            onClick,
            backgroundColor = Color.LightGray,
            modifier = Modifier
                .padding(16.dp)
                .size(48.dp)
        ) {
            Icon(Icons.Filled.Settings, "Settings")
        }
    }
}

@Composable
private fun rememberForeverFeedItemsListState(feedItems: List<FeedItem>): LazyListState {
    val persistence = getPlatform().persistence
    return rememberForeverLazyListState(
        "appScrollState",
        persistence,
        idOfItemAt = { feedItems.getOrNull(it)?.id ?: "___out-of-bounds___" },
        indexOfItem = { id -> feedItems.indexOfFirst { it.id == id }.takeIf { it != -1 } }
    )
}

@Composable
private fun FeedItemRow(
    feedItem: FeedItem,
    tokenAsHttpHeader: Pair<String, String>?,
    modifier: Modifier = Modifier,
    baseUrl: String,
    showRepost: Boolean = true,
) {
    val uriHandler = LocalContextualUriHandler.current

    val formattedDate = remember(feedItem) { getPlatform().formatFeedItemDate(feedItem.published) }
    val link = feedItem.link
    val repostMeta = feedItem.repostMeta

    Card(modifier = modifier
        .fillMaxWidth()
        .let {
            if (link != null)
                it.clickable { uriHandler.openPostUri(link, feedItem.platform) }
            else
                it
        }
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 16.dp)
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
                        FeedItemRow(
                            repost,
                            tokenAsHttpHeader,
                            modifier = Modifier.padding(8.dp),
                            baseUrl = baseUrl,
                            showRepost = false // to avoid deep nesting
                        )
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
            color = Color.DarkGray,
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
