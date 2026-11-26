package com.bookintok.api.user

import com.bookintok.api.IntegrationTest
import org.junit.jupiter.api.Test

class UserApiTest : IntegrationTest() {

    @Test
    fun `requests without a token are rejected`() {
        getAs(null, "/api/v1/users/me").andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `health endpoint is public`() {
        getAs(null, "/actuator/health").andExpect { status { isOk() } }
    }

    @Test
    fun `registers a user and returns the private profile`() {
        postAs("alice", "/api/v1/users/me", """{"nickname": "alice_reads"}""")
            .andExpect {
                status { isCreated() }
                jsonPath("$.uid") { value("alice") }
                jsonPath("$.email") { value("alice@example.com") }
                jsonPath("$.rating.count") { value(0) }
            }
    }

    @Test
    fun `cannot register twice or reuse a nickname`() {
        register("alice")
        postAs("alice", "/api/v1/users/me", """{"nickname": "another"}""").andExpect { status { isConflict() } }
        postAs("bob", "/api/v1/users/me", """{"nickname": "ALICE"}""").andExpect { status { isConflict() } }
    }

    @Test
    fun `invalid nickname returns field errors`() {
        postAs("alice", "/api/v1/users/me", """{"nickname": "a!"}""")
            .andExpect {
                status { isBadRequest() }
                jsonPath("$.errors.nickname") { exists() }
            }
    }

    @Test
    fun `public profile hides email and location`() {
        register("alice", latitude = 39.47, longitude = -0.37)
        getAs("bob", "/api/v1/users/alice")
            .andExpect {
                status { isOk() }
                jsonPath("$.nickname") { value("alice") }
                jsonPath("$.email") { doesNotExist() }
                jsonPath("$.latitude") { doesNotExist() }
            }
    }

    @Test
    fun `rejects coordinates out of range`() {
        register("alice")
        putAs("alice", "/api/v1/users/me/location", """{"latitude": 120.0, "longitude": 0.0}""")
            .andExpect { status { isBadRequest() } }
    }
}
