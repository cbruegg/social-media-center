package com.cbruegg.socialmediaserver.retrieval

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class TwitterList(val id: String)

class Twitter(
    private val listIds: List<TwitterList>,
    private val twitterScriptLocation: String,
    private val dataLocation: String
) : SocialPlatform {
    override val platformId: PlatformId = PlatformId.Twitter

    override suspend fun getFeed(): List<FeedItem> {
        return listIds.flatMap { getFeed(it.id) }
    }

    private suspend fun getFeed(listId: String): List<FeedItem> {
        val output = withContext(Dispatchers.IO) {
            val process =
                ProcessBuilder()
                    .command(twitterScriptLocation, listId, dataLocation)
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