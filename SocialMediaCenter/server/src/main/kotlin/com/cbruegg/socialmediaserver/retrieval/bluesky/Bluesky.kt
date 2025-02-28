package com.cbruegg.socialmediaserver.retrieval.bluesky

import app.bsky.actor.ProfileView
import app.bsky.embed.RecordViewRecord
import app.bsky.embed.RecordViewRecordEmbedUnion
import app.bsky.embed.RecordViewRecordUnion
import app.bsky.embed.RecordWithMediaViewMediaUnion
import app.bsky.feed.FeedViewPost
import app.bsky.feed.FeedViewPostReasonUnion
import app.bsky.feed.GetTimelineQueryParams
import app.bsky.feed.PostView
import app.bsky.feed.PostViewEmbedUnion
import app.bsky.feed.ReplyRefParentUnion
import app.bsky.graph.GetFollowsQueryParams
import com.atproto.server.CreateSessionRequest
import com.atproto.server.CreateSessionResponse
import com.cbruegg.socialmediaserver.retrieval.SocialPlatform
import com.cbruegg.socialmediaserver.shared.FeedItem
import com.cbruegg.socialmediaserver.shared.MediaAttachment
import com.cbruegg.socialmediaserver.shared.MediaType
import com.cbruegg.socialmediaserver.shared.PlatformId
import com.cbruegg.socialmediaserver.shared.RepostMeta
import io.ktor.client.HttpClient
import io.ktor.client.plugins.DefaultRequest
import io.ktor.http.takeFrom
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import sh.christian.ozone.XrpcBlueskyApi
import sh.christian.ozone.api.AtIdentifier
import sh.christian.ozone.api.Did
import sh.christian.ozone.api.Uri
import java.util.Collections

// TODO Stop emulating links by expanding them. Add native supports for spans/facets. Or use HTML for Bluesky posts.

class Bluesky(private val feedsOf: List<BlueskyAccount>) : SocialPlatform {
    private class CachedSession(val session: CreateSessionResponse, val httpClient: HttpClient)

    private val sessionCache: MutableMap<BlueskyAccount, CachedSession> =
        Collections.synchronizedMap(mutableMapOf())

    override val platformId = PlatformId.Bluesky

    override suspend fun getFeed(): List<FeedItem> = feedsOf.flatMap { getFeed(it) }

    private suspend fun getFeed(account: BlueskyAccount): List<FeedItem> {
        try {
            var cachedSession = sessionCache[account]
            if (cachedSession == null) {
                val tokens = MutableStateFlow<Tokens?>(null)
                val httpClient = HttpClient {
                    install(XrpcAuthPlugin) {
                        authTokens = tokens
                    }

                    install(DefaultRequest) {
                        url.takeFrom(account.server)
                    }

                    expectSuccess = false
                }
                val api = XrpcBlueskyApi(httpClient)
                val session = api.createSession(
                    CreateSessionRequest(
                        identifier = account.username,
                        password = account.password
                    )
                ).requireResponse()
                tokens.value = Tokens(session.accessJwt, session.refreshJwt)
                cachedSession = CachedSession(session, httpClient)
                sessionCache[account] = cachedSession
            }

            val api = XrpcBlueskyApi(cachedSession.httpClient)
            val session = cachedSession.session
            val follows = api.getAllFollows(session.did).map { it.did }
            val timeline = api.getTimeline(GetTimelineQueryParams(limit = 100)).requireResponse()
            return timeline.feed
                // Filter out replies to users not followed
                .filterNot { post -> post.inReplyTo.let { it != null && it !in follows } }
                .filterNot { it.isReplyToInaccessiblePost }
                .map { it.toFeedItem() }
        } catch (e: Exception) {
            sessionCache -= account
            e.printStackTrace()
            return emptyList()
        }
    }
}

private val FeedViewPost.inReplyTo: Did?
    get() = when (val parent = reply?.parent) {
        is ReplyRefParentUnion.PostView -> parent.value.author.did
        else -> null
    }

private suspend fun XrpcBlueskyApi.getAllFollows(of: AtIdentifier): List<ProfileView> {
    val follows = mutableListOf<ProfileView>()
    var cursor: String? = null
    do {
        val response =
            getFollows(GetFollowsQueryParams(actor = of, cursor = cursor)).requireResponse()
        follows += response.follows
        cursor = response.cursor
    } while (cursor != null)
    return follows
}

private fun String.expandFacets(facets: JsonArray?): String {
    if (facets == null) return this

    var text = toByteArray(Charsets.UTF_8)
    // Process facets in reverse order to avoid changing indices
    for (facet in facets.map { it.jsonObject }.reversed()) {
        val index = facet["index"]!!.jsonObject
        val byteStart = index["byteStart"]!!.jsonPrimitive.int
        val byteEnd = index["byteEnd"]!!.jsonPrimitive.int
        var span = text.sliceArray(byteStart until byteEnd)
        val features = facet["features"]!!.jsonArray
        for (feature in features) {
            val type = feature.jsonObject["\$type"]!!.jsonPrimitive.content
            when (type) {
                "app.bsky.richtext.facet#mention", "app.bsky.richtext.facet#tag" -> continue // Unsupported for now
                "app.bsky.richtext.facet#link" -> {
                    val uri = feature.jsonObject["uri"]!!.jsonPrimitive.content
                    span = uri.toByteArray(Charsets.UTF_8) // Replace shortened URI with full URI
                }
            }
        }
        text = text.sliceArray(0 until byteStart) + span + text.sliceArray(byteEnd until text.size)
    }
    return text.decodeToString()
}

private val FeedViewPost.isReplyToInaccessiblePost: Boolean
    get() = reply?.parent is ReplyRefParentUnion.NotFoundPost || reply?.parent is ReplyRefParentUnion.BlockedPost

private fun FeedViewPost.toFeedItem(): FeedItem {
    val record = post.record.value.jsonObject
    val embed = post.embed
    val reasonRepost = (reason as? FeedViewPostReasonUnion.ReasonRepost)?.value
    val mentionPrefix = when (val parentOfReply = reply?.parent) {
        is ReplyRefParentUnion.BlockedPost -> "@${parentOfReply.value.author.did.did} "
        is ReplyRefParentUnion.NotFoundPost -> "@[Deleted Post] "
        is ReplyRefParentUnion.PostView -> "@${parentOfReply.value.author.handle.handle} "
        null -> ""
    }
    var text = mentionPrefix + (
            record["text"]?.jsonPrimitive?.contentOrNull?.expandFacets(post.record.value.jsonObject["facets"]?.jsonArray)
                ?: ""
            )
    if (embed is PostViewEmbedUnion.ExternalView && !embed.isGifv()) {
        val uriToExternalContent = embed.value.external.uri.uri
        if (uriToExternalContent !in text) {
            // only add the link if the user hasn't already included it in the body of the post
            text += "\n\n$uriToExternalContent"
        }
    }
    return FeedItem(
        text = text,
        author = "@" + post.author.handle.handle,
        authorImageUrl = post.author.avatar?.uri,
        id = post.cid.cid,
        published = record["createdAt"]!!.jsonPrimitive.content.let(Instant::parse),
        link = post.bskyAppUri,
        platform = PlatformId.Bluesky,
        quotedPost = embed?.extractQuotedFeedItem(),
        mediaAttachments = embed?.toMediaAttachments()?.distinctBy { it.previewImageUrl } ?: emptyList(), // BlueSky sometimes sends the same image twice
        repostMeta = reasonRepost?.let {
            RepostMeta(
                repostingAuthor = "@" + reasonRepost.by.handle.handle,
                repostingAuthorImageUrl = reasonRepost.by.avatar?.uri,
                timeOfRepost = reasonRepost.indexedAt
            )
        }
    )
}

private fun PostViewEmbedUnion?.extractQuotedFeedItem(): FeedItem? {
    return when (this) {
        is PostViewEmbedUnion.RecordView ->
            when (val record = value.record) {
                is RecordViewRecordUnion.ViewRecord -> {
                    val recordJsonObject = record.value.value.value.jsonObject
                    FeedItem(
                        text = recordJsonObject["text"]?.jsonPrimitive?.content?.expandFacets(
                            recordJsonObject["facets"]?.jsonArray
                        ) ?: "",
                        author = record.value.author.handle.handle,
                        authorImageUrl = record.value.author.avatar?.uri,
                        id = record.value.cid.cid,
                        published = recordJsonObject["createdAt"]!!.jsonPrimitive.content.let(
                            Instant::parse
                        ),
                        link = record.value.bskyAppUri,
                        platform = PlatformId.Bluesky,
                        quotedPost = null,
                        mediaAttachments = emptyList()
                    )
                }

                else -> null
            }

        is PostViewEmbedUnion.RecordWithMediaView ->
            when (val record = value.record.record) {
                is RecordViewRecordUnion.ViewRecord -> {
                    val recordJsonObject = record.value.value.value.jsonObject
                    FeedItem(
                        text = recordJsonObject["text"]?.jsonPrimitive?.content?.expandFacets(
                            recordJsonObject["facets"]?.jsonArray
                        ) ?: "",
                        author = record.value.author.handle.handle,
                        authorImageUrl = record.value.author.avatar?.uri,
                        id = record.value.cid.cid,
                        published = recordJsonObject["createdAt"]!!.jsonPrimitive.content.let(
                            Instant::parse
                        ),
                        link = record.value.bskyAppUri,
                        platform = PlatformId.Bluesky,
                        quotedPost = null,
                        mediaAttachments = record.value.embeds.flatMap { it.toMediaAttachments() }
                            .distinctBy { it.previewImageUrl } // BlueSky sometimes sends the same image twice
                    )
                }

                else -> null
            }

        else -> null
    }
}

private val gifvHosts =
    listOf("giphy.com" /* including www., media., media0-4. */, "media.tenor.com")

private fun PostViewEmbedUnion.isGifv(): Boolean = when (this) {
    is PostViewEmbedUnion.ExternalView -> gifvHosts.any {
        value.external.uri.uri.toHttpUrl().host.endsWith(
            it
        )
    }

    else -> false
}

private fun PostViewEmbedUnion.toMediaAttachments(): List<MediaAttachment> = when (this) {
    is PostViewEmbedUnion.ExternalView ->
        if (isGifv() && value.external.thumb != null) listOf(
            MediaAttachment(
                type = MediaType.Gifv,
                previewImageUrl = value.external.thumb!!.uri,
                fullUrl = value.external.uri.uri
            )
        )
        else emptyList()

    is PostViewEmbedUnion.ImagesView -> value.images.map { image ->
        MediaAttachment(
            type = MediaType.Image,
            previewImageUrl = image.thumb.uri,
            fullUrl = image.fullsize.uri
        )
    }

    is PostViewEmbedUnion.RecordView -> emptyList() // Quoted post, not a media attachment
    is PostViewEmbedUnion.RecordWithMediaView -> when (val media = value.media) {
        is RecordWithMediaViewMediaUnion.ExternalView -> {
            if (isGifv() && media.value.external.thumb != null) listOf(
                MediaAttachment(
                    type = MediaType.Gifv,
                    previewImageUrl = media.value.external.thumb!!.uri,
                    fullUrl = media.value.external.uri.uri
                )
            )
            else emptyList()
        }

        is RecordWithMediaViewMediaUnion.ImagesView -> media.value.images.map { image ->
            MediaAttachment(
                type = MediaType.Image,
                previewImageUrl = image.thumb.uri,
                fullUrl = image.fullsize.uri
            )
        }

        is RecordWithMediaViewMediaUnion.VideoView -> media.value.thumbnail?.let { thumbnail ->
            listOf(
                MediaAttachment(
                    type = MediaType.Video,
                    previewImageUrl = thumbnail.uri,
                    fullUrl = thumbnail.uri
                )
            )
        } ?: emptyList()
    }

    is PostViewEmbedUnion.VideoView ->
        when (val thumbnail = value.thumbnail) {
            is Uri -> listOf(
                MediaAttachment(
                    type = MediaType.Video,
                    previewImageUrl = thumbnail.uri,
                    fullUrl = thumbnail.uri
                )
            )

            else -> emptyList()
        }
}

private fun RecordViewRecordEmbedUnion.toMediaAttachments(): List<MediaAttachment> = when (this) {
    is RecordViewRecordEmbedUnion.ExternalView ->
        if (gifvHosts.any { value.external.uri.uri.toHttpUrl().host.endsWith(it) } && value.external.thumb != null) listOf(
            MediaAttachment(
                type = MediaType.Gifv,
                previewImageUrl = value.external.thumb!!.uri,
                fullUrl = value.external.uri.uri
            )
        )
        else emptyList()

    is RecordViewRecordEmbedUnion.ImagesView -> value.images.map { image ->
        MediaAttachment(
            type = MediaType.Image,
            previewImageUrl = image.thumb.uri,
            fullUrl = image.fullsize.uri
        )
    }

    is RecordViewRecordEmbedUnion.RecordView -> emptyList() // Quoted post, not a media attachment
    is RecordViewRecordEmbedUnion.RecordWithMediaView -> emptyList() // Quoted post, not a media attachment
    is RecordViewRecordEmbedUnion.VideoView ->
        when (val thumbnail = value.thumbnail) {
            is Uri -> listOf(
                MediaAttachment(
                    type = MediaType.Video,
                    previewImageUrl = thumbnail.uri,
                    fullUrl = thumbnail.uri
                )
            )

            else -> emptyList()
        }
}

private val PostView.bskyAppUri: String
    get() {
        // for example: at://did:plc:fbtvg6jxtdroidfvq5z635xu/app.bsky.feed.post/3law7ewf4ak2y
        val original = uri.atUri
        val postId = original.substringAfterLast('/')
        // for example: tomwarren.co.uk
        val author = author.handle.handle
        // target: https://bsky.app/profile/tomwarren.co.uk/post/3lawqtlhl722l
        return "https://bsky.app/profile/$author/post/$postId"
    }

private val RecordViewRecord.bskyAppUri: String
    get() {
        // for example: at://did:plc:fbtvg6jxtdroidfvq5z635xu/app.bsky.feed.post/3law7ewf4ak2y
        val original = uri.atUri
        val postId = original.substringAfterLast('/')
        // for example: tomwarren.co.uk
        val author = author.handle.handle
        // target: https://bsky.app/profile/tomwarren.co.uk/post/3lawqtlhl722l
        return "https://bsky.app/profile/$author/post/$postId"
    }

@Serializable
data class BlueskyAccount(
    val username: String,
    val server: String,
    val password: String
)
