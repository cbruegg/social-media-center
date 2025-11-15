package com.cbruegg.socialmediaserver.retrieval

import com.cbruegg.socialmediaserver.shared.FeedItem
import com.cbruegg.socialmediaserver.shared.PlatformId
import io.ktor.http.encodeURLQueryComponent
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
val oldestAcceptedFeedItemInstant get() = Clock.System.now() - 2.days

@OptIn(ExperimentalTime::class)
fun FeedItem.withProxiedUrl(): FeedItem = copy(
    authorImageUrl = authorImageUrl?.proxiedUrl(),
    quotedPost = quotedPost?.withProxiedUrl(),
    mediaAttachments = mediaAttachments.map {
        it.copy(
            previewImageUrl = it.previewImageUrl.proxiedUrl(),
            fullUrl = it.fullUrl.proxiedUrl()
        )
    },
    repostMeta = repostMeta?.let {
        it.copy(repostingAuthorImageUrl = it.repostingAuthorImageUrl?.proxiedUrl())
    }
)

private fun String.proxiedUrl(): String {
    return "/proxy?url=${this.encodeURLQueryComponent()}"
}

interface SocialPlatform {
    val platformId: PlatformId
    suspend fun getFeed(): List<FeedItem>
}