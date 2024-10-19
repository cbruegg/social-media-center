import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cbruegg.socialmediaserver.shared.FeedItem
import com.cbruegg.socialmediaserver.shared.MastodonUser
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import security.ServerConfig
import kotlin.concurrent.Volatile
import kotlin.time.Duration.Companion.minutes

class AppViewModel(
    apiFlow: Flow<Api?>,
    private val serverConfig: ServerConfig
) : ViewModel() {
    @Serializable
    sealed interface State {
        companion object {
            val initial = InitialLoad(started = false)
        }

        data class InitialLoad(val started: Boolean) : State

        data class ShowAuthDialog(val configEntered: Boolean = false) : State

        data class Loaded(
            val feedItems: List<FeedItem>,
            val lastLoadFailure: Throwable?,
            val showLastLoadFailurePopup: Boolean = false,
            val isLoading: Boolean = false,
            val unauthenticatedMastodonAccounts: List<MastodonUser> = emptyList()
        ) : State
    }

    // This is a flow because the baseUrl is configurable by the user
    private val apiFlow =
        apiFlow.stateIn(viewModelScope, SharingStarted.Eagerly, initialValue = null)

    private val mutex = Mutex()
    private val _stateFlow = MutableStateFlow<State>(State.initial)
    val stateFlow = _stateFlow.asStateFlow()
    private var state: State // TODO Remove this and Mutex and use update func of stateflow itself
        get() = _stateFlow.value
        set(value) {
            _stateFlow.value = value
        }

    @Volatile
    private var shouldRefreshPeriodically = true

    private val refreshRequestChannel =
        Channel<Unit>(capacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    init {
        viewModelScope.launch {
            while (isActive) {
                if (shouldRefreshPeriodically) {
                    doRefresh()
                } else {
                    println("Skipping refresh")
                }
                // Wait a minute for the next refresh or until a refresh is requested explicitly
                withTimeoutOrNull(1.minutes) { refreshRequestChannel.receive() }
            }
        }
        viewModelScope.launch {
            // Every time the API (base URL) changes, refresh
            apiFlow.onEach { api ->
                if (api != null) {
                    requestRefresh()
                }
            }
        }
    }

    fun onPause() {
        shouldRefreshPeriodically = false
    }

    fun onResume() {
        shouldRefreshPeriodically = true
        requestRefresh()
    }

    fun requestRefresh() {
        refreshRequestChannel.trySend(Unit)
    }

    private suspend fun doRefresh() = coroutineScope {
        val api = apiFlow.value

        if (api == null) {
            println("User has not configured base URL yet")
            state = State.ShowAuthDialog()
            return@coroutineScope
        }

        val skipRefresh = mutex.withLock {
            val (skipRefresh, nextState) = when (val state = state) {
                is State.InitialLoad -> false to state.copy(started = true)
                is State.ShowAuthDialog -> !state.configEntered to State.InitialLoad(started = true)
                is State.Loaded -> state.isLoading to state.copy(isLoading = true)
            }
            state = nextState
            skipRefresh
        }
        if (skipRefresh) return@coroutineScope

        println("Refreshing...")
        val feedResultAsync = async { api.getFeed() }
        val unauthenticatedMastodonAccountsResultAsync =
            async { api.getUnauthenticatedMastodonAccounts() }
        val feedResult = feedResultAsync.await()
        val unauthenticatedMastodonAccountsResult =
            unauthenticatedMastodonAccountsResultAsync.await()
        val unauthenticatedMastodonAccounts = when (unauthenticatedMastodonAccountsResult) {
            is ApiResponse.Ok -> unauthenticatedMastodonAccountsResult.body
            else -> emptyList()
        }

        mutex.withLock {
            when (feedResult) {
                is ApiResponse.Ok -> state = when (val state = state) {
                    is State.InitialLoad -> State.Loaded(
                        feedItems = feedResult.body,
                        lastLoadFailure = null,
                        unauthenticatedMastodonAccounts = unauthenticatedMastodonAccounts
                    )

                    is State.Loaded -> state.copy(
                        feedItems = feedResult.body,
                        lastLoadFailure = null,
                        isLoading = false,
                        unauthenticatedMastodonAccounts = unauthenticatedMastodonAccounts
                    )

                    is State.ShowAuthDialog -> return@coroutineScope // abort refresh
                }

                is ApiResponse.Unauthorized -> state = State.ShowAuthDialog()
                is ApiResponse.ErrorStatus -> state = when (val state = state) {
                    is State.InitialLoad -> State.Loaded(
                        feedItems = emptyList(),
                        lastLoadFailure = feedResult,
                        unauthenticatedMastodonAccounts = unauthenticatedMastodonAccounts
                    )

                    is State.Loaded -> state.copy(
                        lastLoadFailure = feedResult,
                        isLoading = false,
                        unauthenticatedMastodonAccounts = unauthenticatedMastodonAccounts
                    )

                    is State.ShowAuthDialog -> return@coroutineScope // abort refresh
                }

                is ApiResponse.CaughtException -> state = when (val state = state) {
                    is State.InitialLoad -> State.Loaded(
                        feedItems = emptyList(),
                        lastLoadFailure = feedResult.exception,
                        unauthenticatedMastodonAccounts = unauthenticatedMastodonAccounts
                    )

                    is State.Loaded -> state.copy(
                        lastLoadFailure = feedResult.exception,
                        isLoading = false,
                        unauthenticatedMastodonAccounts = unauthenticatedMastodonAccounts
                    )

                    is State.ShowAuthDialog -> return@coroutineScope // abort refresh
                }
            }
        }
    }

    suspend fun onServerConfigEntered(token: String, baseUrl: String) {
        serverConfig.updateToken(token)
        serverConfig.updateBaseUrl(baseUrl)

        mutex.withLock {
            val lastState = state
            if (lastState !is State.ShowAuthDialog) {
                println("State should always be ShowAuthDialog here!")
                return@withLock
            }

            state = lastState.copy(configEntered = true)
        }

        requestRefresh()
    }

    suspend fun dismissLastLoadFailure() = mutex.withLock {
        state = when (val state = state) {
            is State.Loaded -> state.copy(lastLoadFailure = null)
            else -> state
        }
    }

    suspend fun showLastLoadFailurePopup() = mutex.withLock {
        state = when (val state = state) {
            is State.Loaded -> state.copy(showLastLoadFailurePopup = state.lastLoadFailure != null)
            else -> state
        }
    }

    suspend fun dismissLastLoadFailurePopup() = mutex.withLock {
        state = when (val state = state) {
            is State.Loaded -> state.copy(showLastLoadFailurePopup = false)
            else -> state
        }
    }

    fun onConfigButtonClick() {
        state = State.ShowAuthDialog()
    }
}