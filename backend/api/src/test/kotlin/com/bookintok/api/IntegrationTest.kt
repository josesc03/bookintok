package com.bookintok.api

import org.intellij.lang.annotations.Language
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockHttpServletRequestDsl
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import org.testcontainers.postgresql.PostgreSQLContainer
import org.junit.jupiter.api.BeforeEach

/**
 * Base class for tests that run the whole application against a real PostgreSQL in Docker.
 * Requests are authenticated as a given Firebase uid with spring-security-test's `jwt()`.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
abstract class IntegrationTest {

    companion object {
        /**
         * One container for the whole test run: Spring caches the application context between
         * test classes, so the database must outlive each class (it stops when the JVM exits).
         */
        @ServiceConnection
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:17-alpine").apply { start() }
    }

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var jdbc: JdbcTemplate

    @BeforeEach
    fun cleanDatabase() {
        jdbc.execute("TRUNCATE ratings, exchanges, books, users RESTART IDENTITY CASCADE")
    }

    private fun MockHttpServletRequestDsl.authAs(uid: String?) {
        if (uid != null) with(jwt().jwt { it.subject(uid).claim("email", "$uid@example.com") })
    }

    private fun MockHttpServletRequestDsl.json(body: String?) {
        if (body != null) {
            contentType = MediaType.APPLICATION_JSON
            content = body
        }
    }

    fun getAs(uid: String?, url: String): ResultActionsDsl = mockMvc.get(url) { authAs(uid) }

    fun postAs(uid: String?, url: String, @Language("JSON") body: String? = null): ResultActionsDsl =
        mockMvc.post(url) { authAs(uid); json(body) }

    fun putAs(uid: String?, url: String, @Language("JSON") body: String? = null): ResultActionsDsl =
        mockMvc.put(url) { authAs(uid); json(body) }

    fun deleteAs(uid: String?, url: String): ResultActionsDsl = mockMvc.delete(url) { authAs(uid) }

    // Small helpers to build the scenario of each test through the public API

    fun register(uid: String, latitude: Double? = null, longitude: Double? = null) {
        postAs(uid, "/api/v1/users/me", """{"nickname": "$uid"}""").andExpect { status { isCreated() } }
        if (latitude != null && longitude != null) {
            putAs(uid, "/api/v1/users/me/location", """{"latitude": $latitude, "longitude": $longitude}""")
                .andExpect { status { isNoContent() } }
        }
    }

    fun createBook(ownerUid: String, title: String = "Dune", language: String = "en"): Long {
        val body = """
            {
              "title": "$title", "author": "Frank Herbert", "mainCategory": "Sci-fi",
              "language": "$language", "coverType": "PAPERBACK", "condition": "USED"
            }
        """.trimIndent()
        val response = postAs(ownerUid, "/api/v1/books", body)
            .andExpect { status { isCreated() } }
            .andReturn().response.contentAsString
        return idFrom(response)
    }

    fun requestExchange(bookId: Long, requesterUid: String): Long {
        val response = postAs(requesterUid, "/api/v1/books/$bookId/exchanges")
            .andExpect { status { isCreated() } }
            .andReturn().response.contentAsString
        return idFrom(response)
    }

    private fun idFrom(json: String): Long =
        Regex("\"id\"\\s*:\\s*(\\d+)").find(json)?.groupValues?.get(1)?.toLong()
            ?: error("No id in response: $json")
}
