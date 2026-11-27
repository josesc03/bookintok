package com.bookintok.chat.message

import com.mongodb.kotlin.client.coroutine.MongoClient
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.TestInstance
import org.testcontainers.mongodb.MongoDBContainer
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

/** Runs against a real MongoDB in Docker. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MongoMessageRepositoryTest {

    private val mongo = MongoDBContainer("mongo:8").apply { start() }
    private val client = MongoClient.create(mongo.connectionString)

    // A fresh database per test keeps them independent
    private fun newRepository() =
        MongoMessageRepository(client.getDatabase("test_${UUID.randomUUID().toString().take(8)}"))

    @AfterAll
    fun tearDown() {
        client.close()
        mongo.stop()
    }

    @Test
    fun `saves a message and reads it back`() = runTest {
        val repository = newRepository().apply { ensureIndexes() }
        val sentAt = Instant.parse("2026-05-01T10:00:00.123Z")

        val saved = repository.save(42, "owner", "Hello", sentAt)

        assertEquals(listOf(saved), repository.history(42, before = null, limit = 10))
        assertEquals(sentAt, saved.sentAt)
    }

    @Test
    fun `since returns newer messages of the given exchanges, oldest first`() = runTest {
        val repository = newRepository()
        val base = Instant.parse("2026-05-01T10:00:00Z")
        repository.save(1, "owner", "old", base)
        repository.save(2, "owner", "b", base.plusSeconds(20))
        repository.save(1, "owner", "a", base.plusSeconds(10))
        repository.save(3, "owner", "not asked", base.plusSeconds(30))

        assertEquals(listOf("a", "b"), repository.since(listOf(1L, 2L), base, limit = 10).map { it.content })
        assertEquals(emptyList(), repository.since(emptyList(), base, limit = 10))
    }

    @Test
    fun `history is newest first, per exchange, and pages with before`() = runTest {
        val repository = newRepository()
        val base = Instant.parse("2026-05-01T10:00:00Z")
        (0 until 5).forEach { i -> repository.save(1, "owner", "m$i", base.plusSeconds(i.toLong())) }
        repository.save(2, "owner", "other exchange", base)

        assertEquals(listOf("m4", "m3"), repository.history(1, before = null, limit = 2).map { it.content })
        assertEquals(
            listOf("m2", "m1", "m0"),
            repository.history(1, before = base.plusSeconds(3), limit = 10).map { it.content },
        )
        assertEquals(1, repository.history(2, before = null, limit = 10).size)
    }
}
