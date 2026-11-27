package com.bookintok.chat.routes

import com.bookintok.chat.ChatDependencies
import com.bookintok.chat.ChatJson
import com.bookintok.chat.auth.FIREBASE_AUTH
import com.bookintok.chat.auth.bearerToken
import com.bookintok.chat.auth.uid
import com.bookintok.chat.exchange.AccessResult
import com.bookintok.chat.message.DELETE_WINDOW
import com.bookintok.chat.message.MAX_MESSAGE_LENGTH
import com.bookintok.chat.message.OutgoingMessageRequest
import com.bookintok.chat.message.ServerEvent
import com.bookintok.chat.message.toDto
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.request.receive
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.serialization.SerializationException
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

private val log = LoggerFactory.getLogger("com.bookintok.chat.routes.ChatRoutes")

private const val DEFAULT_PAGE = 50
private const val MAX_PAGE = 100
private const val MAX_INBOX_SYNC = 500

/** How far back an inbox syncs when the client has never synced before. */
private val DEFAULT_SYNC_WINDOW: java.time.Duration = java.time.Duration.ofDays(30)

fun Route.chatRoutes(deps: ChatDependencies) {
    get("/health") { call.respondText("OK") }

    authenticate(FIREBASE_AUTH) {
        /** Message history, newest first. Clients page backwards with `before` (the oldest sentAt they have). */
        get("/api/v1/exchanges/{id}/messages") {
            val exchangeId = call.exchangeId()
            if (deps.exchangeAccess.check(exchangeId, call.bearerToken) is AccessResult.Denied) {
                call.respond(HttpStatusCode.Forbidden)
                return@get
            }
            val before = call.request.queryParameters["before"]?.let { call.instant(it, "before") }
            val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, MAX_PAGE) ?: DEFAULT_PAGE
            call.respond(deps.messages.history(exchangeId, before, limit).map { it.toDto() })
        }

        /**
         * Messages of all the user's exchanges sent after `since` (oldest first), so the app can
         * catch up with what arrived while it was closed and count what is unread.
         */
        get("/api/v1/inbox") {
            val since = call.request.queryParameters["since"]?.let { call.instant(it, "since") }
                ?: Instant.now().minus(DEFAULT_SYNC_WINDOW)
            val exchangeIds = deps.exchangeAccess.exchangeIdsOf(call.bearerToken)
            call.respond(deps.messages.since(exchangeIds, since, MAX_INBOX_SYNC).map { it.toDto() })
        }

        /** The app registers its Firebase Cloud Messaging token to get notifications while closed. */
        put("/api/v1/devices") {
            val token = call.receive<DeviceRequest>().token.trim()
            if (token.isEmpty() || token.length > MAX_TOKEN_LENGTH) throw BadRequestException("Invalid device token")
            deps.devices.register(call.uid, token)
            call.respond(HttpStatusCode.NoContent)
        }

        /** Called when signing out, so the device stops getting this user's notifications. */
        delete("/api/v1/devices") {
            val token = call.request.queryParameters["token"] ?: throw BadRequestException("Missing token")
            deps.devices.unregister(call.uid, token)
            call.respond(HttpStatusCode.NoContent)
        }

        /**
         * First step of deleting an account (the API deletes the rest afterwards): the messages of
         * every exchange of the user, for both participants, and the user's devices.
         */
        delete("/api/v1/me") {
            val exchangeIds = deps.exchangeAccess.exchangeIdsOf(call.bearerToken)
            deps.messages.deleteConversations(exchangeIds)
            deps.devices.removeAllOf(call.uid)
            call.respond(HttpStatusCode.NoContent)
        }

        /** Events of every exchange of the user (new messages, deletions, status changes), while the app is open. */
        webSocket("/ws/inbox") {
            val uid = call.uid
            deps.inboxes.join(uid, this)
            log.info("User {} opened the inbox", uid)
            try {
                for (frame in incoming) Unit // nothing to receive: this socket only pushes
            } finally {
                deps.inboxes.leave(uid, this)
            }
        }

        /** Real-time chat of one exchange. Clients send `{"content": "..."}` and receive [ServerEvent]s. */
        webSocket("/ws/exchanges/{id}") {
            val exchangeId = call.exchangeId()
            val uid = call.uid
            // Access is checked once, when the connection opens
            val access = deps.exchangeAccess.check(exchangeId, call.bearerToken)
            if (access !is AccessResult.Allowed) {
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Not a participant of this exchange"))
                return@webSocket
            }

            deps.rooms.join(exchangeId, this)
            log.info("User {} joined the chat of exchange {}", uid, exchangeId)
            try {
                for (frame in incoming) {
                    if (frame !is Frame.Text) continue
                    handleIncoming(deps, exchangeId, uid, access, frame.readText())
                }
            } finally {
                deps.rooms.leave(exchangeId, this)
                log.info("User {} left the chat of exchange {}", uid, exchangeId)
            }
        }
    }
}

private suspend fun DefaultWebSocketServerSession.handleIncoming(
    deps: ChatDependencies,
    exchangeId: Long,
    uid: String,
    access: AccessResult.Allowed,
    text: String,
) {
    val request = try {
        ChatJson.decodeFromString<OutgoingMessageRequest>(text)
    } catch (_: SerializationException) {
        return sendEvent(ServerEvent.Error("Invalid message format"))
    } catch (_: IllegalArgumentException) {
        return sendEvent(ServerEvent.Error("Invalid message format"))
    }
    when (request.type) {
        OutgoingMessageRequest.EXCHANGE_CHANGED -> {
            deps.publish(exchangeId, access.participants, ServerEvent.ExchangeChanged(exchangeId))
            request.event?.let { deps.notifyExchangeEvent(exchangeId, uid, access, it) }
            return
        }
        OutgoingMessageRequest.DELETE_MESSAGE -> return deleteMessage(deps, exchangeId, uid, access, request.messageId)
    }
    val content = request.content?.trim().orEmpty()
    when {
        !access.open -> sendEvent(ServerEvent.Error("This exchange is closed: the chat is read-only"))
        content.isEmpty() -> sendEvent(ServerEvent.Error("Message is empty"))
        content.length > MAX_MESSAGE_LENGTH -> sendEvent(ServerEvent.Error("Message is too long"))
        else -> {
            val saved = deps.messages.save(exchangeId, uid, content, Instant.now().truncatedTo(ChronoUnit.MILLIS))
            deps.publish(exchangeId, access.participants, ServerEvent.NewMessage(saved.toDto()))
            deps.notifyInBackground(
                access.participants - uid,
                mapOf(
                    "type" to "message",
                    "exchangeId" to exchangeId.toString(),
                    "title" to "@${access.nicknames[uid].orEmpty()} · ${access.bookTitle}",
                    "body" to content.take(PUSH_PREVIEW_LENGTH),
                ),
            )
        }
    }
}

/** Only the sender can delete a message, within [DELETE_WINDOW] of sending it. */
private suspend fun DefaultWebSocketServerSession.deleteMessage(
    deps: ChatDependencies,
    exchangeId: Long,
    uid: String,
    access: AccessResult.Allowed,
    messageId: String?,
) {
    val message = messageId?.let { deps.messages.find(it) }
    when {
        message == null || message.exchangeId != exchangeId -> sendEvent(ServerEvent.Error("Message not found"))
        message.senderUid != uid -> sendEvent(ServerEvent.Error("You can only delete your own messages"))
        message.sentAt.isBefore(Instant.now().minus(DELETE_WINDOW)) ->
            sendEvent(ServerEvent.Error("Messages can only be deleted during the first 30 minutes"))
        else -> {
            deps.messages.delete(message.id)
            deps.publish(exchangeId, access.participants, ServerEvent.MessageDeleted(message.id, exchangeId))
        }
    }
}

private const val MAX_TOKEN_LENGTH = 4096
private const val PUSH_PREVIEW_LENGTH = 200

@Serializable
private data class DeviceRequest(val token: String)

private fun ChatDependencies.notifyInBackground(recipients: Set<String>, data: Map<String, String>) {
    pushScope.launch { notifier.notify(recipients, data) }
}

/** Fixed texts per event: the client only says which event happened, never what to display. */
private fun ChatDependencies.notifyExchangeEvent(exchangeId: Long, uid: String, access: AccessResult.Allowed, event: String) {
    val who = "@${access.nicknames[uid].orEmpty()}"
    val book = "«${access.bookTitle}»"
    val body = when (event) {
        "created" -> "$who quiere tu libro $book"
        "confirmed" -> "$who ha confirmado el intercambio de $book"
        "cancelled" -> "$who ha cancelado el intercambio de $book"
        "reopen_requested" -> "$who quiere retomar el intercambio de $book"
        "reopen_accepted" -> "$who ha aceptado retomar el intercambio de $book"
        "reopen_rejected" -> "$who no quiere retomar el intercambio de $book"
        else -> return
    }
    notifyInBackground(
        access.participants - uid,
        mapOf("type" to "exchange", "exchangeId" to exchangeId.toString(), "title" to "Bookintok", "body" to body),
    )
}

/** Sends an event to everyone in the exchange's chat and to both participants' inboxes. */
private suspend fun ChatDependencies.publish(exchangeId: Long, participants: Set<String>, event: ServerEvent) {
    val text = ChatJson.encodeToString<ServerEvent>(event)
    rooms.broadcast(exchangeId, text)
    inboxes.send(participants, text)
    log.debug("Event for exchange {} sent to the inboxes of {}", exchangeId, participants)
}

private fun ApplicationCall.instant(value: String, name: String): Instant = try {
    Instant.parse(value)
} catch (_: DateTimeParseException) {
    throw BadRequestException("'$name' must be an ISO-8601 instant")
}

private suspend fun DefaultWebSocketServerSession.sendEvent(event: ServerEvent) {
    send(Frame.Text(ChatJson.encodeToString<ServerEvent>(event)))
}

private fun ApplicationCall.exchangeId(): Long =
    parameters["id"]?.toLongOrNull() ?: throw BadRequestException("Invalid exchange id")
