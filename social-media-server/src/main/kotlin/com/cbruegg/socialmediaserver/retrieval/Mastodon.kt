package com.cbruegg.socialmediaserver.retrieval

import com.cbruegg.socialmediaserver.rss.Rss
import com.cbruegg.socialmediaserver.rss.mastodonAuthor
import com.cbruegg.socialmediaserver.rss.rssContentType
import com.cbruegg.socialmediaserver.rss.toFeedItem
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.serialization.kotlinx.xml.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import nl.adaptivity.xmlutil.serialization.XML

// TODO Also support atom (`https://notnow.dev/users/zhuowei/feed.atom`)

@Serializable
data class MastodonUser(val server: String, val username: String)

class Mastodon(val followingsOf: List<MastodonUser>) : AuthenticatedSocialPlatform {
    override val platformId = PlatformId.Mastodon

    private val http = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
            })
            xml(XML {
                defaultPolicy {
                    ignoreUnknownChildren()
                }
            }, contentType = rssContentType)
        }
    }

    override suspend fun getFeed(): List<FeedItem> {
        return followingsOf.flatMap { getFeed(it) }
    }

    private suspend fun getFeed(user: MastodonUser): List<FeedItem> {
        val followingsUrlFirst = "${user.server}/@${user.username}/following.json?page=1"
        val followedUsers: List<String> =
            http.paginate<FollowingsResponse>(followingsUrlFirst) { it.next }.flatMap { it.orderedItems }

        val rssFeeds = coroutineScope {
            followedUsers
                .map {
                    async {
                        val responseResult = runCatching { http.get("$it.rss") { accept(rssContentType) } }
                        responseResult.fold(
                            onSuccess = { response ->
                                val contentType = response.contentType()?.toString()
                                if (response.status.isSuccess() && contentType?.contains("rss") == true)
                                    Result.success(response.body<Rss>())
                                else
                                    Result.failure(RuntimeException("Could not get RSS feed for $it due to status ${response.status} or contentType ${response.contentType()}"))
                            },
                            onFailure = { Result.failure(it) }
                        )

                    }
                }
                .awaitAll()
                .onEach { if (it.isFailure) System.err.println(it.exceptionOrNull()?.message) }
                .mapNotNull { it.getOrNull() }
        }

        val accountInfoByMastodonAuthor: Map<String, AccountLookupResponse> = coroutineScope {
            rssFeeds.asSequence()
                .flatMap { rssFeed -> rssFeed.channel.item }
                .mapNotNull { post -> post.mastodonAuthor }
                .distinct()
                .map { author ->
                    async {
                        runCatching {
                            // We could also ask the server of the user we're querying to distribute the load
                            val account = http.get("${user.server}/api/v1/accounts/lookup?acct=$author")
                                .body<AccountLookupResponse>()
                            author to account
                        }
                    }
                }
                .toList()
                .awaitAll()
                .mapNotNull { it.getOrNull() }
                .toMap()
        }

        return rssFeeds.asSequence()
            .flatMap { rssFeed -> rssFeed.channel.item }
            .map { rssItem ->
                val mastodonAuthor = rssItem.mastodonAuthor
                val accountInfo = mastodonAuthor?.let { accountInfoByMastodonAuthor[it] }
                rssItem.toFeedItem(
                    author = accountInfo?.acct?.let { "@$it" },
                    authorImageUrl = accountInfo?.avatar
                )
            }
            .toList()
    }

}

private suspend inline fun <reified T> HttpClient.paginate(firstUrl: String, getNextUrl: (T) -> String?): List<T> {
    val results = mutableListOf<T>()
    var currentPage: T = get(firstUrl).body()
    do {
        results += currentPage

        val next = getNextUrl(currentPage)
        if (next != null) {
            currentPage = get(next).body()
        } else {
            break
        }
    } while (true)

    return results
}

@Serializable
private data class FollowingsResponse(val next: String? = null, val orderedItems: List<String>)

// Incomplete response, there's more data available
@Serializable
private data class AccountLookupResponse(
    val id: String,
    val username: String,
    val acct: String,
    val display_name: String,
    val avatar: String,
    val avatar_static: String
)