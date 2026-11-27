package com.bookintok.chat

import com.bookintok.chat.auth.TokenVerification
import com.bookintok.chat.auth.configureAuthentication
import com.bookintok.chat.exchange.ApiExchangeAccess
import com.bookintok.chat.exchange.ExchangeAccess
import com.bookintok.chat.exchange.UpstreamUnavailableException
import com.bookintok.chat.message.MessageRepository
import com.bookintok.chat.message.MongoMessageRepository
import com.bookintok.chat.routes.ChatRooms
import com.bookintok.chat.routes.Inboxes
import com.bookintok.chat.routes.chatRoutes
import com.mongodb.kotlin.client.coroutine.MongoClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.timeout
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.slf4j.event.Level
import kotlin.time.Duration.Companion.seconds

/** Everything the module needs, built in [main] and replaced by fakes in tests. */
class ChatDependencies(
    val tokenVerification: TokenVerification,
    val exchangeAccess: ExchangeAccess,
    val messages: MessageRepository,
    val rooms: ChatRooms = ChatRooms(),
    val inboxes: Inboxes = Inboxes(),
)

val ChatJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

fun main() {
    val env = System.getenv()
    fun required(name: String) = env[name]?.takeIf { it.isNotBlank() } ?: error("Missing environment variable $name")

    val mongo = MongoClient.create(env["MONGO_URI"] ?: "mongodb://localhost:27017")
    val messages = MongoMessageRepository(mongo.getDatabase(env["MONGO_DATABASE"] ?: "bookintok_chat"))
    runBlocking { messages.ensureIndexes() }

    val dependencies = ChatDependencies(
        tokenVerification = TokenVerification.Firebase(projectId = required("FIREBASE_PROJECT_ID")),
        exchangeAccess = ApiExchangeAccess(HttpClient(CIO), env["API_BASE_URL"] ?: "http://localhost:8080"),
        messages = messages,
    )

    embeddedServer(Netty, port = env["PORT"]?.toInt() ?: 8081) { module(dependencies) }.start(wait = true)
}

fun Application.module(dependencies: ChatDependencies) {
    install(CallLogging) { level = Level.INFO }
    install(ContentNegotiation) { json(ChatJson) }
    install(WebSockets) {
        pingPeriod = 30.seconds
        timeout = 60.seconds
    }
    install(StatusPages) {
        exception<BadRequestException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to (cause.message ?: "Bad request")))
        }
        exception<UpstreamUnavailableException> { call, _ ->
            call.respond(HttpStatusCode.ServiceUnavailable, mapOf("error" to "Exchange service unavailable"))
        }
    }
    configureAuthentication(dependencies.tokenVerification)
    routing { chatRoutes(dependencies) }
}
