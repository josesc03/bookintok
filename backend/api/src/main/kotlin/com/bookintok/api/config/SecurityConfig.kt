package com.bookintok.api.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtClaimValidator
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtValidators
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.web.SecurityFilterChain

/**
 * Every request is authenticated with a Firebase ID token (Bearer JWT).
 * The token's `sub` claim is the Firebase uid and identifies the user.
 */
@Configuration
class SecurityConfig {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() } // stateless API without cookies
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests {
                it.requestMatchers("/actuator/health/**", "/docs", "/docs/**", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                it.anyRequest().authenticated()
            }
            .oauth2ResourceServer { it.jwt { } }
        return http.build()
    }

    /**
     * Validates Firebase ID tokens against Google's public keys: signature, expiry, issuer,
     * audience (our project) and a verified email address.
     */
    @Bean
    fun jwtDecoder(
        @Value("\${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") jwkSetUri: String,
        @Value("\${spring.security.oauth2.resourceserver.jwt.issuer-uri}") issuer: String,
        @Value("\${spring.security.oauth2.resourceserver.jwt.audiences}") audience: String,
    ): JwtDecoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build().apply {
        setJwtValidator(firebaseTokenValidator(issuer, audience))
    }

    companion object {
        fun firebaseTokenValidator(issuer: String, audience: String): OAuth2TokenValidator<Jwt> =
            DelegatingOAuth2TokenValidator(
                JwtValidators.createDefaultWithIssuer(issuer),
                JwtClaimValidator<List<String>>("aud") { audience in it.orEmpty() },
                emailVerified(),
            )

        /**
         * Accounts created with email and password must confirm their address before using the API.
         * Google accounts always arrive with `email_verified = true`.
         */
        fun emailVerified() = OAuth2TokenValidator<Jwt> { jwt ->
            if (jwt.getClaimAsBoolean("email_verified") == true) {
                OAuth2TokenValidatorResult.success()
            } else {
                OAuth2TokenValidatorResult.failure(OAuth2Error("invalid_token", "The email address is not verified", null))
            }
        }
    }
}
