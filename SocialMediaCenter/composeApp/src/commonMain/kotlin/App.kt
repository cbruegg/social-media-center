
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cbruegg.socialmediaserver.shared.MastodonUser
import com.cbruegg.socialmediaserver.shared.serverWithoutScheme
import components.AuthDialog
import components.ConfigButton
import components.Feed
import components.LastLoadFailurePopup
import components.LifecycleHandler
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.launch
import theme.AppTheme
import util.ContextualUriHandler
import util.LocalContextualUriHandler

// TODO: Remember timeline state across devices
// TODO: (Configurable?) maximum post height (Mastodon posts can be very long)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App() {
    // Ignore bottom window insets in order to draw below the system bar
    val windowInsetSides = WindowInsetsSides.Start + WindowInsetsSides.End + WindowInsetsSides.Top

    AppTheme {
        val dependencies = getAppDependencies()
        CompositionLocalProvider(
            LocalContextualUriHandler provides dependencies.uriHandler
        ) {
            val scope = rememberCoroutineScope()
            val vm = dependencies.viewModel
            val _state by vm.stateFlow.collectAsState()
            val state = _state // to enable smart-casts
            val isLoading =
                state is AppViewModel.State.InitialLoad && state.started || state is AppViewModel.State.Loaded && state.isLoading

            LifecycleHandler(onPause = vm::onPause, onResume = vm::onResume)

            if (state is AppViewModel.State.Loaded && state.showLastLoadFailurePopup && state.lastLoadFailure != null) {
                LastLoadFailurePopup(
                    state.lastLoadFailure,
                    dismissLastLoadFailurePopup = { scope.launch { vm.dismissLastLoadFailurePopup() } }
                )
            }

            if (state is AppViewModel.State.ShowAuthDialog) {
                AuthDialog(
                    dependencies.serverConfig,
                    onServerConfigEntered = { token, baseUrl ->
                        scope.launch { vm.onServerConfigEntered(token, baseUrl) }
                    },
                )
            }

            Surface {
                PullToRefreshBox(
                    isRefreshing = isLoading,
                    onRefresh = { scope.launch { vm.requestRefresh() } },
                    modifier = Modifier.fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(windowInsetSides))
                ) {
                    Column(modifier = Modifier.widthIn(max = 1000.dp).align(Alignment.TopCenter)) {
                        if (state is AppViewModel.State.Loaded) {
                            if (state.lastLoadFailure != null) {
                                LoadFailureCard(
                                    showPopup = { scope.launch { vm.showLastLoadFailurePopup() } },
                                    dismissFailure = { scope.launch { vm.dismissLastLoadFailure() } }
                                )
                            }

                            for (unauthenticatedMastodonAccount in state.unauthenticatedMastodonAccounts) {
                                UnauthenticatedMastodonAccountWarningCard(
                                    unauthenticatedMastodonAccount,
                                    dependencies.uriHandler,
                                    baseUrl = dependencies.serverConfig.baseUrl.value
                                        ?: error("At this point, the base URL should be set")
                                )
                            }

                            Feed(
                                state.feedItems,
                                dependencies.serverConfig,
                                onConfigButtonClick = vm::onConfigButtonClick,
                                firstVisibleItemFlowChanged = vm::firstVisibleItemFlowChanged,
                                suggestedFeedPosition = state.suggestedFeedPosition,
                                acceptSuggestedFeedPosition = vm::acceptSuggestedFeedPosition,
                                revertSyncFeedPositionOption = state.revertSyncFeedPositionOption,
                                revertedSyncFeedPosition = vm::revertedSyncFeedPosition
                            )
                        } else {
                            Box(Modifier.fillMaxSize()) {
                                ConfigButton(visible = true, vm::onConfigButtonClick, Modifier.align(Alignment.BottomStart))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UnauthenticatedMastodonAccountWarningCard(
    unauthenticatedMastodonAccount: MastodonUser,
    uriHandler: ContextualUriHandler,
    baseUrl: String
) {
    Card(modifier = Modifier.padding(8.dp)) {
        Column {
            val instanceName = unauthenticatedMastodonAccount.serverWithoutScheme
            val displayName = "@${unauthenticatedMastodonAccount.username}@$instanceName"
            Text("Please authenticate your account $displayName")
            TextButton({
                uriHandler.openUri("$baseUrl/authorize/mastodon/start?instanceName=$instanceName&socialMediaCenterBaseUrl=${baseUrl.encodeURLParameter()}")
            }) {
                Text("Authenticate")
            }
        }
    }
}

@Composable
private fun LoadFailureCard(showPopup: () -> Unit, dismissFailure: () -> Unit) {
    Card(modifier = Modifier.padding(8.dp)) {
        Row {
            Text("Loading error!")
            TextButton(onClick = showPopup) {
                Text("Details")
            }
            TextButton(onClick = dismissFailure) {
                Text("Dismiss")
            }
        }
    }
}

