package com.bookintok.app.core.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Checks the bundled asset itself, so a broken data file fails the build. */
class SpainProvincesTest {

    private val regions = SpainProvinces.parse(File("src/main/assets/spain_provinces.json").readText())
    private val provinces = regions.flatMap { it.provinces }

    @Test
    fun `asset has every autonomous community and province`() {
        assertEquals(19, regions.size)
        assertEquals(52, provinces.size)
        assertEquals(listOf("Alicante", "Castellón", "Valencia"), regions.first { it.name == "Comunitat Valenciana" }.provinces.map { it.name })
    }

    @Test
    fun `every province is located inside Spain`() {
        provinces.forEach {
            assertTrue("${it.name} latitude", it.lat in 27.0..44.0)
            assertTrue("${it.name} longitude", it.lon in -18.5..4.5)
        }
    }

    @Test
    fun `nearest province is found by distance`() {
        assertEquals("Valencia", SpainProvinces.nearest(provinces, 39.54, -0.44).name) // Paterna
        assertEquals("Madrid", SpainProvinces.nearest(provinces, 40.45, -3.69).name)
        assertEquals("Santa Cruz de Tenerife", SpainProvinces.nearest(provinces, 28.29, -16.63).name) // Teide
    }

    @Test
    fun `distance between Valencia and Madrid is about 300 km`() {
        assertEquals(302.0, SpainProvinces.distanceKm(39.47, -0.3764, 40.4169, -3.7033), 3.0)
    }
}
