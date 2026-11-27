package com.bookintok.api.exchange

import com.bookintok.api.book.BookService
import com.bookintok.api.book.BookStatus
import com.bookintok.api.common.BusinessRuleException
import com.bookintok.api.common.ConflictException
import com.bookintok.api.common.NotFoundException
import com.bookintok.api.common.PageResponse
import com.bookintok.api.user.UserService
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant

@Service
@Transactional(readOnly = true)
class ExchangeService(
    private val exchanges: ExchangeRepository,
    private val bookService: BookService,
    private val userService: UserService,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * There is a single chat per book and requester: if one is open or was cancelled it is returned
     * instead of creating another one (a cancelled one can be resumed with [requestReopen]).
     */
    @Transactional
    fun request(bookId: Long, requesterUid: String): ExchangeRequestResult {
        val book = bookService.getBook(bookId)
        if (book.isOwnedBy(requesterUid)) throw BusinessRuleException("You cannot request your own book")
        exchanges.findFirstByBookIdAndRequesterUidAndStatusInOrderByCreatedAtDesc(
            bookId, requesterUid, listOf(ExchangeStatus.PENDING, ExchangeStatus.CANCELLED),
        )?.let { return ExchangeRequestResult(it.toResponse(requesterUid), created = false) }
        if (book.status != BookStatus.AVAILABLE) throw ConflictException("The book is no longer available")

        val exchange = exchanges.save(
            Exchange(
                book = book,
                owner = book.owner,
                requester = userService.getUser(requesterUid),
                createdAt = Instant.now(clock),
            ),
        )
        log.info("Exchange {} requested for book {}", exchange.id, bookId)
        return ExchangeRequestResult(exchange.toResponse(requesterUid), created = true)
    }

    fun findForUser(uid: String, status: ExchangeStatus?, pageable: Pageable): PageResponse<ExchangeResponse> =
        PageResponse.of(exchanges.findForUser(uid, status, pageable)) { it.toResponse(uid) }

    fun findById(id: Long, uid: String): ExchangeResponse {
        val exchange = getExchange(id)
        exchange.requireParticipant(uid)
        return exchange.toResponse(uid)
    }

    /**
     * Confirms the swap on behalf of [uid]. When both users have confirmed, the book is marked as
     * exchanged and every other pending request for it is cancelled.
     */
    @Transactional
    fun confirm(id: Long, uid: String): ExchangeResponse {
        val exchange = getExchange(id)
        val now = Instant.now(clock)
        val completed = exchange.confirm(uid, now)
        if (completed) {
            exchange.book.markExchanged()
            exchanges.findByBookIdAndStatus(requireNotNull(exchange.book.id), ExchangeStatus.PENDING)
                .filter { it.id != exchange.id }
                .forEach { it.discard(now) }
            log.info("Exchange {} completed", id)
        }
        return exchange.toResponse(uid)
    }

    @Transactional
    fun cancel(id: Long, uid: String): ExchangeResponse {
        val exchange = getExchange(id)
        exchange.cancel(uid, Instant.now(clock))
        return exchange.toResponse(uid)
    }

    @Transactional
    fun requestReopen(id: Long, uid: String): ExchangeResponse {
        val exchange = getExchange(id)
        exchange.requireParticipant(uid)
        requireReopenable(exchange)
        exchange.requestReopen(uid, Instant.now(clock))
        return exchange.toResponse(uid)
    }

    @Transactional
    fun acceptReopen(id: Long, uid: String): ExchangeResponse {
        val exchange = getExchange(id)
        exchange.requireParticipant(uid)
        requireReopenable(exchange)
        exchange.acceptReopen(uid, Instant.now(clock))
        log.info("Exchange {} resumed", id)
        return exchange.toResponse(uid)
    }

    @Transactional
    fun clearReopenRequest(id: Long, uid: String): ExchangeResponse {
        val exchange = getExchange(id)
        exchange.clearReopenRequest(uid, Instant.now(clock))
        return exchange.toResponse(uid)
    }

    private fun requireReopenable(exchange: Exchange) {
        if (exchange.book.status != BookStatus.AVAILABLE) throw ConflictException("The book is no longer available")
        val bookId = requireNotNull(exchange.book.id)
        if (exchanges.existsByBookIdAndRequesterUidAndStatus(bookId, exchange.requester.uid, ExchangeStatus.PENDING)) {
            throw ConflictException("There is already an open exchange for this book")
        }
    }

    /** Archives (or restores) the chat for [uid] only; the other participant is not affected. */
    @Transactional
    fun setArchived(id: Long, uid: String, archived: Boolean): ExchangeResponse {
        val exchange = getExchange(id)
        exchange.setArchived(uid, archived)
        return exchange.toResponse(uid)
    }

    private fun getExchange(id: Long): Exchange =
        exchanges.findByIdOrNull(id) ?: throw NotFoundException("Exchange", id)
}
