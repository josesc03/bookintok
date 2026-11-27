package com.bookintok.chat.message

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

data class ChatMessage(
    val id: String,
    val exchangeId: Long,
    val senderUid: String,
    val content: String,
    val sentAt: Instant,
)

interface MessageRepository {
    suspend fun save(exchangeId: Long, senderUid: String, content: String, sentAt: Instant): ChatMessage

    /** Messages of an exchange, newest first, optionally only those sent before [before]. */
    suspend fun history(exchangeId: Long, before: Instant?, limit: Int): List<ChatMessage>

    suspend fun find(id: String): ChatMessage?

    /** Messages of any of [exchangeIds] sent after [after], oldest first: what an inbox missed. */
    suspend fun since(exchangeIds: Collection<Long>, after: Instant, limit: Int): List<ChatMessage>

    suspend fun delete(id: String)
}

const val MAX_MESSAGE_LENGTH = 2000

/** Senders can take a message back only for a while, like most messaging apps. */
val DELETE_WINDOW: java.time.Duration = java.time.Duration.ofMinutes(30)

// JSON sent and received by clients

@Serializable
data class MessageDto(
    val id: String,
    val exchangeId: Long,
    val senderUid: String,
    val content: String,
    /** ISO-8601 instant */
    val sentAt: String,
)

fun ChatMessage.toDto() = MessageDto(id, exchangeId, senderUid, content, sentAt.toString())

/**
 * What a client sends through the WebSocket:
 * - a chat message: `{"content": "..."}`
 * - `{"type": "exchange_changed"}` after confirming or cancelling the exchange through the API
 * - `{"type": "delete_message", "messageId": "..."}` to take back one of their own recent messages
 */
@Serializable
data class OutgoingMessageRequest(
    val content: String? = null,
    val type: String? = null,
    val messageId: String? = null,
    /** With exchange_changed: what happened (created, confirmed, cancelled, reopen_requested...), for notifications. */
    val event: String? = null,
) {
    companion object {
        const val EXCHANGE_CHANGED = "exchange_changed"
        const val DELETE_MESSAGE = "delete_message"
    }
}

/** What the server pushes through the WebSocket; the `type` field tells them apart. */
@Serializable
sealed interface ServerEvent {
    @Serializable
    @SerialName("message")
    data class NewMessage(val message: MessageDto) : ServerEvent

    @Serializable
    @SerialName("error")
    data class Error(val reason: String) : ServerEvent

    /**
     * The exchange was confirmed or cancelled. It carries no data on purpose: clients reload the
     * exchange from the API, which is the only source of truth, so a client cannot fake a status.
     */
    @Serializable
    @SerialName("exchange_changed")
    data class ExchangeChanged(val exchangeId: Long) : ServerEvent

    @Serializable
    @SerialName("message_deleted")
    data class MessageDeleted(val messageId: String, val exchangeId: Long) : ServerEvent
}
