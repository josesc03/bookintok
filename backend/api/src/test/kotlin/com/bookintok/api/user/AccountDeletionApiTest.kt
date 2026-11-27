package com.bookintok.api.user

import com.bookintok.api.IntegrationTest
import org.junit.jupiter.api.Test

class AccountDeletionApiTest : IntegrationTest() {

    @Test
    fun `deleting the account removes the profile, books, chats and ratings`() {
        register("owner")
        register("reader")
        val ownBook = createBook("reader", title = "Mi libro")
        val othersBook = createBook("owner")
        val exchangeId = requestExchange(othersBook, "reader")
        postAs("owner", "/api/v1/exchanges/$exchangeId/confirmation")
        postAs("reader", "/api/v1/exchanges/$exchangeId/confirmation")
        postAs("owner", "/api/v1/users/reader/ratings", """{"score": 5}""")
        putAs("reader", "/api/v1/users/me/blocks/owner")

        deleteAs("reader", "/api/v1/users/me").andExpect { status { isNoContent() } }

        getAs("owner", "/api/v1/users/reader").andExpect { status { isNotFound() } }
        getAs("owner", "/api/v1/books/$ownBook").andExpect { status { isNotFound() } }
        getAs("owner", "/api/v1/exchanges").andExpect { jsonPath("$.totalElements") { value(0) } }
        // The other user's book is untouched
        getAs("owner", "/api/v1/books/$othersBook").andExpect { status { isOk() } }
        // The same Firebase account can create a new, empty profile afterwards
        register("reader")
    }
}
