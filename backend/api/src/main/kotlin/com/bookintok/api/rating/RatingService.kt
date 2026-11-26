package com.bookintok.api.rating

import com.bookintok.api.common.BusinessRuleException
import com.bookintok.api.common.ConflictException
import com.bookintok.api.common.PageResponse
import com.bookintok.api.exchange.ExchangeRepository
import com.bookintok.api.user.UserService
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant

@Service
@Transactional(readOnly = true)
class RatingService(
    private val ratings: RatingRepository,
    private val exchanges: ExchangeRepository,
    private val userService: UserService,
    private val clock: Clock,
) {

    fun ratingsOf(uid: String, pageable: Pageable): PageResponse<RatingResponse> {
        userService.getUser(uid) // 404 for unknown users instead of an empty page
        return PageResponse.of(ratings.findByRatedUid(uid, pageable)) { it.toResponse() }
    }

    /** A user can rate another one once, and only after completing an exchange with them. */
    @Transactional
    fun rate(raterUid: String, ratedUid: String, request: RateUserRequest): RatingResponse {
        if (raterUid == ratedUid) throw BusinessRuleException("You cannot rate yourself")
        val rated = userService.getUser(ratedUid)
        val rater = userService.getUser(raterUid)
        if (!exchanges.haveCompletedExchange(raterUid, ratedUid)) {
            throw BusinessRuleException("You can only rate users you have completed an exchange with")
        }
        if (ratings.existsByRatedUidAndRaterUid(ratedUid, raterUid)) {
            throw ConflictException("You have already rated this user")
        }
        val rating = Rating(
            rated = rated,
            rater = rater,
            score = request.score.toShort(),
            comment = request.comment?.trim()?.ifEmpty { null },
            createdAt = Instant.now(clock),
        )
        return ratings.save(rating).toResponse()
    }
}
