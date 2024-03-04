package com.cbruegg.socialmediaserver.rss

import com.cbruegg.socialmediaserver.retrieval.FeedItem
import com.cbruegg.socialmediaserver.retrieval.PlatformId
import io.ktor.http.*
import kotlinx.datetime.toKotlinInstant
import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName
import java.text.SimpleDateFormat
import java.util.*

val rssContentType = ContentType.parse("application/rss+xml; charset=utf-8")

val rssPubDateFormat: ThreadLocal<SimpleDateFormat> =
    ThreadLocal.withInitial { SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US) }

@Serializable
@XmlSerialName("rss", "", "")
data class Rss(val channel: RssChannel)

@Serializable
@XmlSerialName("channel", "", "")
data class RssChannel(
    @XmlElement val title: String,
    @XmlElement val description: String,
    @XmlElement val link: String,
    @XmlElement val item: List<RssItem>,
    @XmlElement val image: RssChannelImage? = null
)

@Serializable
@XmlSerialName("image")
data class RssChannelImage(@XmlElement val url: String)

@Serializable
@XmlSerialName("item")
data class RssItem(
    @XmlElement val link: String,
    @XmlElement val pubDate: String,
    @XmlElement val description: String,
    @XmlElement val guid: String,
    @XmlElement val author: RssAuthor? = null,
    @XmlElement val mediaContent: RssMediaContent? = null
)

@Serializable
@XmlSerialName("author")
data class RssAuthor(
    @XmlElement val name: String? = null
)

@Serializable
@XmlSerialName("content", "media", "media")
data class RssMediaContent(
    val medium: String = "image",
    val url: String
)

private val mastodonUrlPattern = Regex("https?://(.*)/(@.*)/.*")
val RssItem.mastodonAuthor: String? get() {
    val matchResult = mastodonUrlPattern.matchEntire(link) ?: return null
    val serverName = matchResult.groupValues[1]
    val userName = matchResult.groupValues[2]
    return "$userName@$serverName"
}

fun RssItem.toFeedItem(author: String?, authorImageUrl: String?): FeedItem {
    val published = rssPubDateFormat.get().parse(pubDate).toInstant().toKotlinInstant()
    return FeedItem(
        text = description,
        author = author ?: mastodonAuthor ?: link,
        authorImageUrl = authorImageUrl,
        id = guid,
        published = published,
        link = link,
        PlatformId.Mastodon
    )
}

fun FeedItem.toRssItem(): RssItem = RssItem(
    link = link,
    pubDate = rssPubDateFormat.get().format(Date(published.toEpochMilliseconds())),
    description = "$author: $text",
    guid = id,
    author = RssAuthor(author),
    mediaContent = authorImageUrl?.let { RssMediaContent(url = it) }
)
