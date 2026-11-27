package com.bookintok.app.ui.chat

import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.PersonOff
import com.bookintok.app.ui.safety.BlockDialog
import com.bookintok.app.ui.safety.ReportSheet
import com.bookintok.app.ui.safety.SafetyViewModel
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bookintok.app.core.network.ExchangeStatus
import com.bookintok.app.data.ChatConnection
import com.bookintok.app.data.ChatMessage
import com.bookintok.app.push.ChatNotifications
import com.bookintok.app.ui.common.InfoBanner
import com.bookintok.app.ui.components.Avatar
import com.bookintok.app.ui.components.GhostButton
import com.bookintok.app.ui.components.OutlineButton
import com.bookintok.app.ui.components.PrimaryButton
import com.bookintok.app.ui.components.SecondaryButton
import com.bookintok.app.ui.components.bookintokFieldColors
import com.bookintok.app.ui.navigation.AppNavigator
import com.bookintok.app.ui.theme.BookintokColors
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val timeFormat = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())
private val dayFormat = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale.forLanguageTag("es"))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(nav: AppNavigator, viewModel: ChatViewModel = hiltViewModel(), safety: SafetyViewModel = hiltViewModel()) {
    var reporting by remember { mutableStateOf(false) }
    var blocking by remember { mutableStateOf(false) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showRating by remember { mutableStateOf(false) }
    var confirmCancel by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<ChatMessage?>(null) }
    val listState = rememberLazyListState()
    val exchange = state.exchange
    val counterpart = state.counterpart

    // What is on screen counts as read (this runs only while the chat is displayed)
    val context = LocalContext.current
    LaunchedEffect(state.messages.lastOrNull()?.id) {
        viewModel.markRead()
        state.exchange?.let { ChatNotifications.cancel(context, it.id) }
    }

    // Keep the newest message in view as messages arrive
    LaunchedEffect(state.messages.size) {
        val total = listState.layoutInfo.totalItemsCount
        if (state.messages.isNotEmpty() && total > 0) listState.animateScrollToItem(total - 1)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                TopAppBar(
                    navigationIcon = { IconButton(onClick = nav.back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás") } },
                    title = {
                        Row(
                            Modifier.clickable(enabled = counterpart != null) { counterpart?.let { nav.openUser(it.uid) } },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Avatar(counterpart?.nickname ?: "?", size = 38)
                            Column {
                                Text("@${counterpart?.nickname.orEmpty()}", style = MaterialTheme.typography.titleMedium, maxLines = 1)
                                Text(
                                    exchange?.bookTitle.orEmpty(),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    },
                    actions = {
                        ChatMenu(
                            state = state,
                            onProfile = { counterpart?.let { nav.openUser(it.uid) } },
                            onBook = { exchange?.let { nav.openBook(it.bookId) } },
                            onArchive = viewModel::toggleArchived,
                            onCancel = { confirmCancel = true },
                            onReport = { reporting = true },
                            onBlock = { blocking = true },
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                StatusStrip(
                    state,
                    onConfirm = viewModel::confirm,
                    onRate = { showRating = true },
                    onRequestReopen = viewModel::requestReopen,
                    onAcceptReopen = viewModel::acceptReopen,
                    onClearReopen = viewModel::clearReopen,
                )
                AnimatedVisibility(state.connection == ChatConnection.Disconnected, enter = expandVertically(), exit = shrinkVertically()) {
                    InfoBanner("Sin conexión: mostrando los mensajes guardados. Reconectando…")
                }
                AnimatedVisibility(state.notice != null, enter = expandVertically(), exit = shrinkVertically()) {
                    Surface(onClick = viewModel::dismissNotice, color = MaterialTheme.colorScheme.secondaryContainer) {
                        Text(
                            state.notice.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        },
        bottomBar = { InputBar(state, onDraft = viewModel::onDraft, onSend = viewModel::send) },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (state.showLoadOlder) {
                item(key = "older") {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { GhostButton("Ver mensajes anteriores", viewModel::loadOlder) }
                }
            }
            if (state.messages.isEmpty()) {
                item(key = "hello") {
                    Text(
                        when {
                            exchange?.status == ExchangeStatus.CANCELLED -> "El intercambio se canceló. Si queréis seguir, pedid retomarlo."
                            state.canWrite -> "Escribe a @${counterpart?.nickname.orEmpty()} para quedar y hacer el intercambio."
                            else -> "No hay mensajes."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                    )
                }
            }
            var previousDay: LocalDate? = null
            state.messages.forEach { message ->
                val day = message.sentAt.atZone(ZoneId.systemDefault()).toLocalDate()
                if (day != previousDay) {
                    previousDay = day
                    item(key = "day-$day") { DayHeader(day, Modifier.animateItem()) }
                }
                item(key = message.id) {
                    MessageBubble(
                        message,
                        mine = message.senderUid == state.myUid,
                        onLongPress = { selected = message },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }

    selected?.let { message ->
        MessageActions(
            message = message,
            canDelete = state.canDelete(message),
            onDelete = {
                viewModel.deleteMessage(message.id)
                selected = null
            },
            onDismiss = { selected = null },
        )
    }
    if (showRating) {
        RatingDialog(
            nickname = counterpart?.nickname.orEmpty(),
            onDismiss = { showRating = false },
            onSubmit = { score, comment ->
                showRating = false
                viewModel.rate(score, comment)
            },
        )
    }
    val safetyState by safety.state.collectAsStateWithLifecycle()
    LaunchedEffect(safetyState.message) {
        safetyState.message?.let {
            viewModel.showNotice(it)
            safety.dismissMessage()
        }
    }
    LaunchedEffect(safetyState.blocked) { if (safetyState.blocked) viewModel.onBlocked() }
    if (reporting && counterpart != null) {
        ReportSheet(
            nickname = counterpart.nickname,
            working = safetyState.working,
            onDismiss = { reporting = false },
            onSend = { reason, comment ->
                safety.report(counterpart.uid, reason, comment, exchange?.id)
                reporting = false
            },
        )
    }
    if (blocking && counterpart != null) {
        BlockDialog(
            nickname = counterpart.nickname,
            onDismiss = { blocking = false },
            onConfirm = {
                safety.block(counterpart.uid, counterpart.nickname)
                blocking = false
            },
        )
    }
    if (confirmCancel) {
        AlertDialog(
            onDismissRequest = { confirmCancel = false },
            title = { Text("¿Cancelar el intercambio?") },
            text = { Text("El libro seguirá disponible para otras personas y el chat quedará en solo lectura. Más adelante podréis retomarlo si los dos estáis de acuerdo.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmCancel = false
                    viewModel.cancel()
                }) { Text("Cancelar intercambio", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmCancel = false }) { Text("Volver", color = MaterialTheme.colorScheme.onSurface) } },
        )
    }
}

@Composable
private fun ChatMenu(
    state: ChatUiState,
    onProfile: () -> Unit,
    onBook: () -> Unit,
    onArchive: () -> Unit,
    onCancel: () -> Unit,
    onReport: () -> Unit,
    onBlock: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Opciones del chat") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("Ver perfil de @${state.counterpart?.nickname.orEmpty()}") },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                onClick = {
                    open = false
                    onProfile()
                },
            )
            DropdownMenuItem(
                text = { Text("Ver el libro") },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null) },
                onClick = {
                    open = false
                    onBook()
                },
            )
            val archived = state.exchange?.archived == true
            DropdownMenuItem(
                text = { Text(if (archived) "Desarchivar chat" else "Archivar chat") },
                leadingIcon = { Icon(if (archived) Icons.Default.Unarchive else Icons.Default.Archive, contentDescription = null) },
                onClick = {
                    open = false
                    onArchive()
                },
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Denunciar") },
                leadingIcon = { Icon(Icons.Default.Flag, contentDescription = null) },
                onClick = {
                    open = false
                    onReport()
                },
            )
            DropdownMenuItem(
                text = { Text("Bloquear a @${state.counterpart?.nickname.orEmpty()}", color = MaterialTheme.colorScheme.error) },
                leadingIcon = { Icon(Icons.Default.PersonOff, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                onClick = {
                    open = false
                    onBlock()
                },
            )
            if (state.exchange?.isOpen == true) {
                DropdownMenuItem(
                    text = { Text("Cancelar intercambio", color = MaterialTheme.colorScheme.error) },
                    leadingIcon = { Icon(Icons.Default.Block, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                    onClick = {
                        open = false
                        onCancel()
                    },
                )
            }
        }
    }
}

/** What the exchange needs next, right under the top bar; it animates when the status changes. */
@Composable
private fun StatusStrip(
    state: ChatUiState,
    onConfirm: () -> Unit,
    onRate: () -> Unit,
    onRequestReopen: () -> Unit,
    onAcceptReopen: () -> Unit,
    onClearReopen: () -> Unit,
) {
    val exchange = state.exchange ?: return
    val nickname = state.counterpart?.nickname.orEmpty()
    AnimatedContent(
        targetState = StripState(exchange.status, state.iConfirmed, state.canRate, state.reopenAskedByMe, state.reopenAskedByOther),
        transitionSpec = { (fadeIn() + expandVertically()) togetherWith (fadeOut() + shrinkVertically()) },
        label = "status",
    ) { (status, iConfirmed, canRate, askedByMe, askedByOther) ->
        if (askedByOther) {
            // The other participant wants to resume: this needs an answer, so it gets two buttons
            Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("@$nickname quiere retomar el intercambio.", style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PrimaryButton("Aceptar", onAcceptReopen)
                        OutlineButton("Rechazar", onClearReopen)
                    }
                }
            }
            return@AnimatedContent
        }
        Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val text = when (status) {
                    ExchangeStatus.PENDING ->
                        if (iConfirmed) "Has confirmado. Falta que @$nickname confirme." else "¿Ya os habéis dado el libro? Confirmadlo los dos."
                    ExchangeStatus.COMPLETED -> "Intercambio completado"
                    ExchangeStatus.CANCELLED ->
                        if (askedByMe) "Has pedido retomar el intercambio. Esperando a @$nickname." else "Intercambio cancelado."
                }
                Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                when {
                    status == ExchangeStatus.PENDING && !iConfirmed -> PrimaryButton("Confirmar", onConfirm)
                    status == ExchangeStatus.COMPLETED && canRate -> SecondaryButton("Valorar", onRate, icon = Icons.Default.Star)
                    status == ExchangeStatus.CANCELLED && askedByMe -> GhostButton("Retirar", onClearReopen)
                    status == ExchangeStatus.CANCELLED -> SecondaryButton("Pedir retomar", onRequestReopen)
                }
            }
        }
    }
}

@Composable
private fun DayHeader(day: LocalDate, modifier: Modifier = Modifier) {
    val label = when (day) {
        LocalDate.now() -> "Hoy"
        LocalDate.now().minusDays(1) -> "Ayer"
        else -> dayFormat.format(day).replaceFirstChar { it.uppercase() }
    }
    Box(modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(50)) {
            Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(message: ChatMessage, mine: Boolean, onLongPress: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth(), contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart) {
        Surface(
            color = if (mine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerLowest,
            contentColor = if (mine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = if (mine) 18.dp else 4.dp, bottomEnd = if (mine) 4.dp else 18.dp),
            shadowElevation = if (mine) 0.dp else 1.dp,
            modifier = Modifier.widthIn(max = 300.dp).combinedClickable(onClick = {}, onLongClick = onLongPress),
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                Text(message.content, style = MaterialTheme.typography.bodyMedium)
                Text(
                    timeFormat.format(message.sentAt),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.align(Alignment.End).alpha(0.65f),
                )
            }
        }
    }
}

/** Long-press menu of a message: copy it, and delete it if it is yours and recent. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MessageActions(message: ChatMessage, canDelete: Boolean, onDelete: () -> Unit, onDismiss: () -> Unit) {
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.background) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 8.dp)) {
            Text(
                message.content,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            val colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background)
            ListItem(
                headlineContent = { Text("Copiar texto") },
                leadingContent = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                colors = colors,
                modifier = Modifier.clickable {
                    clipboard.setText(AnnotatedString(message.content))
                    onDismiss()
                },
            )
            if (canDelete) {
                ListItem(
                    headlineContent = { Text("Eliminar mensaje", color = MaterialTheme.colorScheme.error) },
                    supportingContent = { Text("Se borrará para los dos. Solo puedes hacerlo durante 30 minutos.") },
                    leadingContent = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                    colors = colors,
                    modifier = Modifier.clickable(onClick = onDelete),
                )
            }
        }
    }
}

private data class StripState(
    val status: ExchangeStatus,
    val iConfirmed: Boolean,
    val canRate: Boolean,
    val reopenAskedByMe: Boolean,
    val reopenAskedByOther: Boolean,
)

@Composable
private fun InputBar(state: ChatUiState, onDraft: (String) -> Unit, onSend: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(Modifier.navigationBarsPadding().imePadding()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            if (state.canWrite) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = state.draft,
                        onValueChange = onDraft,
                        placeholder = { Text("Mensaje") },
                        modifier = Modifier.weight(1f),
                        maxLines = 4,
                        shape = RoundedCornerShape(24.dp),
                        colors = bookintokFieldColors(),
                    )
                    FilledIconButton(
                        onClick = onSend,
                        enabled = state.canSend,
                        modifier = Modifier.size(52.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = BookintokColors.Ink,
                        ),
                    ) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Enviar") }
                }
            } else {
                Text(
                    if (state.exchange?.status == ExchangeStatus.CANCELLED) {
                        "Intercambio cancelado: el chat es de solo lectura hasta que lo retoméis."
                    } else {
                        "El intercambio se completó: este chat ya es de solo lectura."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                )
            }
        }
    }
}

@Composable
private fun RatingDialog(nickname: String, onDismiss: () -> Unit, onSubmit: (Int, String?) -> Unit) {
    var score by remember { mutableIntStateOf(5) }
    var comment by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("¿Qué tal con @$nickname?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row {
                    (1..5).forEach { value ->
                        IconButton(onClick = { score = value }) {
                            Icon(
                                if (value <= score) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = "$value estrellas",
                                tint = Color(0xFFE0A100),
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    placeholder = { Text("Comentario (opcional)") },
                    colors = bookintokFieldColors(),
                    shape = RoundedCornerShape(12.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSubmit(score, comment) }) { Text("Enviar", color = MaterialTheme.colorScheme.onSurface) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Ahora no", color = MaterialTheme.colorScheme.onSurfaceVariant) } },
    )
}
