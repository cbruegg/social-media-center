package components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
internal fun JumpToTopButton(listState: LazyListState, modifier: Modifier = Modifier) {
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
internal fun ConfigButton(
    visible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        modifier = modifier,
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        FloatingActionButton(
            onClick,
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            modifier = Modifier
                .padding(16.dp)
                .size(48.dp)
        ) {
            Icon(Icons.Filled.Settings, "Settings")
        }
    }
}

@Composable
internal fun SyncFeedPositionButton(
    visible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        modifier = modifier,
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        FloatingActionButton(
            onClick,
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            modifier = Modifier
                .padding(16.dp)
                .size(48.dp)
        ) {
            Icon(Icons.Filled.SyncAlt, "Sync feed position")
        }
    }
}

@Composable
internal fun RevertSyncFeedPositionButton(
    visible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        modifier = modifier,
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        FloatingActionButton(
            onClick,
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            modifier = Modifier
                .padding(16.dp)
                .size(48.dp)
        ) {
            Icon(Icons.Filled.ArrowDownward, "Revert sync feed position")
        }
    }
}