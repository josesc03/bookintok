package com.bookintok.api.exchange

import com.bookintok.api.book.Book
import com.bookintok.api.book.BookCondition
import com.bookintok.api.book.CoverType
import com.bookintok.api.common.ConflictException
import com.bookintok.api.common.ForbiddenException
import com.bookintok.api.user.User
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExchangeTest {

    private val now = Instant.parse("2026-01-01T10:00:00Z")
    private val owner = User("owner", "owner", "owner@example.com", createdAt = now)
    private val requester = User("requester", "requester", "requester@example.com", createdAt = now)
    private val book = Book(
        owner = owner, title = "Dune", author = "Frank Herbert", description = null,
        mainCategory = "Sci-fi", secondaryCategory = null, language = "en",
        coverType = CoverType.PAPERBACK, condition = BookCondition.USED, createdAt = now,
    )

    private fun newExchange() = Exchange(book = book, owner = owner, requester = requester, createdAt = now)

    @Test
    fun `exchange completes only when both users confirm`() {
        val exchange = newExchange()

        assertFalse(exchange.confirm("owner", now))
        assertEquals(ExchangeStatus.PENDING, exchange.status)

        assertTrue(exchange.confirm("requester", now))
        assertEquals(ExchangeStatus.COMPLETED, exchange.status)
    }

    @Test
    fun `confirming twice does not complete the exchange`() {
        val exchange = newExchange()
        exchange.confirm("owner", now)
        assertFalse(exchange.confirm("owner", now))
        assertEquals(ExchangeStatus.PENDING, exchange.status)
    }

    @Test
    fun `outsiders cannot confirm or cancel`() {
        val exchange = newExchange()
        assertThrows<ForbiddenException> { exchange.confirm("someone-else", now) }
        assertThrows<ForbiddenException> { exchange.cancel("someone-else", now) }
    }

    @Test
    fun `a cancelled exchange cannot be confirmed`() {
        val exchange = newExchange()
        exchange.cancel("requester", now)
        assertThrows<ConflictException> { exchange.confirm("owner", now) }
    }

    @Test
    fun `discard only affects pending exchanges`() {
        val completed = newExchange().apply {
            confirm("owner", now)
            confirm("requester", now)
        }
        completed.discard(now)
        assertEquals(ExchangeStatus.COMPLETED, completed.status)

        val pending = newExchange().apply { discard(now) }
        assertEquals(ExchangeStatus.CANCELLED, pending.status)
    }
}
