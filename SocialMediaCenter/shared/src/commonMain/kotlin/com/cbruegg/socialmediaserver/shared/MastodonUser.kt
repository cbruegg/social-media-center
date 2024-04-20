package com.cbruegg.socialmediaserver.shared

import kotlinx.serialization.Serializable

@Serializable
data class MastodonUser(val server: String, val username: String) {
    init {
        check(server.startsWith("http")) { "Invalid configuration, server should start with http(s)://" }
    }
}

val MastodonUser.serverWithoutScheme get() = server.substringAfter("://")