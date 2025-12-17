package org.bigboyapps.rngenerator

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import app.cash.sqldelight.db.SqlDriver
import org.bigboyapps.rngenerator.ui.GameViewModel
import org.bigboyapps.rngenerator.ui.TerminalScreen

// Dark theme for terminal aesthetic
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF81C784),
    secondary = Color(0xFF4FC3F7),
    tertiary = Color(0xFFCE93D8),
    background = Color(0xFF1E1E1E),
    surface = Color(0xFF2D2D2D),
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onTertiary = Color.Black,
    onBackground = Color(0xFFD4D4D4),
    onSurface = Color(0xFFD4D4D4)
)

@Composable
fun App(driverFactory: () -> SqlDriver) {
    MaterialTheme(colorScheme = DarkColorScheme) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .safeContentPadding(),
            color = MaterialTheme.colorScheme.background
        ) {
            val viewModel = remember { GameViewModel(driverFactory) }
            TerminalScreen(viewModel)
        }
    }
}
