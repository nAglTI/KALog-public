package org.debs.kalog.feature.chat.data.remote.api

import io.ktor.client.plugins.timeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.debs.kalog.core.network.client.SecureApiClient
import org.debs.kalog.core.network.config.NetworkConfig
import org.debs.kalog.core.network.logging.debugHttpLog
import org.debs.kalog.core.network.model.SecurePayload

class ChatApiService(
    private val secureApiClient: SecureApiClient,
    private val networkConfig: NetworkConfig,
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
                        networkConfig.requestTimeoutMillis,
                        MESSAGE_POLL_TIMEOUT_MS,
                    )
                    socketTimeoutMillis = maxOf(
                        networkConfig.socketTimeoutMillis,
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

    suspend fun inviteUserToChat(request: InviteUserToChatRequestDto) {
        postUnit(
            path = "/api/v1/chat/group/invite",
            request = request,
        )
    }

    suspend fun leaveGroupChat(request: LeaveGroupChatRequestDto) {
        postUnit(
            path = "/api/v1/chat/group/leave",
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

    private fun url(path: String): String {
        return "${networkConfig.baseUrl.trimEnd('/')}$path"
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
            .getOrElse { error -> "<failed to read response body: ${error.message.orEmpty()}>" }
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

        val decryptedBody = runCatching {
            secureApiClient.unwrapResponseBody(
                SecurePayload(
                    body = rawBody,
                    chunks = encryptedChunks,
                    isEncrypted = true,
                ),
            )
        }.getOrNull() ?: return null

        return decryptedBody.takeUnless { it == rawBody }
    }

    private fun logRequest(
        method: String,
        fullUrl: String,
        requestBody: String?,
        transportBody: String? = null,
    ) {
        // FIXME: Remove plaintext request logging or heavily redact payloads before production rollout.
        debugHttpLog(
            buildString {
                appendLine("HTTP REQUEST")
                appendLine("method=$method")
                appendLine("url=$fullUrl")
                if (requestBody != null) {
                    appendLine("body=$requestBody")
                }
                if (transportBody != null) {
                    append("transport_body=$transportBody")
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
        // FIXME: Do not log decrypted response bodies in production; this can leak message contents and identifiers.
        debugHttpLog(
            buildString {
                appendLine("HTTP RESPONSE")
                appendLine("method=$method")
                appendLine("url=$fullUrl")
                appendLine("status=$statusCode $statusDescription")
                appendLine("body=$responseBody")
                append("decrypted_body=${decryptedResponseBody ?: "<unavailable>"}")
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
        // TODO: Redact sensitive request data in failure logs before enabling this in shared or release environments.
        debugHttpLog(
            buildString {
                appendLine("HTTP FAILURE")
                appendLine("method=$method")
                appendLine("url=$fullUrl")
                if (requestBody != null) {
                    appendLine("body=$requestBody")
                }
                if (transportBody != null) {
                    appendLine("transport_body=$transportBody")
                }
                append("error=${error::class.simpleName}: ${error.message.orEmpty()}")
            },
        )
    }

    private inline fun <reified Request : Any> serializeForLog(request: Request): String {
        return runCatching { json.encodeToString(request) }
            .getOrDefault(request.toString())
    }

    private inline fun <reified Response : Any> LoggedHttpResponse.decodeEncryptedLoggedBody(): Response {
        throwIfNotSuccessful()
        return json.decodeFromString(requireNotNull(decryptedBody) {
            "Encrypted response body is missing."
        })
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
        error("HTTP $statusCode $statusDescription: ${decryptedBody ?: body}")
    }
}

@Serializable
private object EmptyRequestDto

@Serializable
private data class MessageHistoryRequestDto(
    val offset: Int,
    val limit: Int,
)

private const val MESSAGE_POLL_TIMEOUT_MS = 45_000L
