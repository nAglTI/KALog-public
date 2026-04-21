package org.debs.kalog.core.network.model

import kotlinx.serialization.Serializable

@Serializable
data class SecurePayload(
    val body: String? = null,
    val chunks: List<String> = emptyList(),
    val isEncrypted: Boolean,
)

@Serializable
data class SecureRequestEnvelope(
    val id: String,
    val data: List<String>,
)
