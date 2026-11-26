package com.bookintok.api.config

import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.jwt.Jwt
import java.time.Instant
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FirebaseTokenValidatorTest {

    private val issuer = "https://securetoken.google.com/bookintok-test"
    private val validator = SecurityConfig.firebaseTokenValidator(issuer, "bookintok-test")

    private fun token(
        emailVerified: Boolean? = true,
        audience: String = "bookintok-test",
        tokenIssuer: String = issuer,
    ): Jwt = Jwt.withTokenValue("token")
        .header("alg", "RS256")
        .subject("uid-1")
        .issuer(tokenIssuer)
        .audience(listOf(audience))
        .issuedAt(Instant.now().minusSeconds(60))
        .expiresAt(Instant.now().plusSeconds(3600))
        .apply { if (emailVerified != null) claim("email_verified", emailVerified) }
        .build()

    @Test
    fun `accepts a token for our project with a verified email`() {
        assertFalse(validator.validate(token()).hasErrors())
    }

    @Test
    fun `rejects unverified or missing email verification`() {
        assertTrue(validator.validate(token(emailVerified = false)).hasErrors())
        assertTrue(validator.validate(token(emailVerified = null)).hasErrors())
    }

    @Test
    fun `rejects tokens issued for another Firebase project`() {
        assertTrue(validator.validate(token(audience = "someone-else")).hasErrors())
        assertTrue(validator.validate(token(tokenIssuer = "https://securetoken.google.com/someone-else")).hasErrors())
    }
}
