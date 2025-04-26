package com.cbruegg.socialmediaserver.state

import com.cbruegg.socialmediaserver.shared.DeviceIdToFirstVisibleItem
import kotlinx.serialization.Serializable

@Serializable
data class State(
    val deviceIdToFirstVisibleItem: DeviceIdToFirstVisibleItem = emptyMap()
)
