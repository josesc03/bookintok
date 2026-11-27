package com.bookintok.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bookintok.app.R

/** The Bookintok symbol: an open book with a location pin. */
@Composable
fun LogoSymbol(size: Dp, modifier: Modifier = Modifier) {
    Image(painterResource(R.drawable.ic_logo), contentDescription = null, modifier = modifier.size(size))
}

/** Symbol and name side by side. */
@Composable
fun Wordmark(modifier: Modifier = Modifier, symbolSize: Dp = 40.dp) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LogoSymbol(symbolSize)
        Text("Bookintok", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
    }
}

/** Shown while the session loads: continues the system splash screen, with a gentle pulse. */
@Composable
fun LogoLoading() {
    val pulse by rememberInfiniteTransition(label = "logo").animateFloat(
        initialValue = 0.94f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "pulse",
    )
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LogoSymbol(120.dp, Modifier.scale(pulse))
        }
    }
}
