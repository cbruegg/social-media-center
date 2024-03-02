package com.cbruegg.socialmediaserver.atom

import com.cbruegg.socialmediaserver.retrieval.FeedItem
import com.cbruegg.socialmediaserver.retrieval.PlatformId
import io.ktor.http.*
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName

val atomContentType = ContentType.parse("application/atom+xml; charset=utf-8")

@Serializable
@XmlSerialName("feed", "http://www.w3.org/2005/Atom", "")
data class Atom(@XmlElement val author: AtomAuthor?, @XmlElement val entry: List<AtomEntry>)

@Serializable
@XmlSerialName("entry")
data class AtomEntry(
    @XmlElement val published: String,
    @XmlElement val content: String,
    /**
     * Also serves as the link
     */
    @XmlElement val id: String,
    @XmlElement val link: List<AtomLink>,
)

@Serializable
@XmlSerialName("link")
data class AtomLink(val type: String? = null, val rel: String? = null, val href: String)

@Serializable
@XmlSerialName("author")
data class AtomAuthor(@XmlElement val id: String, @XmlElement val uri: String, @XmlElement val name: String, @XmlElement val link: List<AtomLink>)

fun AtomEntry.toFeedItem(atom: Atom): FeedItem {
    return FeedItem(
        text = content,
        author = atom.author?.name?.let { "@$it" } ?: "[Error] Unknown Author",
        authorImageUrl = atom.author?.link?.find { it.rel == "avatar" }?.href,
        id = id,
        published = Instant.parse(this.published),
        link = id,
        PlatformId.Mastodon
    )
}
