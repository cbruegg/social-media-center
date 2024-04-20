import com.cbruegg.socialmediaserver.shared.FeedItem
import com.cbruegg.socialmediaserver.shared.MastodonUser
import com.hoc081098.kmp.viewmodel.SavedStateHandle
import com.hoc081098.kmp.viewmodel.ViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import security.AuthTokenRepository
import kotlin.concurrent.Volatile
import kotlin.time.Duration.Companion.minutes

private const val KEY_STATE = "state"

class AppViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val api: Api,
    private val authTokenRepository: AuthTokenRepository
) : ViewModel() {
    @Serializable
    sealed interface State {
        companion object {
            val initial = InitialLoad(started = false)
        }

        data class InitialLoad(val started: Boolean) : State

        data class ShowAuthDialog(val tokenEntered: Boolean = false) : State

        data class Loaded(
            val feedItems: List<FeedItem>,
            val lastLoadFailure: Throwable?,
            val showLastLoadFailurePopup: Boolean = false,
            val isLoading: Boolean = false,
            val unauthenticatedMastodonAccounts: List<MastodonUser> = emptyList()
        ) : State
    }

    private val mutex = Mutex()
    val stateFlow = savedStateHandle.getStateFlow<State>(KEY_STATE, State.initial)
    private var state: State
        get() = stateFlow.value
        set(value) {
            savedStateHandle[KEY_STATE] = value
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
        val skipRefresh = mutex.withLock {
            val (skipRefresh, nextState) = when (val state = state) {
                is State.InitialLoad -> false to state.copy(started = true)
                is State.ShowAuthDialog -> !state.tokenEntered to State.InitialLoad(started = true)
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

                    is State.ShowAuthDialog -> error("Should not be refreshing in $state")
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

                    is State.ShowAuthDialog -> error("Should not be refreshing in $state")
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

                    is State.ShowAuthDialog -> error("Should not be refreshing in $state")
                }
            }
        }
    }

    suspend fun onTokenEntered(token: String) {
        authTokenRepository.updateToken(token)

        mutex.withLock {
            val lastState = state
            if (lastState !is State.ShowAuthDialog) {
                println("State should always be ShowAuthDialog here!")
                return@withLock
            }

            state = lastState.copy(tokenEntered = true)
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
}