package com.bookintok.app.core.auth

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

class NotSignedInException : IllegalStateException("No user is signed in")

class FirebaseNotConfiguredException : IllegalStateException("Firebase is not configured")

/** The signed-in Firebase user, as far as the app cares. */
data class AuthUser(val uid: String, val email: String?, val emailVerified: Boolean)

/** Supplies the Firebase ID token sent to the backend. */
fun interface AuthTokenProvider {
    suspend fun idToken(): String
}

interface AuthRepository {
    /** Emits the signed-in user when they sign in or out, or null when signed out. */
    val currentUser: Flow<AuthUser?>

    /** The current user, read now (it reflects [reloadUser] immediately). */
    fun currentUserNow(): AuthUser?

    fun currentUidNow(): String? = currentUserNow()?.uid

    suspend fun signIn(email: String, password: String)

    /** Creates the account and sends the verification email. */
    suspend fun signUp(email: String, password: String)

    /** Signs in with the ID token obtained from Google through Credential Manager. */
    suspend fun signInWithGoogle(googleIdToken: String)

    suspend fun sendVerificationEmail()

    /**
     * Fetches the user's latest state from Firebase (for example after they clicked the link in the
     * verification email) and refreshes the ID token, so the backend sees `email_verified = true`.
     */
    suspend fun reloadUser(): AuthUser?

    fun signOut()
}

@Singleton
class FirebaseAuthRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : AuthRepository, AuthTokenProvider {

    /** Null when Firebase is not configured (local.properties without firebase.* values). */
    private val auth: FirebaseAuth? by lazy {
        if (FirebaseApp.getApps(context).isEmpty()) null else FirebaseAuth.getInstance()
    }

    private fun requireAuth() = auth ?: throw FirebaseNotConfiguredException()

    private fun FirebaseUser.toAuthUser() = AuthUser(uid, email, isEmailVerified)

    override val currentUser: Flow<AuthUser?> = auth?.let { firebase ->
        callbackFlow {
            val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.toAuthUser()) }
            firebase.addAuthStateListener(listener)
            awaitClose { firebase.removeAuthStateListener(listener) }
        }
    } ?: flowOf(null)

    override fun currentUserNow(): AuthUser? = auth?.currentUser?.toAuthUser()

    override suspend fun signIn(email: String, password: String) {
        requireAuth().signInWithEmailAndPassword(email, password).await()
    }

    override suspend fun signUp(email: String, password: String) {
        val result = requireAuth().createUserWithEmailAndPassword(email, password).await()
        requireAuth().setLanguageCode("es")
        result.user?.sendEmailVerification()?.await()
    }

    override suspend fun signInWithGoogle(googleIdToken: String) {
        requireAuth().signInWithCredential(GoogleAuthProvider.getCredential(googleIdToken, null)).await()
    }

    override suspend fun sendVerificationEmail() {
        val user = auth?.currentUser ?: throw NotSignedInException()
        requireAuth().setLanguageCode("es")
        user.sendEmailVerification().await()
    }

    override suspend fun reloadUser(): AuthUser? {
        val user = auth?.currentUser ?: return null
        user.reload().await()
        if (user.isEmailVerified) user.getIdToken(true).await() // new token carries the verified claim
        return user.toAuthUser()
    }

    override fun signOut() {
        auth?.signOut()
    }

    override suspend fun idToken(): String {
        val user = auth?.currentUser ?: throw NotSignedInException()
        // Firebase caches the token and refreshes it shortly before it expires
        return user.getIdToken(false).await().token ?: throw NotSignedInException()
    }
}
