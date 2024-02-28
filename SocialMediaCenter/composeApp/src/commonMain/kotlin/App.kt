import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Button
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.ui.tooling.preview.Preview

import socialmediacenter.composeapp.generated.resources.Res
import socialmediacenter.composeapp.generated.resources.compose_multiplatform

@OptIn(ExperimentalResourceApi::class)
@Composable
@Preview
fun App() {
    val httpClient = HttpClient {
        install(ContentNegotiation) {
            json()
        }
    }
    val feedLoader = FeedLoader("https://socialmediacenter.cbruegg.com", httpClient)

    MaterialTheme {
        var showContent by remember { mutableStateOf(false) }
        var feedItemResult: Result<List<FeedItem>>? by remember { mutableStateOf(null) }

        LaunchedEffect(Unit) {
            launch {
                feedItemResult = feedLoader.fetch()
            }
        }

        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            feedItemResult?.let {
                it.fold(
                    onSuccess = { feedItems ->
                        Text(feedItems.getOrNull(0)?.text ?: "No items")
                    },
                    onFailure = { loadError ->
                        Text("Loading error! ${loadError.message}")
                    }
                )
            } ?: run {
                CircularProgressIndicator()
            }


            Button(onClick = { showContent = !showContent }) {
                Text("Click me!")
            }
            AnimatedVisibility(showContent) {
                val greeting = remember { Greeting().greet() }
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Image(painterResource(Res.drawable.compose_multiplatform), null)
                    Text("Compose: $greeting")
                }
            }
        }
    }
}