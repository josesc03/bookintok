package com.bookintok.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.StarHalf
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.bookintok.app.BuildConfig
import com.bookintok.app.ui.theme.BookintokColors

// Mobile-first building blocks. They follow the Hukly design (type, radii, palette) adapted to a
// phone: full-width controls, flat lists, and colour used for fills rather than for text.

/** Ink at 20 %, used for hairline borders and dividers. */
@Composable
fun subtleBorder(width: Int = 1) = BorderStroke(width.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f))

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        shape = MaterialTheme.shapes.small,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
        modifier = modifier.heightIn(min = 52.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
        } else {
            ButtonContent(text, icon)
        }
    }
}

/** Neutral filled button for secondary actions. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.small,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
        modifier = modifier.heightIn(min = 52.dp),
    ) { ButtonContent(text, icon) }
}

/** Outlined button in ink, for actions that should not compete with the primary one. */
@Composable
fun OutlineButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, danger: Boolean = false) {
    val color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    OutlinedButton(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, color.copy(alpha = 0.4f)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = color),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
        modifier = modifier.heightIn(min = 52.dp),
    ) { ButtonContent(text, icon) }
}

/** Text-only button in ink. */
@Composable
fun GhostButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, danger: Boolean = false) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.small,
        colors = ButtonDefaults.textButtonColors(
            contentColor = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        ),
        modifier = modifier,
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

@Composable
private fun RowScope.ButtonContent(text: String, icon: ImageVector?) {
    if (icon != null) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Box(Modifier.size(8.dp))
    }
    Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/** Text field colours shared by every input: soft fill, hairline border, ink cursor. */
@Composable
fun bookintokFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f),
    focusedBorderColor = BookintokColors.GreenDeep,
    cursorColor = MaterialTheme.colorScheme.onSurface,
    focusedLabelColor = BookintokColors.GreenDeep,
)

@Composable
fun FormField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    error: String? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (label.isNotEmpty()) Text(label, style = MaterialTheme.typography.titleSmall)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = placeholder?.let { { Text(it) } },
            singleLine = singleLine,
            minLines = minLines,
            isError = error != null,
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            visualTransformation = visualTransformation,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            shape = MaterialTheme.shapes.small,
            textStyle = MaterialTheme.typography.bodyMedium,
            colors = bookintokFieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
        AnimatedVisibility(error != null, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Text(error.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

/**
 * Drop-down selector. When [otherOption] is chosen, a text field appears so the user can type a
 * value that is not on the list ([value] then holds the typed text).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectField(
    label: String,
    value: String,
    options: List<String>,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Selecciona",
    otherOption: String? = null,
    allowClear: Boolean = false,
) {
    var expanded by remember { mutableStateOf(false) }
    val isOther = otherOption != null && value.isNotEmpty() && value !in options
    var typingOther by remember(isOther) { mutableStateOf(isOther) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall)
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = if (typingOther) otherOption.orEmpty() else value,
                onValueChange = {},
                readOnly = true,
                placeholder = { Text(placeholder) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                shape = MaterialTheme.shapes.small,
                textStyle = MaterialTheme.typography.bodyMedium,
                colors = bookintokFieldColors(),
                modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            )
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.exposedDropdownSize()) {
                if (allowClear) {
                    DropdownMenuItem(text = { Text("Cualquiera") }, onClick = {
                        typingOther = false
                        onValueChange("")
                        expanded = false
                    })
                }
                (options + listOfNotNull(otherOption)).forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            expanded = false
                            if (option == otherOption) {
                                typingOther = true
                                onValueChange("")
                            } else {
                                typingOther = false
                                onValueChange(option)
                            }
                        },
                    )
                }
            }
        }
        AnimatedVisibility(typingOther, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = { Text("Escribe la categoría") },
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                textStyle = MaterialTheme.typography.bodyMedium,
                colors = bookintokFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Card for content that is a unit on its own (used sparingly on mobile). */
@Composable
fun BkCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)
    val inner: @Composable ColumnScope.() -> Unit = { Column(Modifier.padding(contentPadding), content = content) }
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier, shape = MaterialTheme.shapes.large, colors = colors, border = subtleBorder(), content = inner)
    } else {
        Card(modifier = modifier, shape = MaterialTheme.shapes.large, colors = colors, border = subtleBorder(), content = inner)
    }
}

enum class BadgeStyle { Neutral, Brand, Strong }

/**
 * Small label. Always has a border, so it stays readable over photos or backgrounds of a similar
 * colour. [BadgeStyle.Strong] (ink background) is for states that must stand out, like "Intercambiado".
 */
@Composable
fun Badge(text: String, modifier: Modifier = Modifier, style: BadgeStyle = BadgeStyle.Neutral) {
    val (background, content) = when (style) {
        BadgeStyle.Neutral -> MaterialTheme.colorScheme.surfaceContainerLowest to MaterialTheme.colorScheme.onSurface
        BadgeStyle.Brand -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
        BadgeStyle.Strong -> MaterialTheme.colorScheme.onSurface to MaterialTheme.colorScheme.surface
    }
    Surface(
        color = background,
        contentColor = content,
        shape = MaterialTheme.shapes.extraSmall,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = if (style == BadgeStyle.Strong) 0f else 0.28f)),
        modifier = modifier,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            maxLines = 1,
        )
    }
}

/** Turns an API-relative image path ("/api/v1/books/1/image?v=...") into a full URL. */
fun apiImageUrl(path: String?): String? = path?.let { if (it.startsWith("http")) it else BuildConfig.API_BASE_URL + it }

/**
 * Book cover: the photo when there is one, otherwise a typographic cover made from the title
 * (or its initial on thumbnails). Pass [sharedKey] to animate it between screens.
 */
@Composable
fun BookCover(title: String, imageUrl: String?, modifier: Modifier = Modifier, sharedKey: Any? = null) {
    BoxWithConstraints(
        modifier
            .then(if (sharedKey != null) Modifier.sharedElement(sharedKey) else Modifier)
            .clip(MaterialTheme.shapes.medium)
            .background(coverGradient(title)),
        contentAlignment = Alignment.Center,
    ) {
        when {
            imageUrl != null -> AsyncImage(
                model = apiImageUrl(imageUrl),
                contentDescription = "Portada de $title",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            maxWidth < 88.dp -> Text(title.trim().take(1).uppercase(), style = MaterialTheme.typography.titleLarge, color = BookintokColors.Ink)
            else -> Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                color = BookintokColors.Ink,
                textAlign = TextAlign.Center,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(12.dp),
            )
        }
    }
}

/** A soft brand gradient picked from the title, so every book keeps the same colour. */
private fun coverGradient(title: String): Brush {
    val palettes = listOf(
        listOf(BookintokColors.GreenSurface, BookintokColors.Green.copy(alpha = 0.55f)),
        listOf(Color(0xFFF6DDE8), BookintokColors.Pink.copy(alpha = 0.45f)),
        listOf(BookintokColors.GreenWhite, BookintokColors.GreenAccent.copy(alpha = 0.45f)),
        listOf(Color(0xFFEDE3E8), BookintokColors.InkGray.copy(alpha = 0.5f)),
    )
    return Brush.verticalGradient(palettes[Math.floorMod(title.hashCode(), palettes.size)])
}

/** Round avatar with the user's initial on a soft background (no ring). */
@Composable
fun Avatar(name: String, modifier: Modifier = Modifier, size: Int = 48) {
    val colors = listOf(BookintokColors.GreenSurface, Color(0xFFF6DDE8), BookintokColors.GreenSurfaceDark, Color(0xFFEDE3E8))
    Box(
        modifier.size(size.dp).clip(CircleShape).background(colors[Math.floorMod(name.hashCode(), colors.size)]),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.trimStart('@').take(1).uppercase(),
            style = (if (size >= 64) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleLarge)
                .copy(fontWeight = FontWeight.SemiBold),
            color = BookintokColors.Ink,
        )
    }
}

@Composable
fun StarRow(value: Double, modifier: Modifier = Modifier, size: Int = 16) {
    Row(modifier) {
        (1..5).forEach { i ->
            val icon = when {
                value >= i -> Icons.Default.Star
                value >= i - 0.5 -> Icons.Default.StarHalf
                else -> Icons.Default.StarBorder
            }
            Icon(icon, contentDescription = null, tint = Color(0xFFE0A100), modifier = Modifier.size(size.dp))
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, action: (@Composable RowScope.() -> Unit)? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        action?.invoke(this)
    }
}

/** Bottom bar for the main action of a screen, separated by a hairline and above the navigation bar. */
@Composable
fun BottomActionBar(content: @Composable RowScope.() -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = content,
            )
        }
    }
}

/** Empty state: an icon in a soft circle, a title and a hint. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(80.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, modifier = Modifier.size(36.dp), tint = BookintokColors.GreenDeep) }
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (actionLabel != null) SecondaryButton(actionLabel, onAction)
    }
}
