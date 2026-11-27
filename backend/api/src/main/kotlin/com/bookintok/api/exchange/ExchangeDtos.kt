package com.bookintok.api.exchange

import java.time.Instant

data class ExchangeParticipant(val uid: String, val nickname: String, val imageUrl: String?, val confirmed: Boolean)

data class ExchangeBook(val id: Long, val title: String, val imageUrl: String?)

data class ExchangeResponse(
    val id: Long,
    val status: ExchangeStatus,
    val book: ExchangeBook,
    val owner: ExchangeParticipant,
    val requester: ExchangeParticipant,
    val createdAt: Instant,
    val updatedAt: Instant,
    /** Whether the user asking archived this chat (archiving is personal). */
    val archived: Boolean,
    /** Uid of the participant asking to resume this cancelled exchange, if any. */
    val reopenRequestedBy: String?,
)

/** Result of asking for a book: the chat that already existed for it, or a new one. */
data class ExchangeRequestResult(val exchange: ExchangeResponse, val created: Boolean)

fun Exchange.toResponse(viewerUid: String) = ExchangeResponse(
    id = requireNotNull(id),
    status = status,
    book = ExchangeBook(requireNotNull(book.id), book.title, book.imageUrl()),
    owner = ExchangeParticipant(owner.uid, owner.nickname, owner.imageUrl, ownerConfirmed),
    requester = ExchangeParticipant(requester.uid, requester.nickname, requester.imageUrl, requesterConfirmed),
    createdAt = createdAt,
    updatedAt = updatedAt,
    archived = isArchivedFor(viewerUid),
    reopenRequestedBy = reopenRequestedBy,
)
