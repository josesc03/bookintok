package com.bookintok.api.user

import com.bookintok.api.common.email
import com.bookintok.api.common.uid
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/users")
class UserController(private val userService: UserService) {

    /** Creates the BookinTok profile for an account that already exists in Firebase. */
    @PostMapping("/me")
    @ResponseStatus(HttpStatus.CREATED)
    fun register(@AuthenticationPrincipal jwt: Jwt, @Valid @RequestBody request: RegisterUserRequest) =
        userService.register(jwt.uid, jwt.email, request)

    @GetMapping("/me")
    fun me(@AuthenticationPrincipal jwt: Jwt) = userService.myProfile(jwt.uid)

    @PatchMapping("/me")
    fun updateProfile(@AuthenticationPrincipal jwt: Jwt, @Valid @RequestBody request: UpdateProfileRequest) =
        userService.updateProfile(jwt.uid, request)

    @PutMapping("/me/location")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun updateLocation(@AuthenticationPrincipal jwt: Jwt, @Valid @RequestBody request: UpdateLocationRequest) =
        userService.updateLocation(jwt.uid, request)

    @GetMapping("/{uid}")
    fun publicProfile(@PathVariable uid: String) = userService.publicProfile(uid)
}
