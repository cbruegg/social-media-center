package com.cbruegg.socialmediaserver.retrieval.mastodon

import com.cbruegg.socialmediaserver.retrieval.FeedItem
import com.cbruegg.socialmediaserver.retrieval.PlatformId
import com.cbruegg.socialmediaserver.retrieval.SocialPlatform
import kotlinx.datetime.toKotlinInstant
import kotlinx.serialization.Serializable
import social.bigbone.MastodonClient
import social.bigbone.api.Range
import social.bigbone.api.entity.Status
import java.time.Instant

@Serializable
data class MastodonUser(val server: String, val username: String)

val MastodonUser.serverWithoutScheme get() = server.substringAfter("://")

class Mastodon(
    val followingsOf: List<MastodonUser>,
    private val mastodonCredentialsRepository: MastodonCredentialsRepository
) : SocialPlatform {
    override val platformId = PlatformId.Mastodon

    override suspend fun getFeed(): List<FeedItem> {
        return followingsOf.flatMap { getFeed(it) }
    }

    private suspend fun getFeed(user: MastodonUser): List<FeedItem> {
        val clientConfiguration = mastodonCredentialsRepository.getCredentials().findClientConfiguration(user)
        if (clientConfiguration == null) {
            println("Did not find a clientConfiguration for $user, returning empty feed!")
            return emptyList()
        }

        val (token) = clientConfiguration
        val client = MastodonClient.Builder(user.serverWithoutScheme)
            .accessToken(token.accessToken)
            .build()

        val result = runCatching { client.timelines.getHomeTimeline(Range(limit = 50)).execute().part }
            .map { statuses -> statuses.map { it.toFeedItem() } }

        result.exceptionOrNull()?.printStackTrace()
        return result.getOrDefault(emptyList())
    }

}

private fun Status.toFeedItem(): FeedItem {
    val reblog = reblog
    if (reblog != null) {
        // TODO Add metadata of reposter and display in client
        return reblog.toFeedItem()
    }

    return FeedItem(
        text = content,
        author = account?.acct?.let { "@$it" } ?: "MISSING_ACCOUNT",
        authorImageUrl = account?.avatarStatic,
        id = url,
        published = createdAt.mostPreciseOrFallback(Instant.now()).toKotlinInstant(),
        link = url,
        platform = PlatformId.Mastodon
    )
}
