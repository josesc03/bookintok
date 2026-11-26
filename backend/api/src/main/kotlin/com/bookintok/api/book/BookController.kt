package com.bookintok.api.book

import com.bookintok.api.common.uid
import jakarta.validation.Valid
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
class BookController(private val bookService: BookService) {

    @GetMapping("/api/v1/books")
    fun search(
        @AuthenticationPrincipal jwt: Jwt,
        @Valid @ModelAttribute filter: BookSearchFilter,
        @PageableDefault(size = 20, sort = ["createdAt"], direction = Sort.Direction.DESC) pageable: Pageable,
    ) = bookService.search(jwt.uid, filter, pageable)

    @GetMapping("/api/v1/books/{id}")
    fun findById(@PathVariable id: Long) = bookService.findById(id)

    @GetMapping("/api/v1/users/{uid}/books")
    fun findByOwner(
        @PathVariable uid: String,
        @PageableDefault(size = 20, sort = ["createdAt"], direction = Sort.Direction.DESC) pageable: Pageable,
    ) = bookService.findByOwner(uid, pageable)

    @PostMapping("/api/v1/books")
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@AuthenticationPrincipal jwt: Jwt, @Valid @RequestBody request: BookRequest) =
        bookService.create(jwt.uid, request)

    @PutMapping("/api/v1/books/{id}")
    fun update(@AuthenticationPrincipal jwt: Jwt, @PathVariable id: Long, @Valid @RequestBody request: BookRequest) =
        bookService.update(id, jwt.uid, request)

    @DeleteMapping("/api/v1/books/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@AuthenticationPrincipal jwt: Jwt, @PathVariable id: Long) = bookService.delete(id, jwt.uid)
}
