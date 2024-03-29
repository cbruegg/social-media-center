package com.cbruegg.socialmediaserver.retrieval

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlin.time.Duration.Companion.days

val oldestAcceptedFeedItemInstant get() = Clock.System.now() - 2.days

enum class PlatformId { Twitter, Mastodon, BlueSky }

@Serializable
data class FeedItem(
    val text: String,
    val author: String,
    val authorImageUrl: String?,
    val id: String,
    val published: Instant,
    val link: String,
    val platform: PlatformId
)

interface SocialPlatform {
    val platformId: PlatformId
    suspend fun getFeed(): List<FeedItem>
}