package com.bookintok.app.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.bookintok.app.core.network.BookDto
import com.bookintok.app.core.network.MyProfileDto
import com.bookintok.app.core.network.RatingDto
import com.bookintok.app.data.BookRepository
import com.bookintok.app.data.UserRepository
import com.bookintok.app.ui.books.BookTile
import com.bookintok.app.ui.common.LoadingBox
import com.bookintok.app.ui.common.toUserMessage
import com.bookintok.app.ui.components.Avatar
import com.bookintok.app.ui.components.BkCard
import com.bookintok.app.ui.components.EmptyState
import com.bookintok.app.ui.components.GhostButton
import com.bookintok.app.ui.components.SecondaryButton
import com.bookintok.app.ui.components.SectionTitle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

data class ProfileUiState(
    val profile: MyProfileDto? = null,
    val books: List<BookDto> = emptyList(),
    val ratings: List<RatingDto> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val users: UserRepository,
    private val booksRepository: BookRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ProfileUiState())
    val state = _state.asStateFlow()

    /** Called every time the screen is shown, so changes made elsewhere (location, books) appear. */
    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
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
                _state.update { it.copy(error = e.toUserMessage()) }
            } finally {
                _state.update { it.copy(loading = false) }
            }
        }
    }

    fun signOut() {
        viewModelScope.launch { users.signOut() }
    }
}

@Composable
fun ProfileScreen(onBookClick: (Long) -> Unit, onChangeLocation: () -> Unit, viewModel: ProfileViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        viewModel.load()
        onPauseOrDispose { }
    }

    val profile = state.profile
    when {
        state.loading && profile == null -> LoadingBox()
        profile == null -> EmptyState(
            Icons.Default.ErrorOutline,
            "No hemos podido cargar tu perfil",
            state.error ?: "Inténtalo de nuevo en un momento.",
            actionLabel = "Reintentar",
            onAction = viewModel::load,
        )
        else -> LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 156.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) { ProfileHeader(profile, onChangeLocation) }
            item(span = { GridItemSpan(maxLineSpan) }) { SectionTitle("Mis libros (${state.books.size})") }
            if (state.books.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(Icons.Default.AutoStories, "Aún no has publicado libros", "Publica alguno desde la pestaña Libros.")
                }
            }
            items(state.books, key = { "book-${it.id}" }) { book -> BookTile(book, onClick = { onBookClick(book.id) }, showOwner = false) }
            item(span = { GridItemSpan(maxLineSpan) }) { SectionTitle("Valoraciones") }
            if (state.ratings.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        "Cuando completes un intercambio, la otra persona podrá valorarte.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(state.ratings, key = { "rating-${it.id}" }, span = { GridItemSpan(maxLineSpan) }) { RatingCard(it) }
            item(span = { GridItemSpan(maxLineSpan) }) {
                GhostButton("Cerrar sesión", viewModel::signOut)
            }
        }
    }
}

@Composable
private fun ProfileHeader(profile: MyProfileDto, onChangeLocation: () -> Unit) {
    BkCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Avatar(profile.nickname, size = 64)
                Column(Modifier.weight(1f)) {
                    Text("@${profile.nickname}", style = MaterialTheme.typography.titleLarge)
                    profile.displayName?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    val rating = profile.rating
                    if (rating.count > 0 && rating.average != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Stars(rating.average)
                            Text(
                                " ${String.format(Locale.forLanguageTag("es"), "%.1f", rating.average)} · ${rating.count} valoraciones",
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    } else {
                        Text("Sin valoraciones todavía", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Place, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(
                    profile.locationLabel ?: "Ubicación sin nombre",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                SecondaryButton("Cambiar", onChangeLocation)
            }
        }
    }
}

@Composable
private fun RatingCard(rating: RatingDto) {
    BkCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stars(rating.score.toDouble())
            Text("@${rating.raterNickname}", style = MaterialTheme.typography.titleSmall)
        }
        rating.comment?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
    }
}

@Composable
private fun Stars(value: Double) {
    Row {
        (1..5).forEach { i ->
            Icon(
                if (i <= Math.round(value)) Icons.Default.Star else Icons.Default.StarBorder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
