package com.bookintok.app.ui.books

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.bookintok.app.core.network.BookCondition
import com.bookintok.app.core.network.BookRequest
import com.bookintok.app.core.network.CoverType
import com.bookintok.app.data.BookRepository
import com.bookintok.app.ui.common.label
import com.bookintok.app.ui.common.toUserMessage
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

data class BookEditorUiState(
    val form: BookForm = BookForm(),
    val isEdit: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
    val savedId: Long? = null,
)

@HiltViewModel
class BookEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val books: BookRepository,
) : ViewModel() {
    private val bookId = savedStateHandle.toRoute<BookEditorRoute>().id
    private val _state = MutableStateFlow(BookEditorUiState(isEdit = bookId != null))
    val state = _state.asStateFlow()

    init {
        if (bookId != null) viewModelScope.launch {
            try {
                val book = books.book(bookId)
                _state.update {
                    it.copy(form = BookForm(book.title, book.author, book.description.orEmpty(), book.mainCategory, book.language, book.coverType, book.condition))
                }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.toUserMessage()) }
            }
        }
    }

    fun update(transform: (BookForm) -> BookForm) = _state.update { it.copy(form = transform(it.form), error = null) }

    fun save() {
        val form = _state.value.form
        if (!form.isValid) {
            _state.update { it.copy(error = "Título, autor, categoría e idioma son obligatorios") }
            return
        }
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try {
                val saved = books.save(bookId, form.toRequest())
                _state.update { it.copy(savedId = saved.id) }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.toUserMessage()) }
            } finally {
                _state.update { it.copy(saving = false) }
            }
        }
    }
}

@Composable
fun BookEditorScreen(onSaved: (Long) -> Unit, viewModel: BookEditorViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.savedId) { state.savedId?.let(onSaved) }
    val form = state.form

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(if (state.isEdit) "Editar libro" else "Publicar un libro", style = MaterialTheme.typography.headlineSmall)
        Field("Título", form.title) { v -> viewModel.update { it.copy(title = v) } }
        Field("Autor", form.author) { v -> viewModel.update { it.copy(author = v) } }
        Field("Categoría", form.category) { v -> viewModel.update { it.copy(category = v) } }
        Field("Idioma", form.language) { v -> viewModel.update { it.copy(language = v) } }
        OutlinedTextField(
            value = form.description,
            onValueChange = { v -> viewModel.update { it.copy(description = v) } },
            label = { Text("Descripción (opcional)") },
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )
        Text("Estado", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BookCondition.entries.forEach { condition ->
                FilterChip(
                    selected = form.condition == condition,
                    onClick = { viewModel.update { it.copy(condition = condition) } },
                    label = { Text(condition.label) },
                )
            }
        }
        Text("Cubierta", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CoverType.entries.forEach { cover ->
                FilterChip(
                    selected = form.coverType == cover,
                    onClick = { viewModel.update { it.copy(coverType = cover) } },
                    label = { Text(cover.label) },
                )
            }
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = viewModel::save, enabled = !state.saving, modifier = Modifier.fillMaxWidth()) {
            Text(if (state.isEdit) "Guardar cambios" else "Publicar")
        }
    }
}

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}
