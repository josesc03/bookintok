package com.bookintok.app.data

import com.bookintok.app.core.auth.AuthTokenProvider
import com.bookintok.app.core.database.MessageDao
import com.bookintok.app.core.database.ReadMarkerDao
import com.bookintok.app.core.database.ReadMarkerEntity
import com.bookintok.app.core.network.MessageDto
import com.bookintok.app.core.network.ServerEvent
import com.bookintok.app.core.network.toApiException
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.isSuccess
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import java.time.Instant
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/** Unread messages per chat, computed from the cache and the read markers. */
interface UnreadRepository {
    /** Exchange id → messages from the other participant the user has not seen yet. */
    fun unreadCounts(myUid: String): Flow<Map<Long, Int>>

    /** Marks the chat as read up to [upTo] (the newest message shown, in server time). */
    suspend fun markRead(exchangeId: Long, upTo: Instant)
}

@Singleton
class DefaultUnreadRepository @Inject constructor(
    private val messages: MessageDao,
    private val markers: ReadMarkerDao,
) : UnreadRepository {

    override fun unreadCounts(myUid: String) =
        messages.observeUnread(myUid).map { list -> list.associate { it.exchangeId to it.count } }

    override suspend fun markRead(exchangeId: Long, upTo: Instant) {
        val current = markers.lastReadAt(exchangeId) ?: 0L
        if (upTo.toEpochMilli() > current) markers.upsert(ReadMarkerEntity(exchangeId, upTo.toEpochMilli()))
    }
}

/**
 * While the app is open, keeps one WebSocket with the chat service that receives the events of
 * every exchange of the user. New messages and new or changed exchanges go straight into Room,
 * so the chat list, the unread counters and the open chat update without opening each chat.
 * On every (re)connection it first syncs what arrived while the app was closed.
 */
@Singleton
class InboxManager @Inject constructor(
    private val client: HttpClient,
    private val tokens: AuthTokenProvider,
    private val messages: MessageDao,
    private val markers: ReadMarkerDao,
    private val exchanges: ExchangeRepository,
    private val json: Json,
    @Named(DefaultChatRepository.CHAT_BASE_URL) private val chatBaseUrl: String,
) {
    private val httpBaseUrl = chatBaseUrl.replaceFirst("ws", "http")

    /** Runs until cancelled (the app goes to the background), reconnecting with backoff. */
    suspend fun run() {
        var attempt = 0
        while (true) {
            try {
                sync()
                val token = tokens.idToken()
                client.webSocket("$chatBaseUrl/ws/inbox", request = { bearerAuth(token) }) {
                    attempt = 0
                    for (frame in incoming) {
                        if (frame !is Frame.Text) continue
                        handle(json.decodeFromString<ServerEvent>(frame.readText()))
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Offline or server restart: try again later
                android.util.Log.w("Inbox", "Inbox connection failed", e)
            }
            delay(DefaultChatRepository.backoff(attempt++))
        }
    }

    /** Downloads exchanges (new ones included) and the messages received since the last one cached. */
    suspend fun sync() {
        val latest = messages.latestSentAt()
        exchanges.refreshAll()
        val response = client.get("$httpBaseUrl/api/v1/inbox") {
            bearerAuth(tokens.idToken())
            // A small overlap covers messages written while a connection was being re-established
            parameter("since", latest?.let { Instant.ofEpochMilli(it).minus(SYNC_OVERLAP).toString() })
        }
        if (!response.status.isSuccess()) throw response.toApiException()
        messages.upsert(response.body<List<MessageDto>>().map { it.toEntity() })
        // On a new device, what already happened is not "unread"
        if (latest == null) markers.markAllRead(Instant.now().toEpochMilli())
    }

    private suspend fun handle(event: ServerEvent) {
        when (event) {
            is ServerEvent.NewMessage -> {
                messages.upsert(listOf(event.message.toEntity()))
                // A message from a chat this device does not know yet (someone just asked for a book)
                if (exchanges.observe(event.message.exchangeId).first() == null) {
                    runCatching { exchanges.refresh(event.message.exchangeId) }
                }
            }
            is ServerEvent.ExchangeChanged -> runCatching { exchanges.refresh(event.exchangeId) }
            is ServerEvent.MessageDeleted -> messages.delete(event.messageId)
            is ServerEvent.Error -> Unit
        }
    }

    private companion object {
        val SYNC_OVERLAP: java.time.Duration = java.time.Duration.ofMinutes(5)
    }
}
