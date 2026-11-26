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
import com.bookintok.app.core.auth.NotSignedInException
import com.bookintok.app.core.network.ApiException
import com.bookintok.app.core.network.BookCondition
import com.bookintok.app.core.network.CoverType
import com.bookintok.app.core.network.ExchangeStatus
import java.io.IOException

/** Turns an error into a short message for the user (the UI is in Spanish). */
fun Throwable.toUserMessage(): String = when (this) {
    is ApiException -> detail ?: when (status) {
        401 -> "Tu sesión ha caducado, vuelve a entrar"
        403 -> "No tienes permiso para hacer esto"
        404 -> "No se ha encontrado"
        else -> "Error del servidor ($status)"
    }
    is NotSignedInException -> "Tu sesión ha caducado, vuelve a entrar"
    is IOException -> "Sin conexión. Comprueba tu red e inténtalo de nuevo"
    else -> message ?: "Algo ha ido mal"
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
