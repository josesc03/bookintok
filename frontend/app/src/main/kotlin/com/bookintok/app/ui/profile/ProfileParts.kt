package com.bookintok.app.ui.profile

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bookintok.app.core.network.BookDto
import com.bookintok.app.core.network.RatingDto
import com.bookintok.app.core.network.RatingSummaryDto
import com.bookintok.app.ui.books.BookTile
import com.bookintok.app.ui.components.Avatar
import com.bookintok.app.ui.components.EmptyState
import com.bookintok.app.ui.components.StarRow
import com.bookintok.app.ui.theme.BookintokColors
import java.util.Locale

/** Profile header shared by the user's own profile and other people's. */
@Composable
fun ProfileHeader(
    nickname: String,
    displayName: String?,
    rating: RatingSummaryDto,
    locationLabel: String?,
    trailing: (@Composable () -> Unit)? = null,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Avatar(nickname, size = 76)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("@$nickname", style = MaterialTheme.typography.headlineSmall)
                displayName?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (rating.count > 0 && rating.average != null) {
                        StarRow(rating.average)
                        Text(
                            "${String.format(Locale.forLanguageTag("es"), "%.1f", rating.average)} · ${rating.count} valoraci${if (rating.count == 1L) "ón" else "ones"}",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    } else {
                        Text("Sin valoraciones todavía", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        if (locationLabel != null || trailing != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.Place, contentDescription = null, modifier = Modifier.size(18.dp), tint = BookintokColors.GreenDeep)
                Text(locationLabel ?: "Ubicación sin nombre", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                trailing?.invoke()
            }
        }
    }
}

/**
 * Grid with the header, a tab row (books / ratings) and the selected tab's content, so the
 * whole profile scrolls as one surface however many books there are.
 */
@Composable
fun ProfileContent(
    header: @Composable () -> Unit,
    books: List<BookDto>,
    ratings: List<RatingDto>,
    booksEmpty: String,
    onBookClick: (Long) -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
    footer: (LazyGridScope.() -> Unit)? = null,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }, key = "header") { header() }
        item(span = { GridItemSpan(maxLineSpan) }, key = "tabs") {
            PrimaryTabRow(
                selectedTabIndex = tab,
                containerColor = MaterialTheme.colorScheme.background,
                indicator = {
                    TabRowDefaults.PrimaryIndicator(
                        Modifier.tabIndicatorOffset(tab, matchContentSize = true),
                        width = Dp.Unspecified,
                        color = BookintokColors.GreenDeep,
                    )
                },
                divider = { HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)) },
            ) {
                listOf("Libros (${books.size})", "Valoraciones (${ratings.size})").forEachIndexed { index, title ->
                    Tab(
                        selected = tab == index,
                        onClick = { tab = index },
                        text = { Text(title, fontWeight = if (tab == index) FontWeight.SemiBold else FontWeight.Normal) },
                        selectedContentColor = BookintokColors.Ink,
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (tab == 0) {
            if (books.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "no-books") { EmptyState(Icons.Default.AutoStories, "Sin libros", booksEmpty) }
            }
            items(books, key = { "book-${it.id}" }) { book ->
                BookTile(book, onClick = { onBookClick(book.id) }, showPlace = false, modifier = Modifier.animateItem())
            }
        } else {
            if (ratings.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "no-ratings") {
                    EmptyState(Icons.Default.StarBorder, "Sin valoraciones", "Las valoraciones llegan después de completar un intercambio.")
                }
            }
            items(ratings, key = { "rating-${it.id}" }, span = { GridItemSpan(maxLineSpan) }) { RatingRow(it, Modifier.animateItem()) }
        }
        footer?.invoke(this)
    }
}

@Composable
private fun RatingRow(rating: RatingDto, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Avatar(rating.raterNickname, size = 32)
            Text("@${rating.raterNickname}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            StarRow(rating.score.toDouble(), size = 14)
        }
        rating.comment?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        HorizontalDivider(Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
    }
}

/** Cross-fades between loading, error and content. */
@Composable
fun <T> Phases(state: T, content: @Composable (T) -> Unit) {
    AnimatedContent(state, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "phases") { content(it) }
}
