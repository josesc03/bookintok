package com.bookintok.app.core.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Mirrors of the JSON exchanged with the backend. Dates travel as ISO-8601 strings.

@Serializable
data class RatingSummaryDto(val average: Double? = null, val count: Long = 0)

@Serializable
data class MyProfileDto(
    val uid: String,
    val nickname: String,
    val email: String,
    val displayName: String? = null,
    val imageUrl: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationLabel: String? = null,
    val rating: RatingSummaryDto = RatingSummaryDto(),
)

@Serializable
data class PublicProfileDto(
    val uid: String,
    val nickname: String,
    val displayName: String? = null,
    val imageUrl: String? = null,
    val rating: RatingSummaryDto = RatingSummaryDto(),
)

@Serializable
data class RegisterUserRequest(val nickname: String, val displayName: String? = null)

@Serializable
data class UpdateLocationRequest(val latitude: Double, val longitude: Double, val label: String? = null)

@Serializable
enum class CoverType { HARDCOVER, PAPERBACK }

@Serializable
enum class BookCondition { NEW, LIKE_NEW, USED, WORN }

@Serializable
enum class BookStatus { AVAILABLE, EXCHANGED }

@Serializable
data class BookOwnerDto(val uid: String, val nickname: String, val imageUrl: String? = null, val locationLabel: String? = null)

@Serializable
data class BookDto(
    val id: Long,
    val title: String,
    val author: String,
    val description: String? = null,
    val mainCategory: String,
    val secondaryCategory: String? = null,
    val language: String,
    val coverType: CoverType,
    val condition: BookCondition,
    val status: BookStatus,
    val imageUrl: String? = null,
    val owner: BookOwnerDto,
    /** Approximate distance from the signed-in user, in whole kilometres. */
    val distanceKm: Int? = null,
    val createdAt: String,
)

@Serializable
data class BookRequest(
    val title: String,
    val author: String,
    val description: String? = null,
    val mainCategory: String,
    val secondaryCategory: String? = null,
    val language: String,
    val coverType: CoverType,
    val condition: BookCondition,
)

@Serializable
data class PageDto<T>(
    val content: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
) {
    val hasMore: Boolean get() = page + 1 < totalPages
}

@Serializable
enum class ExchangeStatus { PENDING, COMPLETED, CANCELLED }

@Serializable
data class ExchangeParticipantDto(
    val uid: String,
    val nickname: String,
    val imageUrl: String? = null,
    val confirmed: Boolean,
)

@Serializable
data class ExchangeBookDto(val id: Long, val title: String, val imageUrl: String? = null)

@Serializable
data class ExchangeDto(
    val id: Long,
    val status: ExchangeStatus,
    val book: ExchangeBookDto,
    val owner: ExchangeParticipantDto,
    val requester: ExchangeParticipantDto,
    val createdAt: String,
    val updatedAt: String,
)

@Serializable
data class RateUserRequest(val score: Int, val comment: String? = null)

@Serializable
data class RatingDto(
    val id: Long,
    val score: Int,
    val comment: String? = null,
    val raterUid: String,
    val raterNickname: String,
    val createdAt: String,
)

/** RFC 9457 error body returned by the API. */
@Serializable
data class ProblemDto(
    val title: String? = null,
    val detail: String? = null,
    val errors: Map<String, String> = emptyMap(),
)

// Chat service

@Serializable
data class MessageDto(
    val id: String,
    val exchangeId: Long,
    val senderUid: String,
    val content: String,
    val sentAt: String,
)

@Serializable
data class OutgoingMessage(val content: String? = null, val type: String? = null)

@Serializable
sealed interface ServerEvent {
    @Serializable
    @SerialName("message")
    data class NewMessage(val message: MessageDto) : ServerEvent

    @Serializable
    @SerialName("error")
    data class Error(val reason: String) : ServerEvent

    /** The other participant confirmed or cancelled: reload the exchange from the API. */
    @Serializable
    @SerialName("exchange_changed")
    data class ExchangeChanged(val exchangeId: Long) : ServerEvent
}
