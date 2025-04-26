package com.cbruegg.socialmediaserver.state

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

class StateRepository(private val file: File) {
    private var state: State? = null
    private val mutex = Mutex()

    private suspend fun restoreFromDiskIfNeededWithoutLock(): State {
        val currentState = state
        if (currentState != null) {
            return currentState
        }

        return withContext(Dispatchers.IO) {
            try {
                val inputStream = file.readText()
                val decoded = Json.decodeFromString<State>(inputStream)
                state = decoded

                decoded
            } catch (e: Exception) {
                System.err.println("Couldn't load state, resetting...")
                file.renameTo(File(file.parent, "${file.name}.bak"))
                val newState = State()
                file.writeText(Json.encodeToString(newState))

                newState
            }
        }
    }

    private suspend fun saveToDiskWithoutLock() {
        val currentState = state ?: return
        withContext(Dispatchers.IO) {
            file.writeText(Json.encodeToString(currentState))
        }
    }

    suspend fun getState(): State {
        return mutex.withLock { restoreFromDiskIfNeededWithoutLock() }
    }

    suspend fun updateWith(deviceId: String, firstVisibleItemId: String) {
        mutex.withLock {
            val currentState = restoreFromDiskIfNeededWithoutLock()
            val updatedState = currentState.copy(
                deviceIdToFirstVisibleItem = currentState.deviceIdToFirstVisibleItem + (deviceId to firstVisibleItemId),
            )
            state = updatedState
            saveToDiskWithoutLock()
        }
    }
}
