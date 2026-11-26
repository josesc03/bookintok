package com.bookintok.app.core.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

/** The user closed the Google account picker: not an error worth showing. */
class GoogleSignInCancelledException : Exception("Google sign-in cancelled")

class NoGoogleAccountException : Exception("No Google account on this device")

/**
 * Shows the system "Sign in with Google" sheet (Credential Manager) and returns the Google ID token,
 * which Firebase then exchanges for a Firebase session.
 *
 * [activityContext] must be an Activity context, because the sheet is shown on top of it.
 * [webClientId] is the OAuth "Web client" of the Firebase project: Google issues the token for it.
 */
suspend fun requestGoogleIdToken(activityContext: Context, webClientId: String): String {
    if (webClientId.isBlank()) throw FirebaseNotConfiguredException()
    val request = GetCredentialRequest.Builder()
        .addCredentialOption(GetSignInWithGoogleOption.Builder(webClientId).build())
        .build()
    val result = try {
        CredentialManager.create(activityContext).getCredential(activityContext, request)
    } catch (_: GetCredentialCancellationException) {
        throw GoogleSignInCancelledException()
    } catch (_: NoCredentialException) {
        throw NoGoogleAccountException()
    }
    val credential = result.credential
    if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
        return GoogleIdTokenCredential.createFrom(credential.data).idToken
    }
    error("Unexpected credential type ${credential.type}")
}
