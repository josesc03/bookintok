package com.bookintok.app.ui.books

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import coil3.compose.AsyncImage
import com.bookintok.app.core.image.ImageCompressor
import com.bookintok.app.core.network.BookCondition
import com.bookintok.app.core.network.BookRequest
import com.bookintok.app.core.network.CoverType
import com.bookintok.app.data.BookRepository
import com.bookintok.app.ui.common.label
import com.bookintok.app.ui.common.toUserMessage
import com.bookintok.app.ui.components.FormField
import com.bookintok.app.ui.components.SelectField
import com.bookintok.app.ui.components.BottomActionBar
import com.bookintok.app.core.catalog.BookCatalog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.icons.filled.Close
import com.bookintok.app.ui.components.GhostButton
import com.bookintok.app.ui.components.PrimaryButton
import com.bookintok.app.ui.components.apiImageUrl
import com.bookintok.app.ui.navigation.BookEditorRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BookForm(
    val title: String = "",
    val author: String = "",
    val description: String = "",
    val category: String = "",
    val language: String = "Español",
    val coverType: CoverType = CoverType.PAPERBACK,
    val condition: BookCondition = BookCondition.USED,
) {
    val isValid get() = title.isNotBlank() && author.isNotBlank() && category.isNotBlank() && language.isNotBlank()

    fun toRequest() = BookRequest(
        title = title.trim(),
        author = author.trim(),
        description = description.trim().ifEmpty { null },
        mainCategory = category.trim(),
        language = language.trim(),
        coverType = coverType,
        condition = condition,
    )
}

/** The photo as the editor sees it: kept as is, newly picked, or removed. */
sealed interface PhotoState {
    data object None : PhotoState

    data class Existing(val url: String) : PhotoState

    data class Picked(val uri: Uri) : PhotoState
}

data class BookEditorUiState(
    val form: BookForm = BookForm(),
    val photo: PhotoState = PhotoState.None,
    val hadPhoto: Boolean = false,
    val isEdit: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
    val savedId: Long? = null,
)

@HiltViewModel
class BookEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val books: BookRepository,
    private val compressor: ImageCompressor,
) : ViewModel() {
    private val bookId = savedStateHandle.toRoute<BookEditorRoute>().id
    private val _state = MutableStateFlow(BookEditorUiState(isEdit = bookId != null))
    val state = _state.asStateFlow()

    init {
        if (bookId != null) viewModelScope.launch {
            try {
                val book = books.book(bookId)
                _state.update {
                    it.copy(
                        form = BookForm(book.title, book.author, book.description.orEmpty(), book.mainCategory, book.language, book.coverType, book.condition),
                        photo = book.imageUrl?.let(PhotoState::Existing) ?: PhotoState.None,
                        hadPhoto = book.imageUrl != null,
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.toUserMessage()) }
            }
        }
    }

    fun update(transform: (BookForm) -> BookForm) = _state.update { it.copy(form = transform(it.form), error = null) }

    fun pickPhoto(uri: Uri) = _state.update { it.copy(photo = PhotoState.Picked(uri)) }

    fun removePhoto() = _state.update { it.copy(photo = PhotoState.None) }

    /** Saves the book first and then the photo, so a failed upload never loses the book data. */
    fun save() {
        val current = _state.value
        if (!current.form.isValid) {
            _state.update { it.copy(error = "Título, autor, categoría e idioma son obligatorios") }
            return
        }
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try {
                val saved = books.save(bookId, current.form.toRequest())
                when (val photo = current.photo) {
                    is PhotoState.Picked -> books.uploadImage(saved.id, compressor.compress(photo.uri))
                    PhotoState.None -> if (current.hadPhoto) books.deleteImage(saved.id)
                    is PhotoState.Existing -> Unit
                }
                _state.update { it.copy(savedId = saved.id) }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.toUserMessage()) }
            } finally {
                _state.update { it.copy(saving = false) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun BookEditorScreen(onClose: () -> Unit, onSaved: (Long) -> Unit, viewModel: BookEditorViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.savedId) { state.savedId?.let(onSaved) }
    val form = state.form
    // System photo picker: no storage permission needed
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(viewModel::pickPhoto) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(if (state.isEdit) "Editar libro" else "Publicar libro") },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "Cerrar") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            BottomActionBar {
                PrimaryButton(
                    if (state.isEdit) "Guardar cambios" else "Publicar",
                    onClick = viewModel::save,
                    loading = state.saving,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    ) { padding ->
    Column(
        Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            PhotoSlot(state.photo, onPick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) })
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Foto de la portada", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Una foto real del libro ayuda a que te lo pidan. Quitamos los datos de ubicación de la imagen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (state.photo != PhotoState.None) GhostButton("Quitar foto", viewModel::removePhoto)
            }
        }

        FormField("Título", form.title, { v -> viewModel.update { it.copy(title = v) } }, placeholder = "Dune")
        FormField("Autor", form.author, { v -> viewModel.update { it.copy(author = v) } }, placeholder = "Frank Herbert")
        SelectField(
            label = "Categoría",
            value = form.category,
            options = BookCatalog.CATEGORIES,
            onValueChange = { v -> viewModel.update { it.copy(category = v) } },
            placeholder = "Elige una categoría",
            otherOption = BookCatalog.OTHER,
        )
        SelectField(
            label = "Idioma",
            value = form.language,
            options = BookCatalog.LANGUAGES,
            onValueChange = { v -> viewModel.update { it.copy(language = v) } },
        )
        FormField(
            "Descripción (opcional)",
            form.description,
            { v -> viewModel.update { it.copy(description = v) } },
            placeholder = "Edición, estado, si tiene anotaciones…",
            singleLine = false,
            minLines = 3,
        )

        ChoiceGroup("Estado", BookCondition.entries, form.condition, { it.label }) { c -> viewModel.update { it.copy(condition = c) } }
        ChoiceGroup("Cubierta", CoverType.entries, form.coverType, { it.label }) { c -> viewModel.update { it.copy(coverType = c) } }

        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }
    }
}

@Composable
private fun PhotoSlot(photo: PhotoState, onPick: () -> Unit) {
    val model: Any? = when (photo) {
        is PhotoState.Picked -> photo.uri
        is PhotoState.Existing -> apiImageUrl(photo.url)
        PhotoState.None -> null
    }
    Box(
        Modifier
            .width(110.dp)
            .aspectRatio(3f / 4f)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f), MaterialTheme.shapes.medium)
            .clickable(onClick = onPick),
        contentAlignment = Alignment.Center,
    ) {
        if (model != null) {
            AsyncImage(model, contentDescription = "Foto del libro", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.AddAPhoto, contentDescription = null, modifier = Modifier.size(28.dp))
                Text("Añadir foto", style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChoiceGroup(title: String, options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                FilterChip(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    label = { Text(label(option)) },
                    shape = MaterialTheme.shapes.small,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                )
            }
        }
    }
}
