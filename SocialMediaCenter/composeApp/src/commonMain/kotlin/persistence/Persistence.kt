package persistence

import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer

interface Persistence {
    fun <T : Any> save(key: String, value: T, serializer: KSerializer<T>)
    fun <T : Any> load(key: String, serializer: KSerializer<T>): T?
}

inline fun <reified T : Any> Persistence.save(key: String, value: T) =
    save(key, value, serializer())

inline fun <reified T : Any> Persistence.load(key: String) =
    load(key, serializer<T>())
