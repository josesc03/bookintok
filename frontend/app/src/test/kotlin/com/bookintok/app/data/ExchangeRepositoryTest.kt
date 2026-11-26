package com.bookintok.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.bookintok.app.FakeServer
import com.bookintok.app.core.database.BookintokDatabase
import com.bookintok.app.core.network.BookintokApi
import com.bookintok.app.core.network.ExchangeStatus
import com.bookintok.app.json
import io.ktor.http.HttpMethod
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExchangeRepositoryTest {

    private lateinit var database: BookintokDatabase

    private fun exchange(id: Long, status: String, updatedAt: String, ownerConfirmed: Boolean = false) = """
        {"id": $id, "status": "$status", "book": {"id": 1, "title": "Dune"},
         "owner": {"uid": "owner", "nickname": "ana", "confirmed": $ownerConfirmed},
         "requester": {"uid": "reader", "nickname": "luis", "confirmed": false},
         "createdAt": "2026-05-01T09:00:00Z", "updatedAt": "$updatedAt"}
    """.trimIndent()

    private val server = FakeServer { request ->
        when {
            request.method == HttpMethod.Post && request.url.encodedPath.endsWith("/confirmation") ->
                json(exchange(2, "PENDING", "2026-05-03T10:00:00Z", ownerConfirmed = true))
            else -> json(
                """{"content": [${exchange(1, "COMPLETED", "2026-05-01T10:00:00Z")}, ${exchange(2, "PENDING", "2026-05-02T10:00:00Z")}],
                    "page": 0, "size": 100, "totalElements": 2, "totalPages": 1}""",
            )
        }
    }

    private fun repository() = DefaultExchangeRepository(
        api = BookintokApi(server.client, { "token" }, "http://api.test"),
        dao = database.exchangeDao(),
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
    fun `refreshAll caches exchanges, most recently updated first`() = runTest {
        val repository = repository()
        repository.refreshAll()

        val cached = repository.observeAll().first()
        assertEquals(listOf(2L, 1L), cached.map { it.id })
        assertEquals("luis", cached.first().counterpartOf("owner").nickname)
    }

    @Test
    fun `confirming updates the cached exchange`() = runTest {
        val repository = repository()
        repository.refreshAll()

        repository.confirm(2)

        val exchange = repository.observe(2).first()!!
        assertTrue(exchange.owner.confirmed)
        assertEquals(ExchangeStatus.PENDING, exchange.status)
        assertEquals("http://api.test/api/v1/exchanges/2/confirmation", server.requests.last().url.toString())
    }
}
