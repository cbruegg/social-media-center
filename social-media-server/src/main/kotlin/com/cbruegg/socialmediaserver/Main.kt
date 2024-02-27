package com.cbruegg.socialmediaserver

import com.cbruegg.socialmediaserver.retrieval.Mastodon
import com.cbruegg.socialmediaserver.retrieval.MastodonUser
import com.cbruegg.socialmediaserver.retrieval.Twitter
import com.cbruegg.socialmediaserver.retrieval.TwitterUser
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.coroutineScope
import nl.adaptivity.xmlutil.serialization.XML

private fun createFeedMonitor(twitterScriptLocation: String, twitterCredentialsLocation: String): FeedMonitor {
    val twitterUser = TwitterUser("1757481771950621108")
    val mastodonUser = MastodonUser("https://mastodon.green", "cbruegg")
    val platforms = listOf(
        Twitter(twitterUser, twitterScriptLocation, twitterCredentialsLocation),
        Mastodon(mastodonUser)
    )

    return FeedMonitor(platforms)
}

suspend fun main(args: Array<String>) = coroutineScope {
    val twitterScriptLocation = args.getOrElse(0) { "/Users/cbruegg/PycharmProjects/TWRSS/run.sh" }
    val twitterDataLocation = args.getOrElse(1) { "/Users/cbruegg/PycharmProjects/TWRSS" }

    val feedMonitor = createFeedMonitor(twitterScriptLocation, twitterDataLocation)
    feedMonitor.start(scope = this)

    embeddedServer(Netty, port = 8000) {
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