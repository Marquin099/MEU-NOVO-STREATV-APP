package com.example.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.StreaTVLogo
import com.example.ui.viewmodel.IPTVViewModel
import com.example.ui.viewmodel.Screen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun AppSplashScreen(viewModel: IPTVViewModel) {
    // Fade-in animation for the logo
    val alphaAnim = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        val animJob = launch {
            alphaAnim.animateTo(
                targetValue = 1f,
                animationSpec = tween(1200, easing = LinearOutSlowInEasing)
            )
        }
        val delayJob = launch {
            delay(1200)
        }

        animJob.join()
        delayJob.join()

        if (viewModel.hasLoggedIn()) {
            viewModel.syncNewContentOnStartup {
                viewModel.navigateTo(Screen.ProfileSelection)
            }
        } else {
            viewModel.navigateTo(Screen.Login)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF161616),
                        Color(0xFF050505)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.alpha(alphaAnim.value)
        ) {
            // Animated breathing custom brand S logo
            StreaTVLogo(
                size = 130.dp,
                animated = true,
                showText = true
            )

            Spacer(modifier = Modifier.height(36.dp))

            // Custom modern styled sleek red progress track
            Box(
                modifier = Modifier
                    .width(180.dp)
                    .height(3.dp),
                contentAlignment = Alignment.Center
            ) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFFE50914),
                    trackColor = Color.White.copy(alpha = 0.1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Carregando servidor IPTV...",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.LightGray.copy(alpha = 0.6f),
                letterSpacing = 0.5.sp,
                fontSize = 12.sp
            )
        }
    }
}
