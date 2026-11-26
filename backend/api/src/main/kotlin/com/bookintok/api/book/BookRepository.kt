package com.bookintok.api.book

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.domain.Specification

interface BookRepository : JpaRepository<Book, Long>, JpaSpecificationExecutor<Book> {

    /** Loads the owner in the same query to avoid N+1 selects when mapping a page. */
    @EntityGraph(attributePaths = ["owner"])
    override fun findAll(spec: Specification<Book>, pageable: Pageable): Page<Book>

    @EntityGraph(attributePaths = ["owner"])
    fun findByOwnerUid(ownerUid: String, pageable: Pageable): Page<Book>
}
