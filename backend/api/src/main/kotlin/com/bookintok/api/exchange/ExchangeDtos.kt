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
)

fun Exchange.toResponse() = ExchangeResponse(
    id = requireNotNull(id),
    status = status,
    book = ExchangeBook(requireNotNull(book.id), book.title, book.imageUrl()),
    owner = ExchangeParticipant(owner.uid, owner.nickname, owner.imageUrl, ownerConfirmed),
    requester = ExchangeParticipant(requester.uid, requester.nickname, requester.imageUrl, requesterConfirmed),
    createdAt = createdAt,
    updatedAt = updatedAt,
)
