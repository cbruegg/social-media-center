package com.cbruegg.socialmediaserver

import com.cbruegg.socialmediaserver.retrieval.Twitter
import com.cbruegg.socialmediaserver.retrieval.mastodon.Mastodon
import com.cbruegg.socialmediaserver.retrieval.mastodon.MastodonCredentialsRepository
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.io.path.Path
import kotlin.io.path.readText

// TODO Rename twrss to twadapter

suspend fun main(args: Array<String>): Unit = coroutineScope {
    val twitterScriptLocation = args.getOrNull(0) ?: error("Missing twitterScriptLocation")
    val dataLocation = args.getOrNull(1) ?: error("Missing dataLocation")
    val port = args.getOrNull(2)?.toIntOrNull() ?: 8000
    val socialMediaCenterWebLocation = args.getOrNull(3) ?: error("Missing socialMediaCenterWebLocation")

    println("twitterScriptLocation=$twitterScriptLocation, dataLocation=$dataLocation, port=$port, socialMediaCenterWebLocation=$socialMediaCenterWebLocation")

    val mastodonCredentialsRepository = MastodonCredentialsRepository(File(dataLocation, "credentials_mastodon.json"))
    val sources = loadSources(dataLocation)

    if (sources.mastodonFollowings.isNotEmpty()) {
        // TODO Check if all servers are authorized in config already, otherwise show in app UI link to authorize
    }

    val feedMonitor = createFeedMonitor(twitterScriptLocation, dataLocation, sources, mastodonCredentialsRepository)
    feedMonitor.start(scope = this)

    val httpClient = HttpClient(CIO)

    embeddedServer(Netty, port) {
        install(ContentNegotiation) {
            json()
        }
        install(CORS) {
            anyHost()
        }
        install(Sessions) {
            cookie<MastodonAuthSession>("mastodon_auth_session")
        }

        routing {
            installRoutes(feedMonitor, httpClient, sources, mastodonCredentialsRepository, File(socialMediaCenterWebLocation))
        }
    }.start(wait = true)
}

private fun createFeedMonitor(
    twitterScriptLocation: String,
    dataLocation: String,
    sources: Sources,
    mastodonCredentialsRepository: MastodonCredentialsRepository
): FeedMonitor {
    val platforms = listOf(
        Twitter(sources.twitterLists, twitterScriptLocation, dataLocation),
        Mastodon(sources.mastodonFollowings, mastodonCredentialsRepository)
    )
    return FeedMonitor(platforms)
}

private suspend fun loadSources(dataLocation: String): Sources {
    return withContext(Dispatchers.IO) {
        Json.decodeFromString<Sources>(Path(dataLocation, "sources.json").readText())
    }
}

fun shouldProxyUrlForCors(url: String): Boolean {
    return "twimg.com" in url
}

