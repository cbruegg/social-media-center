package com.cbruegg.socialmediaserver

import com.cbruegg.socialmediaserver.retrieval.Twitter
import com.cbruegg.socialmediaserver.retrieval.bluesky.Bluesky
import com.cbruegg.socialmediaserver.retrieval.mastodon.Mastodon
import com.cbruegg.socialmediaserver.retrieval.mastodon.MastodonCredentialsRepository
import com.cbruegg.socialmediaserver.retrieval.security.Auth
import com.cbruegg.socialmediaserver.state.StateRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.UserIdPrincipal
import io.ktor.server.auth.bearer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.routing.routing
import io.ktor.server.sessions.Sessions
import io.ktor.server.sessions.cookie
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

    val stateRepository = StateRepository(File(dataLocation, "state.json"))

    val feedMonitor = createFeedMonitor(twitterScriptLocation, dataLocation, sources, mastodonCredentialsRepository)
    feedMonitor.start(scope = this)

    val auth = Auth(File(dataLocation))
    auth.initialize()

    val httpClient = HttpClient(CIO)

    embeddedServer(Netty, port) {
        install(ContentNegotiation) {
            json()
        }
        install(CORS) {
            anyHost() // this is fine as the API requires an auth token for sensitive operations
            allowMethod(HttpMethod.Options)
            allowHeader(HttpHeaders.Authorization)
            allowHeader(HttpHeaders.Origin)
        }
        install(Sessions) {
            cookie<MastodonAuthSession>("mastodon_auth_session")
        }
        install(Authentication) {
            bearer {
                authenticate { tokenCredential ->
                    println("Received token=$tokenCredential")
                    if (auth.isValidToken(tokenCredential.token)) {
                        println("Token is valid")
                        UserIdPrincipal("admin")
                    } else {
                        println("Token is invalid")
                        null
                    }
                }
            }
        }

        routing {
            installRoutes(
                feedMonitor = feedMonitor,
                httpClient = httpClient,
                sources = sources,
                mastodonCredentialsRepository = mastodonCredentialsRepository,
                socialMediaCenterWebLocation = File(socialMediaCenterWebLocation),
                stateRepository = stateRepository
            )
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
        Mastodon(sources.mastodonFollowings, mastodonCredentialsRepository),
        Bluesky(sources.blueskyFollowings)
    )
    return FeedMonitor(platforms)
}

private suspend fun loadSources(dataLocation: String): Sources {
    return withContext(Dispatchers.IO) {
        Json.decodeFromString<Sources>(Path(dataLocation, "sources.json").readText())
    }
}
