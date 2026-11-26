package com.bookintok.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bookintok.app.core.auth.FirebaseNotConfiguredException
import com.bookintok.app.core.auth.NotSignedInException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.bookintok.app.core.network.ApiException
import com.bookintok.app.core.network.BookCondition
import com.bookintok.app.core.network.CoverType
import com.bookintok.app.core.network.ExchangeStatus
import java.io.IOException

/**
 * Turns an error into a short message for the user. The UI is in Spanish, while the server and
 * Firebase explain errors in English, so messages are chosen by error type rather than shown as is.
 */
fun Throwable.toUserMessage(): String = when (this) {
    is ApiException -> when (status) {
        400 -> "Revisa los datos: ${detail ?: "hay campos no válidos"}"
        401 -> "Tu sesión ha caducado, vuelve a entrar"
        403 -> "No tienes permiso para hacer esto"
        404 -> "No se ha encontrado"
        409 -> "No se ha podido completar: ya existe o ha cambiado mientras tanto"
        422 -> "No se puede hacer en este momento"
        else -> "Error del servidor ($status). Inténtalo más tarde"
    }
    is NotSignedInException -> "Tu sesión ha caducado, vuelve a entrar"
    is FirebaseNotConfiguredException -> "La app no tiene Firebase configurado (revisa local.properties)"
    is FirebaseAuthInvalidUserException, is FirebaseAuthInvalidCredentialsException -> "Email o contraseña incorrectos"
    is FirebaseAuthUserCollisionException -> "Ya existe una cuenta con ese email"
    is FirebaseAuthWeakPasswordException -> "La contraseña es demasiado débil"
    is FirebaseNetworkException, is IOException -> "Sin conexión. Comprueba tu red e inténtalo de nuevo"
    else -> "Algo ha ido mal. Inténtalo de nuevo"
}

val BookCondition.label
    get() = when (this) {
        BookCondition.NEW -> "Nuevo"
        BookCondition.LIKE_NEW -> "Como nuevo"
        BookCondition.USED -> "Usado"
        BookCondition.WORN -> "Desgastado"
    }

val CoverType.label
    get() = when (this) {
        CoverType.HARDCOVER -> "Tapa dura"
        CoverType.PAPERBACK -> "Tapa blanda"
    }

val ExchangeStatus.label
    get() = when (this) {
        ExchangeStatus.PENDING -> "En curso"
        ExchangeStatus.COMPLETED -> "Completado"
        ExchangeStatus.CANCELLED -> "Cancelado"
    }

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
fun MessageBox(text: String, modifier: Modifier = Modifier, actionLabel: String? = null, onAction: () -> Unit = {}) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        if (actionLabel != null) TextButton(onClick = onAction) { Text(actionLabel) }
    }
}

/** Thin banner shown on top of cached content, e.g. when the app is offline. */
@Composable
fun InfoBanner(text: String, modifier: Modifier = Modifier) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = modifier.fillMaxWidth()) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}
