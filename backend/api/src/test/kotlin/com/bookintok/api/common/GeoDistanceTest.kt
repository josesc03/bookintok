package com.bookintok.api.common

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GeoDistanceTest {

    private val valencia = GeoPoint(39.4699, -0.3763)
    private val madrid = GeoPoint(40.4168, -3.7038)

    @Test
    fun `distance between Valencia and Madrid is about 302 km`() {
        assertEquals(302.0, GeoDistance.haversineKm(valencia, madrid), 2.0)
    }

    @Test
    fun `distance to the same point is zero`() {
        assertEquals(0.0, GeoDistance.haversineKm(valencia, valencia), 1e-9)
    }

    @Test
    fun `bounding box contains every point within the radius`() {
        val box = GeoDistance.boundingBox(valencia, 10.0)
        val nearby = GeoPoint(valencia.latitude + 0.08, valencia.longitude - 0.1) // ~13 km diagonal, edges ~9 km

        assertTrue(GeoDistance.haversineKm(valencia, GeoPoint(valencia.latitude + 0.089, valencia.longitude)) < 10.0)
        assertTrue(box.minLat < valencia.latitude && valencia.latitude < box.maxLat)
        assertTrue(box.minLon < nearby.longitude && nearby.longitude < box.maxLon)
        assertTrue(madrid.longitude < box.minLon)
    }

    @Test
    fun `bounding box is clamped near the poles`() {
        val box = GeoDistance.boundingBox(GeoPoint(89.99, 0.0), 50.0)
        assertEquals(90.0, box.maxLat)
        assertEquals(-180.0, box.minLon)
        assertEquals(180.0, box.maxLon)
    }
}
