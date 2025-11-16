package com.cbruegg.socialmediaserver.retrieval.mastodon

import com.cbruegg.socialmediaserver.retrieval.SocialPlatform
import com.cbruegg.socialmediaserver.shared.FeedItem
import com.cbruegg.socialmediaserver.shared.MastodonUser
import com.cbruegg.socialmediaserver.shared.MediaAttachment
import com.cbruegg.socialmediaserver.shared.MediaType
import com.cbruegg.socialmediaserver.shared.PlatformId
import com.cbruegg.socialmediaserver.shared.RepostMeta
import com.cbruegg.socialmediaserver.shared.serverWithoutScheme
import org.jsoup.Jsoup
import social.bigbone.MastodonClient
import social.bigbone.api.Range
import social.bigbone.api.entity.Quote
import social.bigbone.api.entity.Status
import social.bigbone.api.exception.BigBoneRequestException
import java.time.Instant
import kotlin.time.ExperimentalTime
import kotlin.time.toKotlinInstant
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

        val result =
            runCatching {
                val client = MastodonClient.Builder(user.serverWithoutScheme)
                    .accessToken(token.accessToken)
                    .build() // can throw
                val statuses = client.timelines.getHomeTimeline(Range(limit = 50)).execute().part
                statuses.map { it.toFeedItem(user.server, client) }
            }

        result.exceptionOrNull()?.printStackTrace()
        return result.getOrDefault(emptyList())
    }

}

@Throws(BigBoneRequestException::class)
@OptIn(ExperimentalTime::class)
private suspend fun Status.toFeedItem(userServerBaseUrl: String, client: MastodonClient): FeedItem {
    val id = id.takeIf { it.isNotEmpty() }
    val account = account
    val reblog = reblog
    val quote = quote
    val url = if (id != null && account != null) {
        "$userServerBaseUrl/@${account.acct}/$id"
    } else if (url.isNotEmpty()) {
        url
    } else {
        uri
    }
    if (content.isEmpty() && reblog != null) {
        return reblog.toFeedItem(userServerBaseUrl, client).copy(
            repostMeta = RepostMeta(
                repostingAuthor = account?.acct?.let { "@$it" } ?: "MISSING_ACCOUNT",
                repostingAuthorImageUrl = account?.avatarStatic,
                timeOfRepost = createdAt.mostPreciseOrFallback(Instant.now()).toKotlinInstant()
            )
        )
    } else {
        return FeedItem(
            text = if (quote == null) content else content.withoutInlineQuotes(),
            author = account?.acct?.let { "@$it" } ?: "MISSING_ACCOUNT",
            authorImageUrl = account?.avatarStatic,
            id = id ?: uri,
            published = createdAt.mostPreciseOrFallback(Instant.now()).toKotlinInstant(),
            link = url.takeIf { it.isNotEmpty() },
            platform = PlatformId.Mastodon,
            quotedPost = quote?.toFeedItem(userServerBaseUrl, client)
                ?: reblog?.toFeedItem(userServerBaseUrl, client),
            mediaAttachments = mediaAttachments.mapNotNull { it.toMediaAttachment() }
        )
    }
}

/**
 * Mastodon introduced quotes with backwards-compatibility: In addition to the `quotes` property,
 * it also includes "RE: <link>" in the post body. As we handle the quotes property explicitly,
 * we can remove the inline quote.
 */
private fun String.withoutInlineQuotes(): String {
    val parsed = Jsoup.parseBodyFragment(this)
    parsed.getElementsByClass("quote-inline").forEach { it.remove() }
    return parsed.body().html()
}

@Throws(BigBoneRequestException::class)
private suspend fun Quote.toFeedItem(userServerBaseUrl: String, client: MastodonClient): FeedItem? {
    if (state != Quote.State.ACCEPTED) return null // quote was not accepted by original poster

    val status = quotedStatus ?: client.statuses
        .getStatus(requireNotNull(quotedStatusId) { "In ACCEPTED state, either quotedStatus or quotedStatusId should be set" })
        .execute()

    return status.toFeedItem(userServerBaseUrl, client)
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
