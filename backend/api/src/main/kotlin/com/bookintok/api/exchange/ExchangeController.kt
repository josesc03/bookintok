package com.bookintok.api.exchange

import com.bookintok.api.common.uid
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class ExchangeController(private val exchangeService: ExchangeService) {

    /** 201 with a new chat, or 200 with the chat the user already has for this book. */
    @PostMapping("/api/v1/books/{bookId}/exchanges")
    fun request(@AuthenticationPrincipal jwt: Jwt, @PathVariable bookId: Long): ResponseEntity<ExchangeResponse> {
        val result = exchangeService.request(bookId, jwt.uid)
        return ResponseEntity.status(if (result.created) HttpStatus.CREATED else HttpStatus.OK).body(result.exchange)
    }

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

    /** Asks to resume a cancelled exchange; the other participant has to accept it. */
    @PostMapping("/api/v1/exchanges/{id}/reopen-request")
    fun requestReopen(@AuthenticationPrincipal jwt: Jwt, @PathVariable id: Long) = exchangeService.requestReopen(id, jwt.uid)

    /** Rejects (the other participant) or withdraws (who asked) the request to resume. */
    @DeleteMapping("/api/v1/exchanges/{id}/reopen-request")
    fun clearReopenRequest(@AuthenticationPrincipal jwt: Jwt, @PathVariable id: Long) =
        exchangeService.clearReopenRequest(id, jwt.uid)

    @PostMapping("/api/v1/exchanges/{id}/reopen-acceptance")
    fun acceptReopen(@AuthenticationPrincipal jwt: Jwt, @PathVariable id: Long) = exchangeService.acceptReopen(id, jwt.uid)

    /** Hides the chat from the user's list (only for them). */
    @PutMapping("/api/v1/exchanges/{id}/archive")
    fun archive(@AuthenticationPrincipal jwt: Jwt, @PathVariable id: Long) = exchangeService.setArchived(id, jwt.uid, true)

    @DeleteMapping("/api/v1/exchanges/{id}/archive")
    fun unarchive(@AuthenticationPrincipal jwt: Jwt, @PathVariable id: Long) = exchangeService.setArchived(id, jwt.uid, false)
}
