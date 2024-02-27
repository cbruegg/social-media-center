package com.cbruegg.socialmediaserver.rss

import com.cbruegg.socialmediaserver.retrieval.FeedItem
import com.cbruegg.socialmediaserver.retrieval.PlatformId
import io.ktor.http.*
import kotlinx.datetime.toKotlinInstant
import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName
import nl.adaptivity.xmlutil.serialization.XmlValue
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
    @XmlElement val item: List<RssItem>
)

@Serializable
@XmlSerialName("item")
data class RssItem(
    @XmlElement val link: String,
    @XmlElement val pubDate: String,
    @XmlElement val description: String,
    @XmlElement val guid: String
)

@Serializable
@XmlSerialName("guid")
data class RssGuid(@XmlValue val value: String)

fun RssItem.toFeedItem(): FeedItem {
    val published = rssPubDateFormat.get().parse(pubDate).toInstant().toKotlinInstant()
    return FeedItem(
        text = description,
        author = link,
        id = guid,
        published = published,
        link = link,
        PlatformId.Mastodon
    )
}

fun FeedItem.toRssItem(): RssItem = RssItem(
    link = link,
    pubDate = rssPubDateFormat.get().format(Date(published.toEpochMilliseconds())),
    description = text,
    guid = id
)
