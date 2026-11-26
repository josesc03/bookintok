package com.bookintok.api.rating

import com.bookintok.api.common.uid
import jakarta.validation.Valid
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/users/{uid}/ratings")
class RatingController(private val ratingService: RatingService) {

    @GetMapping
    fun list(
        @PathVariable uid: String,
        @PageableDefault(size = 20, sort = ["createdAt"], direction = Sort.Direction.DESC) pageable: Pageable,
    ) = ratingService.ratingsOf(uid, pageable)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun rate(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable uid: String,
        @Valid @RequestBody request: RateUserRequest,
    ) = ratingService.rate(jwt.uid, uid, request)
}
