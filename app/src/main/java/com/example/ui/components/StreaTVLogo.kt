package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R

@Composable
fun StreaTVLogo(
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    showText: Boolean = true,
    animated: Boolean = false
) {
    val transition = rememberInfiniteTransition(label = "logo_anim")
    
    // Scale animation for breathing/loading effect (defined unconditionally)
    val animatedScale = transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    val scale = if (animated) animatedScale.value else 1.0f

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer(
                scaleX = scale,
                scaleY = scale
            ),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.streatv_logo),
            contentDescription = "StreaTV Logo",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )
    }
}
