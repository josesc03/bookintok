package com.bookintok.api.book

import com.bookintok.api.common.GeoDistance
import com.bookintok.api.common.GeoPoint
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size
import java.time.Instant

/** The photo is not part of the request: it is uploaded separately to `PUT /api/v1/books/{id}/image`. */
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

/** The owner as other users see them: the municipality, never coordinates. */
data class BookOwner(val uid: String, val nickname: String, val imageUrl: String?, val locationLabel: String?)

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
    /** Relative to the API base URL. */
    val imageUrl: String?,
    val owner: BookOwner,
    /** Approximate distance from the viewer, in whole kilometres (at least 1); null if unknown. */
    val distanceKm: Int?,
    val createdAt: Instant,
)

/** [viewer] is the location of the user asking, used to tell them how far the book is. */
fun Book.toResponse(viewer: GeoPoint? = null) = BookResponse(
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
    imageUrl = imageUrl(),
    owner = BookOwner(owner.uid, owner.nickname, owner.imageUrl, owner.locationLabel),
    distanceKm = viewer?.let { from -> owner.location?.let { GeoDistance.roundedKm(from, it) } },
    createdAt = createdAt,
)
