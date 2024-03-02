package com.cbruegg.socialmediaserver

import com.cbruegg.socialmediaserver.retrieval.AuthenticatedSocialPlatform
import com.cbruegg.socialmediaserver.retrieval.FeedItem
import com.cbruegg.socialmediaserver.retrieval.PlatformId
import com.cbruegg.socialmediaserver.retrieval.oldestAcceptedFeedItemInstant
import com.cbruegg.socialmediaserver.rss.Rss
import com.cbruegg.socialmediaserver.rss.RssChannel
import com.cbruegg.socialmediaserver.rss.toRssItem
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

class FeedMonitor(
    private val platforms: List<AuthenticatedSocialPlatform>,
    private val minFetchInterval: Duration = 1.minutes,
    private val maxFetchInterval: Duration = 5.minutes
) {

    private val mutex = Mutex()
    private val feedByPlatform = mutableMapOf<PlatformId, MutableSet<WrappedFeedItem>>()

    private val nextFetchInterval
        get() = Random.nextLong(minFetchInterval.inWholeMilliseconds, maxFetchInterval.inWholeMilliseconds).milliseconds

    fun start(scope: CoroutineScope): Job = scope.launch {
        while (isActive) {
            val platformFeeds = platforms.map { async { it.platformId to it.getFeed() } }.awaitAll()

            mutex.withLock {
                for ((platformId, platformFeed) in platformFeeds) {
                    val storedPlatformFeed = feedByPlatform.getOrPut(platformId) { mutableSetOf() }
                    storedPlatformFeed += platformFeed.map { WrappedFeedItem(it) }
                    storedPlatformFeed.removeIf { it.feedItem.published < oldestAcceptedFeedItemInstant }
                }
            }

            delay(nextFetchInterval)
        }
    }

    /**
     * @param isCorsRestricted If true, image URLs will be replaced with CORS-free ones
     * @param baseUrl This server's base URL
     */
    suspend fun getMergedFeed(isCorsRestricted: Boolean, baseUrl: Url): List<FeedItem> = mutex.withLock {
        feedByPlatform.values
            .flatMap { wrappedFeedItems -> wrappedFeedItems.asSequence() }
            .map { it.feedItem }
            .map {
                if (isCorsRestricted && it.authorImageUrl != null && shouldProxyUrlForCors(it.authorImageUrl))
                    it.copy(authorImageUrl = it.authorImageUrl.proxiedUrl(baseUrl))
                else
                    it
            }
            .sortedByDescending { it.published }
    }
}

private fun String.proxiedUrl(baseUrl: Url): String {
    val urlToProxy = this
    return URLBuilder(baseUrl).apply {
        path("proxy")
        parameters.append("url", urlToProxy)
    }.buildString()
}

fun List<FeedItem>.toRssFeed(): Rss {
    return Rss(
        RssChannel(
            title = "Combined Social Media Feed",
            description = "",
            link = "",
            item = this.map { it.toRssItem() }
        )
    )
}

private class WrappedFeedItem(val feedItem: FeedItem) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as WrappedFeedItem

        if (feedItem.id != other.feedItem.id) return false
        if (feedItem.platform != other.feedItem.platform) return false

        return true
    }

    override fun hashCode(): Int {
        var result = feedItem.id.hashCode()
        result = 31 * result + feedItem.platform.hashCode()
        return result
    }
}
