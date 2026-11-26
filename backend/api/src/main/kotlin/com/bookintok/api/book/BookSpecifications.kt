package com.bookintok.api.book

import com.bookintok.api.common.GeoDistance
import com.bookintok.api.common.GeoPoint
import com.bookintok.api.user.User
import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.Expression
import jakarta.persistence.criteria.Path
import org.springframework.data.jpa.domain.Specification

/** Composable query filters for the book search. */
object BookSpecifications {

    fun isAvailable() = Specification<Book> { root, _, cb ->
        cb.equal(root.get<BookStatus>("status"), BookStatus.AVAILABLE)
    }

    fun notOwnedBy(uid: String) = Specification<Book> { root, _, cb ->
        cb.notEqual(root.get<User>("owner").get<String>("uid"), uid)
    }

    fun matchesText(text: String) = Specification<Book> { root, _, cb ->
        val pattern = "%${escapeLike(text.lowercase())}%"
        cb.or(
            cb.like(cb.lower(root.get("title")), pattern, '\\'),
            cb.like(cb.lower(root.get("author")), pattern, '\\'),
        )
    }

    fun hasLanguage(language: String) = Specification<Book> { root, _, cb ->
        cb.equal(cb.lower(root.get("language")), language.lowercase())
    }

    fun hasCategory(category: String) = Specification<Book> { root, _, cb ->
        val value = category.lowercase()
        cb.or(
            cb.equal(cb.lower(root.get("mainCategory")), value),
            cb.equal(cb.lower(root.get("secondaryCategory")), value),
        )
    }

    fun hasCoverType(coverType: CoverType) = Specification<Book> { root, _, cb ->
        cb.equal(root.get<CoverType>("coverType"), coverType)
    }

    fun hasCondition(condition: BookCondition) = Specification<Book> { root, _, cb ->
        cb.equal(root.get<BookCondition>("condition"), condition)
    }

    /**
     * Books whose owner is within [radiusKm] of [center].
     * A bounding box narrows the rows first; the Haversine formula then gives the exact distance.
     */
    fun withinDistance(center: GeoPoint, radiusKm: Double) = Specification<Book> { root, _, cb ->
        val owner = root.get<User>("owner")
        val lat = owner.get<Double>("latitude")
        val lon = owner.get<Double>("longitude")
        val box = GeoDistance.boundingBox(center, radiusKm)

        cb.and(
            cb.between(lat, box.minLat, box.maxLat),
            cb.between(lon, box.minLon, box.maxLon),
            cb.le(haversineKm(cb, center, lat, lon), radiusKm),
        )
    }

    private fun haversineKm(
        cb: CriteriaBuilder,
        center: GeoPoint,
        lat: Path<Double>,
        lon: Path<Double>,
    ): Expression<Double> {
        fun fn(name: String, arg: Expression<*>): Expression<Double> = cb.function(name, Double::class.java, arg)

        val centerLatRad = Math.toRadians(center.latitude)
        val latRad = fn("radians", lat)
        val dLat = fn("radians", cb.diff(lat, center.latitude))
        val dLon = fn("radians", cb.diff(lon, center.longitude))

        val sinHalfDLat = fn("sin", cb.quot(dLat, 2.0))
        val sinHalfDLon = fn("sin", cb.quot(dLon, 2.0))
        val h = cb.sum(
            cb.prod(sinHalfDLat, sinHalfDLat),
            cb.prod(
                cb.prod(fn("cos", latRad), Math.cos(centerLatRad)),
                cb.prod(sinHalfDLon, sinHalfDLon),
            ),
        )
        @Suppress("UNCHECKED_CAST")
        return cb.prod(fn("asin", fn("sqrt", h)), 2 * GeoDistance.EARTH_RADIUS_KM) as Expression<Double>
    }

    private fun escapeLike(value: String) = value
        .replace("\\", "\\\\")
        .replace("%", "\\%")
        .replace("_", "\\_")
}
