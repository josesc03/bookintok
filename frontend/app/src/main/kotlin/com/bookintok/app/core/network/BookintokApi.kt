package com.bookintok.app.core.network

import com.bookintok.app.core.auth.AuthTokenProvider
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.parameter
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import javax.inject.Inject
import javax.inject.Named

/** The API answered with an error status. [detail] is the server's explanation, when it gives one. */
class ApiException(val status: Int, val detail: String?) : Exception("HTTP $status: $detail") {
    val isNotFound get() = status == HttpStatusCode.NotFound.value
}

/** Search criteria. Sets accept several values at once (any of them matches). */
data class BookSearch(
    val query: String? = null,
    val languages: Set<String> = emptySet(),
    val category: String? = null,
    val conditions: Set<BookCondition> = emptySet(),
    val coverTypes: Set<CoverType> = emptySet(),
    val maxDistanceKm: Int? = null,
) {
    /** Filters in use besides the text and the distance (which always has a value), for the filter button. */
    val activeFilters: Int
        get() = listOf(languages.isNotEmpty(), category != null, conditions.isNotEmpty(), coverTypes.isNotEmpty())
            .count { it }
}

/** Typed client for the Spring Boot API. Every request carries the user's Firebase ID token. */
class BookintokApi @Inject constructor(
    private val client: HttpClient,
    private val tokens: AuthTokenProvider,
    @Named(API_BASE_URL) private val baseUrl: String,
) {
    // Users

    suspend fun myProfile(): MyProfileDto = send(HttpMethod.Get, "/api/v1/users/me").body()

    suspend fun register(request: RegisterUserRequest): MyProfileDto =
        send(HttpMethod.Post, "/api/v1/users/me") { json(request) }.body()

    suspend fun updateLocation(request: UpdateLocationRequest) {
        send(HttpMethod.Put, "/api/v1/users/me/location") { json(request) }
    }

    suspend fun publicProfile(uid: String): PublicProfileDto = send(HttpMethod.Get, "/api/v1/users/$uid").body()

    suspend fun ratings(uid: String): PageDto<RatingDto> = send(HttpMethod.Get, "/api/v1/users/$uid/ratings").body()

    suspend fun rate(uid: String, request: RateUserRequest): RatingDto =
        send(HttpMethod.Post, "/api/v1/users/$uid/ratings") { json(request) }.body()

    // Safety and account

    suspend fun report(uid: String, request: ReportRequest) {
        send(HttpMethod.Post, "/api/v1/users/$uid/reports") { json(request) }
    }

    suspend fun block(uid: String) {
        send(HttpMethod.Put, "/api/v1/users/me/blocks/$uid")
    }

    suspend fun unblock(uid: String) {
        send(HttpMethod.Delete, "/api/v1/users/me/blocks/$uid")
    }

    suspend fun blocked(): List<BlockedUserDto> = send(HttpMethod.Get, "/api/v1/users/me/blocks").body()

    suspend fun deleteAccount() {
        send(HttpMethod.Delete, "/api/v1/users/me")
    }

    // Books

    suspend fun searchBooks(search: BookSearch, page: Int): PageDto<BookDto> =
        send(HttpMethod.Get, "/api/v1/books") {
            parameter("q", search.query?.takeIf { it.isNotBlank() })
            search.languages.forEach { parameter("language", it) }
            parameter("category", search.category)
            search.conditions.forEach { parameter("condition", it.name) }
            search.coverTypes.forEach { parameter("coverType", it.name) }
            parameter("maxDistanceKm", search.maxDistanceKm)
            parameter("page", page)
        }.body()

    suspend fun book(id: Long): BookDto = send(HttpMethod.Get, "/api/v1/books/$id").body()

    suspend fun booksOf(uid: String): PageDto<BookDto> = send(HttpMethod.Get, "/api/v1/users/$uid/books").body()

    suspend fun createBook(request: BookRequest): BookDto =
        send(HttpMethod.Post, "/api/v1/books") { json(request) }.body()

    suspend fun updateBook(id: Long, request: BookRequest): BookDto =
        send(HttpMethod.Put, "/api/v1/books/$id") { json(request) }.body()

    suspend fun deleteBook(id: Long) {
        send(HttpMethod.Delete, "/api/v1/books/$id")
    }

    /** [jpeg] is already compressed on the device; the server re-encodes it anyway. */
    suspend fun uploadBookImage(id: Long, jpeg: ByteArray): BookDto =
        send(HttpMethod.Put, "/api/v1/books/$id/image") {
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append(
                            "file",
                            jpeg,
                            Headers.build {
                                append(HttpHeaders.ContentType, "image/jpeg")
                                append(HttpHeaders.ContentDisposition, "filename=\"cover.jpg\"")
                            },
                        )
                    },
                ),
            )
        }.body()

    suspend fun deleteBookImage(id: Long) {
        send(HttpMethod.Delete, "/api/v1/books/$id/image")
    }

    // Exchanges

    /** 201 creates a chat; 200 returns the one the user already has for this book. */
    suspend fun requestExchange(bookId: Long): ExchangeRequestDto {
        val response = send(HttpMethod.Post, "/api/v1/books/$bookId/exchanges")
        return ExchangeRequestDto(response.body(), created = response.status == HttpStatusCode.Created)
    }

    suspend fun requestReopen(id: Long): ExchangeDto =
        send(HttpMethod.Post, "/api/v1/exchanges/$id/reopen-request").body()

    /** Rejects (the other participant) or withdraws (who asked) the request to resume. */
    suspend fun clearReopenRequest(id: Long): ExchangeDto =
        send(HttpMethod.Delete, "/api/v1/exchanges/$id/reopen-request").body()

    suspend fun acceptReopen(id: Long): ExchangeDto =
        send(HttpMethod.Post, "/api/v1/exchanges/$id/reopen-acceptance").body()

    suspend fun myExchanges(): PageDto<ExchangeDto> =
        send(HttpMethod.Get, "/api/v1/exchanges") { parameter("size", 100) }.body()

    suspend fun exchange(id: Long): ExchangeDto = send(HttpMethod.Get, "/api/v1/exchanges/$id").body()

    suspend fun confirmExchange(id: Long): ExchangeDto =
        send(HttpMethod.Post, "/api/v1/exchanges/$id/confirmation").body()

    suspend fun cancelExchange(id: Long): ExchangeDto =
        send(HttpMethod.Post, "/api/v1/exchanges/$id/cancellation").body()

    suspend fun setArchived(id: Long, archived: Boolean): ExchangeDto =
        send(if (archived) HttpMethod.Put else HttpMethod.Delete, "/api/v1/exchanges/$id/archive").body()

    private suspend fun send(
        method: HttpMethod,
        path: String,
        block: HttpRequestBuilder.() -> Unit = {},
    ): HttpResponse {
        val response = client.request("$baseUrl$path") {
            this.method = method
            bearerAuth(tokens.idToken())
            block()
        }
        if (!response.status.isSuccess()) throw response.toApiException()
        return response
    }

    companion object {
        const val API_BASE_URL = "apiBaseUrl"
    }
}

inline fun <reified T> HttpRequestBuilder.json(body: T) {
    contentType(ContentType.Application.Json)
    setBody(body)
}

suspend fun HttpResponse.toApiException(): ApiException {
    val problem = runCatching { body<ProblemDto>() }.getOrNull()
    val detail = problem?.errors?.values?.firstOrNull() ?: problem?.detail
    return ApiException(status.value, detail)
}
