package com.bookintok.chat.message

import com.mongodb.client.model.Filters
import com.mongodb.client.model.Indexes
import com.mongodb.client.model.Sorts
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.bson.Document
import org.bson.types.ObjectId
import java.time.Instant
import java.util.Date

/**
 * Messages are stored in MongoDB: an append-only stream per exchange, always read by
 * exchange and date, which fits a document store with a compound index.
 */
class MongoMessageRepository(database: MongoDatabase) : MessageRepository {

    private val collection = database.getCollection<Document>("messages")

    suspend fun ensureIndexes() {
        collection.createIndex(Indexes.compoundIndex(Indexes.ascending("exchangeId"), Indexes.descending("sentAt")))
    }

    override suspend fun save(exchangeId: Long, senderUid: String, content: String, sentAt: Instant): ChatMessage {
        val id = ObjectId()
        val document = Document("_id", id)
            .append("exchangeId", exchangeId)
            .append("senderUid", senderUid)
            .append("content", content)
            .append("sentAt", Date.from(sentAt))
        collection.insertOne(document)
        // MongoDB dates have millisecond precision: return what was really stored
        return document.toMessage()
    }

    override suspend fun history(exchangeId: Long, before: Instant?, limit: Int): List<ChatMessage> {
        val filter = if (before == null) {
            Filters.eq("exchangeId", exchangeId)
        } else {
            Filters.and(Filters.eq("exchangeId", exchangeId), Filters.lt("sentAt", Date.from(before)))
        }
        return collection.find(filter)
            .sort(Sorts.orderBy(Sorts.descending("sentAt"), Sorts.descending("_id")))
            .limit(limit)
            .map { it.toMessage() }
            .toList()
    }

    override suspend fun find(id: String): ChatMessage? {
        if (!ObjectId.isValid(id)) return null
        return collection.find(Filters.eq("_id", ObjectId(id))).firstOrNull()?.toMessage()
    }

    override suspend fun delete(id: String) {
        if (ObjectId.isValid(id)) collection.deleteOne(Filters.eq("_id", ObjectId(id)))
    }

    private fun Document.toMessage() = ChatMessage(
        id = getObjectId("_id").toHexString(),
        exchangeId = getLong("exchangeId"),
        senderUid = getString("senderUid"),
        content = getString("content"),
        sentAt = getDate("sentAt").toInstant(),
    )
}
