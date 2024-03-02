import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json

private val httpClient = HttpClient {
    install(ContentNegotiation) {
        json()
    }
    install(HttpRequestRetry)
}

val feedLoader = FeedLoader("https://socialmediacenter.cbruegg.com", httpClient)
