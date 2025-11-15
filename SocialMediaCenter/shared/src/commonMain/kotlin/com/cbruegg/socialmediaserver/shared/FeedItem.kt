package com.cbruegg.socialmediaserver.shared

import kotlin.time.Instant
import kotlinx.serialization.Serializable
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
@Serializable
data class FeedItem(
    val text: String,
    val author: String,
    val authorImageUrl: String?,
    val id: String,
    val published: Instant,
    val link: String?,
    val platform: PlatformId,
    val quotedPost: FeedItem?,
    val mediaAttachments: List<MediaAttachment> = emptyList(),
    val repostMeta: RepostMeta? = null
)

@OptIn(ExperimentalTime::class)
@Serializable
data class RepostMeta(
    val repostingAuthor: String,
    val repostingAuthorImageUrl: String?,
    val timeOfRepost: Instant
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