import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json

private val httpClient = HttpClient {
    install(ContentNegotiation) {
        json()
    }
}

val feedLoader = FeedLoader("https://socialmediacenter.cbruegg.com", httpClient)
