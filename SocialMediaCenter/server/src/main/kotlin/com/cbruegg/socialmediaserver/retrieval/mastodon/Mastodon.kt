package com.cbruegg.socialmediaserver.retrieval.mastodon

import com.cbruegg.socialmediaserver.retrieval.SocialPlatform
import com.cbruegg.socialmediaserver.shared.FeedItem
import com.cbruegg.socialmediaserver.shared.MastodonUser
import com.cbruegg.socialmediaserver.shared.MediaAttachment
import com.cbruegg.socialmediaserver.shared.MediaType
import com.cbruegg.socialmediaserver.shared.PlatformId
import com.cbruegg.socialmediaserver.shared.serverWithoutScheme
import kotlinx.datetime.toKotlinInstant
import social.bigbone.MastodonClient
import social.bigbone.api.Range
import social.bigbone.api.entity.Status
import java.time.Instant
import social.bigbone.api.entity.MediaAttachment as MastodonMediaAttachment


class Mastodon(
    private val followingsOf: List<MastodonUser>,
    private val mastodonCredentialsRepository: MastodonCredentialsRepository
) : SocialPlatform {
    override val platformId = PlatformId.Mastodon

    override suspend fun getFeed(): List<FeedItem> {
        return followingsOf.flatMap { getFeed(it) }
    }

    private suspend fun getFeed(user: MastodonUser): List<FeedItem> {
        val clientConfiguration =
            mastodonCredentialsRepository.getCredentials().findClientConfiguration(user)
        if (clientConfiguration == null) {
            println("Did not find a clientConfiguration for $user, returning empty feed!")
            return emptyList()
        }

        val (token) = clientConfiguration
        val client = MastodonClient.Builder(user.serverWithoutScheme)
            .accessToken(token.accessToken)
            .build()

        val result =
            runCatching { client.timelines.getHomeTimeline(Range(limit = 50)).execute().part }
                .map { statuses -> statuses.map { it.toFeedItem(user.server) } }

        result.exceptionOrNull()?.printStackTrace()
        return result.getOrDefault(emptyList())
    }

}

private fun Status.toFeedItem(userServerBaseUrl: String): FeedItem {
    val isSkyBridge = application?.name == "Bluesky"
    val id = id.takeIf { it.isNotEmpty() }
    val account = account
    val url = if (!isSkyBridge && id != null && account != null) {
        // Skybridge does not implement ACCT, so we can't use this
        "$userServerBaseUrl/@${account.acct}/$id"
    } else if (url.isNotEmpty()) {
        url
    } else {
        uri
    }
    return FeedItem(
        text = content,
        author = account?.acct?.let { "@$it" } ?: "MISSING_ACCOUNT",
        authorImageUrl = account?.avatarStatic,
        id = id ?: uri,
        published = createdAt.mostPreciseOrFallback(Instant.now()).toKotlinInstant(),
        link = url.takeIf { it.isNotEmpty() },
        platform = PlatformId.Mastodon,
        repost = reblog?.toFeedItem(userServerBaseUrl),
        mediaAttachments = mediaAttachments.mapNotNull { it.toMediaAttachment() },
        isSkyBridgePost = isSkyBridge
    )
}

private fun MastodonMediaAttachment.toMediaAttachment(): MediaAttachment? {
    val type = when (type) {
        MastodonMediaAttachment.MediaType.AUDIO -> return null // unsupported
        MastodonMediaAttachment.MediaType.IMAGE -> MediaType.Image
        MastodonMediaAttachment.MediaType.VIDEO -> MediaType.Video
        MastodonMediaAttachment.MediaType.GIFV -> MediaType.Gifv
        MastodonMediaAttachment.MediaType.UNKNOWN -> return null
    }
    val url = url ?: return null
    return MediaAttachment(
        type = type,
        previewImageUrl = previewUrl,
        fullUrl = url
    )
}
