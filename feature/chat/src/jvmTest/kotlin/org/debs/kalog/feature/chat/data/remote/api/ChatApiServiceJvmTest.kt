package org.debs.kalog.feature.chat.data.remote.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.content.OutgoingContent
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.debs.kalog.core.network.client.SecureApiClient
import org.debs.kalog.core.network.config.NetworkConfig
import org.debs.kalog.core.network.model.SecurePayload
import org.debs.kalog.core.network.model.SecureRequestEnvelope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChatApiServiceJvmTest {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Test
    fun start_usesPlainPostBody() = runBlocking {
        val service = createService(
            responseBody = """
                {"spk":"server-key","uid":"user-1"}
            """.trimIndent(),
            encryptResponse = false,
        ) { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/api/v1/start", request.url.encodedPath)
            assertTrue(request.url.parameters.isEmpty())

            val requestJson = request.bodyJson().jsonObject
            assertEquals("public-key", requestJson.getValue("pk").jsonPrimitive.content)
            assertEquals(1, requestJson.size)
        }

        val response = service.start(StartRequestDto(publicKey = "public-key"))

        assertEquals("server-key", response.serverPublicKey)
        assertEquals("user-1", response.userId)
    }

    @Test
    fun getChatList_usesPostWithEmptyEncryptedBody() = runBlocking {
        val service = createService(
            responseBody = "[]",
        ) { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/api/v1/chat/list", request.url.encodedPath)
            assertTrue(request.url.parameters.isEmpty())

            val envelope = request.encryptedEnvelope()
            assertEquals("user-1", envelope.id)
            assertEquals(buildJsonObject { }, json.parseToJsonElement(envelope.data.single()).jsonObject)
        }

        assertEquals(emptyList(), service.getChatList())
    }

    @Test
    fun getChatInfo_usesPathParameterAndEmptyEncryptedBody() = runBlocking {
        val service = createService(
            responseBody = """
                {"id":"chat-1","title":"Chat","type":"group","users":[]}
            """.trimIndent(),
        ) { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/api/v1/chat/info/chat-1", request.url.encodedPath)
            assertTrue(request.url.parameters.isEmpty())

            val envelope = request.encryptedEnvelope()
            assertEquals(buildJsonObject { }, json.parseToJsonElement(envelope.data.single()).jsonObject)
        }

        val response = service.getChatInfo("chat-1")

        assertEquals("chat-1", response.id)
        assertEquals("Chat", response.title)
        assertEquals("group", response.type)
        assertTrue(response.users.isEmpty())
    }

    @Test
    fun getMessageHistory_usesPostBodyWithoutQueryParameters() = runBlocking {
        val service = createService(
            responseBody = """
                [{"chat_id":"chat-1","data":["chunk"],"from":"user-1","id":"message-1","message_type":"default","to":"user-2"}]
            """.trimIndent(),
        ) { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/api/v1/message/history/chat-1", request.url.encodedPath)
            assertTrue(request.url.parameters.isEmpty())

            val requestJson = json.parseToJsonElement(request.encryptedEnvelope().data.single()).jsonObject
            assertEquals(10, requestJson.getValue("offset").jsonPrimitive.int)
            assertEquals(25, requestJson.getValue("limit").jsonPrimitive.int)
            assertEquals(2, requestJson.size)
        }

        val response = service.getMessageHistory(chatId = "chat-1", offset = 10, limit = 25)

        assertEquals(1, response.size)
        assertEquals("message-1", response.single().id)
    }

    @Test
    fun sendMessages_usesEncryptedSendPayload() = runBlocking {
        val service = createService(
            responseBody = "{}",
        ) { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/api/v1/message/send", request.url.encodedPath)
            assertTrue(request.url.parameters.isEmpty())

            val requestJson = json.parseToJsonElement(request.encryptedEnvelope().data.single()).jsonObject
            assertEquals("chat-1", requestJson.getValue("chat_id").jsonPrimitive.content)
            assertEquals("default", requestJson.getValue("message_type").jsonPrimitive.content)
            val sendData = requestJson.getValue("send_data").jsonArray
            assertEquals(1, sendData.size)
            assertEquals("user-2", sendData.single().jsonObject.getValue("to").jsonPrimitive.content)
            assertEquals(
                listOf("chunk-1", "chunk-2"),
                sendData.single().jsonObject.getValue("data").jsonArray.map { it.jsonPrimitive.content },
            )
        }

        service.sendMessages(
            SendMessagesRequestDto(
                chatId = "chat-1",
                messageType = "default",
                sendData = listOf(
                    SendDataDto(
                        to = "user-2",
                        data = listOf("chunk-1", "chunk-2"),
                    ),
                ),
            ),
        )
    }

    @Test
    fun createDirectChat_usesEncryptedBody() = runBlocking {
        val service = createService(
            responseBody = """
                {"id":"chat-1","title":"Chat","type":"personal"}
            """.trimIndent(),
        ) { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/api/v1/chat/create", request.url.encodedPath)
            assertTrue(request.url.parameters.isEmpty())

            val requestJson = json.parseToJsonElement(request.encryptedEnvelope().data.single()).jsonObject
            assertEquals("public-key", requestJson.getValue("pk").jsonPrimitive.content)
            assertEquals("user-2", requestJson.getValue("uid").jsonPrimitive.content)
            assertEquals(2, requestJson.size)
        }

        val response = service.createDirectChat(
            CreateDirectChatRequestDto(
                publicKey = "public-key",
                userId = "user-2",
            ),
        )

        assertEquals("chat-1", response.id)
    }

    @Test
    fun createGroupChat_usesEncryptedBody() = runBlocking {
        val service = createService(
            responseBody = """
                {"id":"chat-1","title":"Group","type":"group"}
            """.trimIndent(),
        ) { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/api/v1/chat/group/create", request.url.encodedPath)
            assertTrue(request.url.parameters.isEmpty())

            val requestJson = json.parseToJsonElement(request.encryptedEnvelope().data.single()).jsonObject
            assertEquals("public-key", requestJson.getValue("pk").jsonPrimitive.content)
            assertEquals(1, requestJson.size)
        }

        val response = service.createGroupChat(CreateGroupChatRequestDto(publicKey = "public-key"))

        assertEquals("chat-1", response.id)
    }

    @Test
    fun inviteUserToChat_usesEncryptedBody() = runBlocking {
        val service = createService(
            responseBody = "{}",
        ) { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/api/v1/chat/group/invite", request.url.encodedPath)
            assertTrue(request.url.parameters.isEmpty())

            val requestJson = json.parseToJsonElement(request.encryptedEnvelope().data.single()).jsonObject
            assertEquals("chat-1", requestJson.getValue("chat_id").jsonPrimitive.content)
            assertEquals("user-2", requestJson.getValue("uid").jsonPrimitive.content)
            assertEquals(2, requestJson.size)
        }

        service.inviteUserToChat(
            InviteUserToChatRequestDto(
                chatId = "chat-1",
                userId = "user-2",
            ),
        )
    }

    @Test
    fun leaveGroupChat_usesEncryptedBody() = runBlocking {
        val service = createService(
            responseBody = "{}",
        ) { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/api/v1/chat/group/leave", request.url.encodedPath)
            assertTrue(request.url.parameters.isEmpty())

            val requestJson = json.parseToJsonElement(request.encryptedEnvelope().data.single()).jsonObject
            assertEquals("chat-1", requestJson.getValue("chat_id").jsonPrimitive.content)
            assertEquals(1, requestJson.size)
        }

        service.leaveGroupChat(
            LeaveGroupChatRequestDto(chatId = "chat-1"),
        )
    }

    @Test
    fun setGroupChatPublicKey_usesEncryptedBody() = runBlocking {
        val service = createService(
            responseBody = "{}",
        ) { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/api/v1/chat/group/key", request.url.encodedPath)
            assertTrue(request.url.parameters.isEmpty())

            val requestJson = json.parseToJsonElement(request.encryptedEnvelope().data.single()).jsonObject
            assertEquals("chat-1", requestJson.getValue("chat_id").jsonPrimitive.content)
            assertEquals("group-public-key", requestJson.getValue("pk").jsonPrimitive.content)
            assertEquals(2, requestJson.size)
        }

        service.setGroupChatPublicKey(
            SetGroupChatPublicKeyRequestDto(
                chatId = "chat-1",
                publicKey = "group-public-key",
            ),
        )
    }

    @Test
    fun pollMessages_usesPostBodyForSinceCursor() = runBlocking {
        val service = createService(
            responseBody = """
                {"messages":[{"chat_id":"chat-1","created_at":"2026-03-21T10:15:30Z","data":["chunk"],"from":"user-1","id":"message-1","message_type":"default","to":"user-2"}],"timestamp":"2026-03-21T10:15:30Z"}
            """.trimIndent(),
        ) { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/api/v1/message/poll", request.url.encodedPath)
            assertTrue(request.url.parameters.isEmpty())

            val requestJson = json.parseToJsonElement(request.encryptedEnvelope().data.single()).jsonObject
            assertEquals("2026-03-21T10:00:00Z", requestJson.getValue("since").jsonPrimitive.content)
        }

        val response = service.pollMessages(
            PollMessagesRequestDto(since = "2026-03-21T10:00:00Z"),
        )

        assertEquals("2026-03-21T10:15:30Z", response.timestamp)
        assertEquals(1, response.messages.size)
        assertEquals("message-1", response.messages.single().id)
    }

    private fun createService(
        responseBody: String,
        encryptResponse: Boolean = true,
        assertRequest: suspend (HttpRequestData) -> Unit,
    ): ChatApiService {
        val engine = MockEngine { request ->
            assertRequest(request)
            respond(
                content = if (encryptResponse) json.encodeToString(listOf(responseBody)) else responseBody,
                headers = io.ktor.http.headersOf(
                    HttpHeaders.ContentType,
                    ContentType.Application.Json.toString(),
                ),
            )
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) {
                json(json)
            }
        }
        return ChatApiService(
            secureApiClient = FakeSecureApiClient(client),
            networkConfig = NetworkConfig(baseUrl = "https://example.com"),
            json = json,
        )
    }
}

private class FakeSecureApiClient(
    override val httpClient: HttpClient,
) : SecureApiClient {
    override suspend fun prepareRequestBody(body: String): SecurePayload {
        return SecurePayload(
            chunks = listOf(body),
            isEncrypted = true,
        )
    }

    override suspend fun prepareEncryptedRequest(body: String): SecureRequestEnvelope {
        return SecureRequestEnvelope(
            id = "user-1",
            data = listOf(body),
        )
    }

    override suspend fun unwrapResponseBody(payload: SecurePayload): String {
        return payload.chunks.singleOrNull() ?: payload.body.orEmpty()
    }
}

private val envelopeJson = Json { ignoreUnknownKeys = true }

private fun HttpRequestData.bodyJson() = envelopeJson.parseToJsonElement(bodyText())

private fun HttpRequestData.encryptedEnvelope(): SecureRequestEnvelope {
    return envelopeJson.decodeFromString(bodyText())
}

private fun HttpRequestData.bodyText(): String {
    val bodyText = when (val body = body) {
        is OutgoingContent.ByteArrayContent -> body.bytes().decodeToString()
        is OutgoingContent.ReadChannelContent -> error("Unexpected read-channel body in test.")
        is OutgoingContent.WriteChannelContent -> error("Unexpected write-channel body in test.")
        is OutgoingContent.ContentWrapper -> error("Unexpected wrapped body in test.")
        is OutgoingContent.NoContent -> error("Expected JSON body but request was empty.")
        is OutgoingContent.ProtocolUpgrade -> error("Unexpected protocol upgrade body in test.")
    }
    return bodyText
}
