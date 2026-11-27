package com.bookintok.app.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.bookintok.app.MainActivity
import com.bookintok.app.core.auth.AuthRepository
import com.bookintok.app.core.auth.AuthTokenProvider
import com.bookintok.app.core.network.toApiException
import com.bookintok.app.data.DefaultChatRepository
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.parameter
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Serializable
private data class DeviceRequest(val token: String)

/** Tells the chat service which device (FCM token) should get the signed-in user's notifications. */
@Singleton
class DeviceRegistration @Inject constructor(
    private val client: HttpClient,
    private val tokens: AuthTokenProvider,
    @Named(DefaultChatRepository.CHAT_BASE_URL) chatBaseUrl: String,
) {
    private val httpBaseUrl = chatBaseUrl.replaceFirst("ws", "http")

    suspend fun register(fcmToken: String? = null) {
        val token = fcmToken ?: FirebaseMessaging.getInstance().token.await()
        val response = client.put("$httpBaseUrl/api/v1/devices") {
            bearerAuth(tokens.idToken())
            contentType(ContentType.Application.Json)
            setBody(DeviceRequest(token))
        }
        if (!response.status.isSuccess()) throw response.toApiException()
    }

    /** Before signing out, so this device stops getting the account's notifications. */
    suspend fun unregister() {
        val token = FirebaseMessaging.getInstance().token.await()
        client.delete("$httpBaseUrl/api/v1/devices") {
            bearerAuth(tokens.idToken())
            parameter("token", token)
        }
    }
}

/** Shows chat notifications. Grouped per chat: a new message replaces the previous notification of that chat. */
object ChatNotifications {
    const val EXTRA_EXCHANGE_ID = "com.bookintok.app.EXCHANGE_ID"
    private const val CHANNEL_ID = "chats"

    fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL_ID, "Chats", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Mensajes y novedades de tus intercambios"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun show(context: Context, exchangeId: Long, title: String, body: String) {
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_EXCHANGE_ID, exchangeId)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val open = PendingIntent.getActivity(
            context, exchangeId.toInt(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        val manager = NotificationManagerCompat.from(context)
        if (manager.areNotificationsEnabled()) {
            try {
                manager.notify(exchangeId.toInt(), notification)
            } catch (_: SecurityException) {
                // Permission revoked meanwhile: nothing to show
            }
        }
    }

    /** Opening a chat clears its notification. */
    fun cancel(context: Context, exchangeId: Long) = NotificationManagerCompat.from(context).cancel(exchangeId.toInt())
}

/** Receives Firebase Cloud Messaging data messages, also when the app is closed. */
@AndroidEntryPoint
class BookintokMessagingService : FirebaseMessagingService() {
    @Inject lateinit var registration: DeviceRegistration

    @Inject lateinit var auth: AuthRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        if (auth.currentUserNow() == null) return // registered after signing in
        scope.launch { runCatching { registration.register(token) } }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val exchangeId = data["exchangeId"]?.toLongOrNull() ?: return
        ChatNotifications.show(this, exchangeId, data["title"].orEmpty().ifEmpty { "Bookintok" }, data["body"].orEmpty())
    }
}
