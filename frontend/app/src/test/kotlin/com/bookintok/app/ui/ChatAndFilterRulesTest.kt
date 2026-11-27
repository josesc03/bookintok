package com.bookintok.app.ui

import com.bookintok.app.core.network.ExchangeStatus
import com.bookintok.app.data.ChatConnection
import com.bookintok.app.data.ChatMessage
import com.bookintok.app.data.Exchange
import com.bookintok.app.data.Participant
import com.bookintok.app.ui.books.DistanceSteps
import com.bookintok.app.ui.chat.ChatUiState
import com.bookintok.app.ui.exchanges.ChatTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class DistanceStepsTest {

    @Test
    fun `steps grow with the distance`() {
        val values = DistanceSteps.values
        assertEquals(1, values.first())
        assertEquals(1000, values.last())
        assertTrue((1..10).all { it in values })
        assertTrue(12 in values && 11 !in values)
        assertTrue(60 in values && 55 !in values)
        assertTrue(550 in values && 512 !in values)
        assertEquals(values.sorted(), values)
    }

    @Test
    fun `slider snaps to the closest offered distance`() {
        assertEquals(DistanceSteps.values.indexOf(5), DistanceSteps.indexOf(5))
        assertEquals(550, DistanceSteps.values[DistanceSteps.indexOf(540)])
    }
}

class ChatRulesTest {

    private val now = Instant.parse("2026-05-01T10:00:00Z")

    private fun exchange(status: ExchangeStatus, archived: Boolean = false) = Exchange(
        id = 1, status = status, bookId = 1, bookTitle = "Dune", bookImageUrl = null,
        owner = Participant("owner", "ana", false), requester = Participant("me", "luis", false),
        updatedAt = now, archived = archived,
    )

    private fun message(sender: String, minutesAgo: Long) =
        ChatMessage("m", 1, sender, "hola", now.minusSeconds(minutesAgo * 60))

    @Test
    fun `own messages can be deleted only during 30 minutes and while connected`() {
        val state = ChatUiState(myUid = "me", exchange = exchange(ExchangeStatus.PENDING), connection = ChatConnection.Connected)
        assertTrue(state.canDelete(message("me", 5), now))
        assertFalse(state.canDelete(message("me", 31), now))
        assertFalse(state.canDelete(message("owner", 5), now))
        assertFalse(state.copy(connection = ChatConnection.Disconnected).canDelete(message("me", 5), now))
    }

    @Test
    fun `only pending chats are writable`() {
        assertTrue(ChatUiState(myUid = "me", exchange = exchange(ExchangeStatus.PENDING)).canWrite)
        assertFalse(ChatUiState(myUid = "me", exchange = exchange(ExchangeStatus.CANCELLED)).canWrite)
        assertFalse(ChatUiState(myUid = "me", exchange = exchange(ExchangeStatus.COMPLETED)).canWrite)
    }

    @Test
    fun `a request to resume is told apart by who asked`() {
        val cancelled = exchange(ExchangeStatus.CANCELLED)
        val mine = ChatUiState(myUid = "me", exchange = cancelled.copy(reopenRequestedBy = "me"))
        val theirs = ChatUiState(myUid = "me", exchange = cancelled.copy(reopenRequestedBy = "owner"))
        assertTrue(mine.reopenAskedByMe && !mine.reopenAskedByOther)
        assertTrue(theirs.reopenAskedByOther && !theirs.reopenAskedByMe)
        assertFalse(ChatUiState(myUid = "me", exchange = cancelled).reopenAskedByOther)
    }

    @Test
    fun `archived chats only appear in the archived tab`() {
        val archived = exchange(ExchangeStatus.PENDING, archived = true)
        assertEquals(listOf(ChatTab.ARCHIVED), ChatTab.entries.filter { it.matches(archived) })
        assertEquals(listOf(ChatTab.COMPLETED), ChatTab.entries.filter { it.matches(exchange(ExchangeStatus.COMPLETED)) })
    }
}
