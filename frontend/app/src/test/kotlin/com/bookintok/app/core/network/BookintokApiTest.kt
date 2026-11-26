package com.bookintok.app.core.network

import com.bookintok.app.FakeServer
import com.bookintok.app.json
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BookintokApiTest {

    private val emptyPage = """{"content": [], "page": 0, "size": 20, "totalElements": 0, "totalPages": 0}"""

    @Test
    fun `search sends only the filters that are set, with the user's token`() = runTest {
        val server = FakeServer { json(emptyPage) }
        val api = BookintokApi(server.client, { "id-token" }, "http://api.test")

        api.searchBooks(BookSearch(query = "dune", maxDistanceKm = 20), page = 1)

        val request = server.requests.single()
        assertEquals("Bearer id-token", request.headers[HttpHeaders.Authorization])
        assertEquals("dune", request.url.parameters["q"])
        assertEquals("20", request.url.parameters["maxDistanceKm"])
        assertEquals("1", request.url.parameters["page"])
        assertNull(request.url.parameters["language"])
    }

    @Test
    fun `problem details become a readable ApiException`() = runTest {
        val server = FakeServer {
            json("""{"title": "Conflict", "detail": "You already have an open exchange for this book"}""", HttpStatusCode.Conflict)
        }
        val api = BookintokApi(server.client, { "t" }, "http://api.test")

        val error = runCatching { api.requestExchange(5) }.exceptionOrNull() as ApiException
        assertEquals(409, error.status)
        assertEquals("You already have an open exchange for this book", error.detail)
    }

    @Test
    fun `validation errors expose the field message`() = runTest {
        val server = FakeServer {
            json("""{"detail": "Request validation failed", "errors": {"nickname": "is taken"}}""", HttpStatusCode.BadRequest)
        }
        val api = BookintokApi(server.client, { "t" }, "http://api.test")

        val error = runCatching { api.register(RegisterUserRequest("x")) }.exceptionOrNull()
        assertTrue(error is ApiException)
        assertEquals("is taken", (error as ApiException).detail)
    }
}
