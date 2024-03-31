package com.cbruegg.socialmediaserver.retrieval.mastodon

import kotlinx.serialization.Serializable
import social.bigbone.api.entity.Account
import social.bigbone.api.entity.Application
import social.bigbone.api.entity.Token

typealias MastodonInstanceName = String

@Serializable
data class MastodonCredentials(val servers: Map<MastodonInstanceName, MastodonServerCredentials>) {
    fun withClientApplication(instanceName: String, application: Application): MastodonCredentials {
        val server = servers[instanceName]?.copy(clientApplication = application)
            ?: MastodonServerCredentials(instanceName, application, accounts = emptyMap())
        return copy(
            servers = servers + (instanceName to server)
        )
    }

    fun withToken(instanceName: String, account: Account, token: Token): MastodonCredentials {
        val server = servers[instanceName] ?: error("At this point, should always have server config")
        val newServer =
            server.copy(accounts = server.accounts + ("${account.username}@$instanceName" to MastodonAccountToken(account, token)))
        return copy(
            servers = servers + (instanceName to newServer)
        )
    }

    fun findClientConfiguration(user: MastodonUser): Pair<Token, Application>? {
        val instanceName = user.serverWithoutScheme
        val server = servers[instanceName] ?: return null
        val clientApplication = server.clientApplication ?: return null
        val token = server.accounts["${user.username}@$instanceName"]?.token ?: return null
        return token to clientApplication
    }
}

typealias MastodonFullUsername = String

@Serializable
data class MastodonServerCredentials(
    val serverName: String,
    val clientApplication: Application?,
    val accounts: Map<MastodonFullUsername, MastodonAccountToken>
)

@Serializable
data class MastodonAccountToken(
    val account: Account,
    val token: Token
)