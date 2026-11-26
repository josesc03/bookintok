package com.bookintok.app.ui.profile

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.bookintok.app.core.network.BookDto
import com.bookintok.app.core.network.MyProfileDto
import com.bookintok.app.core.network.RatingDto
import com.bookintok.app.data.BookRepository
import com.bookintok.app.data.UserRepository
import com.bookintok.app.ui.books.BookCard
import com.bookintok.app.ui.common.LoadingBox
import com.bookintok.app.ui.common.MessageBox
import com.bookintok.app.ui.common.toUserMessage
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Locale
import javax.inject.Inject

data class ProfileUiState(
    val profile: MyProfileDto? = null,
    val books: List<BookDto> = emptyList(),
    val ratings: List<RatingDto> = emptyList(),
    val loading: Boolean = true,
    val message: String? = null,
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val users: UserRepository,
    private val booksRepository: BookRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ProfileUiState())
    val state = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, message = null) }
            try {
                val profile = users.myProfile() ?: return@launch
                _state.update {
                    it.copy(
                        profile = profile,
                        books = booksRepository.booksOf(profile.uid),
                        ratings = users.ratings(profile.uid),
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(message = e.toUserMessage()) }
            } finally {
                _state.update { it.copy(loading = false) }
            }
        }
    }

    fun updateLocation(latitude: Double, longitude: Double) {
        viewModelScope.launch {
            try {
                users.updateLocation(latitude, longitude)
                _state.update { it.copy(message = "Ubicación actualizada: ya puedes buscar libros cerca") }
                load()
            } catch (e: Exception) {
                _state.update { it.copy(message = e.toUserMessage()) }
            }
        }
    }

    fun locationUnavailable() = _state.update { it.copy(message = "No se ha podido obtener tu ubicación") }

    fun signOut() {
        viewModelScope.launch { users.signOut() }
    }
}

@Composable
fun ProfileScreen(onBookClick: (Long) -> Unit, viewModel: ProfileViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val requestLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            scope.launch { fetchLocation(context, onResult = viewModel::updateLocation, onError = viewModel::locationUnavailable) }
        } else {
            viewModel.locationUnavailable()
        }
    }

    val profile = state.profile
    when {
        state.loading && profile == null -> LoadingBox()
        profile == null -> MessageBox(state.message ?: "No se ha podido cargar tu perfil", actionLabel = "Reintentar", onAction = viewModel::load)
        else -> LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text("@${profile.nickname}", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 16.dp))
                profile.displayName?.let { Text(it) }
                val rating = profile.rating
                Text(
                    if (rating.count == 0L) "Sin valoraciones todavía"
                    else "★ ${String.format(Locale.getDefault(), "%.1f", rating.average)} (${rating.count} valoraciones)",
                )
                state.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                OutlinedButton(
                    onClick = { requestLocation.launch(Manifest.permission.ACCESS_COARSE_LOCATION) },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) {
                    Text(if (profile.latitude == null) "Usar mi ubicación para buscar cerca" else "Actualizar mi ubicación")
                }
            }
            item { Text("Mis libros", style = MaterialTheme.typography.titleMedium) }
            if (state.books.isEmpty()) item { Text("Aún no has publicado ningún libro") }
            items(state.books, key = { "book-${it.id}" }) { book -> BookCard(book, onClick = { onBookClick(book.id) }) }
            item { Text("Valoraciones", style = MaterialTheme.typography.titleMedium) }
            items(state.ratings, key = { "rating-${it.id}" }) { rating ->
                Text("★ ${rating.score} · @${rating.raterNickname}${rating.comment?.let { ": $it" }.orEmpty()}")
            }
            item { TextButton(onClick = viewModel::signOut) { Text("Cerrar sesión") } }
        }
    }
}

/** Approximate location is enough: the search works in kilometres. */
@SuppressLint("MissingPermission") // only called after the permission is granted
private suspend fun fetchLocation(context: Context, onResult: (Double, Double) -> Unit, onError: () -> Unit) {
    val client = LocationServices.getFusedLocationProviderClient(context)
    try {
        val location = client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null).await()
        if (location != null) onResult(location.latitude, location.longitude) else onError()
    } catch (e: Exception) {
        if (e is kotlinx.coroutines.CancellationException) throw e
        onError()
    }
}
