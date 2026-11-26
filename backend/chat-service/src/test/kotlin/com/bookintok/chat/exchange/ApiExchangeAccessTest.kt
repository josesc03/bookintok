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
        val access = accessWith(HttpStatusCode.OK, """{"id": 7, "status": "PENDING", "book": {"id": 1}}""")
        assertEquals(AccessResult.Allowed(open = true), access.check(7, "token-123"))
        assertEquals("Bearer token-123", lastAuthorization)
    }

    @Test
    fun `completed exchange is read-only`() = runTest {
        val access = accessWith(HttpStatusCode.OK, """{"id": 7, "status": "COMPLETED"}""")
        assertEquals(AccessResult.Allowed(open = false), access.check(7, "token"))
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
