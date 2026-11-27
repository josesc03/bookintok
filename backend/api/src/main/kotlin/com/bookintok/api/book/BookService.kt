package com.bookintok.api.book

import com.bookintok.api.common.BusinessRuleException
import com.bookintok.api.common.ConflictException
import com.bookintok.api.common.ForbiddenException
import com.bookintok.api.common.NotFoundException
import com.bookintok.api.common.PageResponse
import com.bookintok.api.user.UserService
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.domain.Specification
import com.bookintok.api.safety.SafetyService
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant

@Service
@Transactional(readOnly = true)
class BookService(
    private val books: BookRepository,
    private val userService: UserService,
    private val safety: SafetyService,
    private val clock: Clock,
) {

    fun getBook(id: Long): Book = books.findByIdOrNull(id) ?: throw NotFoundException("Book", id)

    /** Available books from other users, newest first, optionally near the viewer. */
    fun search(viewerUid: String, filter: BookSearchFilter, pageable: Pageable): PageResponse<BookResponse> {
        val viewerLocation = userService.getUser(viewerUid).location
        val specs = buildList {
            add(BookSpecifications.isAvailable())
            add(BookSpecifications.notOwnedBy(viewerUid))
            // Blocking works both ways: neither user sees the other's books
            safety.hiddenFrom(viewerUid).takeIf { it.isNotEmpty() }?.let { add(BookSpecifications.notOwnedByAny(it)) }
            filter.q?.takeIf { it.isNotBlank() }?.let { add(BookSpecifications.matchesText(it.trim())) }
            filter.language?.takeIf { it.isNotEmpty() }?.let { add(BookSpecifications.hasLanguage(it)) }
            filter.category?.takeIf { it.isNotBlank() }?.let { add(BookSpecifications.hasCategory(it.trim())) }
            filter.coverType?.takeIf { it.isNotEmpty() }?.let { add(BookSpecifications.hasCoverType(it)) }
            filter.condition?.takeIf { it.isNotEmpty() }?.let { add(BookSpecifications.hasCondition(it)) }
            filter.maxDistanceKm?.let { radius ->
                val location = viewerLocation
                    ?: throw BusinessRuleException("Set your location before searching by distance")
                add(BookSpecifications.withinDistance(location, radius))
            }
        }
        return PageResponse.of(books.findAll(Specification.allOf(specs), pageable)) { it.toResponse(viewerLocation) }
    }

    fun findById(id: Long, viewerUid: String): BookResponse =
        getBook(id).toResponse(userService.getUser(viewerUid).location)

    fun findByOwner(ownerUid: String, viewerUid: String, pageable: Pageable): PageResponse<BookResponse> {
        val viewerLocation = userService.getUser(viewerUid).location
        return PageResponse.of(books.findByOwnerUid(ownerUid, pageable)) { it.toResponse(viewerLocation) }
    }

    @Transactional
    fun create(ownerUid: String, request: BookRequest): BookResponse {
        val book = Book(
            owner = userService.getUser(ownerUid),
            title = request.title,
            author = request.author,
            description = request.description,
            mainCategory = request.mainCategory,
            secondaryCategory = request.secondaryCategory,
            language = request.language,
            coverType = request.coverType,
            condition = request.condition,

            createdAt = Instant.now(clock),
        )
        return books.save(book).toResponse()
    }

    @Transactional
    fun update(id: Long, uid: String, request: BookRequest): BookResponse {
        val book = getOwnedBook(id, uid)
        if (book.status == BookStatus.EXCHANGED) throw ConflictException("An exchanged book can no longer be edited")
        book.apply {
            title = request.title
            author = request.author
            description = request.description
            mainCategory = request.mainCategory
            secondaryCategory = request.secondaryCategory
            language = request.language
            coverType = request.coverType
            condition = request.condition

        }
        return book.toResponse()
    }

    @Transactional
    fun delete(id: Long, uid: String) {
        val book = getOwnedBook(id, uid)
        // Exchanged books are kept: the completed exchange is what allows users to rate each other
        if (book.status == BookStatus.EXCHANGED) throw ConflictException("An exchanged book cannot be deleted")
        books.delete(book)
    }

    private fun getOwnedBook(id: Long, uid: String): Book {
        val book = getBook(id)
        if (!book.isOwnedBy(uid)) throw ForbiddenException("Only the owner can modify this book")
        return book
    }
}
