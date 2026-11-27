package com.bookintok.app.data

import com.bookintok.app.core.auth.AuthTokenProvider
import com.bookintok.app.core.database.MessageDao
import com.bookintok.app.core.network.MessageDto
import com.bookintok.app.core.network.OutgoingMessage
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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds

sealed interface ChatConnection {
    data object Connecting : ChatConnection

    data object Connected : ChatConnection

    /** Offline: cached messages are still shown, a reconnection is scheduled. */
    data object Disconnected : ChatConnection

    /** The other participant confirmed or cancelled the exchange: it should be reloaded. */
    data object ExchangeChanged : ChatConnection

    /** The server rejected something we sent (closed exchange, empty message...). */
    data class Rejected(val reason: String) : ChatConnection
}

class NotConnectedException : IllegalStateException("The chat is not connected")

interface ChatRepository {
    /** Cached messages of an exchange, oldest first. Works offline. */
    fun messages(exchangeId: Long): Flow<List<ChatMessage>>

    /** Downloads the most recent messages into the cache. */
    suspend fun refresh(exchangeId: Long)

    /** Downloads the page before the oldest cached message. Returns false when there are no more. */
    suspend fun loadOlder(exchangeId: Long): Boolean

    /**
     * Keeps a WebSocket open while collected, reconnecting with backoff. Incoming messages are
     * written to the cache, so the UI only has to observe [messages].
     */
    fun connect(exchangeId: Long): Flow<ChatConnection>

    suspend fun send(exchangeId: Long, content: String)

    /** Tells the other participant that the exchange changed (best effort: ignored when offline). */
    suspend fun notifyExchangeChanged(exchangeId: Long)
}

@Singleton
class DefaultChatRepository @Inject constructor(
    private val client: HttpClient,
    private val tokens: AuthTokenProvider,
    private val dao: MessageDao,
    private val json: Json,
    @Named(CHAT_BASE_URL) private val chatBaseUrl: String,
) : ChatRepository {

    /** ws://host:port → http://host:port (wss → https) for the REST history endpoint. */
    private val httpBaseUrl = chatBaseUrl.replaceFirst("ws", "http")
    private val outgoing = ConcurrentHashMap<Long, Channel<OutgoingMessage>>()

    override fun messages(exchangeId: Long) = dao.observe(exchangeId).map { list -> list.map { it.toModel() } }

    override suspend fun refresh(exchangeId: Long) {
        fetchHistory(exchangeId, before = null)
    }

    override suspend fun loadOlder(exchangeId: Long): Boolean {
        val oldest = dao.oldestSentAt(exchangeId) ?: return fetchHistory(exchangeId, null) == PAGE_SIZE
        return fetchHistory(exchangeId, Instant.ofEpochMilli(oldest)) == PAGE_SIZE
    }

    private suspend fun fetchHistory(exchangeId: Long, before: Instant?): Int {
        val response = client.get("$httpBaseUrl/api/v1/exchanges/$exchangeId/messages") {
            bearerAuth(tokens.idToken())
            parameter("limit", PAGE_SIZE)
            parameter("before", before?.toString())
        }
        if (!response.status.isSuccess()) throw response.toApiException()
        val page = response.body<List<MessageDto>>()
        dao.upsert(page.map { it.toEntity() })
        return page.size
    }

    override fun connect(exchangeId: Long): Flow<ChatConnection> = channelFlow {
        var attempt = 0
        while (true) {
            send(ChatConnection.Connecting)
            val queue = Channel<OutgoingMessage>(Channel.BUFFERED)
            outgoing[exchangeId] = queue
            try {
                val token = tokens.idToken()
                client.webSocket("$chatBaseUrl/ws/exchanges/$exchangeId", request = { bearerAuth(token) }) {
                    attempt = 0
                    this@channelFlow.send(ChatConnection.Connected)
                    val sender = launch {
                        for (message in queue) send(Frame.Text(json.encodeToString(message)))
                    }
                    for (frame in incoming) {
                        if (frame !is Frame.Text) continue
                        when (val event = json.decodeFromString<ServerEvent>(frame.readText())) {
                            is ServerEvent.NewMessage -> dao.upsert(listOf(event.message.toEntity()))
                            is ServerEvent.Error -> this@channelFlow.send(ChatConnection.Rejected(event.reason))
                            is ServerEvent.ExchangeChanged -> this@channelFlow.send(ChatConnection.ExchangeChanged)
                        }
                    }
                    sender.cancel()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                // Network errors fall through to the reconnection below
            } finally {
                outgoing.remove(exchangeId, queue)
                queue.close()
            }
            send(ChatConnection.Disconnected)
            delay(backoff(attempt++))
        }
    }

    override suspend fun send(exchangeId: Long, content: String) {
        val queue = outgoing[exchangeId] ?: throw NotConnectedException()
        queue.send(OutgoingMessage(content = content))
    }

    override suspend fun notifyExchangeChanged(exchangeId: Long) {
        outgoing[exchangeId]?.send(OutgoingMessage(type = "exchange_changed"))
    }

    companion object {
        const val CHAT_BASE_URL = "chatBaseUrl"
        const val PAGE_SIZE = 50

        /** 1s, 2s, 4s... up to 30s between reconnection attempts. */
        fun backoff(attempt: Int) = (1L shl attempt.coerceAtMost(5)).coerceAtMost(30).seconds
    }
}
