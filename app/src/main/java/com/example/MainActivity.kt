package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.IPTVViewModel
import com.example.ui.viewmodel.Screen
import com.example.ui.components.VideoPlayerScreen

val LocalIPTVViewModel = staticCompositionLocalOf<IPTVViewModel> {
    error("No IPTVViewModel provided")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val viewModel: IPTVViewModel = viewModel()
                CompositionLocalProvider(LocalIPTVViewModel provides viewModel) {
                    Surface(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        AppNavigation(viewModel)
                    }
                }
            }
        }
    }
}

@Composable
fun AppNavigation(viewModel: IPTVViewModel) {
    val currentScreen by viewModel.currentScreen.collectAsState()

    when (val screen = currentScreen) {
        is Screen.Splash -> AppSplashScreen(viewModel = viewModel)
        is Screen.Login -> LoginScreen(viewModel = viewModel)
        is Screen.ProfileSelection -> ProfileSelectionScreen(viewModel = viewModel)
        is Screen.Main -> MainScreen(viewModel = viewModel)
        is Screen.Detail -> DetailScreen(item = screen.item, viewModel = viewModel, onBack = { viewModel.navigateBack() }, autoPlay = screen.autoPlay)
        is Screen.Player -> VideoPlayerScreen(item = screen.item, viewModel = viewModel, onBack = { viewModel.navigateBack() })
    }
}
