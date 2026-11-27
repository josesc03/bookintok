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
import io.ktor.server.routing.get
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
            val before = call.request.queryParameters["before"]?.let {
                try {
                    Instant.parse(it)
                } catch (_: DateTimeParseException) {
                    throw BadRequestException("'before' must be an ISO-8601 instant")
                }
            }
            val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, MAX_PAGE) ?: DEFAULT_PAGE
            call.respond(deps.messages.history(exchangeId, before, limit).map { it.toDto() })
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
                    handleIncoming(deps, exchangeId, uid, access.open, frame.readText())
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
    exchangeOpen: Boolean,
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
            deps.rooms.broadcast(exchangeId, ChatJson.encodeToString<ServerEvent>(ServerEvent.ExchangeChanged(exchangeId)))
            return
        }
        OutgoingMessageRequest.DELETE_MESSAGE -> return deleteMessage(deps, exchangeId, uid, request.messageId)
    }
    val content = request.content?.trim().orEmpty()
    when {
        !exchangeOpen -> sendEvent(ServerEvent.Error("This exchange is closed: the chat is read-only"))
        content.isEmpty() -> sendEvent(ServerEvent.Error("Message is empty"))
        content.length > MAX_MESSAGE_LENGTH -> sendEvent(ServerEvent.Error("Message is too long"))
        else -> {
            val saved = deps.messages.save(exchangeId, uid, content, Instant.now().truncatedTo(ChronoUnit.MILLIS))
            deps.rooms.broadcast(exchangeId, ChatJson.encodeToString<ServerEvent>(ServerEvent.NewMessage(saved.toDto())))
        }
    }
}

/** Only the sender can delete a message, within [DELETE_WINDOW] of sending it. */
private suspend fun DefaultWebSocketServerSession.deleteMessage(
    deps: ChatDependencies,
    exchangeId: Long,
    uid: String,
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
            deps.rooms.broadcast(exchangeId, ChatJson.encodeToString<ServerEvent>(ServerEvent.MessageDeleted(message.id)))
        }
    }
}

private suspend fun DefaultWebSocketServerSession.sendEvent(event: ServerEvent) {
    send(Frame.Text(ChatJson.encodeToString<ServerEvent>(event)))
}

private fun ApplicationCall.exchangeId(): Long =
    parameters["id"]?.toLongOrNull() ?: throw BadRequestException("Invalid exchange id")
