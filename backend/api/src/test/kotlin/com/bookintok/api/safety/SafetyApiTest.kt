package com.bookintok.api.safety

import com.bookintok.api.IntegrationTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SafetyApiTest : IntegrationTest() {

    private var bookId = 0L

    @BeforeEach
    fun setUp() {
        register("owner")
        register("reader")
        bookId = createBook("owner")
    }

    @Test
    fun `blocking cancels open chats, hides books both ways and prevents new requests`() {
        val exchangeId = requestExchange(bookId, "reader")

        putAs("owner", "/api/v1/users/me/blocks/reader").andExpect { status { isNoContent() } }

        getAs("reader", "/api/v1/exchanges/$exchangeId").andExpect {
            jsonPath("$.status") { value("CANCELLED") }
            // The app hides "resume" for blocked chats, on both sides
            jsonPath("$.blocked") { value(true) }
        }
        getAs("reader", "/api/v1/books").andExpect { jsonPath("$.totalElements") { value(0) } }
        postAs("reader", "/api/v1/exchanges/$exchangeId/reopen-request").andExpect { status { isForbidden() } }
        postAs("reader", "/api/v1/books/${createBook("owner", title = "Otro")}/exchanges").andExpect { status { isForbidden() } }
        getAs("owner", "/api/v1/users/me/blocks").andExpect { jsonPath("$[0].nickname") { value("reader") } }
    }

    @Test
    fun `unblocking makes the books visible again`() {
        putAs("reader", "/api/v1/users/me/blocks/owner")
        getAs("reader", "/api/v1/books").andExpect { jsonPath("$.totalElements") { value(0) } }

        deleteAs("reader", "/api/v1/users/me/blocks/owner").andExpect { status { isNoContent() } }
        getAs("reader", "/api/v1/books").andExpect { jsonPath("$.totalElements") { value(1) } }
        val exchangeId = requestExchange(bookId, "reader")
        getAs("owner", "/api/v1/exchanges/$exchangeId").andExpect { jsonPath("$.blocked") { value(false) } }
    }

    @Test
    fun `users can report others but not themselves`() {
        postAs("reader", "/api/v1/users/owner/reports", """{"reason": "SCAM", "comment": "Pide dinero"}""")
            .andExpect { status { isCreated() } }
        postAs("reader", "/api/v1/users/reader/reports", """{"reason": "SPAM"}""").andExpect { status { isUnprocessableContent() } }
        postAs("reader", "/api/v1/users/owner/reports", """{"reason": "NOT_A_REASON"}""").andExpect { status { isBadRequest() } }
        postAs("reader", "/api/v1/users/nobody/reports", """{"reason": "SPAM"}""").andExpect { status { isNotFound() } }
    }
}
