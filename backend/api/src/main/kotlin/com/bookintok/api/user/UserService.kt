package com.bookintok.api.user

import com.bookintok.api.common.BusinessRuleException
import com.bookintok.api.common.ConflictException
import com.bookintok.api.common.GeoPoint
import com.bookintok.api.common.NotFoundException
import com.bookintok.api.rating.RatingRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant

@Service
@Transactional(readOnly = true)
class UserService(
    private val users: UserRepository,
    private val ratings: RatingRepository,
    private val clock: Clock,
) {

    fun getUser(uid: String): User = users.findByIdOrNull(uid) ?: throw NotFoundException("User", uid)

    @Transactional
    fun register(uid: String, email: String?, request: RegisterUserRequest): MyProfileResponse {
        if (email.isNullOrBlank()) {
            throw BusinessRuleException("The Firebase account has no email address")
        }
        if (users.existsById(uid)) throw ConflictException("User is already registered")
        if (users.existsByNicknameIgnoreCase(request.nickname)) {
            throw ConflictException("Nickname '${request.nickname}' is already taken")
        }
        if (users.existsByEmailIgnoreCase(email)) throw ConflictException("Email is already registered")

        val user = users.save(
            User(
                uid = uid,
                nickname = request.nickname,
                email = email,
                displayName = request.displayName,
                createdAt = Instant.now(clock),
            ),
        )
        return user.toMyProfile(ratings.summaryFor(uid))
    }

    fun myProfile(uid: String): MyProfileResponse = getUser(uid).toMyProfile(ratings.summaryFor(uid))

    fun publicProfile(uid: String): PublicProfileResponse =
        getUser(uid).toPublicProfile(ratings.summaryFor(uid))

    @Transactional
    fun updateProfile(uid: String, request: UpdateProfileRequest): MyProfileResponse {
        val user = getUser(uid)
        request.displayName?.let { user.displayName = it }
        request.imageUrl?.let { user.imageUrl = it }
        return user.toMyProfile(ratings.summaryFor(uid))
    }

    @Transactional
    fun updateLocation(uid: String, request: UpdateLocationRequest) {
        getUser(uid).moveTo(GeoPoint(request.latitude, request.longitude))
    }
}
