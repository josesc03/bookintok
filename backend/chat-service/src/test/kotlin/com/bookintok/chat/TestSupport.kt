package com.bookintok.chat

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.bookintok.chat.auth.TokenVerification
import com.bookintok.chat.exchange.AccessResult
import com.bookintok.chat.exchange.ExchangeAccess
import com.bookintok.chat.message.ChatMessage
import com.bookintok.chat.message.MessageRepository
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong

object TestTokens {
    private val algorithm = Algorithm.HMAC256("test-secret")
    private const val ISSUER = "test-issuer"

    val verification = TokenVerification.Static(JWT.require(algorithm).withIssuer(ISSUER).build())

    fun forUser(uid: String): String = JWT.create().withIssuer(ISSUER).withSubject(uid).sign(algorithm)
}

/** Exchanges as the API would describe them: id to (participants, open). */
class FakeExchangeAccess(private val exchanges: Map<Long, Pair<Set<String>, Boolean>>) : ExchangeAccess {
    override suspend fun check(exchangeId: Long, bearerToken: String): AccessResult {
        val uid = JWT.decode(bearerToken).subject
        val (participants, open) = exchanges[exchangeId] ?: return AccessResult.Denied
        return if (uid in participants) AccessResult.Allowed(open) else AccessResult.Denied
    }
}

class InMemoryMessageRepository : MessageRepository {
    private val messages = CopyOnWriteArrayList<ChatMessage>()
    private val ids = AtomicLong()

    override suspend fun save(exchangeId: Long, senderUid: String, content: String, sentAt: Instant) =
        ChatMessage(ids.incrementAndGet().toString(), exchangeId, senderUid, content, sentAt).also { messages += it }

    override suspend fun history(exchangeId: Long, before: Instant?, limit: Int) = messages
        .filter { it.exchangeId == exchangeId && (before == null || it.sentAt < before) }
        .sortedByDescending { it.sentAt }
        .take(limit)
}
