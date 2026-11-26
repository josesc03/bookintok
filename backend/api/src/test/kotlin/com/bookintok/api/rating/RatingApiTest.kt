package com.bookintok.api.rating

import com.bookintok.api.IntegrationTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class RatingApiTest : IntegrationTest() {

    @BeforeEach
    fun setUp() {
        register("owner")
        register("reader")
    }

    private fun completeExchange() {
        val exchangeId = requestExchange(createBook("owner"), "reader")
        postAs("owner", "/api/v1/exchanges/$exchangeId/confirmation")
        postAs("reader", "/api/v1/exchanges/$exchangeId/confirmation")
    }

    @Test
    fun `cannot rate without a completed exchange`() {
        requestExchange(createBook("owner"), "reader")
        postAs("reader", "/api/v1/users/owner/ratings", """{"score": 5}""")
            .andExpect { status { isUnprocessableContent() } }
    }

    @Test
    fun `both users can rate each other once and the average is updated`() {
        completeExchange()

        postAs("reader", "/api/v1/users/owner/ratings", """{"score": 4, "comment": "Book as described"}""")
            .andExpect {
                status { isCreated() }
                jsonPath("$.raterNickname") { value("reader") }
            }
        postAs("reader", "/api/v1/users/owner/ratings", """{"score": 1}""").andExpect { status { isConflict() } }
        postAs("owner", "/api/v1/users/reader/ratings", """{"score": 5}""").andExpect { status { isCreated() } }

        getAs("reader", "/api/v1/users/owner")
            .andExpect {
                jsonPath("$.rating.average") { value(4.0) }
                jsonPath("$.rating.count") { value(1) }
            }
        getAs("owner", "/api/v1/users/owner/ratings")
            .andExpect { jsonPath("$.content[0].comment") { value("Book as described") } }
    }

    @Test
    fun `score must be between 1 and 5 and users cannot rate themselves`() {
        completeExchange()
        postAs("reader", "/api/v1/users/owner/ratings", """{"score": 6}""").andExpect { status { isBadRequest() } }
        postAs("reader", "/api/v1/users/reader/ratings", """{"score": 5}""")
            .andExpect { status { isUnprocessableContent() } }
    }
}
