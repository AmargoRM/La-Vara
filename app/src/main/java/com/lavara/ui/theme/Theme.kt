package com.lavara.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val VaraPrincipal = Color(0xFF0F7C73)
private val VaraPrincipalClaro = Color(0xFF7AD7CC)

private val LightColors = lightColorScheme(
    primary = VaraPrincipal,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFA6F1E6),
    onPrimaryContainer = Color(0xFF00201D),
    background = Color(0xFFFAFDFB),
    surface = Color(0xFFFAFDFB),
)

private val DarkColors = darkColorScheme(
    primary = VaraPrincipalClaro,
    onPrimary = Color(0xFF003732),
    primaryContainer = VaraPrincipal,
    onPrimaryContainer = Color(0xFFA6F1E6),
    background = Color(0xFF0F1514),
    surface = Color(0xFF0F1514),
)

/** Tema de La Vara: sigue el modo claro/oscuro del sistema. Sin colores dinámicos, para conservar el color propio. */
@Composable
fun LaVaraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
