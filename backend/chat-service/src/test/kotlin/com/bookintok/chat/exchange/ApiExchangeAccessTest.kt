package com.bookintok.chat.exchange

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ApiExchangeAccessTest {

    private var lastAuthorization: String? = null

    private fun accessWith(status: HttpStatusCode, body: String = "") = ApiExchangeAccess(
        HttpClient(
            MockEngine { request ->
                lastAuthorization = request.headers[HttpHeaders.Authorization]
                respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
            },
        ),
        apiBaseUrl = "http://api",
    )

    @Test
    fun `participant of a pending exchange can write`() = runTest {
        val access = accessWith(
            HttpStatusCode.OK,
            """{"id": 7, "status": "PENDING", "book": {"id": 1}, "owner": {"uid": "ana", "nickname": "ana_lee"}, "requester": {"uid": "luis"}, "book": {"id": 1, "title": "Dune"}}""",
        )
        val expected = AccessResult.Allowed(
            open = true,
            participants = setOf("ana", "luis"),
            nicknames = mapOf("ana" to "ana_lee", "luis" to ""),
            bookTitle = "Dune",
        )
        assertEquals(expected, access.check(7, "token-123"))
        assertEquals("Bearer token-123", lastAuthorization)
    }

    @Test
    fun `cancelled and completed exchanges are read-only`() = runTest {
        assertEquals(AccessResult.Allowed(open = false), accessWith(HttpStatusCode.OK, """{"id": 7, "status": "CANCELLED"}""").check(7, "token"))
        val access = accessWith(HttpStatusCode.OK, """{"id": 7, "status": "COMPLETED"}""")
        assertEquals(AccessResult.Allowed(open = false), access.check(7, "token"))
    }

    @Test
    fun `lists the ids of the user's exchanges`() = runTest {
        val access = accessWith(HttpStatusCode.OK, """{"content": [{"id": 3, "status": "PENDING"}, {"id": 9, "status": "CANCELLED"}], "page": 0}""")
        assertEquals(listOf(3L, 9L), access.exchangeIdsOf("token"))
    }

    @Test
    fun `forbidden or missing exchange is denied`() = runTest {
        assertEquals(AccessResult.Denied, accessWith(HttpStatusCode.Forbidden).check(7, "token"))
        assertEquals(AccessResult.Denied, accessWith(HttpStatusCode.NotFound).check(7, "token"))
    }

    @Test
    fun `server errors are not mistaken for a denial`() = runTest {
        assertFailsWith<UpstreamUnavailableException> {
            accessWith(HttpStatusCode.InternalServerError).check(7, "token")
        }
    }
}
