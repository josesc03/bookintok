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
@Database(entities = [ExchangeEntity::class, MessageEntity::class, ReadMarkerEntity::class], version = 4, exportSchema = true)
abstract class BookintokDatabase : RoomDatabase() {
    abstract fun exchangeDao(): ExchangeDao

    abstract fun messageDao(): MessageDao

    abstract fun readMarkerDao(): ReadMarkerDao

    companion object {
        /** v2: chats can be archived (per user). */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE exchanges ADD COLUMN archived INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** v3: requests to resume cancelled exchanges, and read markers for unread counters. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE exchanges ADD COLUMN reopenRequestedBy TEXT")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS read_markers (exchangeId INTEGER NOT NULL, lastReadAt INTEGER NOT NULL, PRIMARY KEY(exchangeId))",
                )
                // What was already on this device counts as read
                db.execSQL("INSERT OR REPLACE INTO read_markers SELECT id, CAST(strftime('%s','now') AS INTEGER) * 1000 FROM exchanges")
            }
        }

        /** v4: whether the participants blocked each other (hides "resume"). */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE exchanges ADD COLUMN blocked INTEGER NOT NULL DEFAULT 0")
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
    /** Uid of the participant asking to resume this cancelled exchange. */
    val reopenRequestedBy: String? = null,
    val blocked: Boolean = false,
)

/** Up to when (epoch ms, server time of the last message seen) the user has read a chat. */
@Entity(tableName = "read_markers")
data class ReadMarkerEntity(@PrimaryKey val exchangeId: Long, val lastReadAt: Long)

data class UnreadCount(val exchangeId: Long, val count: Int)

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

    /** Exchanges the server no longer returns (for example, the other user deleted their account). */
    @Query("DELETE FROM exchanges WHERE id NOT IN (:keepIds)")
    suspend fun deleteMissing(keepIds: List<Long>)

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

    @Query("SELECT MAX(sentAt) FROM messages")
    suspend fun latestSentAt(): Long?

    /** Messages from others newer than the read marker, per chat (archived chats do not count). */
    @Query(
        """
        SELECT m.exchangeId AS exchangeId, COUNT(*) AS count FROM messages m
        JOIN exchanges e ON e.id = m.exchangeId
        LEFT JOIN read_markers r ON r.exchangeId = m.exchangeId
        WHERE m.senderUid != :me AND e.archived = 0 AND m.sentAt > COALESCE(r.lastReadAt, 0)
        GROUP BY m.exchangeId
        """,
    )
    fun observeUnread(me: String): Flow<List<UnreadCount>>

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

@Dao
interface ReadMarkerDao {
    @Query("SELECT lastReadAt FROM read_markers WHERE exchangeId = :exchangeId")
    suspend fun lastReadAt(exchangeId: Long): Long?

    @Upsert
    suspend fun upsert(marker: ReadMarkerEntity)

    /** Marks every cached chat as read up to [now] (first sync on a new device). */
    @Query("INSERT OR REPLACE INTO read_markers (exchangeId, lastReadAt) SELECT id, :now FROM exchanges")
    suspend fun markAllRead(now: Long)
}
