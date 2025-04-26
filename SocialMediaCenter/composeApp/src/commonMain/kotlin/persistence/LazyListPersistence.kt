package persistence

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlin.time.Duration.Companion.seconds

/**
 * Save scroll state for all time.
 * @param key value for comparing screen
 * @param initialFirstVisibleItemIndex see [LazyListState.firstVisibleItemIndex]
 * @param initialFirstVisibleItemScrollOffset see [LazyListState.firstVisibleItemScrollOffset]
 */
@Composable
fun rememberForeverLazyListState(
    key: String,
    persistence: Persistence,
    idOfItemAt: (index: Int) -> String,
    indexOfItem: (id: String) -> Int?,
    initialFirstVisibleItemIndex: Int = 0,
    initialFirstVisibleItemScrollOffset: Int = 0,
    firstVisibleItemIndexStateFlowChanged: (StateFlow<Int>) -> Unit = {},
): LazyListState {
    val scrollState = rememberSaveable(saver = LazyListState.Saver) {
        val savedItemId = persistence.load<String>(key)
        val savedIndex = savedItemId?.let { indexOfItem(it) } ?: initialFirstVisibleItemIndex
        LazyListState(
            savedIndex,
            initialFirstVisibleItemScrollOffset
        )
    }
    val firstVisibleItemStateFlow = remember { MutableStateFlow(scrollState.firstVisibleItemIndex) }
    LaunchedEffect(firstVisibleItemStateFlow) {
        firstVisibleItemIndexStateFlowChanged(firstVisibleItemStateFlow)
    }
    LaunchedEffect(idOfItemAt) {
        while (isActive) {
            delay(5.seconds)
            val lastIndex = scrollState.firstVisibleItemIndex
            firstVisibleItemStateFlow.value = lastIndex
            val itemId = idOfItemAt(lastIndex)
            persistence.save(key, itemId)
        }
    }
    DisposableEffect(idOfItemAt) {
        onDispose {
            val lastIndex = scrollState.firstVisibleItemIndex
            firstVisibleItemStateFlow.value = lastIndex
            val itemId = idOfItemAt(lastIndex)
            persistence.save(key, itemId)
        }
    }
    return scrollState
}
