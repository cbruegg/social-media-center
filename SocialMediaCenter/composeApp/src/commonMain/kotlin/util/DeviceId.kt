package util

import getPlatform
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.serializer
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private const val DEVICE_ID_KEY = "device_id"
private val mutex = Mutex()

suspend fun getDeviceId(): String =
    mutex.withLock {
        val existing = getPlatform().persistence.load(DEVICE_ID_KEY, String.serializer())

        if (existing != null) {
            existing
        } else {
            val new = generateDeviceId()
            getPlatform().persistence.save(DEVICE_ID_KEY, new, String.serializer())
            new
        }
    }

@OptIn(ExperimentalUuidApi::class)
private fun generateDeviceId(): String = Uuid.random().toHexDashString()