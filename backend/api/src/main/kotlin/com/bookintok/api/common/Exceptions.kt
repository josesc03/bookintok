package com.bookintok.api.common

/** Base class for errors that map to a well-defined HTTP response. */
sealed class ApiException(message: String) : RuntimeException(message)

class NotFoundException(resource: String, id: Any) : ApiException("$resource $id not found")

class ForbiddenException(message: String) : ApiException(message)

/** The request is valid but conflicts with the current state (duplicate, wrong status...). */
class ConflictException(message: String) : ApiException(message)

/** The request breaks a business rule. */
class BusinessRuleException(message: String) : ApiException(message)
