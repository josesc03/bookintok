package com.bookintok.api.book

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size
import org.hibernate.validator.constraints.URL
import java.time.Instant

data class BookRequest(
    @field:NotBlank @field:Size(max = 255)
    val title: String,

    @field:NotBlank @field:Size(max = 255)
    val author: String,

    @field:Size(max = 2000)
    val description: String? = null,

    @field:NotBlank @field:Size(max = 100)
    val mainCategory: String,

    @field:Size(max = 100)
    val secondaryCategory: String? = null,

    @field:NotBlank @field:Size(max = 50)
    val language: String,

    val coverType: CoverType,

    val condition: BookCondition,

    @field:URL @field:Size(max = 500)
    val imageUrl: String? = null,
)

/** Query parameters of `GET /api/v1/books`. All of them are optional. */
data class BookSearchFilter(
    val q: String? = null,
    val language: String? = null,
    val category: String? = null,
    val coverType: CoverType? = null,
    val condition: BookCondition? = null,
    @field:Positive
    val maxDistanceKm: Double? = null,
)

data class BookOwner(val uid: String, val nickname: String, val imageUrl: String?)

data class BookResponse(
    val id: Long,
    val title: String,
    val author: String,
    val description: String?,
    val mainCategory: String,
    val secondaryCategory: String?,
    val language: String,
    val coverType: CoverType,
    val condition: BookCondition,
    val status: BookStatus,
    val imageUrl: String?,
    val owner: BookOwner,
    val createdAt: Instant,
)

fun Book.toResponse() = BookResponse(
    id = requireNotNull(id),
    title = title,
    author = author,
    description = description,
    mainCategory = mainCategory,
    secondaryCategory = secondaryCategory,
    language = language,
    coverType = coverType,
    condition = condition,
    status = status,
    imageUrl = imageUrl,
    owner = BookOwner(owner.uid, owner.nickname, owner.imageUrl),
    createdAt = createdAt,
)
