package persistence

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
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
    firstVisibleItemIndexStateFlowChanged: (Flow<Int>) -> Unit = {},
): LazyListState {
    val scrollState = rememberSaveable(saver = LazyListState.Saver) {
        val savedItemId = persistence.load<String>(key)
        val savedIndex = savedItemId?.let { indexOfItem(it) } ?: initialFirstVisibleItemIndex
        LazyListState(
            savedIndex,
            initialFirstVisibleItemScrollOffset
        )
    }
    LaunchedEffect(scrollState) {
        firstVisibleItemIndexStateFlowChanged(snapshotFlow { scrollState.firstVisibleItemIndex })
    }
    LaunchedEffect(idOfItemAt) {
        while (isActive) {
            delay(5.seconds)
            val lastIndex = scrollState.firstVisibleItemIndex
            val itemId = idOfItemAt(lastIndex)
            persistence.save(key, itemId)
        }
    }
    DisposableEffect(idOfItemAt) {
        onDispose {
            val lastIndex = scrollState.firstVisibleItemIndex
            val itemId = idOfItemAt(lastIndex)
            persistence.save(key, itemId)
        }
    }
    return scrollState
}
