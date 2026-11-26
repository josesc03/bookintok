package com.bookintok.chat.exchange

import com.bookintok.chat.ChatJson
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

sealed interface AccessResult {
    /** The user takes part in the exchange. Only open (pending) exchanges accept new messages. */
    data class Allowed(val open: Boolean) : AccessResult

    data object Denied : AccessResult
}

class UpstreamUnavailableException(message: String) : RuntimeException(message)

/**
 * Who may join an exchange's chat is decided by the API, which owns exchanges.
 * The chat service keeps no copy of that data.
 */
fun interface ExchangeAccess {
    suspend fun check(exchangeId: Long, bearerToken: String): AccessResult
}

/** Asks the API with the user's own token: it answers 200 only to the exchange's participants. */
class ApiExchangeAccess(private val client: HttpClient, private val apiBaseUrl: String) : ExchangeAccess {

    private val log = LoggerFactory.getLogger(javaClass)

    @Serializable
    private data class ExchangeSummary(val id: Long, val status: String)

    override suspend fun check(exchangeId: Long, bearerToken: String): AccessResult {
        val response = try {
            client.get("$apiBaseUrl/api/v1/exchanges/$exchangeId") { bearerAuth(bearerToken) }
        } catch (e: Exception) {
            log.warn("Cannot reach the API to check exchange {}", exchangeId, e)
            throw UpstreamUnavailableException("API unreachable")
        }
        return when (response.status) {
            HttpStatusCode.OK -> {
                val exchange = ChatJson.decodeFromString<ExchangeSummary>(response.bodyAsText())
                AccessResult.Allowed(open = exchange.status == "PENDING")
            }
            HttpStatusCode.Unauthorized, HttpStatusCode.Forbidden, HttpStatusCode.NotFound -> AccessResult.Denied
            else -> throw UpstreamUnavailableException("API answered ${response.status}")
        }
    }
}
