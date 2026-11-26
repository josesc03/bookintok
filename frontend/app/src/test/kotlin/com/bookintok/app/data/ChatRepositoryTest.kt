package com.bookintok.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.bookintok.app.FakeServer
import com.bookintok.app.core.database.BookintokDatabase
import com.bookintok.app.core.network.ApiException
import com.bookintok.app.json
import com.bookintok.app.testJson
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import kotlin.time.Duration.Companion.seconds

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ChatRepositoryTest {

    private lateinit var database: BookintokDatabase
    private var online = true

    private fun message(id: String, at: String, content: String = "msg $id") =
        """{"id": "$id", "exchangeId": 7, "senderUid": "owner", "content": "$content", "sentAt": "$at"}"""

    private val server = FakeServer { request ->
        if (!online) throw IOException("No network")
        val before = request.url.parameters["before"]
        when {
            before == null -> json("[${message("3", "2026-05-01T10:03:00Z")}, ${message("2", "2026-05-01T10:02:00Z")}]")
            before == "2026-05-01T10:02:00Z" -> json("[${message("1", "2026-05-01T10:01:00Z")}]")
            else -> respondError(HttpStatusCode.InternalServerError)
        }
    }

    private fun repository() = DefaultChatRepository(
        client = server.client,
        tokens = { "token-123" },
        dao = database.messageDao(),
        json = testJson,
        chatBaseUrl = "ws://chat.test:8081",
    )

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), BookintokDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `refresh stores the history in Room, oldest first`() = runTest {
        val repository = repository()
        repository.refresh(7)

        assertEquals(listOf("msg 2", "msg 3"), repository.messages(7).first().map { it.content })
        val request = server.requests.single()
        assertEquals("http://chat.test:8081/api/v1/exchanges/7/messages", request.url.toString().substringBefore('?'))
        assertEquals("Bearer token-123", request.headers[HttpHeaders.Authorization])
    }

    @Test
    fun `cached conversation stays readable when the network is gone`() = runTest {
        val repository = repository()
        repository.refresh(7)

        online = false
        assertThrows(IOException::class.java) { kotlinx.coroutines.runBlocking { repository.refresh(7) } }

        repository.messages(7).test {
            assertEquals(listOf("2", "3"), awaitItem().map { it.id })
        }
    }

    @Test
    fun `loadOlder asks for messages before the oldest cached one`() = runTest {
        val repository = repository()
        repository.refresh(7)

        val more = repository.loadOlder(7)

        assertFalse("A short page means there is nothing older", more)
        assertEquals("2026-05-01T10:02:00Z", server.requests.last().url.parameters["before"])
        assertEquals(listOf("1", "2", "3"), repository.messages(7).first().map { it.id })
    }

    @Test
    fun `server errors become ApiException`() = runTest {
        val failing = FakeServer { json("""{"detail": "Not a participant"}""", HttpStatusCode.Forbidden) }
        val repository = DefaultChatRepository(failing.client, { "t" }, database.messageDao(), testJson, "ws://chat.test")

        val error = runCatching { repository.refresh(7) }.exceptionOrNull()
        assertTrue(error is ApiException)
        assertEquals(403, (error as ApiException).status)
    }

    @Test
    fun `sending without an open connection fails clearly`() = runTest {
        val error = runCatching { repository().send(7, "hello") }.exceptionOrNull()
        assertTrue(error is NotConnectedException)
    }

    @Test
    fun `reconnection backoff grows and is capped at 30 seconds`() {
        assertEquals(1.seconds, DefaultChatRepository.backoff(0))
        assertEquals(8.seconds, DefaultChatRepository.backoff(3))
        assertEquals(30.seconds, DefaultChatRepository.backoff(10))
    }

}
