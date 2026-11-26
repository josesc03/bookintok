package com.bookintok.api.common

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class GeoPoint(val latitude: Double, val longitude: Double)

/** A latitude/longitude rectangle that contains every point within a radius. */
data class BoundingBox(val minLat: Double, val maxLat: Double, val minLon: Double, val maxLon: Double)

object GeoDistance {
    const val EARTH_RADIUS_KM = 6371.0

    /** Great-circle distance in kilometres (Haversine formula). */
    fun haversineKm(a: GeoPoint, b: GeoPoint): Double {
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val h = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(a.latitude)) * cos(Math.toRadians(b.latitude)) * sin(dLon / 2).pow(2)
        return 2 * EARTH_RADIUS_KM * asin(sqrt(h))
    }

    /**
     * Rectangle around [center] that contains the circle of [radiusKm].
     * Used as a cheap, index-friendly pre-filter before the exact distance check.
     */
    fun boundingBox(center: GeoPoint, radiusKm: Double): BoundingBox {
        val latDelta = Math.toDegrees(radiusKm / EARTH_RADIUS_KM)
        val cosLat = cos(Math.toRadians(center.latitude))
        val lonDelta = if (cosLat < 1e-6) 180.0 else Math.toDegrees(radiusKm / (EARTH_RADIUS_KM * cosLat))
        return BoundingBox(
            minLat = (center.latitude - latDelta).coerceAtLeast(-90.0),
            maxLat = (center.latitude + latDelta).coerceAtMost(90.0),
            minLon = (center.longitude - lonDelta).coerceAtLeast(-180.0),
            maxLon = (center.longitude + lonDelta).coerceAtMost(180.0),
        )
    }
}
