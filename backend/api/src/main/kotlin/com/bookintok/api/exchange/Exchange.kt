package com.bookintok.api.exchange

import com.bookintok.api.book.Book
import com.bookintok.api.common.ConflictException
import com.bookintok.api.common.ForbiddenException
import com.bookintok.api.user.User
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.Instant

enum class ExchangeStatus { PENDING, COMPLETED, CANCELLED }

/**
 * A request from [requester] to get [book] from its [owner].
 * It is completed once both users confirm that the swap took place; the chat lives in chat-service.
 */
@Entity
@Table(name = "exchanges")
class Exchange(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "book_id")
    val book: Book,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_uid")
    val owner: User,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requester_uid")
    val requester: User,

    @Column(nullable = false, updatable = false)
    val createdAt: Instant,

    var updatedAt: Instant = createdAt,

    @Enumerated(EnumType.STRING)
    var status: ExchangeStatus = ExchangeStatus.PENDING,

    var ownerConfirmed: Boolean = false,

    var requesterConfirmed: Boolean = false,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
) {
    /** Optimistic locking: two simultaneous confirmations cannot overwrite each other. */
    @Version
    var version: Long = 0

    fun isParticipant(uid: String) = owner.uid == uid || requester.uid == uid

    /** Records [uid]'s confirmation. Returns true when this confirmation completes the exchange. */
    fun confirm(uid: String, now: Instant): Boolean {
        requireParticipant(uid)
        requirePending()
        when (uid) {
            owner.uid -> ownerConfirmed = true
            requester.uid -> requesterConfirmed = true
        }
        updatedAt = now
        if (ownerConfirmed && requesterConfirmed) {
            status = ExchangeStatus.COMPLETED
            return true
        }
        return false
    }

    fun cancel(uid: String, now: Instant) {
        requireParticipant(uid)
        requirePending()
        status = ExchangeStatus.CANCELLED
        updatedAt = now
    }

    /** Used when the book went to another user. */
    fun discard(now: Instant) {
        if (status == ExchangeStatus.PENDING) {
            status = ExchangeStatus.CANCELLED
            updatedAt = now
        }
    }

    fun requireParticipant(uid: String) {
        if (!isParticipant(uid)) throw ForbiddenException("You are not part of this exchange")
    }

    private fun requirePending() {
        if (status != ExchangeStatus.PENDING) throw ConflictException("The exchange is already ${status.name.lowercase()}")
    }
}
