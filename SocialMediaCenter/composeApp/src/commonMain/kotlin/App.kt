
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import components.FeedItemContentText
import components.LifecycleHandler
import io.ktor.client.HttpClient
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.kodein.emoji.compose.EmojiUrl
import org.kodein.emoji.compose.LocalEmojiDownloader
import org.kodein.emoji.compose.WithPlatformEmoji
import persistence.rememberForeverLazyListState
import util.LocalContextualUriHandler
import util.LocalInAppBrowserOpener
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
    // Ignore bottom window insets in order to draw below the system bar
    val windowInsetSides = WindowInsetsSides.Start + WindowInsetsSides.End + WindowInsetsSides.Top

    MaterialTheme(
        colors = if (isSystemInDarkTheme()) darkColors() else lightColors()
    ) {
        // TODO Move logic to viewmodel
        // TODO Use Dagger for DI
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
        val httpClient = remember(authTokenRepository) { createHttpClient(authTokenRepository) }
        val api = remember(httpClient) { createApi(httpClient) }
        val downloadEmoji = remember(httpClient) { createEmojiDownloader(httpClient) }

        CompositionLocalProvider(
            LocalContextualUriHandler provides uriHandler,
            LocalEmojiDownloader provides downloadEmoji
        ) {
            val scope = rememberCoroutineScope()
            var feedItems: List<FeedItem>? by remember { mutableStateOf(null) }
            var unauthenticatedMastodonAccounts by remember { mutableStateOf(emptyList<MastodonUser>()) }
            var lastLoadFailure: Throwable? by remember { mutableStateOf(null) }
            var isLoading by remember { mutableStateOf(false) }
            var shouldRefreshPeriodically by remember { mutableStateOf(true) }
            var showAuthDialog by remember { mutableStateOf(authTokenRepository.token == null) }
            val refresh = suspend {
                if (!isLoading) {
                    println("Refreshing...")
                    isLoading = true
                    when (val feedResult = api.getFeed()) {
                        is ApiResponse.Ok -> {
                            feedItems = feedResult.body
                            lastLoadFailure = null
                        }
                        is ApiResponse.Unauthorized -> showAuthDialog = true
                        is ApiResponse.ErrorStatus -> lastLoadFailure = feedResult
                        is ApiResponse.CaughtException -> lastLoadFailure = feedResult.exception
                    }
                    unauthenticatedMastodonAccounts =
                        when (val unauthenticatedMastodonAccountsResult =
                            api.getUnauthenticatedMastodonAccounts()) {
                            is ApiResponse.Ok -> unauthenticatedMastodonAccountsResult.body
                            else -> emptyList()
                        }
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
                        if (shouldRefreshPeriodically && !showAuthDialog) {
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

            // TODO Break up this huge Composable
            if (showAuthDialog) {
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
                                authTokenRepository.updateToken(tokenInput)
                                scope.launch { refresh() }
                                showAuthDialog = false
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
                        if (lastLoadFailure != null) {
                            Card(modifier = Modifier.padding(8.dp)) {
                                Row {
                                    Text("Loading error!")
                                    TextButton({ showLastLoadFailure = true }) { Text("Details") }
                                    TextButton({ lastLoadFailure = null }) { Text("Dismiss") }
                                }
                            }
                        }
                        for (unauthenticatedMastodonAccount in unauthenticatedMastodonAccounts) {
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
                        feedItems?.let { feedItems ->
                            LazyColumn(state = rememberForeverFeedItemsListState(feedItems)) {
                                items(
                                    feedItems.size,
                                    key = { feedItems[it].id },
                                    itemContent = {
                                        FeedItemRow(
                                            feedItems[it],
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

@Composable
private fun FeedItemRow(
    feedItem: FeedItem,
    modifier: Modifier = Modifier,
    showRepost: Boolean = true
) {
    val uriHandler = LocalContextualUriHandler.current

    val formattedDate = remember(feedItem) { getPlatform().formatFeedItemDate(feedItem.published) }
    val link = feedItem.link

    Card(modifier = modifier
        .fillMaxWidth()
        .apply {
            if (link != null) clickable {
                uriHandler.openPostUri(
                    link,
                    feedItem.platform
                )
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
                if (feedItem.repost != null && showRepost) {
                    FeedItemRow(
                        feedItem.repost,
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