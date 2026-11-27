package com.bookintok.api.exchange

import com.bookintok.api.IntegrationTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ExchangeApiTest : IntegrationTest() {

    private var bookId = 0L

    @BeforeEach
    fun setUp() {
        register("owner")
        register("reader")
        register("other")
        bookId = createBook("owner")
    }

    @Test
    fun `full exchange flow marks the book as exchanged and cancels other requests`() {
        val exchangeId = requestExchange(bookId, "reader")
        val otherExchangeId = requestExchange(bookId, "other")

        postAs("owner", "/api/v1/exchanges/$exchangeId/confirmation")
            .andExpect {
                status { isOk() }
                jsonPath("$.status") { value("PENDING") }
                jsonPath("$.owner.confirmed") { value(true) }
            }
        postAs("reader", "/api/v1/exchanges/$exchangeId/confirmation")
            .andExpect { jsonPath("$.status") { value("COMPLETED") } }

        getAs("owner", "/api/v1/books/$bookId").andExpect { jsonPath("$.status") { value("EXCHANGED") } }
        getAs("other", "/api/v1/exchanges/$otherExchangeId").andExpect { jsonPath("$.status") { value("CANCELLED") } }
        getAs("other", "/api/v1/books").andExpect { jsonPath("$.totalElements") { value(0) } }
        deleteAs("owner", "/api/v1/books/$bookId").andExpect { status { isConflict() } }
    }

    @Test
    fun `cannot request your own book and asking again returns the existing chat`() {
        postAs("owner", "/api/v1/books/$bookId/exchanges").andExpect { status { isUnprocessableContent() } }

        val exchangeId = requestExchange(bookId, "reader")
        postAs("reader", "/api/v1/books/$bookId/exchanges").andExpect {
            status { isOk() }
            jsonPath("$.id") { value(exchangeId) }
        }
    }

    @Test
    fun `asking again after cancelling returns the cancelled chat instead of a new one`() {
        val exchangeId = requestExchange(bookId, "reader")
        postAs("owner", "/api/v1/exchanges/$exchangeId/cancellation")
            .andExpect { jsonPath("$.status") { value("CANCELLED") } }

        postAs("reader", "/api/v1/books/$bookId/exchanges").andExpect {
            status { isOk() }
            jsonPath("$.id") { value(exchangeId) }
            jsonPath("$.status") { value("CANCELLED") }
        }
        getAs("owner", "/api/v1/exchanges").andExpect { jsonPath("$.totalElements") { value(1) } }
    }

    @Test
    fun `a cancelled exchange resumes only when the other participant accepts`() {
        val exchangeId = requestExchange(bookId, "reader")
        postAs("owner", "/api/v1/exchanges/$exchangeId/confirmation")
        postAs("owner", "/api/v1/exchanges/$exchangeId/cancellation")

        postAs("reader", "/api/v1/exchanges/$exchangeId/reopen-request").andExpect {
            status { isOk() }
            jsonPath("$.status") { value("CANCELLED") }
            jsonPath("$.reopenRequestedBy") { value("reader") }
        }
        postAs("reader", "/api/v1/exchanges/$exchangeId/reopen-acceptance").andExpect { status { isConflict() } }
        postAs("other", "/api/v1/exchanges/$exchangeId/reopen-acceptance").andExpect { status { isForbidden() } }

        postAs("owner", "/api/v1/exchanges/$exchangeId/reopen-acceptance").andExpect {
            status { isOk() }
            jsonPath("$.status") { value("PENDING") }
            jsonPath("$.owner.confirmed") { value(false) }
            jsonPath("$.reopenRequestedBy") { value(null) }
        }
    }

    @Test
    fun `a request to resume can be rejected or withdrawn`() {
        val exchangeId = requestExchange(bookId, "reader")
        postAs("reader", "/api/v1/exchanges/$exchangeId/cancellation")
        postAs("reader", "/api/v1/exchanges/$exchangeId/reopen-request")

        deleteAs("owner", "/api/v1/exchanges/$exchangeId/reopen-request").andExpect {
            status { isOk() }
            jsonPath("$.status") { value("CANCELLED") }
            jsonPath("$.reopenRequestedBy") { value(null) }
        }
        deleteAs("owner", "/api/v1/exchanges/$exchangeId/reopen-request").andExpect { status { isConflict() } }
    }

    @Test
    fun `pending exchanges cannot be resumed and exchanged books cannot be resumed either`() {
        val exchangeId = requestExchange(bookId, "reader")
        postAs("reader", "/api/v1/exchanges/$exchangeId/reopen-request").andExpect { status { isConflict() } }

        val otherId = requestExchange(bookId, "other")
        postAs("other", "/api/v1/exchanges/$otherId/cancellation")
        postAs("owner", "/api/v1/exchanges/$exchangeId/confirmation")
        postAs("reader", "/api/v1/exchanges/$exchangeId/confirmation")

        postAs("other", "/api/v1/exchanges/$otherId/reopen-request").andExpect { status { isConflict() } }
    }

    @Test
    fun `only participants can see or act on an exchange`() {
        val exchangeId = requestExchange(bookId, "reader")

        getAs("other", "/api/v1/exchanges/$exchangeId").andExpect { status { isForbidden() } }
        postAs("other", "/api/v1/exchanges/$exchangeId/confirmation").andExpect { status { isForbidden() } }
        getAs("reader", "/api/v1/exchanges/$exchangeId").andExpect { status { isOk() } }
    }

    @Test
    fun `archiving hides the chat only for the user who archives it`() {
        val exchangeId = requestExchange(bookId, "reader")

        putAs("reader", "/api/v1/exchanges/$exchangeId/archive").andExpect { jsonPath("$.archived") { value(true) } }
        getAs("owner", "/api/v1/exchanges/$exchangeId").andExpect { jsonPath("$.archived") { value(false) } }
        putAs("other", "/api/v1/exchanges/$exchangeId/archive").andExpect { status { isForbidden() } }

        deleteAs("reader", "/api/v1/exchanges/$exchangeId/archive").andExpect { jsonPath("$.archived") { value(false) } }
    }

    @Test
    fun `lists the user's exchanges filtered by status`() {
        val exchangeId = requestExchange(bookId, "reader")
        requestExchange(createBook("other", title = "Other book"), "reader")
        postAs("reader", "/api/v1/exchanges/$exchangeId/cancellation")

        getAs("reader", "/api/v1/exchanges").andExpect { jsonPath("$.totalElements") { value(2) } }
        getAs("reader", "/api/v1/exchanges?status=PENDING").andExpect { jsonPath("$.totalElements") { value(1) } }
        getAs("owner", "/api/v1/exchanges").andExpect { jsonPath("$.totalElements") { value(1) } }
    }
}
