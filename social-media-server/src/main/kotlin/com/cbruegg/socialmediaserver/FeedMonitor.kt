package com.cbruegg.socialmediaserver

import com.cbruegg.socialmediaserver.retrieval.FeedItem
import com.cbruegg.socialmediaserver.retrieval.PlatformId
import com.cbruegg.socialmediaserver.retrieval.SocialPlatform
import com.cbruegg.socialmediaserver.retrieval.oldestAcceptedFeedItemInstant
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.*
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

class FeedMonitor(
    private val platforms: List<SocialPlatform>,
    private val minFetchInterval: Duration = 1.minutes,
    private val maxFetchInterval: Duration = 5.minutes
) {

    private val mutex = Mutex()
    private val feedByPlatform = mutableMapOf<PlatformId, MutableSet<WrappedFeedItem>>()

    private val nextFetchInterval
        get() = Random.nextLong(minFetchInterval.inWholeMilliseconds, maxFetchInterval.inWholeMilliseconds).milliseconds

    fun start(scope: CoroutineScope): Job = scope.launch {
        while (isActive) {
            update()
            delay(nextFetchInterval)
        }
    }

    suspend fun update(platformIds: Set<PlatformId> = EnumSet.allOf(PlatformId::class.java)) = coroutineScope {
        val platformFeeds = platforms
            .filter { it.platformId in platformIds }
            .map { it.platformId to async { it.getFeed() } }
            .map { (platformId, asyncFeed) -> platformId to runCatching { asyncFeed.await() } }
            .asSequence()
            .onEach { (platformId, feedResult) ->
                if (feedResult.isFailure) {
                    System.err.println("Refreshing platform $platformId failed, ignoring!")
                }
            }
            .filter { (_, feedResult) -> feedResult.isSuccess }
            .map { (platformId, feedResult) -> platformId to feedResult.getOrThrow() }

        mutex.withLock {
            for ((platformId, platformFeed) in platformFeeds) {
                val storedPlatformFeed = feedByPlatform.getOrPut(platformId) { mutableSetOf() }
                storedPlatformFeed += platformFeed.map { WrappedFeedItem(it) }
                storedPlatformFeed.removeIf { it.feedItem.published < oldestAcceptedFeedItemInstant }
            }
        }
    }

    /**
     * @param isCorsRestricted If true, image URLs will be replaced with CORS-free ones
     */
    suspend fun getMergedFeed(isCorsRestricted: Boolean): List<FeedItem> = mutex.withLock {
        feedByPlatform.values
            .flatMap { wrappedFeedItems -> wrappedFeedItems.asSequence() }
            .map { it.feedItem }
            .map {
                if (isCorsRestricted && it.authorImageUrl != null)
                    it.withProxiedUrl()
                else
                    it
            }
            .sortedByDescending { it.published }
    }
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
