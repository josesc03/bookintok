package com.bookintok.app.ui.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.bookintok.app.core.auth.AuthRepository
import com.bookintok.app.core.network.ExchangeStatus
import com.bookintok.app.data.ChatConnection
import com.bookintok.app.data.ChatMessage
import com.bookintok.app.data.ChatRepository
import com.bookintok.app.data.DefaultChatRepository
import com.bookintok.app.data.Exchange
import com.bookintok.app.data.ExchangeRepository
import com.bookintok.app.data.UserRepository
import com.bookintok.app.ui.common.toUserMessage
import com.bookintok.app.ui.navigation.ChatRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import com.bookintok.app.data.UnreadRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

val DELETE_WINDOW: java.time.Duration = java.time.Duration.ofMinutes(30)

data class ChatUiState(
    val myUid: String = "",
    val exchange: Exchange? = null,
    val messages: List<ChatMessage> = emptyList(),
    val connection: ChatConnection = ChatConnection.Connecting,
    val draft: String = "",
    val notice: String? = null,
    val canLoadOlder: Boolean = true,
    val rated: Boolean = false,
) {
    /** Only pending exchanges accept messages: completed and cancelled chats are read-only. */
    val canWrite get() = exchange?.status == ExchangeStatus.PENDING

    /** A cancelled exchange can be resumed when both agree: one asks, the other accepts. */
    val reopenAskedByMe get() = exchange?.status == ExchangeStatus.CANCELLED && exchange.reopenRequestedBy == myUid
    val reopenAskedByOther get() =
        exchange?.status == ExchangeStatus.CANCELLED && exchange.reopenRequestedBy != null && exchange.reopenRequestedBy != myUid
    val canSend get() = canWrite && connection == ChatConnection.Connected && draft.isNotBlank()
    val iConfirmed get() = exchange?.participant(myUid)?.confirmed == true
    val canRate get() = exchange?.status == ExchangeStatus.COMPLETED && !rated
    val counterpart get() = exchange?.counterpartOf(myUid)

    /** Users can take back their own messages during the first 30 minutes (the server checks it too). */
    fun canDelete(message: ChatMessage, now: java.time.Instant = java.time.Instant.now()) =
        message.senderUid == myUid && message.sentAt.isAfter(now.minus(DELETE_WINDOW)) && connection == ChatConnection.Connected

    /** Older messages can only exist once a full page has been loaded. */
    val showLoadOlder get() = canLoadOlder && messages.size >= DefaultChatRepository.PAGE_SIZE
}

/**
 * Messages come from Room; the WebSocket and the history request only fill Room.
 * That is what makes past conversations readable offline.
 */
@HiltViewModel
class ChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val chat: ChatRepository,
    private val exchanges: ExchangeRepository,
    private val users: UserRepository,
    private val unread: UnreadRepository,
    auth: AuthRepository,
) : ViewModel() {
    private val route = savedStateHandle.toRoute<ChatRoute>()
    private val exchangeId = route.exchangeId
    private val local = MutableStateFlow(
        ChatUiState(
            myUid = auth.currentUidNow().orEmpty(),
            notice = if (route.existing) "Ya tienes un chat sobre este libro: te hemos traído a él." else null,
        ),
    )

    /** The owner learns about a new chat through their inbox; announced once, on the first connection. */
    private var announceCreation = route.created
    private var connection: Job? = null

    val state: StateFlow<ChatUiState> = combine(local, exchanges.observe(exchangeId), chat.messages(exchangeId)) { local, exchange, messages ->
        local.copy(exchange = exchange, messages = messages)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), local.value)

    init {
        viewModelScope.launch {
            try {
                exchanges.refresh(exchangeId)
                chat.refresh(exchangeId)
            } catch (e: Exception) {
                // Offline: the cached messages are shown and the connection banner says so
                if (e is CancellationException) throw e
            }
        }
        connect()
        viewModelScope.launch {
            // Access is checked when the socket opens: after resuming an exchange, reconnect to write again
            exchanges.observe(exchangeId).map { it?.status }.filterNotNull().distinctUntilChanged().drop(1).collect { status ->
                if (status == ExchangeStatus.PENDING) connect()
            }
        }
    }

    private fun connect() {
        connection?.cancel()
        connection = viewModelScope.launch {
            chat.connect(exchangeId).collect { connection ->
                if (connection == ChatConnection.Connected && announceCreation) {
                    announceCreation = false
                    chat.notifyExchangeChanged(exchangeId)
                }
                if (connection == ChatConnection.ExchangeChanged) {
                    // The other participant confirmed or cancelled: reload it from the API
                    launch { runCatching { exchanges.refresh(exchangeId) } }
                    return@collect
                }
                local.update {
                    when (connection) {
                        is ChatConnection.Rejected -> it.copy(notice = connection.reason)
                        else -> it.copy(connection = connection)
                    }
                }
            }
        }
    }

    /** Called while the chat is on screen: what it shows counts as read. */
    fun markRead() {
        val newest = state.value.messages.lastOrNull() ?: return
        viewModelScope.launch { unread.markRead(exchangeId, newest.sentAt) }
    }

    fun requestReopen() = launchCatching {
        exchanges.requestReopen(exchangeId)
        chat.notifyExchangeChanged(exchangeId)
        local.update { it.copy(notice = "Le hemos pedido a @${state.value.counterpart?.nickname.orEmpty()} retomar el intercambio") }
    }

    fun acceptReopen() = launchCatching {
        exchanges.acceptReopen(exchangeId)
        chat.notifyExchangeChanged(exchangeId)
        local.update { it.copy(notice = "Intercambio retomado: ya podéis volver a escribir") }
    }

    /** Rejects the other participant's request, or withdraws one's own. */
    fun clearReopen() = launchCatching {
        exchanges.clearReopenRequest(exchangeId)
        chat.notifyExchangeChanged(exchangeId)
    }

    fun onDraft(value: String) = local.update { it.copy(draft = value) }

    fun send() {
        val text = local.value.draft.trim()
        if (text.isEmpty()) return
        launchCatching {
            chat.send(exchangeId, text)
            local.update { it.copy(draft = "") }
        }
    }

    fun loadOlder() = launchCatching {
        val more = chat.loadOlder(exchangeId)
        local.update { it.copy(canLoadOlder = more) }
    }

    fun confirm() = launchCatching {
        exchanges.confirm(exchangeId)
        chat.notifyExchangeChanged(exchangeId)
    }

    fun cancel() = launchCatching {
        exchanges.cancel(exchangeId)
        chat.notifyExchangeChanged(exchangeId)
    }

    fun deleteMessage(messageId: String) = launchCatching { chat.deleteMessage(exchangeId, messageId) }

    /** Archives the chat for this user (the other participant still sees it). */
    fun toggleArchived() = launchCatching {
        val archived = state.value.exchange?.archived ?: return@launchCatching
        exchanges.setArchived(exchangeId, !archived)
        local.update { it.copy(notice = if (archived) "Chat restaurado" else "Chat archivado") }
    }

    fun dismissNotice() = local.update { it.copy(notice = null) }

    fun rate(score: Int, comment: String?) = launchCatching {
        val exchange = state.value.exchange ?: return@launchCatching
        users.rate(exchange.counterpartOf(local.value.myUid).uid, score, comment)
        local.update { it.copy(rated = true, notice = "¡Gracias por tu valoración!") }
    }

    private fun launchCatching(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                local.update { it.copy(notice = e.toUserMessage()) }
            }
        }
    }
}
