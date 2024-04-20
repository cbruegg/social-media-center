package com.cbruegg.socialmediaserver.shared

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class FeedItem(
    val text: String,
    val author: String,
    val authorImageUrl: String?,
    val id: String,
    val published: Instant,
    val link: String?,
    val platform: PlatformId,
    val repost: FeedItem?
)

enum class PlatformId { Twitter, Mastodon, BlueSky }