package com.bookintok.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.bookintok.app.FakeServer
import com.bookintok.app.core.database.BookintokDatabase
import com.bookintok.app.core.database.ExchangeEntity
import com.bookintok.app.core.database.MessageEntity
import com.bookintok.app.core.network.ExchangeStatus
import com.bookintok.app.json
import com.bookintok.app.testJson
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class InboxTest {

    private lateinit var database: BookintokDatabase

    private val server = FakeServer {
        json("""[{"id": "m1", "exchangeId": 7, "senderUid": "ana", "content": "¿Sigue disponible?", "sentAt": "2026-05-01T10:00:00Z"}]""")
    }

    private fun exchange(id: Long, archived: Boolean = false) = ExchangeEntity(
        id = id, status = ExchangeStatus.PENDING, bookId = 1, bookTitle = "Dune", bookImageUrl = null,
        ownerUid = "me", ownerNickname = "yo", ownerConfirmed = false,
        requesterUid = "ana", requesterNickname = "ana", requesterConfirmed = false,
        updatedAt = 0, archived = archived,
    )

    /** Only what the inbox uses: refreshAll brings the exchanges "from the API". */
    private inner class CachedExchanges(private val remote: List<ExchangeEntity>) : ExchangeRepository {
        private val dao get() = database.exchangeDao()
        override fun observeAll() = dao.observeAll().map { list -> list.map { it.toModel() } }
        override fun observe(id: Long) = dao.observe(id).map { it?.toModel() }
        override suspend fun refreshAll() = dao.upsert(remote)
        override suspend fun refresh(id: Long) = dao.upsert(remote.filter { it.id == id })
        override suspend fun request(bookId: Long) = error("unused")
        override suspend fun requestReopen(id: Long) = error("unused")
        override suspend fun acceptReopen(id: Long) = error("unused")
        override suspend fun clearReopenRequest(id: Long) = error("unused")
        override suspend fun confirm(id: Long) = error("unused")
        override suspend fun cancel(id: Long) = error("unused")
        override suspend fun setArchived(id: Long, archived: Boolean) = error("unused")
    }

    private fun inbox(remote: List<ExchangeEntity>) = InboxManager(
        client = server.client,
        tokens = { "token" },
        messages = database.messageDao(),
        markers = database.readMarkerDao(),
        exchanges = CachedExchanges(remote),
        json = testJson,
        chatBaseUrl = "ws://chat.test:8081",
    )

    private val unread get() = DefaultUnreadRepository(database.messageDao(), database.readMarkerDao())

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), BookintokDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `first sync on a device brings new chats and their messages, already marked as read`() = runTest {
        inbox(listOf(exchange(7))).sync()

        assertEquals(listOf(7L), database.exchangeDao().observeAll().first().map { it.id })
        assertEquals(1, database.messageDao().observe(7).first().size)
        assertNull(server.requests.single().url.parameters["since"])
        assertEquals(emptyMap<Long, Int>(), unread.unreadCounts("me").first())
    }

    @Test
    fun `later syncs ask only for what is new and it shows up as unread`() = runTest {
        database.exchangeDao().upsert(listOf(exchange(7)))
        database.messageDao().upsert(listOf(MessageEntity("m0", 7, "me", "hola", Instant.parse("2026-05-01T09:00:00Z").toEpochMilli())))

        inbox(listOf(exchange(7))).sync()

        assertEquals("2026-05-01T08:55:00Z", server.requests.single().url.parameters["since"])
        assertEquals(mapOf(7L to 1), unread.unreadCounts("me").first())
    }

    @Test
    fun `reading a chat clears its counter, and archived chats do not count`() = runTest {
        database.exchangeDao().upsert(listOf(exchange(7), exchange(8, archived = true)))
        val sentAt = Instant.parse("2026-05-01T10:00:00Z")
        database.messageDao().upsert(
            listOf(
                MessageEntity("a", 7, "ana", "uno", sentAt.toEpochMilli()),
                MessageEntity("b", 7, "ana", "dos", sentAt.plusSeconds(1).toEpochMilli()),
                MessageEntity("c", 7, "me", "mío", sentAt.plusSeconds(2).toEpochMilli()),
                MessageEntity("d", 8, "ana", "archivado", sentAt.toEpochMilli()),
            ),
        )
        assertEquals(mapOf(7L to 2), unread.unreadCounts("me").first())

        unread.markRead(7, sentAt)
        assertEquals(mapOf(7L to 1), unread.unreadCounts("me").first())

        unread.markRead(7, sentAt.plusSeconds(2))
        unread.markRead(7, sentAt) // an older marker never moves it back
        assertEquals(emptyMap<Long, Int>(), unread.unreadCounts("me").first())
    }
}
