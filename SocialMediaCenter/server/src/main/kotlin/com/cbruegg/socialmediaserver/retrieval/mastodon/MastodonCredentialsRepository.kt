package com.cbruegg.socialmediaserver.retrieval.mastodon

import com.cbruegg.socialmediaserver.Sources
import com.cbruegg.socialmediaserver.shared.MastodonUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class MastodonCredentialsRepository(private val file: File) {
    private val didInitialize = AtomicBoolean(false)
    private var current = MastodonCredentials(emptyMap())

    suspend fun getCredentials(): MastodonCredentials {
        if (!didInitialize.getAndSet(true)) {
            try {
                withContext(Dispatchers.IO) {
                    val inputStream = file.readText()
                    current = Json.decodeFromString<MastodonCredentials>(inputStream)
                }
            } catch (e: Exception) {
                System.err.println("Couldn't load Mastodon credentials, resetting...")
                file.renameTo(File(file.parent, "${file.name}.bak"))
                file.writeText(Json.encodeToString(current))
            }
        }

        return current
    }

    suspend fun updateWith(mastodonCredentials: MastodonCredentials) {
        current = mastodonCredentials
        withContext(Dispatchers.IO) {
            file.writeText(Json.encodeToString(mastodonCredentials))
        }
    }

    suspend fun update(updater: (MastodonCredentials) -> MastodonCredentials) {
        val updated = updater(getCredentials())
        updateWith(updated)
    }

    suspend fun findMissingCredentials(sources: Sources): List<MastodonUser> {
        val credentials = getCredentials()
        return sources.mastodonFollowings.filter { credentials.findClientConfiguration(it) == null }
    }
}