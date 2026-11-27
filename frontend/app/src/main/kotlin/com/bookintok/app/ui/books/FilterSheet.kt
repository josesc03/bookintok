package com.bookintok.app.ui.books

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bookintok.app.core.catalog.BookCatalog
import com.bookintok.app.core.network.BookCondition
import com.bookintok.app.core.network.BookSearch
import com.bookintok.app.core.network.CoverType
import com.bookintok.app.ui.common.label
import com.bookintok.app.ui.components.GhostButton
import com.bookintok.app.ui.components.PrimaryButton
import com.bookintok.app.ui.components.SelectField
import com.bookintok.app.ui.theme.BookintokColors

/**
 * Search filters in a bottom sheet. Changes are kept as a draft and only applied with
 * "Ver resultados", so the list does not reload on every tap.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FilterSheet(current: BookSearch, onApply: (BookSearch) -> Unit, onDismiss: () -> Unit) {
    var draft by remember { mutableStateOf(current) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Filtros", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                GhostButton("Restablecer", onClick = {
                    draft = BookSearch(query = current.query, maxDistanceKm = DistanceSteps.DEFAULT_KM)
                })
            }
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                DistanceFilter(draft.maxDistanceKm ?: DistanceSteps.DEFAULT_KM) { draft = draft.copy(maxDistanceKm = it) }

                ChoiceSection("Idioma", BookCatalog.LANGUAGES, draft.languages, { it }) { draft = draft.copy(languages = it) }

                SelectField(
                    label = "Categoría",
                    value = draft.category.orEmpty(),
                    options = BookCatalog.CATEGORIES,
                    onValueChange = { draft = draft.copy(category = it.ifBlank { null }) },
                    placeholder = "Cualquier categoría",
                    otherOption = BookCatalog.OTHER,
                    allowClear = true,
                )

                ChoiceSection("Estado", BookCondition.entries, draft.conditions, { it.label }) { draft = draft.copy(conditions = it) }

                ChoiceSection("Cubierta", CoverType.entries, draft.coverTypes, { it.label }) { draft = draft.copy(coverTypes = it) }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            PrimaryButton(
                "Ver resultados",
                onClick = { onApply(draft) },
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
            )
        }
    }
}

@Composable
private fun DistanceFilter(km: Int, onChange: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Distancia", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text("Hasta $km km", style = MaterialTheme.typography.titleMedium, color = BookintokColors.GreenDeep)
        }
        val steps = DistanceSteps.values
        Slider(
            value = DistanceSteps.indexOf(km).toFloat(),
            onValueChange = { onChange(steps[it.toInt().coerceIn(steps.indices)]) },
            valueRange = 0f..steps.lastIndex.toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = BookintokColors.GreenDeep,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            ),
        )
        Row {
            Text("1 km", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text("1000 km", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Multi-select chips: any of the selected values matches. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChoiceSection(title: String, options: List<T>, selected: Set<T>, label: (T) -> String, onChange: (Set<T>) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (selected.isNotEmpty()) {
                Text("${selected.size} seleccionado${if (selected.size > 1) "s" else ""}", style = MaterialTheme.typography.labelMedium)
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                val isSelected = option in selected
                FilterChip(
                    selected = isSelected,
                    onClick = { onChange(if (isSelected) selected - option else selected + option) },
                    label = { Text(label(option)) },
                    leadingIcon = if (isSelected) ({ Icon(Icons.Default.Check, contentDescription = null) }) else null,
                    shape = MaterialTheme.shapes.small,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                )
            }
        }
    }
}
