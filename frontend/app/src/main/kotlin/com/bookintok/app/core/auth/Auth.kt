package com.bookintok.app.core.auth

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

class NotSignedInException : IllegalStateException("No user is signed in")

/** Supplies the Firebase ID token sent to the backend. */
fun interface AuthTokenProvider {
    suspend fun idToken(): String
}

interface AuthRepository {
    /** Emits the signed-in user's uid, or null when signed out. */
    val currentUid: Flow<String?>

    fun currentUidNow(): String?

    suspend fun signIn(email: String, password: String)

    suspend fun signUp(email: String, password: String)

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

    private fun requireAuth() = auth ?: error("Firebase is not configured")

    override val currentUid: Flow<String?> = auth?.let { firebase ->
        callbackFlow {
            val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.uid) }
            firebase.addAuthStateListener(listener)
            awaitClose { firebase.removeAuthStateListener(listener) }
        }
    } ?: flowOf(null)

    override fun currentUidNow(): String? = auth?.currentUser?.uid

    override suspend fun signIn(email: String, password: String) {
        requireAuth().signInWithEmailAndPassword(email, password).await()
    }

    override suspend fun signUp(email: String, password: String) {
        requireAuth().createUserWithEmailAndPassword(email, password).await()
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
