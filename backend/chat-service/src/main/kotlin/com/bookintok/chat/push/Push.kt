package com.bookintok.chat.push

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.AndroidConfig
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.MessagingErrorCode
import com.google.firebase.messaging.MulticastMessage
import com.mongodb.client.model.Filters
import com.mongodb.client.model.ReplaceOptions
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext
import org.bson.Document
import org.slf4j.LoggerFactory
import java.io.File
import java.util.Date

/** Firebase Cloud Messaging tokens of each user's devices (one user can have several). */
interface DeviceRepository {
    /** A token belongs to one device: registering it again moves it to [uid] (another account signed in). */
    suspend fun register(uid: String, token: String)

    suspend fun unregister(uid: String, token: String)

    suspend fun tokensOf(uid: String): List<String>

    suspend fun remove(tokens: Collection<String>)

    suspend fun removeAllOf(uid: String)
}

class MongoDeviceRepository(database: MongoDatabase) : DeviceRepository {
    private val collection = database.getCollection<Document>("devices")

    suspend fun ensureIndexes() {
        collection.createIndex(Document("uid", 1))
    }

    override suspend fun register(uid: String, token: String) {
        collection.replaceOne(
            Filters.eq("_id", token),
            Document("_id", token).append("uid", uid).append("updatedAt", Date()),
            ReplaceOptions().upsert(true),
        )
    }

    override suspend fun unregister(uid: String, token: String) {
        collection.deleteOne(Filters.and(Filters.eq("_id", token), Filters.eq("uid", uid)))
    }

    override suspend fun tokensOf(uid: String): List<String> =
        collection.find(Filters.eq("uid", uid)).map { it.getString("_id") }.toList()

    override suspend fun remove(tokens: Collection<String>) {
        if (tokens.isNotEmpty()) collection.deleteMany(Filters.`in`("_id", tokens))
    }

    override suspend fun removeAllOf(uid: String) {
        collection.deleteMany(Filters.eq("uid", uid))
    }
}

/** Sends data messages to devices. Returns the tokens that are no longer valid. */
fun interface PushSender {
    suspend fun send(tokens: List<String>, data: Map<String, String>): List<String>

    companion object {
        private val log = LoggerFactory.getLogger(PushSender::class.java)

        /** Without a service account key the service still works, only without push notifications. */
        val Disabled = PushSender { _, _ -> emptyList() }

        fun fromCredentialsFile(path: String?): PushSender {
            val file = path?.let(::File)?.takeIf { it.isFile }
            if (file == null) {
                log.warn("No Firebase service account key found: push notifications are disabled")
                return Disabled
            }
            val options = FirebaseOptions.builder()
                .setCredentials(file.inputStream().use { GoogleCredentials.fromStream(it) })
                .build()
            log.info("Push notifications enabled through Firebase Cloud Messaging")
            return FcmPushSender(FirebaseMessaging.getInstance(FirebaseApp.initializeApp(options)))
        }
    }
}

class FcmPushSender(private val messaging: FirebaseMessaging) : PushSender {
    private val log = LoggerFactory.getLogger(javaClass)

    override suspend fun send(tokens: List<String>, data: Map<String, String>): List<String> {
        if (tokens.isEmpty()) return emptyList()
        val message = MulticastMessage.builder()
            .addAllTokens(tokens)
            .putAllData(data)
            // Data-only and high priority: the app builds the notification and it arrives with the app closed
            .setAndroidConfig(AndroidConfig.builder().setPriority(AndroidConfig.Priority.HIGH).build())
            .build()
        val response = withContext(Dispatchers.IO) { messaging.sendEachForMulticast(message) }
        return response.responses.mapIndexedNotNull { index, result ->
            val error = result.exception?.messagingErrorCode
            if (error != null) log.debug("Push to a device failed: {}", error)
            tokens[index].takeIf { error == MessagingErrorCode.UNREGISTERED || error == MessagingErrorCode.INVALID_ARGUMENT }
        }
    }
}

/**
 * Tells users about chat activity when the app is not open (no inbox connection).
 * Messages carry only what the notification shows; the app syncs the rest when opened.
 */
class Notifier(
    private val devices: DeviceRepository,
    private val sender: PushSender,
    private val isOnline: (String) -> Boolean,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun notify(recipients: Collection<String>, data: Map<String, String>) {
        recipients.filterNot(isOnline).forEach { uid ->
            try {
                val tokens = devices.tokensOf(uid)
                devices.remove(sender.send(tokens, data))
            } catch (e: Exception) {
                log.warn("Could not send a push notification", e)
            }
        }
    }
}
