package com.cbruegg.socialmediaserver

import com.cbruegg.socialmediaserver.retrieval.TwitterList
import com.cbruegg.socialmediaserver.retrieval.bluesky.BlueskyAccount
import com.cbruegg.socialmediaserver.shared.MastodonUser
import kotlinx.serialization.Serializable

@Serializable
data class Sources(
    val mastodonFollowings: List<MastodonUser> = emptyList(),
    val twitterLists: List<TwitterList> = emptyList(),
    val blueskyFollowings: List<BlueskyAccount> = emptyList()
)