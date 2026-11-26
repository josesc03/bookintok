package com.bookintok.chat.routes

import com.bookintok.chat.ChatDependencies
import com.bookintok.chat.ChatJson
import com.bookintok.chat.FakeExchangeAccess
import com.bookintok.chat.InMemoryMessageRepository
import com.bookintok.chat.TestTokens
import com.bookintok.chat.message.MessageDto
import com.bookintok.chat.message.ServerEvent
import com.bookintok.chat.module
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ChatRoutesTest {

    private val openExchange = 1L
    private val closedExchange = 2L
    private val messages = InMemoryMessageRepository()
    private val rooms = ChatRooms()

    private fun chatTest(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            module(
                ChatDependencies(
                    tokenVerification = TestTokens.verification,
                    exchangeAccess = FakeExchangeAccess(
                        mapOf(
                            openExchange to (setOf("owner", "reader") to true),
                            closedExchange to (setOf("owner", "reader") to false),
                        ),
                    ),
                    messages = messages,
                    rooms = rooms,
                ),
            )
        }
        block()
    }

    private fun ApplicationTestBuilder.wsClient(): HttpClient = createClient { install(WebSockets) }

    private suspend fun receiveEvent(incoming: kotlinx.coroutines.channels.ReceiveChannel<Frame>): ServerEvent =
        withTimeout(5_000) { ChatJson.decodeFromString((incoming.receive() as Frame.Text).readText()) }

    /** Waits until the server has registered [count] sessions, so broadcasts are not missed. */
    private suspend fun awaitConnections(exchangeId: Long, count: Int) = withTimeout(5_000) {
        while (rooms.connectionCount(exchangeId) < count) delay(10)
    }

    @Test
    fun `health is public`() = chatTest {
        assertEquals("OK", client.get("/health").bodyAsText())
    }

    @Test
    fun `history requires a valid token`() = chatTest {
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/v1/exchanges/$openExchange/messages").status)
        val forged = client.get("/api/v1/exchanges/$openExchange/messages") { bearerAuth("not-a-jwt") }
        assertEquals(HttpStatusCode.Unauthorized, forged.status)
    }

    @Test
    fun `accounts with an unverified email are rejected`() = chatTest {
        val response = client.get("/api/v1/exchanges/$openExchange/messages") {
            bearerAuth(TestTokens.forUser("reader", emailVerified = false))
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `history is only visible to participants`() = chatTest {
        val response = client.get("/api/v1/exchanges/$openExchange/messages") { bearerAuth(TestTokens.forUser("stranger")) }
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `history pages backwards from the newest message`() = chatTest {
        val base = Instant.parse("2026-05-01T10:00:00Z")
        repeat(3) { i -> messages.save(openExchange, "owner", "message $i", base.plusSeconds(i.toLong())) }

        val page = client.get("/api/v1/exchanges/$openExchange/messages?limit=2&before=${base.plusSeconds(2)}") {
            bearerAuth(TestTokens.forUser("reader"))
        }
        val body = ChatJson.decodeFromString<List<MessageDto>>(page.bodyAsText())
        assertEquals(listOf("message 1", "message 0"), body.map { it.content })
    }

    @Test
    fun `invalid before parameter is a bad request`() = chatTest {
        val response = client.get("/api/v1/exchanges/$openExchange/messages?before=yesterday") {
            bearerAuth(TestTokens.forUser("reader"))
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `a message is delivered to both participants and stored`() = chatTest {
        val client = wsClient()
        client.webSocket("/ws/exchanges/$openExchange", request = { bearerAuth(TestTokens.forUser("owner")) }) {
            val ownerSession = this
            client.webSocket("/ws/exchanges/$openExchange", request = { bearerAuth(TestTokens.forUser("reader")) }) {
                awaitConnections(openExchange, 2)
                send(Frame.Text("""{"content": "  Hi! Is the book still available?  "}"""))

                val received = assertIs<ServerEvent.NewMessage>(receiveEvent(incoming)).message
                assertEquals("Hi! Is the book still available?", received.content)
                assertEquals("reader", received.senderUid)

                val ownerReceived = assertIs<ServerEvent.NewMessage>(receiveEvent(ownerSession.incoming)).message
                assertEquals(received.id, ownerReceived.id)
            }
        }
        assertEquals(1, messages.history(openExchange, null, 10).size)
    }

    @Test
    fun `strangers cannot open the chat`() = chatTest {
        wsClient().webSocket("/ws/exchanges/$openExchange", request = { bearerAuth(TestTokens.forUser("stranger")) }) {
            val reason = withTimeout(5_000) { closeReason.await() }
            assertEquals(CloseReason.Codes.VIOLATED_POLICY.code, reason?.code)
        }
    }

    @Test
    fun `closed exchanges are read-only and invalid payloads are reported`() = chatTest {
        wsClient().webSocket("/ws/exchanges/$closedExchange", request = { bearerAuth(TestTokens.forUser("owner")) }) {
            send(Frame.Text("""{"content": "hello"}"""))
            assertIs<ServerEvent.Error>(receiveEvent(incoming))

            send(Frame.Text("not json"))
            assertEquals("Invalid message format", assertIs<ServerEvent.Error>(receiveEvent(incoming)).reason)
        }
        assertEquals(0, messages.history(closedExchange, null, 10).size)
    }

    @Test
    fun `empty messages are rejected`() = chatTest {
        wsClient().webSocket("/ws/exchanges/$openExchange", request = { bearerAuth(TestTokens.forUser("owner")) }) {
            send(Frame.Text("""{"content": "   "}"""))
            assertEquals("Message is empty", assertIs<ServerEvent.Error>(receiveEvent(incoming)).reason)
        }
    }
}
