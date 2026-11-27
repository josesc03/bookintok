package com.bookintok.chat.exchange

import com.bookintok.chat.ChatJson
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

sealed interface AccessResult {
    /**
     * The user takes part in the exchange. Only pending exchanges accept new messages: completed
     * and cancelled ones are read-only (a cancelled one can be resumed through the API).
     * [participants] are both users, so events can also reach their inboxes.
     */
    data class Allowed(val open: Boolean, val participants: Set<String> = emptySet()) : AccessResult

    data object Denied : AccessResult
}

class UpstreamUnavailableException(message: String) : RuntimeException(message)

/**
 * Who may join an exchange's chat is decided by the API, which owns exchanges.
 * The chat service keeps no copy of that data.
 */
interface ExchangeAccess {
    suspend fun check(exchangeId: Long, bearerToken: String): AccessResult

    /** Ids of the exchanges the token's user takes part in. */
    suspend fun exchangeIdsOf(bearerToken: String): List<Long>
}

/** Asks the API with the user's own token: it answers 200 only to the exchange's participants. */
class ApiExchangeAccess(private val client: HttpClient, private val apiBaseUrl: String) : ExchangeAccess {

    private val log = LoggerFactory.getLogger(javaClass)

    @Serializable
    private data class Participant(val uid: String)

    @Serializable
    private data class ExchangeSummary(val id: Long, val status: String, val owner: Participant? = null, val requester: Participant? = null)

    @Serializable
    private data class ExchangePage(val content: List<ExchangeSummary>)

    override suspend fun check(exchangeId: Long, bearerToken: String): AccessResult {
        val response = call("$apiBaseUrl/api/v1/exchanges/$exchangeId", bearerToken)
        return when (response.status) {
            HttpStatusCode.OK -> {
                val exchange = ChatJson.decodeFromString<ExchangeSummary>(response.bodyAsText())
                AccessResult.Allowed(
                    open = exchange.status == "PENDING",
                    participants = setOfNotNull(exchange.owner?.uid, exchange.requester?.uid),
                )
            }
            HttpStatusCode.Unauthorized, HttpStatusCode.Forbidden, HttpStatusCode.NotFound -> AccessResult.Denied
            else -> throw UpstreamUnavailableException("API answered ${response.status}")
        }
    }

    override suspend fun exchangeIdsOf(bearerToken: String): List<Long> {
        val response = call("$apiBaseUrl/api/v1/exchanges?size=$MAX_EXCHANGES", bearerToken)
        return when (response.status) {
            HttpStatusCode.OK -> ChatJson.decodeFromString<ExchangePage>(response.bodyAsText()).content.map { it.id }
            HttpStatusCode.Unauthorized, HttpStatusCode.Forbidden -> emptyList()
            else -> throw UpstreamUnavailableException("API answered ${response.status}")
        }
    }

    private suspend fun call(url: String, bearerToken: String): HttpResponse = try {
        client.get(url) { bearerAuth(bearerToken) }
    } catch (e: Exception) {
        log.warn("Cannot reach the API ({})", url, e)
        throw UpstreamUnavailableException("API unreachable")
    }

    private companion object {
        /** The most recently active exchanges are enough to sync an inbox. */
        const val MAX_EXCHANGES = 100
    }
}
