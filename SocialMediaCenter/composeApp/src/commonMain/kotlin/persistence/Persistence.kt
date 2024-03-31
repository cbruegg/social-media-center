package persistence

import kotlinx.serialization.KSerializer

interface Persistence {
    fun <T : Any> save(key: String, value: T, serializer: KSerializer<T>)
    fun <T : Any> load(key: String, serializer: KSerializer<T>): T?
}
