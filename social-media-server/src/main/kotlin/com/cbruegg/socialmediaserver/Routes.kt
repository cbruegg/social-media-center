package com.cbruegg.socialmediaserver

import com.cbruegg.socialmediaserver.retrieval.PlatformId
import com.cbruegg.socialmediaserver.retrieval.mastodon.MastodonCredentialsRepository
import com.cbruegg.socialmediaserver.retrieval.mastodon.getOrCreateSocialMediaCenterApp
import com.cbruegg.socialmediaserver.retrieval.mastodon.mastodonAppScope
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
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
    get("/json") {
        val isCorsRestricted = context.request.queryParameters["isCorsRestricted"] == "true"
        val mergedFeed = feedMonitor.getMergedFeed(isCorsRestricted)
        call.respond(mergedFeed)
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
        // TODO Maybe we don't need this anymore? Does response from Mastodon perhaps include URL to status on app user server?
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
        val socialMediaCenterBaseUrl = context.request.queryParameters["socialMediaCenterBaseUrl"]
        if (instanceName == null || socialMediaCenterBaseUrl == null) {
            call.respond(HttpStatusCode.BadRequest)
            return@get
        }

        val client = MastodonClient.Builder(instanceName).build()
        val appRegistration = client.apps.getOrCreateSocialMediaCenterApp(
            instanceName,
            mastodonCredentialsRepository,
            getMastodonAuthRedirectUri(instanceName, socialMediaCenterBaseUrl)
        )
        val clientId = appRegistration.clientId

        if (clientId == null) {
            call.respond(HttpStatusCode.BadGateway, "Invalid client configuration")
            return@get
        }

        val oauthUrl = client.oauth.getOAuthUrl(
            clientId = clientId,
            redirectUri = getMastodonAuthRedirectUri(instanceName, socialMediaCenterBaseUrl),
            scope = mastodonAppScope
        )
        call.respondRedirect(oauthUrl)
    }
    get(MASTODON_COMPLETE_AUTH_URL) {
        val authCode = context.request.queryParameters["code"]
        val instanceName = context.request.queryParameters["instanceName"]
        val socialMediaCenterBaseUrl = context.request.queryParameters["socialMediaCenterBaseUrl"]
        if (authCode == null || instanceName == null || socialMediaCenterBaseUrl == null) {
            call.respond(HttpStatusCode.BadRequest)
            return@get
        }

        val client = MastodonClient.Builder(instanceName).build()
        val appRegistration = client.apps.getOrCreateSocialMediaCenterApp(
            instanceName,
            mastodonCredentialsRepository,
            getMastodonAuthRedirectUri(instanceName, socialMediaCenterBaseUrl)
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
            redirectUri = getMastodonAuthRedirectUri(instanceName, socialMediaCenterBaseUrl),
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
    get("/unauthenticated-mastodon-accounts") {
        call.respond(mastodonCredentialsRepository.findMissingCredentials(sources))
    }
    staticFiles("/", socialMediaCenterWebLocation)
}

private fun getMastodonAuthRedirectUri(mastodonInstanceName: String, socialMediaCenterBaseUrl: String): String {
    return "$socialMediaCenterBaseUrl/$MASTODON_COMPLETE_AUTH_URL?instanceName=$mastodonInstanceName&socialMediaCenterBaseUrl=$socialMediaCenterBaseUrl"
}
