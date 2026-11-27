package com.bookintok.app.di

import android.content.Context
import androidx.room.Room
import com.bookintok.app.BuildConfig
import com.bookintok.app.core.auth.AuthRepository
import com.bookintok.app.core.auth.AuthTokenProvider
import com.bookintok.app.core.auth.FirebaseAuthRepository
import com.bookintok.app.core.database.BookintokDatabase
import com.bookintok.app.core.network.BookintokApi
import com.bookintok.app.data.BookRepository
import com.bookintok.app.data.ChatRepository
import com.bookintok.app.data.DefaultBookRepository
import com.bookintok.app.data.DefaultChatRepository
import com.bookintok.app.data.DefaultExchangeRepository
import com.bookintok.app.data.DefaultUnreadRepository
import com.bookintok.app.data.DefaultUserRepository
import com.bookintok.app.data.ExchangeRepository
import com.bookintok.app.data.UnreadRepository
import com.bookintok.app.data.UserRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import javax.inject.Named
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun json(): Json = Json { ignoreUnknownKeys = true }

    @Provides
    @Singleton
    fun httpClient(json: Json): HttpClient = HttpClient(OkHttp) {
        install(ContentNegotiation) { json(json) }
        install(WebSockets) { pingIntervalMillis = 20.seconds.inWholeMilliseconds }
        install(HttpTimeout) {
            connectTimeoutMillis = 10_000
            requestTimeoutMillis = 20_000
        }
    }

    @Provides
    @Named(BookintokApi.API_BASE_URL)
    fun apiBaseUrl(): String = BuildConfig.API_BASE_URL

    @Provides
    @Named(DefaultChatRepository.CHAT_BASE_URL)
    fun chatBaseUrl(): String = BuildConfig.CHAT_BASE_URL

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): BookintokDatabase =
        Room.databaseBuilder(context, BookintokDatabase::class.java, "bookintok.db")
            .addMigrations(BookintokDatabase.MIGRATION_1_2, BookintokDatabase.MIGRATION_2_3)
            .build()

    @Provides
    fun exchangeDao(database: BookintokDatabase) = database.exchangeDao()

    @Provides
    fun messageDao(database: BookintokDatabase) = database.messageDao()

    @Provides
    fun readMarkerDao(database: BookintokDatabase) = database.readMarkerDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class BindingsModule {
    @Binds
    abstract fun authRepository(impl: FirebaseAuthRepository): AuthRepository

    @Binds
    abstract fun authTokenProvider(impl: FirebaseAuthRepository): AuthTokenProvider

    @Binds
    abstract fun userRepository(impl: DefaultUserRepository): UserRepository

    @Binds
    abstract fun bookRepository(impl: DefaultBookRepository): BookRepository

    @Binds
    abstract fun exchangeRepository(impl: DefaultExchangeRepository): ExchangeRepository

    @Binds
    abstract fun unreadRepository(impl: DefaultUnreadRepository): UnreadRepository

    @Binds
    abstract fun safetyRepository(impl: com.bookintok.app.data.DefaultSafetyRepository): com.bookintok.app.data.SafetyRepository

    @Binds
    abstract fun chatRepository(impl: DefaultChatRepository): ChatRepository
}
