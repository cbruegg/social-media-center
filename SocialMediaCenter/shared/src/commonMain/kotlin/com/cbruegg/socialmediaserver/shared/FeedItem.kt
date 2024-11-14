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
    val repost: FeedItem?,
    val mediaAttachments: List<MediaAttachment> = emptyList(),
    /**
     * Gets some special treatment as we mostly handle it like a Mastodon post, but not completely
     */
    val isSkyBridgePost: Boolean = false
)

@Serializable
data class MediaAttachment(
    val type: MediaType,
    val previewImageUrl: String,
    val fullUrl: String
)

enum class MediaType {
    Image, Video, Gifv
}

enum class PlatformId { Twitter, Mastodon, Bluesky }