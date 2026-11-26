package com.bookintok.api.book

import com.bookintok.api.common.BusinessRuleException
import com.bookintok.api.common.ConflictException
import com.bookintok.api.common.ForbiddenException
import com.bookintok.api.common.NotFoundException
import com.bookintok.api.common.uid
import com.bookintok.api.image.ImageProcessor
import com.bookintok.api.image.ImageStorage
import org.springframework.http.CacheControl
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.util.UUID
import java.util.concurrent.TimeUnit

@Service
class BookImageService(private val bookService: BookService, private val storage: ImageStorage) {

    @Transactional
    fun upload(bookId: Long, uid: String, bytes: ByteArray): BookResponse {
        val book = ownedEditableBook(bookId, uid)
        val key = UUID.randomUUID().toString()
        storage.save(key, ImageProcessor.normalize(bytes))
        val previous = book.imageKey
        book.imageKey = key
        previous?.let(storage::delete)
        return book.toResponse()
    }

    @Transactional
    fun delete(bookId: Long, uid: String) {
        val book = ownedEditableBook(bookId, uid)
        book.imageKey?.let(storage::delete)
        book.imageKey = null
    }

    fun load(bookId: Long): ByteArray {
        val key = bookService.getBook(bookId).imageKey ?: throw NotFoundException("Image of book", bookId)
        return storage.load(key) ?: throw NotFoundException("Image of book", bookId)
    }

    private fun ownedEditableBook(bookId: Long, uid: String): Book {
        val book = bookService.getBook(bookId)
        if (!book.isOwnedBy(uid)) throw ForbiddenException("Only the owner can change the photo")
        if (book.status == BookStatus.EXCHANGED) throw ConflictException("An exchanged book can no longer be edited")
        return book
    }
}

@RestController
class BookImageController(private val images: BookImageService) {

    /** Uploads or replaces the photo. Accepts JPEG or PNG up to 5 MB; it is stored as a JPEG without metadata. */
    @PutMapping("/api/v1/books/{id}/image", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun upload(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable id: Long,
        @RequestPart("file") file: MultipartFile,
    ): BookResponse {
        if (file.isEmpty) throw BusinessRuleException("The file is empty")
        return images.upload(id, jwt.uid, file.bytes)
    }

    @DeleteMapping("/api/v1/books/{id}/image")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@AuthenticationPrincipal jwt: Jwt, @PathVariable id: Long) = images.delete(id, jwt.uid)

    /**
     * Public, so image loaders can fetch it without a token. The URL carries the image key, which
     * changes on every upload, so the response can be cached for a long time.
     */
    @GetMapping("/api/v1/books/{id}/image", produces = [MediaType.IMAGE_JPEG_VALUE])
    fun get(@PathVariable id: Long): ResponseEntity<ByteArray> = ResponseEntity.ok()
        .cacheControl(CacheControl.maxAge(30, TimeUnit.DAYS).cachePublic())
        .body(images.load(id))
}
