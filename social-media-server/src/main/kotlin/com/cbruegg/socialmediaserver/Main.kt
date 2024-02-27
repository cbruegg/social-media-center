package com.cbruegg.socialmediaserver

import com.cbruegg.socialmediaserver.retrieval.Mastodon
import com.cbruegg.socialmediaserver.retrieval.Twitter
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import nl.adaptivity.xmlutil.serialization.XML
import kotlin.io.path.Path
import kotlin.io.path.readText

// TODO Rename twrss to twadapter

suspend fun main(args: Array<String>) = coroutineScope {
    val twitterScriptLocation = args.getOrNull(0) ?: error("Missing twitterScriptLocation")
    val dataLocation = args.getOrNull(1) ?: error("Missing dataLocation")
    val port = args.getOrNull(2)?.toIntOrNull() ?: 8000

    val feedMonitor = createFeedMonitor(twitterScriptLocation, dataLocation)
    feedMonitor.start(scope = this)

    embeddedServer(Netty, port) {
        install(ContentNegotiation) {
            json()
        }

        routing {
            get("/json") {
                val mergedFeed = feedMonitor.getMergedFeed()
                call.respond(mergedFeed)
            }
            get("/rss") {
                val mergedFeed = feedMonitor.getMergedFeed()
                call.respond(XML.encodeToString(mergedFeed.toRssFeed()))
            }
        }
    }.start(wait = true)

    return@coroutineScope
}

private suspend fun createFeedMonitor(twitterScriptLocation: String, dataLocation: String): FeedMonitor {
    val sources = withContext(Dispatchers.IO) {
        Json.decodeFromString<Sources>(Path(dataLocation).readText())
    }

    val platforms = listOf(
        Twitter(sources.twitterLists, twitterScriptLocation, dataLocation),
        Mastodon(sources.mastodonFollowings)
    )
    return FeedMonitor(platforms)
}