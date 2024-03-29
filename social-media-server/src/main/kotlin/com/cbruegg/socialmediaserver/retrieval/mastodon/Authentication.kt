package com.cbruegg.socialmediaserver.retrieval.mastodon

import social.bigbone.api.Scope
import social.bigbone.api.entity.Application
import social.bigbone.api.method.AppMethods


val mastodonAppScope = Scope(Scope.READ.ALL)

suspend fun AppMethods.getOrCreateSocialMediaCenterApp(
    instanceName: String,
    mastodonCredentialsRepository: MastodonCredentialsRepository,
    redirectUri: String
): Application {
    val credentials = mastodonCredentialsRepository.getCredentials()
    val serverCredentials = credentials.servers[instanceName]
    if (serverCredentials?.clientApplication != null) {
        return serverCredentials.clientApplication
    }

    val created = createApp(
        clientName = "SocialMediaCenter",
        redirectUris = redirectUri,
        scope = mastodonAppScope,
        website = "https://cbruegg.com/"
    ).execute()

    val newCredentials = credentials.withClientApplication(instanceName, created)
    mastodonCredentialsRepository.updateWith(newCredentials)

    return created
}