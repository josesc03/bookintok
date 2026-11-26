package com.bookintok.app.ui.welcome

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * First screen: the brand and a vertical slider. Sliding the thumb all the way up enters the app,
 * as in the first version of BookinTok.
 */
@Composable
fun WelcomeScreen(onEnter: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Box(
            Modifier.size(88.dp).clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(48.dp))
        }
        Spacer(Modifier.height(24.dp))
        Text("BookinTok", style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(12.dp))
        Text(
            "Intercambia libros,\nconecta historias",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.weight(1f))
        SlideUpToEnter(onEnter)
        Spacer(Modifier.height(16.dp))
        Text("Desliza hacia arriba para empezar", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Vertical track; the thumb springs back unless it reaches the top. Accessible as a plain button too. */
@Composable
private fun SlideUpToEnter(onEnter: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val progress = remember { Animatable(0f) }
    val hint = rememberInfiniteTransition(label = "hint")
    val nudge by hint.animateFloat(0f, 1f, infiniteRepeatable(tween(1200), RepeatMode.Reverse), label = "nudge")

    val trackWidth = 88.dp
    val trackHeight = 240.dp
    val thumb = 72.dp
    val padding = 8.dp
    val travel = with(density) { (trackHeight - thumb - padding * 2).toPx() }

    BoxWithConstraints(
        Modifier
            .width(trackWidth)
            .height(trackHeight)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .semantics {
                role = Role.Button
                contentDescription = "Entrar en BookinTok"
                onClick { onEnter(); true }
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        scope.launch {
                            if (progress.value > 0.85f) {
                                progress.animateTo(1f, tween(120))
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onEnter()
                            } else {
                                progress.animateTo(0f, spring())
                            }
                        }
                    },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        scope.launch { progress.snapTo((progress.value - dragAmount / travel).coerceIn(0f, 1f)) }
                    },
                )
            },
        contentAlignment = Alignment.BottomCenter,
    ) {
        // Filled part of the track grows with the thumb
        Box(
            Modifier
                .padding(padding)
                .width(thumb)
                .height(thumb + with(density) { (travel * progress.value).toDp() })
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
        )
        Icon(
            Icons.Default.KeyboardArrowUp,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 20.dp).alpha(0.4f + 0.6f * nudge * (1 - progress.value)),
        )
        Box(
            Modifier
                .padding(padding)
                .offset { IntOffset(0, -(travel * progress.value).roundToInt()) }
                .size(thumb)
                .shadow(6.dp, CircleShape)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.KeyboardArrowUp, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(36.dp))
        }
    }
}
