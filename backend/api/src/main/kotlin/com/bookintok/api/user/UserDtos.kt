package com.bookintok.api.user

import com.bookintok.api.rating.RatingSummary
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import org.hibernate.validator.constraints.URL
import java.time.Instant

data class RegisterUserRequest(
    @field:Pattern(
        regexp = "^[a-zA-Z0-9_.]{3,30}$",
        message = "must be 3-30 characters: letters, digits, '_' or '.'",
    )
    val nickname: String,

    @field:Size(max = 100)
    val displayName: String? = null,
)

data class UpdateProfileRequest(
    @field:Size(max = 100)
    val displayName: String? = null,

    @field:URL
    @field:Size(max = 500)
    val imageUrl: String? = null,
)

data class UpdateLocationRequest(
    @field:DecimalMin("-90.0") @field:DecimalMax("90.0")
    val latitude: Double,

    @field:DecimalMin("-180.0") @field:DecimalMax("180.0")
    val longitude: Double,
)

/** The authenticated user's own profile, including private fields. */
data class MyProfileResponse(
    val uid: String,
    val nickname: String,
    val email: String,
    val displayName: String?,
    val imageUrl: String?,
    val latitude: Double?,
    val longitude: Double?,
    val rating: RatingSummary,
    val createdAt: Instant,
)

/** What other users can see: no email and no exact location. */
data class PublicProfileResponse(
    val uid: String,
    val nickname: String,
    val displayName: String?,
    val imageUrl: String?,
    val rating: RatingSummary,
    val createdAt: Instant,
)

fun User.toMyProfile(rating: RatingSummary) = MyProfileResponse(
    uid = uid,
    nickname = nickname,
    email = email,
    displayName = displayName,
    imageUrl = imageUrl,
    latitude = latitude,
    longitude = longitude,
    rating = rating,
    createdAt = createdAt,
)

fun User.toPublicProfile(rating: RatingSummary) = PublicProfileResponse(
    uid = uid,
    nickname = nickname,
    displayName = displayName,
    imageUrl = imageUrl,
    rating = rating,
    createdAt = createdAt,
)
