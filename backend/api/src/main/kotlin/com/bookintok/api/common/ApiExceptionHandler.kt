package com.bookintok.api.common

import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler

/** Maps errors to RFC 9457 Problem Details responses. */
@RestControllerAdvice
class ApiExceptionHandler : ResponseEntityExceptionHandler() {

    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(ApiException::class)
    fun handleApiException(ex: ApiException): ProblemDetail {
        val status = when (ex) {
            is NotFoundException -> HttpStatus.NOT_FOUND
            is ForbiddenException -> HttpStatus.FORBIDDEN
            is ConflictException -> HttpStatus.CONFLICT
            is BusinessRuleException -> HttpStatus.UNPROCESSABLE_CONTENT
        }
        return ProblemDetail.forStatusAndDetail(status, ex.message)
    }

    @ExceptionHandler(DataIntegrityViolationException::class, ObjectOptimisticLockingFailureException::class)
    fun handleConcurrentWrite(ex: RuntimeException): ProblemDetail {
        log.info("Conflicting write rejected: {}", ex.message)
        return ProblemDetail.forStatusAndDetail(
            HttpStatus.CONFLICT,
            "The resource was modified or already exists, please retry",
        )
    }

    override fun handleMethodArgumentNotValid(
        ex: MethodArgumentNotValidException,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any>? {
        val problem = ProblemDetail.forStatusAndDetail(status, "Request validation failed")
        problem.setProperty(
            "errors",
            ex.bindingResult.fieldErrors.associate { it.field to (it.defaultMessage ?: "invalid") },
        )
        return ResponseEntity.status(status).body(problem)
    }
}
