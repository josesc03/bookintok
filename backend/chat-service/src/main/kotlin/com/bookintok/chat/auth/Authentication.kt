package com.bookintok.chat.auth

import com.auth0.jwk.JwkProviderBuilder
import com.auth0.jwt.interfaces.JWTVerifier
import io.ktor.http.HttpStatusCode
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.auth.parseAuthorizationHeader
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import java.net.URI
import java.util.concurrent.TimeUnit

const val FIREBASE_AUTH = "firebase"

/** How incoming ID tokens are verified. */
sealed interface TokenVerification {
    /** Production: tokens signed by Firebase, checked against Google's public keys. */
    data class Firebase(
        val projectId: String,
        val jwksUrl: String = "https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com",
    ) : TokenVerification

    /** Tests: a verifier built locally, so no network access is needed. */
    class Static(val verifier: JWTVerifier) : TokenVerification
}

fun Application.configureAuthentication(verification: TokenVerification) {
    install(Authentication) {
        jwt(FIREBASE_AUTH) {
            when (verification) {
                is TokenVerification.Firebase -> {
                    val keys = JwkProviderBuilder(URI(verification.jwksUrl).toURL())
                        .cached(10, 6, TimeUnit.HOURS)
                        .rateLimited(10, 1, TimeUnit.MINUTES)
                        .build()
                    verifier(keys, "https://securetoken.google.com/${verification.projectId}") {
                        withAudience(verification.projectId)
                        acceptLeeway(5)
                    }
                }
                is TokenVerification.Static -> verifier(verification.verifier)
            }
            validate { credential -> credential.payload.subject?.let { JWTPrincipal(credential.payload) } }
            challenge { _, _ -> call.respond(HttpStatusCode.Unauthorized) }
        }
    }
}

/** Firebase uid of the authenticated user. */
val ApplicationCall.uid: String
    get() = requireNotNull(principal<JWTPrincipal>()?.subject) { "Route is not authenticated" }

/** The raw ID token, forwarded to the API to check the user's access to an exchange. */
val ApplicationCall.bearerToken: String
    get() = (request.parseAuthorizationHeader() as? HttpAuthHeader.Single)?.blob
        ?: error("Route is not authenticated")
