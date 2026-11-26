package com.bookintok.app.ui.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bookintok.app.ui.components.BkCard
import com.bookintok.app.ui.components.PrimaryButton
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker

/**
 * Where the user is. Shown after sign-up and from the profile.
 * [onBack] is null when the step cannot be skipped (sign-up) or when a top bar already offers it.
 */
@Composable
fun LocationScreen(
    onDone: () -> Unit,
    onBack: (() -> Unit)? = null,
    applyInsets: Boolean = true,
    viewModel: LocationViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) { if (state.saved) onDone() }
    BackHandler(enabled = state.mode != LocationMode.CHOOSE) { viewModel.show(LocationMode.CHOOSE) }

    val insets = if (applyInsets) Modifier.systemBarsPadding() else Modifier
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).then(insets).imePadding()) {
        // Inside the app the top bar already has a back arrow (and system back returns to the options)
        if (applyInsets) {
            Row(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                val back = if (state.mode != LocationMode.CHOOSE) ({ viewModel.show(LocationMode.CHOOSE) }) else onBack
                if (back != null) {
                    IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás") }
                }
            }
        }
        when (state.mode) {
            LocationMode.CHOOSE -> ChooseMode(state, viewModel)
            LocationMode.PROVINCE -> ProvincePicker(state, viewModel)
            LocationMode.MAP -> MapPicker(state, viewModel)
        }
    }
}

@Composable
private fun ChooseMode(state: LocationUiState, viewModel: LocationViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var locating by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) {
            viewModel.deviceLocationUnavailable()
            return@rememberLauncherForActivityResult
        }
        locating = true
        scope.launch {
            val point = currentLocation(context)
            locating = false
            if (point != null) viewModel.saveDeviceLocation(point.first, point.second) else viewModel.deviceLocationUnavailable()
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("¿Dónde estás?", style = MaterialTheme.typography.headlineLarge)
        Text(
            "Así te enseñamos los libros que tienes cerca. Elige como prefieras:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OptionCard(
            icon = Icons.Default.MyLocation,
            title = if (locating || state.saving) "Buscando tu ubicación…" else "Usar mi ubicación",
            description = "La forma más rápida. Solo usamos una ubicación aproximada.",
            enabled = !locating && !state.saving,
            onClick = { permission.launch(Manifest.permission.ACCESS_COARSE_LOCATION) },
        )
        OptionCard(
            icon = Icons.Default.Place,
            title = "Elegir mi provincia",
            description = "Sin permisos: eliges tu comunidad y tu provincia.",
            onClick = { viewModel.show(LocationMode.PROVINCE) },
        )
        OptionCard(
            icon = Icons.Default.Map,
            title = "Marcar un punto en el mapa",
            description = "Por ejemplo, el barrio donde quieres quedar para los intercambios.",
            onClick = { viewModel.show(LocationMode.MAP) },
        )
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        PrivacyNote()
    }
}

@Composable
private fun OptionCard(icon: ImageVector, title: String, description: String, onClick: () -> Unit, enabled: Boolean = true) {
    BkCard(onClick = if (enabled) onClick else null, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun PrivacyNote() {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.medium) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "Tu ubicación exacta nunca se guarda ni se comparte. Guardamos un punto aproximado (a ~1 km) " +
                    "y los demás solo ven tu municipio o provincia y una distancia redondeada.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProvincePicker(state: LocationUiState, viewModel: LocationViewModel) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("Elige tu provincia", style = MaterialTheme.typography.headlineLarge)
        Dropdown(
            label = "Comunidad autónoma",
            placeholder = "Selecciona tu comunidad",
            selected = state.region?.name,
            options = state.regions.map { it.name },
            onSelect = { name -> state.regions.first { it.name == name }.let(viewModel::selectRegion) },
        )
        Dropdown(
            label = "Provincia",
            placeholder = if (state.region == null) "Primero elige la comunidad" else "Selecciona tu provincia",
            selected = state.province?.name,
            options = state.region?.provinces?.map { it.name }.orEmpty(),
            enabled = state.region != null,
            onSelect = { name -> state.region?.provinces?.first { it.name == name }?.let(viewModel::selectProvince) },
        )
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        Box(Modifier.weight(1f))
        PrimaryButton(
            "Guardar ubicación",
            onClick = viewModel::saveProvince,
            enabled = state.province != null,
            loading = state.saving,
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Dropdown(
    label: String,
    placeholder: String,
    selected: String?,
    options: List<String>,
    onSelect: (String) -> Unit,
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall)
        ExposedDropdownMenuBox(expanded = expanded && enabled, onExpandedChange = { if (enabled) expanded = it }) {
            OutlinedTextField(
                value = selected.orEmpty(),
                onValueChange = {},
                readOnly = true,
                enabled = enabled,
                placeholder = { Text(placeholder) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                shape = MaterialTheme.shapes.small,
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                    focusedBorderColor = MaterialTheme.colorScheme.secondary,
                ),
                modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled),
            )
            DropdownMenu(
                expanded = expanded && enabled,
                onDismissRequest = { expanded = false },
                modifier = Modifier.exposedDropdownSize(),
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            onSelect(option)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun MapPicker(state: LocationUiState, viewModel: LocationViewModel) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Marca un punto", style = MaterialTheme.typography.headlineLarge)
            Text(
                "Toca el mapa donde te viene bien quedar. No hace falta que sea tu casa.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth().padding(16.dp).clip(MaterialTheme.shapes.large)) {
            OsmMap(context, state.mapPoint, onTap = viewModel::pickOnMap)
        }
        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 24.dp))
        }
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton(
                if (state.mapPoint == null) "Toca el mapa para elegir" else "Guardar este punto",
                onClick = viewModel::saveMapPoint,
                enabled = state.mapPoint != null,
                loading = state.saving,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Mapa © colaboradores de OpenStreetMap",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

/** OpenStreetMap through osmdroid: free, no API key. Starts over Spain. */
@Composable
private fun OsmMap(context: Context, point: Pair<Double, Double>?, onTap: (Double, Double) -> Unit) {
    val map = remember {
        Configuration.getInstance().userAgentValue = context.packageName
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(6.2)
            controller.setCenter(GeoPoint(39.9, -3.7))
            overlays.add(
                MapEventsOverlay(object : MapEventsReceiver {
                    override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                        onTap(p.latitude, p.longitude)
                        return true
                    }

                    override fun longPressHelper(p: GeoPoint) = false
                }),
            )
        }
    }
    val marker = remember { Marker(map).apply { setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM) } }
    DisposableEffect(Unit) {
        map.onResume()
        onDispose { map.onPause(); map.onDetach() }
    }
    AndroidView(factory = { map }, modifier = Modifier.fillMaxSize()) { view ->
        view.overlays.remove(marker)
        if (point != null) {
            marker.position = GeoPoint(point.first, point.second)
            view.overlays.add(marker)
        }
        view.invalidate()
    }
}

@SuppressLint("MissingPermission") // only called after the permission is granted
private suspend fun currentLocation(context: Context): Pair<Double, Double>? = try {
    LocationServices.getFusedLocationProviderClient(context)
        .getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
        .await()
        ?.let { it.latitude to it.longitude }
} catch (e: Exception) {
    if (e is kotlinx.coroutines.CancellationException) throw e
    null
}

