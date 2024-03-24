package com.cbruegg.socialmediaserver

import com.cbruegg.socialmediaserver.retrieval.Mastodon
import com.cbruegg.socialmediaserver.retrieval.Twitter
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.http.content.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import nl.adaptivity.xmlutil.serialization.XML
import java.io.File
import kotlin.io.path.Path
import kotlin.io.path.readText

// TODO Rename twrss to twadapter

suspend fun main(args: Array<String>) = coroutineScope {
    val twitterScriptLocation = args.getOrNull(0) ?: error("Missing twitterScriptLocation")
    val dataLocation = args.getOrNull(1) ?: error("Missing dataLocation")
    val port = args.getOrNull(2)?.toIntOrNull() ?: 8000
    val socialMediaCenterWebLocation = args.getOrNull(3) ?: error("Missing socialMediaCenterWebLocation")

    println("twitterScriptLocation=$twitterScriptLocation, dataLocation=$dataLocation, port=$port, socialMediaCenterWebLocation=$socialMediaCenterWebLocation")

    val sources = loadSources(dataLocation)
    val feedMonitor = createFeedMonitor(twitterScriptLocation, dataLocation, sources)
    feedMonitor.start(scope = this)

    val httpClient = HttpClient(CIO)

    embeddedServer(Netty, port) {
        install(ContentNegotiation) {
            json()
        }
        install(CORS) {
            anyHost()
        }

        routing {
            get("/json") {
                val isCorsRestricted = context.request.queryParameters["isCorsRestricted"] == "true"
                val mergedFeed = feedMonitor.getMergedFeed(isCorsRestricted)
                call.respond(mergedFeed)
            }
            get("/rss") {
                val mergedFeed = feedMonitor.getMergedFeed(isCorsRestricted = true)
                call.respond(XML.encodeToString(mergedFeed.toRssFeed()))
            }
            get("/proxy") {
                val urlToProxy = context.request.queryParameters["url"]
                if (urlToProxy == null || !shouldProxyUrlForCors(urlToProxy)) {
                    call.respond(HttpStatusCode.BadRequest)
                    return@get
                }

                val upstreamResponse = httpClient.get(urlToProxy)
                val upstreamResponseChannel = upstreamResponse.bodyAsChannel()
                call.respondBytesWriter(
                    contentType = upstreamResponse.contentType(),
                    status = upstreamResponse.status,
                    contentLength = upstreamResponse.contentLength()
                ) {
                    upstreamResponseChannel.copyAndClose(this)
                }
            }
            get("/mastodon-status") {
                val statusUrlOnAuthorServer: String? = context.request.queryParameters["statusUrl"]
                if (statusUrlOnAuthorServer == null) {
                    call.respond(HttpStatusCode.BadRequest)
                    return@get
                }

                // just pick the first user server:
                val mastodonServerOfUser = sources.mastodonFollowings.map { it.server }.firstOrNull()
                if (mastodonServerOfUser == null) {
                    // No Mastodon configured, just redirect to author server
                    call.respondRedirect(statusUrlOnAuthorServer)
                    return@get
                }

                val statusOnUserServerUrl = "$mastodonServerOfUser/authorize_interaction?uri=$statusUrlOnAuthorServer"
                call.respondRedirect(statusOnUserServerUrl)

            }
            staticFiles("/", File(socialMediaCenterWebLocation))
        }
    }.start(wait = true)

    return@coroutineScope
}

private fun createFeedMonitor(twitterScriptLocation: String, dataLocation: String, sources: Sources): FeedMonitor {
    val platforms = listOf(
        Twitter(sources.twitterLists, twitterScriptLocation, dataLocation),
        Mastodon(sources.mastodonFollowings)
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