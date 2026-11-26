package com.bookintok.app.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bookintok.app.data.ChatConnection
import com.bookintok.app.data.ChatMessage
import com.bookintok.app.ui.common.InfoBanner
import com.bookintok.app.ui.common.label
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val timeFormat = DateTimeFormatter.ofPattern("dd/MM HH:mm").withZone(ZoneId.systemDefault())

@Composable
fun ChatScreen(viewModel: ChatViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showRating by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // Keep the newest message in view as messages arrive
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.size)
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        val exchange = state.exchange
        if (exchange != null) {
            Surface(tonalElevation = 2.dp) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(exchange.bookTitle, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Con @${exchange.counterpartOf(state.myUid).nickname} · ${exchange.status.label}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (exchange.isOpen) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = viewModel::confirm, enabled = !state.iConfirmed) {
                                Text(if (state.iConfirmed) "Esperando a la otra persona" else "Confirmar intercambio")
                            }
                            OutlinedButton(onClick = viewModel::cancel) { Text("Cancelar") }
                        }
                    } else if (state.canRate) {
                        Button(onClick = { showRating = true }) { Text("Valorar a @${exchange.counterpartOf(state.myUid).nickname}") }
                    }
                }
            }
        }
        when (state.connection) {
            ChatConnection.Disconnected -> InfoBanner("Sin conexión: reconectando…")
            ChatConnection.Connecting -> InfoBanner("Conectando…")
            else -> Unit
        }
        state.notice?.let { InfoBanner(it) }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (state.canLoadOlder && state.messages.isNotEmpty()) {
                item { TextButton(onClick = viewModel::loadOlder) { Text("Ver mensajes anteriores") } }
            }
            items(state.messages, key = { it.id }) { message -> MessageBubble(message, mine = message.senderUid == state.myUid) }
        }

        if (exchange?.isOpen != false) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = state.draft,
                    onValueChange = viewModel::onDraft,
                    placeholder = { Text("Escribe un mensaje") },
                    modifier = Modifier.weight(1f),
                    maxLines = 4,
                )
                IconButton(onClick = viewModel::send, enabled = state.canSend) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Enviar")
                }
            }
        }
    }

    if (showRating) {
        RatingDialog(
            onDismiss = { showRating = false },
            onSubmit = { score, comment ->
                showRating = false
                viewModel.rate(score, comment)
            },
        )
    }
}

@Composable
private fun MessageBubble(message: ChatMessage, mine: Boolean) {
    Box(Modifier.fillMaxWidth(), contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart) {
        Surface(
            color = if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(message.content)
                Text(timeFormat.format(message.sentAt), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun RatingDialog(onDismiss: () -> Unit, onSubmit: (Int, String?) -> Unit) {
    var score by remember { mutableIntStateOf(5) }
    var comment by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("¿Qué tal ha ido?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row {
                    (1..5).forEach { value ->
                        IconButton(onClick = { score = value }) {
                            Icon(if (value <= score) Icons.Default.Star else Icons.Default.StarBorder, contentDescription = "$value estrellas")
                        }
                    }
                }
                OutlinedTextField(value = comment, onValueChange = { comment = it }, label = { Text("Comentario (opcional)") })
            }
        },
        confirmButton = { TextButton(onClick = { onSubmit(score, comment) }) { Text("Enviar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Ahora no") } },
    )
}
