package com.cbruegg.socialmediaserver.retrieval.bluesky

import app.bsky.embed.RecordViewRecord
import app.bsky.embed.RecordViewRecordEmbedUnion
import app.bsky.embed.RecordViewRecordUnion
import app.bsky.feed.FeedViewPost
import app.bsky.feed.FeedViewPostReasonUnion
import app.bsky.feed.GetTimelineQueryParams
import app.bsky.feed.PostView
import app.bsky.feed.PostViewEmbedUnion
import app.bsky.feed.ReplyRefParentUnion
import com.atproto.server.CreateSessionRequest
import com.cbruegg.socialmediaserver.retrieval.SocialPlatform
import com.cbruegg.socialmediaserver.shared.FeedItem
import com.cbruegg.socialmediaserver.shared.MediaAttachment
import com.cbruegg.socialmediaserver.shared.MediaType
import com.cbruegg.socialmediaserver.shared.PlatformId
import io.ktor.client.HttpClient
import io.ktor.client.plugins.DefaultRequest
import io.ktor.http.takeFrom
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import sh.christian.ozone.XrpcBlueskyApi
import sh.christian.ozone.api.Uri

// TODO Fix links in https://bsky.app/profile/cbruegg.com/post/3lazcztqnhk2z
// TODO Subscribe to followings feed to avoid seeing mentions of users not followed: https://bsky.app/profile/joemerrick.bsky.social/post/3lazebeebdc22

class Bluesky(private val feedsOf: List<BlueskyAccount>) : SocialPlatform {
    override val platformId = PlatformId.Bluesky

    override suspend fun getFeed(): List<FeedItem> = feedsOf.flatMap { getFeed(it) }

    private suspend fun getFeed(account: BlueskyAccount): List<FeedItem> {
        try {
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
            val timeline = api.getTimeline(GetTimelineQueryParams(limit = 100)).requireResponse()
            return timeline.feed.map { it.toFeedItem() }
        } catch (e: Exception) {
            e.printStackTrace()
            return emptyList()
        }
    }
}

private fun FeedViewPost.toFeedItem(): FeedItem {
    val record = post.record.value.jsonObject
    val reasonRepost = (reason as? FeedViewPostReasonUnion.ReasonRepost)?.value
    val mentionPrefix = when (val parentOfReply = reply?.parent) {
        is ReplyRefParentUnion.BlockedPost -> "@${parentOfReply.value.author.did.did} "
        is ReplyRefParentUnion.NotFoundPost -> "@[Deleted Post] "
        is ReplyRefParentUnion.PostView -> "@${parentOfReply.value.author.handle.handle} "
        null -> ""
    }
    return FeedItem(
        text = if (reasonRepost != null)
            ""
        else
            mentionPrefix + (record["text"]?.jsonPrimitive?.contentOrNull ?: ""),
        author = "@" + (reasonRepost?.by?.handle?.handle ?: post.author.handle.handle),
        authorImageUrl = reasonRepost?.by?.avatar?.uri ?: post.author.avatar?.uri,
        id = post.cid.cid,
        published = reasonRepost?.by?.createdAt ?: record["createdAt"]!!.jsonPrimitive.content.let(
            Instant::parse
        ),
        link = post.bskyAppUri,
        platform = PlatformId.Bluesky,
        repost = post.embed?.extractQuotedFeedItem() ?: reasonRepost?.let {
            FeedItem(
                text = record["text"]?.jsonPrimitive?.contentOrNull ?: "",
                author = "@" + post.author.handle.handle,
                authorImageUrl = post.author.avatar?.uri,
                id = post.cid.cid,
                published = record["createdAt"]!!.jsonPrimitive.content.let(Instant::parse),
                link = post.bskyAppUri,
                platform = PlatformId.Bluesky,
                repost = null,
                mediaAttachments = post.embed?.toMediaAttachments() ?: emptyList(),
                isSkyBridgePost = false
            )
        },
        mediaAttachments = post.embed?.toMediaAttachments() ?: emptyList(),
        isSkyBridgePost = false // TODO Remove Skybridge support, also from guide and docker file
    )
}

private fun PostViewEmbedUnion?.extractQuotedFeedItem(): FeedItem? {
    return when (this) {
        is PostViewEmbedUnion.RecordView ->
            when (val record = value.record) {
                is RecordViewRecordUnion.ViewRecord -> FeedItem(
                    text = record.value.value.value.jsonObject["text"]?.jsonPrimitive?.content
                        ?: "",
                    author = record.value.author.handle.handle,
                    authorImageUrl = record.value.author.avatar?.uri,
                    id = record.value.cid.cid,
                    published = record.value.value.value.jsonObject["createdAt"]!!.jsonPrimitive.content.let(
                        Instant::parse
                    ),
                    link = record.value.bskyAppUri,
                    platform = PlatformId.Bluesky,
                    repost = null,
                    mediaAttachments = emptyList(),
                    isSkyBridgePost = false
                )

                else -> null
            }

        is PostViewEmbedUnion.RecordWithMediaView ->
            when (val record = value.record.record) {
                is RecordViewRecordUnion.ViewRecord -> FeedItem(
                    text = record.value.value.value.jsonObject["text"]?.jsonPrimitive?.content
                        ?: "",
                    author = record.value.author.handle.handle,
                    authorImageUrl = record.value.author.avatar?.uri,
                    id = record.value.cid.cid,
                    published = record.value.value.value.jsonObject["createdAt"]!!.jsonPrimitive.content.let(
                        Instant::parse
                    ),
                    link = record.value.bskyAppUri,
                    platform = PlatformId.Bluesky,
                    repost = null,
                    mediaAttachments = record.value.embeds.flatMap { it.toMediaAttachments() }, // TODO Test with https://bsky.app/profile/chenchenzh.bsky.social/post/3lawca5ebk223
                    isSkyBridgePost = false
                )

                else -> null
            }

        else -> null
    }
}

private val gifvHosts =
    listOf("giphy.com" /* including www., media., media0-4. */, "media.tenor.com")

private fun PostViewEmbedUnion.toMediaAttachments(): List<MediaAttachment> = when (this) {
    is PostViewEmbedUnion.ExternalView ->
        if (gifvHosts.any { value.external.uri.uri.toHttpUrl().host.endsWith(it) } && value.external.thumb != null) listOf(
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
    is PostViewEmbedUnion.RecordWithMediaView -> emptyList() // Quoted post, not a media attachment
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
