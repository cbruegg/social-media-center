package com.cbruegg.socialmediaserver.retrieval

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

data class TwitterUser(val listId: String)

class Twitter(
    val user: TwitterUser,
    private val twitterScriptLocation: String,
    private val dataLocation: String
) : AuthenticatedSocialPlatform {
    override val platformId: PlatformId = PlatformId.Twitter

    override suspend fun getFeed(): List<FeedItem> {
        val output = withContext(Dispatchers.IO) {
            val process =
                ProcessBuilder()
                    .command(twitterScriptLocation, user.listId, dataLocation)
                    .redirectError(ProcessBuilder.Redirect.INHERIT)
                    .start()
            val output = process.inputReader().readText()
            process.destroy()
            output
        }

        println("Got output from Twitter: $output")

        val feedItemsResult = runCatching { Json.decodeFromString<List<FeedItem>>(output) }
        feedItemsResult.exceptionOrNull()?.printStackTrace()
        return feedItemsResult.getOrDefault(emptyList())
    }

}