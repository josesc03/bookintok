package com.bookintok.api.exchange

import com.bookintok.api.common.uid
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
class ExchangeController(private val exchangeService: ExchangeService) {

    @PostMapping("/api/v1/books/{bookId}/exchanges")
    @ResponseStatus(HttpStatus.CREATED)
    fun request(@AuthenticationPrincipal jwt: Jwt, @PathVariable bookId: Long) =
        exchangeService.request(bookId, jwt.uid)

    /** Exchanges where the authenticated user is the owner or the requester. */
    @GetMapping("/api/v1/exchanges")
    fun mine(
        @AuthenticationPrincipal jwt: Jwt,
        @RequestParam(required = false) status: ExchangeStatus?,
        @PageableDefault(size = 20, sort = ["updatedAt"], direction = Sort.Direction.DESC) pageable: Pageable,
    ) = exchangeService.findForUser(jwt.uid, status, pageable)

    /** Also used by chat-service to check that a user may join the exchange's chat. */
    @GetMapping("/api/v1/exchanges/{id}")
    fun findById(@AuthenticationPrincipal jwt: Jwt, @PathVariable id: Long) =
        exchangeService.findById(id, jwt.uid)

    @PostMapping("/api/v1/exchanges/{id}/confirmation")
    fun confirm(@AuthenticationPrincipal jwt: Jwt, @PathVariable id: Long) = exchangeService.confirm(id, jwt.uid)

    @PostMapping("/api/v1/exchanges/{id}/cancellation")
    fun cancel(@AuthenticationPrincipal jwt: Jwt, @PathVariable id: Long) = exchangeService.cancel(id, jwt.uid)

    /** Hides the chat from the user's list (only for them). */
    @PutMapping("/api/v1/exchanges/{id}/archive")
    fun archive(@AuthenticationPrincipal jwt: Jwt, @PathVariable id: Long) = exchangeService.setArchived(id, jwt.uid, true)

    @DeleteMapping("/api/v1/exchanges/{id}/archive")
    fun unarchive(@AuthenticationPrincipal jwt: Jwt, @PathVariable id: Long) = exchangeService.setArchived(id, jwt.uid, false)
}
