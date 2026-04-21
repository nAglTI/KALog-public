package org.debs.kalog.feature.chat.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class ServiceMessageData(
    @SerialName("userID") val userID: String,
)

@Serializable
internal data class UserJoinedServiceData(
    @SerialName("userID") val userID: String,
    @SerialName("nickname") val nickname: String,
)
