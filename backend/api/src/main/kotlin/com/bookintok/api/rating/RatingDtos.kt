package com.bookintok.api.rating

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Size
import java.time.Instant

/** Average score (null when there are no ratings yet) and number of ratings. */
data class RatingSummary(val average: Double?, val count: Long)

data class RateUserRequest(
    @field:Min(1) @field:Max(5)
    val score: Int,

    @field:Size(max = 1000)
    val comment: String? = null,
)

data class RatingResponse(
    val id: Long,
    val score: Int,
    val comment: String?,
    val raterUid: String,
    val raterNickname: String,
    val createdAt: Instant,
)

fun Rating.toResponse() = RatingResponse(
    id = requireNotNull(id),
    score = score.toInt(),
    comment = comment,
    raterUid = rater.uid,
    raterNickname = rater.nickname,
    createdAt = createdAt,
)
