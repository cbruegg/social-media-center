package util

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

val currentTimeFlow: Flow<Instant> = flow {
    while (true) {
        emit(Clock.System.now())
        delay(500)
    }
}
