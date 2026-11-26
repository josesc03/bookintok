package com.bookintok.app.core.geo

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Gives a point a human name to show other users ("Paterna"), so the app never needs to share
 * coordinates. Uses the system geocoder when it is available and falls back to the nearest province.
 */
@Singleton
class PlaceNamer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val provinces: SpainProvinces,
) {
    suspend fun nameOf(latitude: Double, longitude: Double): String =
        geocode(latitude, longitude)?.let { it.locality ?: it.subAdminArea }
            ?: provinces.nearest(latitude, longitude).name

    private suspend fun geocode(latitude: Double, longitude: Double): Address? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(context, Locale.forLanguageTag("es-ES"))
        return withTimeoutOrNull(4_000) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine { continuation ->
                    geocoder.getFromLocation(latitude, longitude, 1, object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) = continuation.resume(addresses.firstOrNull())
                        override fun onError(errorMessage: String?) = continuation.resume(null)
                    })
                }
            } else {
                withContext(Dispatchers.IO) {
                    @Suppress("DEPRECATION")
                    runCatching { geocoder.getFromLocation(latitude, longitude, 1)?.firstOrNull() }.getOrNull()
                }
            }
        }
    }
}
