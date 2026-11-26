package com.bookintok.app.data

import com.bookintok.app.core.auth.AuthRepository
import com.bookintok.app.core.database.BookintokDatabase
import com.bookintok.app.core.network.ApiException
import com.bookintok.app.core.network.BookDto
import com.bookintok.app.core.network.BookRequest
import com.bookintok.app.core.network.BookSearch
import com.bookintok.app.core.network.BookintokApi
import com.bookintok.app.core.network.MyProfileDto
import com.bookintok.app.core.network.PageDto
import com.bookintok.app.core.network.PublicProfileDto
import com.bookintok.app.core.network.RateUserRequest
import com.bookintok.app.core.network.RatingDto
import com.bookintok.app.core.network.RegisterUserRequest
import com.bookintok.app.core.network.UpdateLocationRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

interface UserRepository {
    /** The user's profile, or null if they signed in to Firebase but never created one. */
    suspend fun myProfile(): MyProfileDto?

    suspend fun register(nickname: String, displayName: String?): MyProfileDto

    suspend fun updateLocation(latitude: Double, longitude: Double)

    suspend fun publicProfile(uid: String): PublicProfileDto

    suspend fun ratings(uid: String): List<RatingDto>

    suspend fun rate(uid: String, score: Int, comment: String?)

    /** Signs out and deletes the local cache, so the next user does not see this user's chats. */
    suspend fun signOut()
}

interface BookRepository {
    suspend fun search(search: BookSearch, page: Int): PageDto<BookDto>

    suspend fun book(id: Long): BookDto

    suspend fun booksOf(uid: String): List<BookDto>

    suspend fun save(id: Long?, request: BookRequest): BookDto

    suspend fun delete(id: Long)
}

@Singleton
class DefaultUserRepository @Inject constructor(
    private val api: BookintokApi,
    private val auth: AuthRepository,
    private val database: BookintokDatabase,
) : UserRepository {

    override suspend fun myProfile(): MyProfileDto? = try {
        api.myProfile()
    } catch (e: ApiException) {
        if (e.isNotFound) null else throw e
    }

    override suspend fun register(nickname: String, displayName: String?) =
        api.register(RegisterUserRequest(nickname.trim(), displayName?.trim()?.ifEmpty { null }))

    override suspend fun updateLocation(latitude: Double, longitude: Double) =
        api.updateLocation(UpdateLocationRequest(latitude, longitude))

    override suspend fun publicProfile(uid: String) = api.publicProfile(uid)

    override suspend fun ratings(uid: String) = api.ratings(uid).content

    override suspend fun rate(uid: String, score: Int, comment: String?) {
        api.rate(uid, RateUserRequest(score, comment?.trim()?.ifEmpty { null }))
    }

    override suspend fun signOut() {
        auth.signOut()
        withContext(Dispatchers.IO) { database.clearAllTables() }
    }
}

@Singleton
class DefaultBookRepository @Inject constructor(private val api: BookintokApi) : BookRepository {
    override suspend fun search(search: BookSearch, page: Int) = api.searchBooks(search, page)

    override suspend fun book(id: Long) = api.book(id)

    override suspend fun booksOf(uid: String) = api.booksOf(uid).content

    override suspend fun save(id: Long?, request: BookRequest) =
        if (id == null) api.createBook(request) else api.updateBook(id, request)

    override suspend fun delete(id: Long) = api.deleteBook(id)
}
