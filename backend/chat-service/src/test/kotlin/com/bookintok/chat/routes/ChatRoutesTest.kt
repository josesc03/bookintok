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
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
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
    private val inboxes = Inboxes()
    private val devices = com.bookintok.chat.push.InMemoryDevices()

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
                    inboxes = inboxes,
                    devices = devices,
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
    fun `exchange changes are announced to the other participant, even on closed exchanges`() = chatTest {
        val client = wsClient()
        client.webSocket("/ws/exchanges/$closedExchange", request = { bearerAuth(TestTokens.forUser("owner")) }) {
            val ownerSession = this
            client.webSocket("/ws/exchanges/$closedExchange", request = { bearerAuth(TestTokens.forUser("reader")) }) {
                awaitConnections(closedExchange, 2)
                send(Frame.Text("""{"type": "exchange_changed"}"""))
                val event = assertIs<ServerEvent.ExchangeChanged>(receiveEvent(ownerSession.incoming))
                assertEquals(closedExchange, event.exchangeId)
            }
        }
        assertEquals(0, messages.history(closedExchange, null, 10).size)
    }

    @Test
    fun `senders can delete their recent messages and both sides are told`() = chatTest {
        val mine = messages.save(openExchange, "reader", "oops", Instant.now())
        val client = wsClient()
        client.webSocket("/ws/exchanges/$openExchange", request = { bearerAuth(TestTokens.forUser("owner")) }) {
            val ownerSession = this
            client.webSocket("/ws/exchanges/$openExchange", request = { bearerAuth(TestTokens.forUser("reader")) }) {
                awaitConnections(openExchange, 2)
                send(Frame.Text("""{"type": "delete_message", "messageId": "${mine.id}"}"""))
                assertEquals(mine.id, assertIs<ServerEvent.MessageDeleted>(receiveEvent(ownerSession.incoming)).messageId)
            }
        }
        assertEquals(null, messages.find(mine.id))
    }

    @Test
    fun `messages of others or older than 30 minutes cannot be deleted`() = chatTest {
        val theirs = messages.save(openExchange, "owner", "hi", Instant.now())
        val old = messages.save(openExchange, "reader", "old", Instant.now().minusSeconds(31 * 60))
        wsClient().webSocket("/ws/exchanges/$openExchange", request = { bearerAuth(TestTokens.forUser("reader")) }) {
            send(Frame.Text("""{"type": "delete_message", "messageId": "${theirs.id}"}"""))
            assertIs<ServerEvent.Error>(receiveEvent(incoming))
            send(Frame.Text("""{"type": "delete_message", "messageId": "${old.id}"}"""))
            assertIs<ServerEvent.Error>(receiveEvent(incoming))
        }
        assertEquals(2, messages.history(openExchange, null, 10).size)
    }

    @Test
    fun `the other participant's inbox gets new messages without opening the chat`() = chatTest {
        val client = wsClient()
        client.webSocket("/ws/inbox", request = { bearerAuth(TestTokens.forUser("owner")) }) {
            val ownerInbox = this
            withTimeout(5_000) { while (inboxes.connectionCount("owner") < 1) delay(10) }
            client.webSocket("/ws/exchanges/$openExchange", request = { bearerAuth(TestTokens.forUser("reader")) }) {
                send(Frame.Text("""{"content": "hola"}"""))
                receiveEvent(incoming)
                send(Frame.Text("""{"type": "exchange_changed"}"""))
            }
            val message = assertIs<ServerEvent.NewMessage>(receiveEvent(ownerInbox.incoming)).message
            assertEquals(openExchange, message.exchangeId)
            assertIs<ServerEvent.ExchangeChanged>(receiveEvent(ownerInbox.incoming))
        }
    }

    @Test
    fun `inbox sync returns what the user's exchanges received since a date`() = chatTest {
        val base = Instant.parse("2026-05-01T10:00:00Z")
        messages.save(openExchange, "owner", "old", base)
        messages.save(openExchange, "owner", "new", base.plusSeconds(60))
        messages.save(99, "someone", "not mine", base.plusSeconds(60))

        val response = client.get("/api/v1/inbox?since=${base.plusSeconds(1)}") { bearerAuth(TestTokens.forUser("reader")) }
        val body = ChatJson.decodeFromString<List<MessageDto>>(response.bodyAsText())
        assertEquals(listOf("new"), body.map { it.content })
    }

    @Test
    fun `devices register their push token and remove it when signing out`() = chatTest {
        val put = client.put("/api/v1/devices") {
            bearerAuth(TestTokens.forUser("reader"))
            contentType(ContentType.Application.Json)
            setBody("""{"token": "fcm-token-1"}""")
        }
        assertEquals(HttpStatusCode.NoContent, put.status)
        assertEquals(listOf("fcm-token-1"), devices.tokensOf("reader"))

        client.delete("/api/v1/devices?token=fcm-token-1") { bearerAuth(TestTokens.forUser("reader")) }
        assertEquals(emptyList(), devices.tokensOf("reader"))
    }

    @Test
    fun `deleting the account removes the user's conversations and devices`() = chatTest {
        messages.save(openExchange, "owner", "hola", Instant.now())
        messages.save(closedExchange, "reader", "adios", Instant.now())
        messages.save(99, "someone", "not theirs", Instant.now())
        devices.register("reader", "phone")

        val response = client.delete("/api/v1/me") { bearerAuth(TestTokens.forUser("reader")) }

        assertEquals(HttpStatusCode.NoContent, response.status)
        assertEquals(0, messages.history(openExchange, null, 10).size + messages.history(closedExchange, null, 10).size)
        assertEquals(1, messages.history(99, null, 10).size)
        assertEquals(emptyList(), devices.tokensOf("reader"))
    }

    @Test
    fun `empty messages are rejected`() = chatTest {
        wsClient().webSocket("/ws/exchanges/$openExchange", request = { bearerAuth(TestTokens.forUser("owner")) }) {
            send(Frame.Text("""{"content": "   "}"""))
            assertEquals("Message is empty", assertIs<ServerEvent.Error>(receiveEvent(incoming)).reason)
        }
    }
}
