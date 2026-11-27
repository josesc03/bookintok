package com.bookintok.api.user

import com.bookintok.api.common.uid
import com.bookintok.api.image.ImageStorage
import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.AuthErrorCode
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.io.File

/** Deletes the sign-in account itself (Firebase Authentication). */
fun interface IdentityAdmin {
    fun deleteAccount(uid: String)
}

@Configuration
class IdentityAdminConfig {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Uses the Firebase service account key when it is available. Without it, the app deletes the
     * Firebase account itself from the device after this API has deleted the data.
     */
    @Bean
    fun identityAdmin(@Value("\${bookintok.firebase.credentials-file:}") credentialsFile: String): IdentityAdmin {
        val file = credentialsFile.takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.isFile }
            ?: return IdentityAdmin { log.info("No Firebase admin credentials: the app deletes the sign-in account") }
        val options = FirebaseOptions.builder()
            .setCredentials(file.inputStream().use { GoogleCredentials.fromStream(it) })
            .build()
        val auth = FirebaseAuth.getInstance(FirebaseApp.initializeApp(options, "bookintok-api"))
        return IdentityAdmin { uid ->
            try {
                auth.deleteUser(uid)
            } catch (e: FirebaseAuthException) {
                if (e.authErrorCode != AuthErrorCode.USER_NOT_FOUND) throw e
            }
        }
    }
}

/**
 * Right to erasure: removes everything the API stores about a user. Their exchanges (and so the
 * chats with them), books, photos, ratings, blocks and reports about them; then the sign-in account.
 * Messages live in chat-service, which the app asks to delete them first.
 */
@Service
class AccountDeletionService(
    private val jdbc: JdbcClient,
    private val images: ImageStorage,
    private val identity: IdentityAdmin,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun delete(uid: String) {
        val imageKeys = jdbc.sql("SELECT image_key FROM books WHERE owner_uid = :uid AND image_key IS NOT NULL")
            .param("uid", uid).query(String::class.java).list().filterNotNull()
        // Exchanges first: they also reference books owned by other users
        jdbc.sql("DELETE FROM exchanges WHERE owner_uid = :uid OR requester_uid = :uid").param("uid", uid).update()
        jdbc.sql("DELETE FROM books WHERE owner_uid = :uid").param("uid", uid).update()
        // Ratings, blocks and reports go with the user through ON DELETE rules
        jdbc.sql("DELETE FROM users WHERE uid = :uid").param("uid", uid).update()
        imageKeys.forEach { key -> runCatching { images.delete(key) } }
        identity.deleteAccount(uid)
        log.info("Account {} deleted", uid)
    }
}

@RestController
class AccountController(private val deletion: AccountDeletionService) {

    @DeleteMapping("/api/v1/users/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@AuthenticationPrincipal jwt: Jwt) = deletion.delete(jwt.uid)
}
