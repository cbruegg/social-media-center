package security

import io.ktor.http.HttpHeaders
import kotlinx.serialization.builtins.serializer
import persistence.Persistence

private const val KEY = "AUTH_TOKEN"

class AuthTokenRepository(private val persistence: Persistence) {
    var token = persistence.load(KEY, String.serializer())
        private set

    fun updateToken(value: String) {
        token = value
        persistence.save(KEY, value, String.serializer())
    }
}

val AuthTokenRepository.tokenAsHttpHeader: Pair<String, String>?
    get() = token?.let { HttpHeaders.Authorization to "Bearer $it" }