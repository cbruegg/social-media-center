package security

import io.ktor.http.HttpHeaders
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import persistence.Persistence
import persistence.load
import persistence.save

private const val KEY_TOKEN = "AUTH_TOKEN"
private const val KEY_BASE_URL = "BASE_URL"

class ServerConfig(private val persistence: Persistence) {
    var token: String? = persistence.load(KEY_TOKEN)
        private set

    private val _baseUrl: MutableStateFlow<String?> = MutableStateFlow(persistence.load(KEY_BASE_URL))
    val baseUrl = _baseUrl.asStateFlow()

    fun updateToken(value: String) {
        token = value
        persistence.save(KEY_TOKEN, value)
    }

    fun updateBaseUrl(value: String) {
        _baseUrl.value = value
        persistence.save(KEY_BASE_URL, value)
    }
}

val ServerConfig.tokenAsHttpHeader: Pair<String, String>?
    get() = token?.let { HttpHeaders.Authorization to "Bearer $it" }