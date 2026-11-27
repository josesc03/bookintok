package com.bookintok.app.data

import com.bookintok.app.core.database.ExchangeEntity
import com.bookintok.app.core.database.MessageEntity
import com.bookintok.app.core.network.ExchangeDto
import com.bookintok.app.core.network.ExchangeStatus
import com.bookintok.app.core.network.MessageDto
import java.time.Instant

data class Participant(val uid: String, val nickname: String, val confirmed: Boolean)

data class Exchange(
    val id: Long,
    val status: ExchangeStatus,
    val bookId: Long,
    val bookTitle: String,
    val bookImageUrl: String?,
    val owner: Participant,
    val requester: Participant,
    val updatedAt: Instant,
    val archived: Boolean = false,
    /** Uid of the participant asking to resume this cancelled exchange. */
    val reopenRequestedBy: String? = null,
) {
    val isOpen get() = status == ExchangeStatus.PENDING

    fun counterpartOf(uid: String) = if (owner.uid == uid) requester else owner

    fun participant(uid: String) = if (owner.uid == uid) owner else requester
}

data class ChatMessage(
    val id: String,
    val exchangeId: Long,
    val senderUid: String,
    val content: String,
    val sentAt: Instant,
)

fun ExchangeDto.toEntity() = ExchangeEntity(
    id = id,
    status = status,
    bookId = book.id,
    bookTitle = book.title,
    bookImageUrl = book.imageUrl,
    ownerUid = owner.uid,
    ownerNickname = owner.nickname,
    ownerConfirmed = owner.confirmed,
    requesterUid = requester.uid,
    requesterNickname = requester.nickname,
    requesterConfirmed = requester.confirmed,
    updatedAt = Instant.parse(updatedAt).toEpochMilli(),
    archived = archived,
    reopenRequestedBy = reopenRequestedBy,
)

fun ExchangeEntity.toModel() = Exchange(
    id = id,
    status = status,
    bookId = bookId,
    bookTitle = bookTitle,
    bookImageUrl = bookImageUrl,
    owner = Participant(ownerUid, ownerNickname, ownerConfirmed),
    requester = Participant(requesterUid, requesterNickname, requesterConfirmed),
    updatedAt = Instant.ofEpochMilli(updatedAt),
    archived = archived,
    reopenRequestedBy = reopenRequestedBy,
)

fun MessageDto.toEntity() = MessageEntity(id, exchangeId, senderUid, content, Instant.parse(sentAt).toEpochMilli())

fun MessageEntity.toModel() = ChatMessage(id, exchangeId, senderUid, content, Instant.ofEpochMilli(sentAt))
