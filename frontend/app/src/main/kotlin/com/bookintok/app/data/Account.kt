package com.bookintok.app.data

import android.content.Context
import com.bookintok.app.core.auth.AuthRepository
import com.bookintok.app.core.auth.AuthTokenProvider
import com.bookintok.app.core.database.BookintokDatabase
import com.bookintok.app.core.network.BlockedUserDto
import com.bookintok.app.core.network.BookintokApi
import com.bookintok.app.core.network.ReportReason
import com.bookintok.app.core.network.ReportRequest
import com.bookintok.app.core.network.toApiException
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/** Report and block other users. */
interface SafetyRepository {
    suspend fun report(uid: String, reason: ReportReason, comment: String?, exchangeId: Long?)

    /** Also cancels the open chats with that user (the server does it). */
    suspend fun block(uid: String)

    suspend fun unblock(uid: String)

    suspend fun blocked(): List<BlockedUserDto>
}

@Singleton
class DefaultSafetyRepository @Inject constructor(
    private val api: BookintokApi,
    private val exchanges: ExchangeRepository,
) : SafetyRepository {
    override suspend fun report(uid: String, reason: ReportReason, comment: String?, exchangeId: Long?) =
        api.report(uid, ReportRequest(reason, comment?.trim()?.ifEmpty { null }, exchangeId))

    override suspend fun block(uid: String) {
        api.block(uid)
        runCatching { exchanges.refreshAll() } // the chats with them are now cancelled
    }

    override suspend fun unblock(uid: String) = api.unblock(uid)

    override suspend fun blocked() = api.blocked()
}

/**
 * Deletes the account: first the conversations and devices (chat-service), then everything else
 * and the sign-in account (API). Finally the local cache is wiped and the session ends.
 */
@Singleton
class AccountRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: HttpClient,
    private val tokens: AuthTokenProvider,
    private val api: BookintokApi,
    private val auth: AuthRepository,
    private val database: BookintokDatabase,
    @Named(DefaultChatRepository.CHAT_BASE_URL) chatBaseUrl: String,
) {
    private val httpBaseUrl = chatBaseUrl.replaceFirst("ws", "http")

    suspend fun deleteAccount() {
        val response = client.delete("$httpBaseUrl/api/v1/me") { bearerAuth(tokens.idToken()) }
        if (!response.status.isSuccess()) throw response.toApiException()
        api.deleteAccount()
        // The server deletes the Firebase account when it has admin credentials; otherwise do it here
        if (FirebaseApp.getApps(context).isNotEmpty()) {
            runCatching { FirebaseAuth.getInstance().currentUser?.delete()?.await() }
        }
        auth.signOut()
        withContext(Dispatchers.IO) { database.clearAllTables() }
    }
}

/** Which notifications the user wants. Stored on the device; the system permission still applies. */
@Singleton
class NotificationPreferences @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("notifications", Context.MODE_PRIVATE)

    private val _messages = MutableStateFlow(prefs.getBoolean(MESSAGES, true))
    val messages: StateFlow<Boolean> = _messages.asStateFlow()

    private val _exchanges = MutableStateFlow(prefs.getBoolean(EXCHANGES, true))
    val exchanges: StateFlow<Boolean> = _exchanges.asStateFlow()

    fun setMessages(enabled: Boolean) {
        prefs.edit().putBoolean(MESSAGES, enabled).apply()
        _messages.value = enabled
    }

    fun setExchanges(enabled: Boolean) {
        prefs.edit().putBoolean(EXCHANGES, enabled).apply()
        _exchanges.value = enabled
    }

    /** [type] is the "type" field of the push message: "message" or "exchange". */
    fun allows(type: String?) = if (type == "message") messages.value else exchanges.value

    private companion object {
        const val MESSAGES = "messages"
        const val EXCHANGES = "exchanges"
    }
}
