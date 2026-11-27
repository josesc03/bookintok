package com.bookintok.chat.routes

import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.websocket.Frame
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * Open WebSocket sessions grouped by exchange.
 *
 * It lives in memory, so it works for a single instance. To scale out, broadcasts would go
 * through a pub/sub channel (for example Redis or a MongoDB change stream) instead.
 */
class ChatRooms {
    private val log = LoggerFactory.getLogger(javaClass)
    private val rooms = ConcurrentHashMap<Long, MutableSet<DefaultWebSocketServerSession>>()

    fun join(exchangeId: Long, session: DefaultWebSocketServerSession) {
        rooms.computeIfAbsent(exchangeId) { ConcurrentHashMap.newKeySet() }.add(session)
    }

    fun leave(exchangeId: Long, session: DefaultWebSocketServerSession) {
        rooms.computeIfPresent(exchangeId) { _, sessions ->
            sessions.remove(session)
            sessions.ifEmpty { null } // drop empty rooms
        }
    }

    suspend fun broadcast(exchangeId: Long, text: String) {
        rooms[exchangeId]?.forEach { session ->
            try {
                session.send(Frame.Text(text))
            } catch (e: Exception) {
                log.debug("Dropping message for a closed session in exchange {}", exchangeId, e)
            }
        }
    }

    fun connectionCount(exchangeId: Long): Int = rooms[exchangeId]?.size ?: 0
}

/**
 * Inbox connections, one or more per user (one per open app). They receive the events of all the
 * user's exchanges, so the app can update its chat list and unread counters without opening each chat.
 * In memory, like [ChatRooms].
 */
class Inboxes {
    private val log = LoggerFactory.getLogger(javaClass)
    private val inboxes = ConcurrentHashMap<String, MutableSet<DefaultWebSocketServerSession>>()

    fun join(uid: String, session: DefaultWebSocketServerSession) {
        inboxes.computeIfAbsent(uid) { ConcurrentHashMap.newKeySet() }.add(session)
    }

    fun leave(uid: String, session: DefaultWebSocketServerSession) {
        inboxes.computeIfPresent(uid) { _, sessions ->
            sessions.remove(session)
            sessions.ifEmpty { null }
        }
    }

    suspend fun send(uids: Collection<String>, text: String) {
        uids.forEach { uid ->
            inboxes[uid]?.forEach { session ->
                try {
                    session.send(Frame.Text(text))
                } catch (e: Exception) {
                    log.debug("Dropping an inbox event for a closed session of {}", uid, e)
                }
            }
        }
    }

    fun connectionCount(uid: String): Int = inboxes[uid]?.size ?: 0
}
