package org.debs.kalog.feature.chat.data.remote.api

import io.ktor.client.plugins.timeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.content.OutgoingContent
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.writeFully
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.debs.kalog.core.network.client.SecureApiClient
import org.debs.kalog.core.network.config.NetworkEnvironment
import org.debs.kalog.core.network.logging.debugHttpLog
import org.debs.kalog.core.network.model.SecurePayload
import org.debs.kalog.core.network.security.NetworkDecryptionResult
import org.debs.kalog.feature.chat.localization.chatLocalized

private class ProgressByteArrayContent(
    private val bytes: ByteArray,
    override val contentType: ContentType?,
    private val onProgress: (bytesSent: Long, totalBytes: Long) -> Unit,
) : OutgoingContent.WriteChannelContent() {
    override val contentLength: Long = bytes.size.toLong()

    override suspend fun writeTo(channel: ByteWriteChannel) {
        var offset = 0
        while (offset < bytes.size) {
            val chunkSize = minOf(PROGRESS_UPLOAD_CHUNK_SIZE, bytes.size - offset)
            channel.writeFully(bytes, offset, offset + chunkSize)
            offset += chunkSize
            onProgress(offset.toLong(), contentLength)
        }
    }

    private companion object {
        private const val PROGRESS_UPLOAD_CHUNK_SIZE = 64 * 1024
    }
}

class ChatApiService(
    private val secureApiClient: SecureApiClient,
    private val networkEnvironment: NetworkEnvironment,
    private val json: Json,
) {
    suspend fun start(request: StartRequestDto): StartResponseDto {
        return postPlainJson(
            path = "/api/v1/start",
            request = request,
        )
    }

    suspend fun getChatList(): List<ChatListItemDto> {
        return postJson(
            path = "/api/v1/chat/list",
            request = EmptyRequestDto,
        )
    }

    suspend fun getChatInfo(chatId: String): ChatInfoDto {
        return postJson(
            path = "/api/v1/chat/info/$chatId",
            request = EmptyRequestDto,
        )
    }

    suspend fun getMessageHistory(chatId: String, offset: Int, limit: Int): List<MessageResponseDto> {
        return postJson(
            path = "/api/v1/message/history/$chatId",
            request = MessageHistoryRequestDto(
                offset = offset,
                limit = limit,
            ),
        )
    }

    suspend fun pollMessages(request: PollMessagesRequestDto): PollMessagesResponseDto {
        return postJson(
            path = "/api/v1/message/poll",
            request = request,
            requestConfig = {
                timeout {
                    requestTimeoutMillis = maxOf(
                        networkEnvironment.requestTimeoutMillis,
                        MESSAGE_POLL_TIMEOUT_MS,
                    )
                    socketTimeoutMillis = maxOf(
                        networkEnvironment.socketTimeoutMillis,
                        MESSAGE_POLL_TIMEOUT_MS,
                    )
                }
            },
        )
    }

    suspend fun sendMessages(request: SendMessagesRequestDto) {
        postUnit(
            path = "/api/v1/message/send",
            request = request,
        )
    }

    suspend fun createDirectChat(request: CreateDirectChatRequestDto): ChatResponseDto {
        return postJson(
            path = "/api/v1/chat/create",
            request = request,
        )
    }

    suspend fun createGroupChat(request: CreateGroupChatRequestDto): ChatResponseDto {
        return postJson(
            path = "/api/v1/chat/group/create",
            request = request,
        )
    }

    suspend fun createSelfChat(request: CreateSelfChatRequestDto): CreateSelfChatResultDto {
        val fullUrl = url("/api/v1/chat/self/create")
        val requestBody = serializeForLog(request)
        val transportRequest = secureApiClient.prepareEncryptedRequest(requestBody)
        logRequest(
            method = "POST",
            fullUrl = fullUrl,
            requestBody = requestBody,
            transportBody = serializeForLog(transportRequest),
        )
        val response = executeLoggedRequest(
            method = "POST",
            fullUrl = fullUrl,
            requestBody = requestBody,
            transportBody = serializeForLog(transportRequest),
        ) {
            secureApiClient.httpClient.post(fullUrl) {
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody(transportRequest)
            }
        }
        return if (response.statusCode == 409) {
            CreateSelfChatResultDto.AlreadyExists
        } else {
            CreateSelfChatResultDto.Created(response.decodeEncryptedLoggedBody())
        }
    }

    suspend fun inviteUserToChat(request: InviteUserToChatRequestDto) {
        postUnit(
            path = "/api/v1/chat/group/invite",
            request = request,
        )
    }

    suspend fun leaveGroupChat(request: LeaveGroupChatRequestDto) {
        postUnit(
            path = "/api/v1/chat/leave",
            request = request,
        )
    }

    suspend fun leaveChat(request: LeaveGroupChatRequestDto) {
        postUnit(
            path = "/api/v1/chat/leave",
            request = request,
        )
    }

    suspend fun setGroupChatPublicKey(request: SetGroupChatPublicKeyRequestDto) {
        postUnit(
            path = "/api/v1/chat/group/key",
            request = request,
        )
    }

    suspend fun initAttachmentUpload(): InitAttachmentResponseDto {
        return postJson(
            path = "/api/v1/attachment/init",
            request = EmptyRequestDto,
        )
    }

    suspend fun uploadAttachment(
        attachmentId: String,
        uploadToken: String,
        bytes: ByteArray,
        contentType: String? = null,
        onProgress: (bytesSent: Long, totalBytes: Long) -> Unit = { _, _ -> },
    ) {
        val fullUrl = url("/api/v1/attachment/$attachmentId")
        logRequest(
            method = "PUT",
            fullUrl = fullUrl,
            requestBody = "<${bytes.size} bytes>",
        )
        val response = executeLoggedRequest(
            method = "PUT",
            fullUrl = fullUrl,
            requestBody = "<${bytes.size} bytes>",
            decryptResponse = false,
        ) {
            secureApiClient.httpClient.put(fullUrl) {
                timeout {
                    requestTimeoutMillis = maxOf(
                        networkEnvironment.requestTimeoutMillis,
                        ATTACHMENT_TRANSFER_TIMEOUT_MS,
                    )
                    socketTimeoutMillis = maxOf(
                        networkEnvironment.socketTimeoutMillis,
                        ATTACHMENT_TRANSFER_TIMEOUT_MS,
                    )
                }
                header(UPLOAD_TOKEN_HEADER, uploadToken)
                setBody(
                    ProgressByteArrayContent(
                        bytes = bytes,
                        contentType = contentType.toBinaryContentType(),
                        onProgress = onProgress,
                    ),
                )
            }
        }
        response.throwIfNotSuccessful()
    }

    suspend fun downloadAttachment(
        attachmentId: String,
        rangeHeader: String? = null,
    ): ByteArray {
        val fullUrl = url("/api/v1/attachment/$attachmentId")
        logRequest(
            method = "GET",
            fullUrl = fullUrl,
            requestBody = rangeHeader?.let { "Range=$it" },
        )
        val response = try {
            secureApiClient.httpClient.get(fullUrl) {
                timeout {
                    requestTimeoutMillis = maxOf(
                        networkEnvironment.requestTimeoutMillis,
                        ATTACHMENT_TRANSFER_TIMEOUT_MS,
                    )
                    socketTimeoutMillis = maxOf(
                        networkEnvironment.socketTimeoutMillis,
                        ATTACHMENT_TRANSFER_TIMEOUT_MS,
                    )
                }
                if (rangeHeader != null) {
                    header(HttpHeaders.Range, rangeHeader)
                }
            }
        } catch (error: Throwable) {
            logFailure(
                method = "GET",
                fullUrl = fullUrl,
                requestBody = rangeHeader?.let { "Range=$it" },
                error = error,
            )
            throw error
        }

        if (response.status.value !in 200..299) {
            val responseBody = runCatching { response.bodyAsText() }
                .getOrElse { error ->
                    chatLocalized(
                        en = "<failed to read response body: ${error.message.orEmpty()}>",
                        ru = "<не удалось прочитать тело ответа: ${error.message.orEmpty()}>",
                    )
                }
            error("HTTP ${response.status.value} ${response.status.description}: $responseBody")
        }

        return response.bodyAsBytes()
    }

    private fun url(path: String): String {
        return networkEnvironment.resolveUrl(path)
    }

    private suspend inline fun <reified Request : Any, reified Response : Any> postJson(
        path: String,
        request: Request,
        noinline requestConfig: HttpRequestBuilder.() -> Unit = {},
    ): Response {
        val fullUrl = url(path)
        val requestBody = serializeForLog(request)
        val transportRequest = secureApiClient.prepareEncryptedRequest(requestBody)
        logRequest(
            method = "POST",
            fullUrl = fullUrl,
            requestBody = requestBody,
            transportBody = serializeForLog(transportRequest),
        )
        val response = executeLoggedRequest(
            method = "POST",
            fullUrl = fullUrl,
            requestBody = requestBody,
            transportBody = serializeForLog(transportRequest),
        ) {
            secureApiClient.httpClient.post(fullUrl) {
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody(transportRequest)
                requestConfig()
            }
        }
        return response.decodeEncryptedLoggedBody()
    }

    private suspend inline fun <reified Request : Any, reified Response : Any> postPlainJson(
        path: String,
        request: Request,
    ): Response {
        val fullUrl = url(path)
        val requestBody = serializeForLog(request)
        logRequest(
            method = "POST",
            fullUrl = fullUrl,
            requestBody = requestBody,
        )
        val response = executeLoggedRequest(
            method = "POST",
            fullUrl = fullUrl,
            requestBody = requestBody,
            decryptResponse = false,
        ) {
            secureApiClient.httpClient.post(fullUrl) {
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody(request)
            }
        }
        return response.decodePlainLoggedBody()
    }

    private suspend inline fun <reified Request : Any> postUnit(
        path: String,
        request: Request,
    ) {
        val fullUrl = url(path)
        val requestBody = serializeForLog(request)
        val transportRequest = secureApiClient.prepareEncryptedRequest(requestBody)
        logRequest(
            method = "POST",
            fullUrl = fullUrl,
            requestBody = requestBody,
            transportBody = serializeForLog(transportRequest),
        )
        val response = executeLoggedRequest(
            method = "POST",
            fullUrl = fullUrl,
            requestBody = requestBody,
            transportBody = serializeForLog(transportRequest),
        ) {
            secureApiClient.httpClient.post(fullUrl) {
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody(transportRequest)
            }
        }
        response.throwIfNotSuccessful()
    }

    private suspend fun executeLoggedRequest(
        method: String,
        fullUrl: String,
        requestBody: String?,
        transportBody: String? = null,
        decryptResponse: Boolean = true,
        execute: suspend () -> HttpResponse,
    ): LoggedHttpResponse {
        val response = try {
            execute()
        } catch (error: Throwable) {
            logFailure(
                method = method,
                fullUrl = fullUrl,
                requestBody = requestBody,
                transportBody = transportBody,
                error = error,
            )
            throw error
        }

        val responseBody = runCatching { response.bodyAsText() }
            .getOrElse { error ->
                chatLocalized(
                    en = "<failed to read response body: ${error.message.orEmpty()}>",
                    ru = "<не удалось прочитать тело ответа: ${error.message.orEmpty()}>",
                )
            }
        val decryptedResponseBody = if (decryptResponse) {
            decryptResponseBody(responseBody)
        } else {
            null
        }

        logResponse(
            method = method,
            fullUrl = fullUrl,
            statusCode = response.status.value,
            statusDescription = response.status.description,
            responseBody = responseBody,
            decryptedResponseBody = decryptedResponseBody,
        )

        return LoggedHttpResponse(
            statusCode = response.status.value,
            statusDescription = response.status.description,
            body = responseBody,
            decryptedBody = decryptedResponseBody,
        )
    }

    private suspend fun decryptResponseBody(rawBody: String): String? {
        if (rawBody.isBlank()) return null

        val encryptedChunks = runCatching { json.decodeFromString<List<String>>(rawBody) }
            .getOrNull()
            ?: return null

        if (encryptedChunks.isEmpty()) return ""

        return when (
            val result = secureApiClient.unwrapResponseBody(
                SecurePayload(
                    body = rawBody,
                    chunks = encryptedChunks,
                    isEncrypted = true,
                ),
            )
        ) {
            is NetworkDecryptionResult.Decrypted -> result.body
            NetworkDecryptionResult.Undecryptable -> null
        }
    }

    private fun logRequest(
        method: String,
        fullUrl: String,
        requestBody: String?,
        transportBody: String? = null,
    ) {
        if (!networkEnvironment.enableLogging) return
        debugHttpLog(
            buildString {
                appendLine("HTTP REQUEST")
                appendLine("method=$method")
                appendLine("url=${fullUrl.redactedUrlForLog()}")
                if (requestBody != null) {
                    appendLine("body=${requestBody.redactedBodyForLog()}")
                }
                if (transportBody != null) {
                    append("transport_body=${transportBody.redactedBodyForLog()}")
                }
            },
        )
    }

    private fun logResponse(
        method: String,
        fullUrl: String,
        statusCode: Int,
        statusDescription: String,
        responseBody: String,
        decryptedResponseBody: String?,
    ) {
        if (!networkEnvironment.enableLogging) return
        debugHttpLog(
            buildString {
                appendLine("HTTP RESPONSE")
                appendLine("method=$method")
                appendLine("url=${fullUrl.redactedUrlForLog()}")
                appendLine("status=$statusCode $statusDescription")
                appendLine("body=${responseBody.redactedBodyForLog()}")
                append("decrypted_body=${decryptedResponseBody?.redactedBodyForLog() ?: "<unavailable>"}")
            },
        )
    }

    private fun logFailure(
        method: String,
        fullUrl: String,
        requestBody: String?,
        transportBody: String? = null,
        error: Throwable,
    ) {
        if (!networkEnvironment.enableLogging) return
        debugHttpLog(
            buildString {
                appendLine("HTTP FAILURE")
                appendLine("method=$method")
                appendLine("url=${fullUrl.redactedUrlForLog()}")
                if (requestBody != null) {
                    appendLine("body=${requestBody.redactedBodyForLog()}")
                }
                if (transportBody != null) {
                    appendLine("transport_body=${transportBody.redactedBodyForLog()}")
                }
                append("error=${error::class.simpleName ?: "Throwable"}")
            },
        )
    }

    private inline fun <reified Request : Any> serializeForLog(request: Request): String {
        return runCatching { json.encodeToString(request) }
            .getOrDefault(request.toString())
    }

    private inline fun <reified Response : Any> LoggedHttpResponse.decodeEncryptedLoggedBody(): Response {
        throwIfNotSuccessful()
        return json.decodeFromString(
            decryptedBody ?: error(
                chatLocalized(
                    en = "Encrypted response body could not be decrypted.",
                    ru = "Не удалось расшифровать зашифрованное тело ответа.",
                ),
            ),
        )
    }

    private inline fun <reified Response : Any> LoggedHttpResponse.decodePlainLoggedBody(): Response {
        throwIfNotSuccessful()
        return json.decodeFromString(body)
    }
}

private data class LoggedHttpResponse(
    val statusCode: Int,
    val statusDescription: String,
    val body: String,
    val decryptedBody: String?,
)

private fun LoggedHttpResponse.throwIfNotSuccessful() {
    if (statusCode !in 200..299) {
        error("HTTP $statusCode $statusDescription")
    }
}

private fun String.redactedBodyForLog(): String {
    return "<redacted>"
}

private fun String.redactedUrlForLog(): String {
    val schemeSeparatorIndex = indexOf("://")
    if (schemeSeparatorIndex < 0) return "<redacted>"

    val pathStartIndex = indexOf('/', startIndex = schemeSeparatorIndex + 3)
    val path = if (pathStartIndex >= 0) substring(pathStartIndex).substringBefore('?') else ""
    return "<redacted-host>$path"
}

@Serializable
private object EmptyRequestDto

@Serializable
private data class MessageHistoryRequestDto(
    val offset: Int,
    val limit: Int,
)

private const val MESSAGE_POLL_TIMEOUT_MS = 45_000L
private const val ATTACHMENT_TRANSFER_TIMEOUT_MS = 60 * 60 * 1000L
private const val UPLOAD_TOKEN_HEADER = "X-Upload-Token"

private fun String?.toBinaryContentType(): ContentType {
    return ContentType.Application.OctetStream
}
