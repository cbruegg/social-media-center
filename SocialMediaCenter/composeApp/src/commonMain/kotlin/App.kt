import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.AlertDialog
import androidx.compose.material.Card
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TextField
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import com.cbruegg.socialmediaserver.shared.FeedItem
import com.cbruegg.socialmediaserver.shared.serverWithoutScheme
import com.hoc081098.kmp.viewmodel.compose.kmpViewModel
import com.hoc081098.kmp.viewmodel.createSavedStateHandle
import components.FeedItemContentText
import components.LifecycleHandler
import io.ktor.client.HttpClient
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.launch
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.kodein.emoji.compose.EmojiUrl
import org.kodein.emoji.compose.LocalEmojiDownloader
import org.kodein.emoji.compose.WithPlatformEmoji
import persistence.rememberForeverLazyListState
import security.tokenAsHttpHeader
import util.LocalContextualUriHandler
import util.LocalInAppBrowserOpener
import util.toContextualUriHandler

// TODO: Configurable server
// TODO: Remember timeline state across devices
// TODO: (Configurable?) maximum post height (Mastodon posts can be very long)
// TODO: Move logic to some ViewModel
// TODO: Support mentions (maybe not for all platforms)

@OptIn(ExperimentalMaterialApi::class)
@Composable
@Preview
fun App() {
    // Ignore bottom window insets in order to draw below the system bar
    val windowInsetSides = WindowInsetsSides.Start + WindowInsetsSides.End + WindowInsetsSides.Top

    MaterialTheme(
        colors = if (isSystemInDarkTheme()) darkColors() else lightColors()
    ) {
        // TODO Move logic to viewmodel
        // TODO Use something for DI
        val clipboardManager = LocalClipboardManager.current
        val localUriHandler = LocalUriHandler.current
        val inAppBrowserOpener = LocalInAppBrowserOpener.current
        val uriHandler = remember(clipboardManager, localUriHandler, inAppBrowserOpener) {
            getPlatform().createUriHandler(
                clipboardManager,
                localUriHandler,
                inAppBrowserOpener,
                socialMediaCenterBaseUrl,
            ) ?: localUriHandler.toContextualUriHandler(inAppBrowserOpener)
        }
        val authTokenRepository = remember { createAuthTokenRepository() }
        val api =
            remember(authTokenRepository) { createApi(createApiHttpClient(authTokenRepository)) }
        val downloadEmoji = remember() { createEmojiDownloader(createGenericHttpClient()) }

        CompositionLocalProvider(
            LocalContextualUriHandler provides uriHandler,
            LocalEmojiDownloader provides downloadEmoji
        ) {
            val scope = rememberCoroutineScope()
            val vm =
                kmpViewModel { AppViewModel(createSavedStateHandle(), api, authTokenRepository) }
            val _state by vm.stateFlow.collectAsState()
            val state = _state // to enable smart-casts
            val isLoading =
                state is AppViewModel.State.InitialLoad && state.started || state is AppViewModel.State.Loaded && state.isLoading

            val pullRefreshState =
                rememberPullRefreshState(isLoading, { scope.launch { vm.requestRefresh() } })

            LifecycleHandler(onPause = vm::onPause, onResume = vm::onResume)

            if (state is AppViewModel.State.Loaded && state.showLastLoadFailurePopup) {
                val lastLoadFailure = state.lastLoadFailure
                AlertDialog(
                    text = {
                        Text(
                            lastLoadFailure?.message ?: lastLoadFailure?.toString() ?: "No error!"
                        )
                    },
                    onDismissRequest = { scope.launch { vm.dismissLastLoadFailurePopup() } },
                    dismissButton = {
                        TextButton(onClick = {
                            scope.launch { vm.dismissLastLoadFailurePopup() }
                        }) { Text("Dismiss") }
                    },
                    confirmButton = {}
                )
            }

            // TODO Break up this huge Composable
            if (state is AppViewModel.State.ShowAuthDialog) {
                Dialog(
                    onDismissRequest = {}, properties = DialogProperties(
                        dismissOnBackPress = false,
                        dismissOnClickOutside = false
                    )
                ) {
                    var tokenInput by remember { mutableStateOf(authTokenRepository.token ?: "") }

                    Card {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                "Please enter your authentication token.",
                                modifier = Modifier.padding(8.dp)
                            )
                            TextField(
                                value = tokenInput,
                                onValueChange = { tokenInput = it },
                                label = { Text("Token") }
                            )
                            TextButton(onClick = {
                                scope.launch { vm.onTokenEntered(tokenInput) }
                            }) {
                                Text("Save")
                            }
                        }
                    }
                }
            }

            Surface {
                Box(
                    modifier = Modifier.fillMaxSize()
                        .pullRefresh(pullRefreshState)
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(windowInsetSides))
                ) {
                    Column(modifier = Modifier.widthIn(max = 1000.dp).align(Alignment.TopCenter)) {
                        if (state is AppViewModel.State.Loaded && state.lastLoadFailure != null) {
                            Card(modifier = Modifier.padding(8.dp)) {
                                Row {
                                    Text("Loading error!")
                                    TextButton({ scope.launch { vm.showLastLoadFailurePopup() } }) {
                                        Text(
                                            "Details"
                                        )
                                    }
                                    TextButton({ scope.launch { vm.dismissLastLoadFailure() } }) {
                                        Text(
                                            "Dismiss"
                                        )
                                    }
                                }
                            }
                        }
                        if (state is AppViewModel.State.Loaded) {
                            for (unauthenticatedMastodonAccount in state.unauthenticatedMastodonAccounts) {
                                Card(modifier = Modifier.padding(8.dp)) {
                                    Column {
                                        val instanceName =
                                            unauthenticatedMastodonAccount.serverWithoutScheme
                                        val displayName =
                                            "@${unauthenticatedMastodonAccount.username}@$instanceName"
                                        Text("Please authenticate your account $displayName")
                                        TextButton({
                                            uriHandler.openUri("$socialMediaCenterBaseUrl/authorize/mastodon/start?instanceName=$instanceName&socialMediaCenterBaseUrl=${socialMediaCenterBaseUrl.encodeURLParameter()}")
                                        }) {
                                            Text("Authenticate")
                                        }
                                    }
                                }
                            }
                        }
                        if (state is AppViewModel.State.Loaded) {
                            val feedItems = state.feedItems
                            LazyColumn(state = rememberForeverFeedItemsListState(feedItems)) {
                                items(
                                    feedItems.size,
                                    key = { feedItems[it].id },
                                    itemContent = {
                                        FeedItemRow(
                                            feedItems[it],
                                            tokenAsHttpHeader = authTokenRepository.tokenAsHttpHeader,
                                            Modifier.padding(top = if (it == 0) 8.dp else 0.dp)
                                        )
                                    }
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
}

@Composable
private fun rememberForeverFeedItemsListState(feedItems: List<FeedItem>): LazyListState {
    val persistence = getPlatform().persistence
    return rememberForeverLazyListState(
        "appScrollState",
        persistence,
        idOfItemAt = { feedItems[it].id },
        indexOfItem = { id -> feedItems.indexOfFirst { it.id == id }.takeIf { it != -1 } }
    )
}

@OptIn(ExperimentalCoilApi::class)
@Composable
private fun FeedItemRow(
    feedItem: FeedItem,
    tokenAsHttpHeader: Pair<String, String>?,
    modifier: Modifier = Modifier,
    showRepost: Boolean = true
) {
    val uriHandler = LocalContextualUriHandler.current

    val formattedDate = remember(feedItem) { getPlatform().formatFeedItemDate(feedItem.published) }
    val link = feedItem.link

    Card(modifier = modifier
        .fillMaxWidth()
        .let {
            if (link != null) {
                it.clickable { uriHandler.openPostUri(link, feedItem.platform) }
            } else {
                it
            }
        }
    ) {
        Row(
            modifier = Modifier.padding(
                start = 16.dp,
                top = 8.dp,
                end = 16.dp,
                bottom = 16.dp
            )
        ) {
            val request = ImageRequest.Builder(LocalPlatformContext.current)
                .data(feedItem.authorImageUrl?.let {
                    getPlatform().corsProxiedUrlToAbsoluteUrl(socialMediaCenterBaseUrl, it)
                })
                .also {
                    if (tokenAsHttpHeader != null) {
                        val (key, value) = tokenAsHttpHeader
                        it.httpHeaders(NetworkHeaders.Builder().add(key, value).build())
                    }
                }
                .build()
            AsyncImage(
                model = request,
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
                val repost = feedItem.repost
                if (repost != null && showRepost) {
                    FeedItemRow(
                        repost,
                        tokenAsHttpHeader,
                        modifier = Modifier.padding(8.dp),
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

private fun createEmojiDownloader(httpClient: HttpClient): suspend (EmojiUrl) -> ByteArray =
    { emojiUrl -> downloadEmoji(httpClient, emojiUrl) }