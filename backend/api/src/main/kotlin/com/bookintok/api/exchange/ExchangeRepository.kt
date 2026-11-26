package com.bookintok.api.exchange

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ExchangeRepository : JpaRepository<Exchange, Long> {

    @EntityGraph(attributePaths = ["book", "owner", "requester"])
    @Query(
        """
        select e from Exchange e
        where (e.owner.uid = :uid or e.requester.uid = :uid)
          and (:status is null or e.status = :status)
        """,
    )
    fun findForUser(
        @Param("uid") uid: String,
        @Param("status") status: ExchangeStatus?,
        pageable: Pageable,
    ): Page<Exchange>

    fun existsByBookIdAndRequesterUidAndStatus(bookId: Long, requesterUid: String, status: ExchangeStatus): Boolean

    fun findByBookIdAndStatus(bookId: Long, status: ExchangeStatus): List<Exchange>

    /** Whether the two users completed at least one exchange together, in either direction. */
    @Query(
        """
        select count(e) > 0 from Exchange e
        where e.status = com.bookintok.api.exchange.ExchangeStatus.COMPLETED
          and ((e.owner.uid = :a and e.requester.uid = :b) or (e.owner.uid = :b and e.requester.uid = :a))
        """,
    )
    fun haveCompletedExchange(@Param("a") a: String, @Param("b") b: String): Boolean
}
