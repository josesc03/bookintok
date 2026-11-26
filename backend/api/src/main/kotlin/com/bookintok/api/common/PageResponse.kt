package com.bookintok.api.common

import org.springframework.data.domain.Page

/** Stable JSON shape for paginated results (Spring's Page is not meant to be serialized). */
data class PageResponse<T : Any>(
    val content: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
) {
    companion object {
        fun <E : Any, T : Any> of(page: Page<E>, mapper: (E) -> T) = PageResponse(
            content = page.content.map(mapper),
            page = page.number,
            size = page.size,
            totalElements = page.totalElements,
            totalPages = page.totalPages,
        )
    }
}
