package com.bookintok.app.ui.location

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bookintok.app.core.geo.PlaceNamer
import com.bookintok.app.core.geo.Province
import com.bookintok.app.core.geo.Region
import com.bookintok.app.core.geo.SpainProvinces
import com.bookintok.app.data.UserRepository
import com.bookintok.app.ui.common.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class LocationMode { CHOOSE, PROVINCE, MAP }

data class LocationUiState(
    val mode: LocationMode = LocationMode.CHOOSE,
    val regions: List<Region> = emptyList(),
    val region: Region? = null,
    val province: Province? = null,
    /** Point picked on the map. */
    val mapPoint: Pair<Double, Double>? = null,
    val saving: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false,
)

/**
 * Three ways to say where you are: the device location, a province, or a point on the map.
 * Whatever is chosen, the server keeps only an approximate position (about 1 km) and a place name.
 */
@HiltViewModel
class LocationViewModel @Inject constructor(
    private val users: UserRepository,
    private val provinces: SpainProvinces,
    private val placeNamer: PlaceNamer,
) : ViewModel() {
    private val _state = MutableStateFlow(LocationUiState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch { _state.update { it.copy(regions = provinces.regions()) } }
    }

    fun show(mode: LocationMode) = _state.update { it.copy(mode = mode, error = null) }

    fun selectRegion(region: Region) = _state.update {
        // Communities with a single province (Madrid, Murcia...) select it right away
        it.copy(region = region, province = region.provinces.singleOrNull(), error = null)
    }

    fun selectProvince(province: Province) = _state.update { it.copy(province = province, error = null) }

    fun pickOnMap(latitude: Double, longitude: Double) = _state.update { it.copy(mapPoint = latitude to longitude, error = null) }

    fun saveProvince() {
        val province = _state.value.province ?: return
        save(province.lat, province.lon) { province.name }
    }

    fun saveMapPoint() {
        val (lat, lon) = _state.value.mapPoint ?: return
        save(lat, lon) { placeNamer.nameOf(lat, lon) }
    }

    fun saveDeviceLocation(latitude: Double, longitude: Double) = save(latitude, longitude) { placeNamer.nameOf(latitude, longitude) }

    fun deviceLocationUnavailable() = _state.update {
        it.copy(error = "No hemos podido obtener tu ubicación. Prueba a elegir tu provincia o un punto en el mapa.")
    }

    private fun save(latitude: Double, longitude: Double, label: suspend () -> String) {
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try {
                users.updateLocation(latitude, longitude, label())
                _state.update { it.copy(saved = true) }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.toUserMessage()) }
            } finally {
                _state.update { it.copy(saving = false) }
            }
        }
    }
}
