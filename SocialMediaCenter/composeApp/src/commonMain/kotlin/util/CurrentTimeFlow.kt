package util

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
val currentTimeFlow: Flow<Instant> = flow {
    while (true) {
        emit(Clock.System.now())
        delay(500)
    }
}
