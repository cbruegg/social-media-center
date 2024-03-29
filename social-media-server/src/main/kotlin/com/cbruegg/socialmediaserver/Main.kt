package com.cbruegg.socialmediaserver

import com.cbruegg.socialmediaserver.retrieval.PlatformId
import com.cbruegg.socialmediaserver.retrieval.Twitter
import com.cbruegg.socialmediaserver.retrieval.mastodon.Mastodon
import com.cbruegg.socialmediaserver.retrieval.mastodon.MastodonCredentialsRepository
import com.cbruegg.socialmediaserver.retrieval.mastodon.getOrCreateSocialMediaCenterApp
import com.cbruegg.socialmediaserver.retrieval.mastodon.mastodonAppScope
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
import io.ktor.server.plugins.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.pipeline.*
import io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import nl.adaptivity.xmlutil.serialization.XML
import social.bigbone.MastodonClient
import java.io.File
import kotlin.io.path.Path
import kotlin.io.path.readText

// TODO Rename twrss to twadapter

private const val MASTODON_COMPLETE_AUTH_URL = "/authorize/mastodon/complete"

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

        routing {
            get("/json") {
                val isCorsRestricted = context.request.queryParameters["isCorsRestricted"] == "true"
                val mergedFeed = feedMonitor.getMergedFeed(isCorsRestricted)
                call.respond(mergedFeed)
            }
            get("/rss") {
                // TODO Remove this method
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
            get("/authorize/mastodon/start") {
                val instanceName = context.request.queryParameters["instanceName"]
                if (instanceName == null) {
                    call.respond(HttpStatusCode.BadRequest)
                    return@get
                }

                val client = MastodonClient.Builder(instanceName).build()
                val appRegistration = client.apps.getOrCreateSocialMediaCenterApp(
                    instanceName,
                    mastodonCredentialsRepository,
                    getMastodonAuthRedirectUri(instanceName)
                )
                val clientId = appRegistration.clientId

                if (clientId == null) {
                    call.respond(HttpStatusCode.BadGateway, "Invalid client configuration")
                    return@get
                }

                val oauthUrl = client.oauth.getOAuthUrl(
                    clientId = clientId,
                    redirectUri = getMastodonAuthRedirectUri(instanceName),
                    scope = mastodonAppScope
                )
                call.respondRedirect(oauthUrl)
            }
            get(MASTODON_COMPLETE_AUTH_URL) {
                val authCode = context.request.queryParameters["code"]
                val instanceName = context.request.queryParameters["instanceName"]
                if (authCode == null || instanceName == null) {
                    call.respond(HttpStatusCode.BadRequest)
                    return@get
                }

                val client = MastodonClient.Builder(instanceName).build()
                val appRegistration = client.apps.getOrCreateSocialMediaCenterApp(
                    instanceName,
                    mastodonCredentialsRepository,
                    getMastodonAuthRedirectUri(instanceName)
                )
                val clientId = appRegistration.clientId
                val clientSecret = appRegistration.clientSecret

                if (clientId == null || clientSecret == null) {
                    call.respond(HttpStatusCode.InternalServerError, "Invalid client configuration")
                    return@get
                }

                val token = client.oauth.getUserAccessTokenWithAuthorizationCodeGrant(
                    clientId = clientId,
                    clientSecret = clientSecret,
                    redirectUri = getMastodonAuthRedirectUri(instanceName),
                    code = authCode,
                    scope = mastodonAppScope
                ).execute()

                val authenticatedClient = MastodonClient.Builder(instanceName)
                    .accessToken(token.accessToken)
                    .build()
                val account = authenticatedClient.accounts.verifyCredentials().execute()

                mastodonCredentialsRepository.update {
                    it.withToken(instanceName, account, token)
                }

                runCatching { feedMonitor.update(setOf(PlatformId.Mastodon)) }

                call.respondRedirect("/")
            }
            staticFiles("/", File(socialMediaCenterWebLocation))
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

private fun PipelineContext<*, ApplicationCall>.getMastodonAuthRedirectUri(mastodonInstanceName: String): String {
    val req = context.request.origin
    return "${req.scheme}://${req.serverHost}:${req.serverPort}/$MASTODON_COMPLETE_AUTH_URL?instanceName=$mastodonInstanceName"
}
