package com.cbruegg.socialmediaserver

import com.cbruegg.socialmediaserver.retrieval.MastodonUser
import com.cbruegg.socialmediaserver.retrieval.TwitterList
import kotlinx.serialization.Serializable

@Serializable
data class Sources(
    val mastodonFollowings: List<MastodonUser>,
    val twitterLists: List<TwitterList>
)