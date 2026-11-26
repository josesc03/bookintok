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
}

const val MAX_MESSAGE_LENGTH = 2000

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

/** What a client sends through the WebSocket. */
@Serializable
data class OutgoingMessageRequest(val content: String)

/** What the server pushes through the WebSocket; the `type` field tells them apart. */
@Serializable
sealed interface ServerEvent {
    @Serializable
    @SerialName("message")
    data class NewMessage(val message: MessageDto) : ServerEvent

    @Serializable
    @SerialName("error")
    data class Error(val reason: String) : ServerEvent
}
