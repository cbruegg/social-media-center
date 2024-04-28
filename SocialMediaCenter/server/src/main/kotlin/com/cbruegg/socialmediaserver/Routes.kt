package com.cbruegg.socialmediaserver

import com.cbruegg.socialmediaserver.retrieval.mastodon.MastodonCredentialsRepository
import com.cbruegg.socialmediaserver.retrieval.mastodon.getOrCreateSocialMediaCenterApp
import com.cbruegg.socialmediaserver.retrieval.mastodon.mastodonAppScope
import com.cbruegg.socialmediaserver.shared.PlatformId
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentLength
import io.ktor.http.contentType
import io.ktor.http.decodeURLQueryComponent
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.http.content.staticFiles
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set
import io.ktor.utils.io.copyAndClose
import social.bigbone.MastodonClient
import java.io.File

// TODO: Add authentication

private const val MASTODON_COMPLETE_AUTH_URL = "/authorize/mastodon/complete"

fun Routing.installRoutes(
    feedMonitor: FeedMonitor,
    httpClient: HttpClient,
    sources: Sources,
    mastodonCredentialsRepository: MastodonCredentialsRepository,
    socialMediaCenterWebLocation: File
) {
    authenticate {
        get("/json") {
            val isCorsRestricted = context.request.queryParameters["isCorsRestricted"] == "true"
            val mergedFeed = feedMonitor.getMergedFeed(isCorsRestricted)
            call.respond(mergedFeed)
        }
        get("/proxy") {
            val urlToProxy = context.request.queryParameters["url"]
            if (urlToProxy == null) {
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
        get("/unauthenticated-mastodon-accounts") {
            call.respond(mastodonCredentialsRepository.findMissingCredentials(sources))
        }
    }

    get("/authorize/mastodon/start") {
        val instanceName = context.request.queryParameters["instanceName"]
        val socialMediaCenterBaseUrl =
            context.request.queryParameters["socialMediaCenterBaseUrl"]?.decodeURLQueryComponent()
        if (instanceName == null || socialMediaCenterBaseUrl == null) {
            call.respond(HttpStatusCode.BadRequest)
            return@get
        }

        call.sessions.set(MastodonAuthSession(instanceName, socialMediaCenterBaseUrl))

        val client = MastodonClient.Builder(instanceName).build()
        val appRegistration = client.apps.getOrCreateSocialMediaCenterApp(
            instanceName,
            mastodonCredentialsRepository,
            getMastodonAuthRedirectUri(socialMediaCenterBaseUrl)
        )

        val clientId = appRegistration.clientId

        if (clientId == null) {
            call.respond(HttpStatusCode.BadGateway, "Invalid client configuration")
            return@get
        }

        val oauthUrl = client.oauth.getOAuthUrl(
            clientId = clientId,
            redirectUri = getMastodonAuthRedirectUri(socialMediaCenterBaseUrl),
            scope = mastodonAppScope
        )
        call.respondRedirect(oauthUrl)
    }

    get(MASTODON_COMPLETE_AUTH_URL) {
        val authCode = context.request.queryParameters["code"]
        val authSession = context.sessions.get<MastodonAuthSession>()
        if (authCode == null || authSession == null) {
            call.respond(HttpStatusCode.BadRequest)
            return@get
        }

        val instanceName = authSession.instanceName
        val socialMediaCenterBaseUrl = authSession.socialMediaCenterBaseUrl

        val client = MastodonClient.Builder(instanceName).build()
        val appRegistration = client.apps.getOrCreateSocialMediaCenterApp(
            instanceName,
            mastodonCredentialsRepository,
            getMastodonAuthRedirectUri(socialMediaCenterBaseUrl)
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
            redirectUri = getMastodonAuthRedirectUri(socialMediaCenterBaseUrl),
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
    staticFiles("/", socialMediaCenterWebLocation)
}

private fun getMastodonAuthRedirectUri(socialMediaCenterBaseUrl: String): String {
    return "$socialMediaCenterBaseUrl/$MASTODON_COMPLETE_AUTH_URL"
}

data class MastodonAuthSession(val instanceName: String, val socialMediaCenterBaseUrl: String)