package com.bookintok.api.book

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

enum class CoverType { HARDCOVER, PAPERBACK }

enum class BookCondition { NEW, LIKE_NEW, USED, WORN }

enum class BookStatus { AVAILABLE, EXCHANGED }

@Entity
@Table(name = "books")
class Book(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_uid")
    val owner: User,

    var title: String,
    var author: String,
    var description: String?,
    var mainCategory: String,
    var secondaryCategory: String?,
    var language: String,

    @Enumerated(EnumType.STRING)
    var coverType: CoverType,

    @Enumerated(EnumType.STRING)
    @Column(name = "condition")
    var condition: BookCondition,

    var imageUrl: String?,

    @Column(nullable = false, updatable = false)
    val createdAt: Instant,

    @Enumerated(EnumType.STRING)
    var status: BookStatus = BookStatus.AVAILABLE,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
) {
    /** Optimistic locking: two exchanges cannot complete for the same book at the same time. */
    @Version
    var version: Long = 0

    fun isOwnedBy(uid: String) = owner.uid == uid

    fun markExchanged() {
        status = BookStatus.EXCHANGED
    }
}
