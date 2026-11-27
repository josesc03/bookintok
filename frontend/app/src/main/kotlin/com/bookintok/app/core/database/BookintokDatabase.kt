package com.bookintok.app.core.database

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.bookintok.app.core.network.ExchangeStatus
import kotlinx.coroutines.flow.Flow

/**
 * Local cache of the user's exchanges and their messages.
 * The UI always reads from here, so past conversations stay readable without connection;
 * the network only refreshes it.
 */
@Database(entities = [ExchangeEntity::class, MessageEntity::class], version = 2, exportSchema = true)
abstract class BookintokDatabase : RoomDatabase() {
    abstract fun exchangeDao(): ExchangeDao

    abstract fun messageDao(): MessageDao

    companion object {
        /** v2: chats can be archived (per user). */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE exchanges ADD COLUMN archived INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}

@Entity(tableName = "exchanges")
data class ExchangeEntity(
    @PrimaryKey val id: Long,
    val status: ExchangeStatus,
    val bookId: Long,
    val bookTitle: String,
    val bookImageUrl: String?,
    val ownerUid: String,
    val ownerNickname: String,
    val ownerConfirmed: Boolean,
    val requesterUid: String,
    val requesterNickname: String,
    val requesterConfirmed: Boolean,
    /** Epoch milliseconds */
    val updatedAt: Long,
    val archived: Boolean = false,
)

@Entity(tableName = "messages", indices = [Index(value = ["exchangeId", "sentAt"])])
data class MessageEntity(
    @PrimaryKey val id: String,
    val exchangeId: Long,
    val senderUid: String,
    val content: String,
    /** Epoch milliseconds */
    val sentAt: Long,
)

@Dao
interface ExchangeDao {
    @Query("SELECT * FROM exchanges ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<ExchangeEntity>>

    @Query("SELECT * FROM exchanges WHERE id = :id")
    fun observe(id: Long): Flow<ExchangeEntity?>

    @Upsert
    suspend fun upsert(exchanges: List<ExchangeEntity>)

    @Query("DELETE FROM exchanges")
    suspend fun clear()
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE exchangeId = :exchangeId ORDER BY sentAt ASC, id ASC")
    fun observe(exchangeId: Long): Flow<List<MessageEntity>>

    @Query("SELECT MIN(sentAt) FROM messages WHERE exchangeId = :exchangeId")
    suspend fun oldestSentAt(exchangeId: Long): Long?

    /** Last message of every exchange, for the conversation list. */
    @Query(
        """
        SELECT m.* FROM messages m
        WHERE m.sentAt = (SELECT MAX(sentAt) FROM messages WHERE exchangeId = m.exchangeId)
        GROUP BY m.exchangeId
        """,
    )
    fun observeLastMessages(): Flow<List<MessageEntity>>

    @Upsert
    suspend fun upsert(messages: List<MessageEntity>)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun delete(id: String)

    /**
     * Removes cached messages that the server no longer has (deleted while this device was
     * offline), within the time range a downloaded page covers.
     */
    @Query("DELETE FROM messages WHERE exchangeId = :exchangeId AND sentAt >= :fromSentAt AND id NOT IN (:keepIds)")
    suspend fun deleteMissing(exchangeId: Long, fromSentAt: Long, keepIds: List<String>)

    @Query("DELETE FROM messages")
    suspend fun clear()
}
