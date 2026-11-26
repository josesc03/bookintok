package com.bookintok.api.rating

import com.bookintok.api.user.User
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "ratings")
class Rating(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rated_uid")
    val rated: User,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rater_uid")
    val rater: User,

    val score: Short,

    val comment: String?,

    @Column(nullable = false, updatable = false)
    val createdAt: Instant,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
)
