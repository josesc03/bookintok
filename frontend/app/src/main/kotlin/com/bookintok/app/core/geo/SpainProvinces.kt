package com.bookintok.app.core.geo

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** A province, located at its capital (INE names, coordinates from Wikidata). */
@Serializable
data class Province(val name: String, val capital: String, val lat: Double, val lon: Double)

/** An autonomous community and its provinces. */
@Serializable
data class Region(val name: String, val provinces: List<Province>)

/** The 52 Spanish provinces grouped by autonomous community, bundled in `assets/spain_provinces.json`. */
@Singleton
class SpainProvinces @Inject constructor(@ApplicationContext private val context: Context) {

    private var cached: List<Region>? = null

    suspend fun regions(): List<Region> = cached ?: withContext(Dispatchers.IO) {
        context.assets.open("spain_provinces.json").bufferedReader().use { parse(it.readText()) }
    }.also { cached = it }

    suspend fun nearest(latitude: Double, longitude: Double): Province =
        nearest(regions().flatMap { it.provinces }, latitude, longitude)

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(text: String): List<Region> = json.decodeFromString(text)

        fun nearest(provinces: List<Province>, latitude: Double, longitude: Double): Province =
            provinces.minBy { distanceKm(latitude, longitude, it.lat, it.lon) }

        /** Haversine distance in kilometres. */
        fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val h = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
            return 2 * 6371.0 * asin(sqrt(h))
        }
    }
}
