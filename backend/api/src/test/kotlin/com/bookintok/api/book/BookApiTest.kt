package com.bookintok.api.book

import com.bookintok.api.IntegrationTest
import org.junit.jupiter.api.Test

class BookApiTest : IntegrationTest() {

    @Test
    fun `search excludes the viewer's own books`() {
        register("alice")
        register("bob")
        createBook("alice", title = "Alice's book")
        createBook("bob", title = "Bob's book")

        getAs("alice", "/api/v1/books")
            .andExpect {
                status { isOk() }
                jsonPath("$.totalElements") { value(1) }
                jsonPath("$.content[0].title") { value("Bob's book") }
                jsonPath("$.content[0].owner.nickname") { value("bob") }
            }
    }

    @Test
    fun `search filters by text and language, case-insensitively`() {
        register("alice")
        register("bob")
        createBook("bob", title = "Dune", language = "en")
        createBook("bob", title = "Cien años de soledad", language = "es")

        getAs("alice", "/api/v1/books?q=DUNE").andExpect { jsonPath("$.totalElements") { value(1) } }
        getAs("alice", "/api/v1/books?language=ES")
            .andExpect { jsonPath("$.content[0].title") { value("Cien años de soledad") } }
    }

    @Test
    fun `like wildcards in the search text are treated literally`() {
        register("alice")
        register("bob")
        createBook("bob", title = "Dune")

        getAs("alice", "/api/v1/books?q=%25").andExpect { jsonPath("$.totalElements") { value(0) } }
    }

    @Test
    fun `search by distance uses the owners' location`() {
        register("alice", latitude = 39.4699, longitude = -0.3763) // Valencia
        register("near", latitude = 39.4840, longitude = -0.3530) // ~2.6 km away
        register("far", latitude = 40.4168, longitude = -3.7038) // Madrid
        createBook("near", title = "Near book")
        createBook("far", title = "Far book")

        getAs("alice", "/api/v1/books?maxDistanceKm=10")
            .andExpect {
                jsonPath("$.totalElements") { value(1) }
                jsonPath("$.content[0].title") { value("Near book") }
            }
        getAs("alice", "/api/v1/books?maxDistanceKm=500").andExpect { jsonPath("$.totalElements") { value(2) } }
    }

    @Test
    fun `distance search requires the viewer's location`() {
        register("alice")
        getAs("alice", "/api/v1/books?maxDistanceKm=10").andExpect { status { isUnprocessableContent() } }
    }

    @Test
    fun `only the owner can edit or delete a book`() {
        register("alice")
        register("bob")
        val bookId = createBook("alice")
        val update = """
            {"title": "Dune Messiah", "author": "Frank Herbert", "mainCategory": "Sci-fi",
             "language": "en", "coverType": "HARDCOVER", "condition": "LIKE_NEW"}
        """.trimIndent()

        putAs("bob", "/api/v1/books/$bookId", update).andExpect { status { isForbidden() } }
        deleteAs("bob", "/api/v1/books/$bookId").andExpect { status { isForbidden() } }

        putAs("alice", "/api/v1/books/$bookId", update)
            .andExpect {
                status { isOk() }
                jsonPath("$.title") { value("Dune Messiah") }
            }
        deleteAs("alice", "/api/v1/books/$bookId").andExpect { status { isNoContent() } }
        getAs("alice", "/api/v1/books/$bookId").andExpect { status { isNotFound() } }
    }

    @Test
    fun `invalid book is rejected`() {
        register("alice")
        postAs("alice", "/api/v1/books", """{"title": "", "author": "x", "mainCategory": "x",
            "language": "en", "coverType": "PAPERBACK", "condition": "USED"}""")
            .andExpect {
                status { isBadRequest() }
                jsonPath("$.errors.title") { exists() }
            }
    }
}
