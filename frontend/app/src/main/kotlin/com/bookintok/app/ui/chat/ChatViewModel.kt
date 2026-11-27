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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

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
    val canSend get() = exchange?.isOpen == true && connection == ChatConnection.Connected && draft.isNotBlank()
    val iConfirmed get() = exchange?.participant(myUid)?.confirmed == true
    val canRate get() = exchange?.status == ExchangeStatus.COMPLETED && !rated

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
    auth: AuthRepository,
) : ViewModel() {
    private val exchangeId = savedStateHandle.toRoute<ChatRoute>().exchangeId
    private val local = MutableStateFlow(ChatUiState(myUid = auth.currentUidNow().orEmpty()))

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
        viewModelScope.launch {
            chat.connect(exchangeId).collect { connection ->
                if (connection == ChatConnection.ExchangeChanged) {
                    // The other participant confirmed or cancelled: reload it from the API
                    launch { runCatching { exchanges.refresh(exchangeId) } }
                    return@collect
                }
                local.update {
                    when (connection) {
                        is ChatConnection.Rejected -> it.copy(notice = connection.reason)
                        ChatConnection.Connected -> it.copy(connection = connection, notice = null)
                        else -> it.copy(connection = connection)
                    }
                }
            }
        }
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
