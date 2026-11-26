package com.bookintok.api.common

import org.springframework.security.oauth2.jwt.Jwt

/** Firebase uid of the authenticated user. */
val Jwt.uid: String get() = requireNotNull(subject) { "Token without subject" }

/** Email from the Firebase ID token, if the sign-in provider supplies one. */
val Jwt.email: String? get() = getClaimAsString("email")
