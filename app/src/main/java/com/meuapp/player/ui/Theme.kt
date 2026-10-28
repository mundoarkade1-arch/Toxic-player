package com.meuapp.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** TOKENS DE DESIGN: para trocar as cores do app inteiro, mude só aqui. */
object Neon {
    val Bg = Color(0xFF040705)
    val Glow = Color(0xFF0B3D22)
    val Green = Color(0xFF2BFF88)
    val OnGreen = Color(0xFF04120A)
    val Glass = Color.White.copy(alpha = 0.06f)
    val Border = Color.White.copy(alpha = 0.12f)
    val Dim = Color.White.copy(alpha = 0.65f)
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Neon.Green, onPrimary = Neon.OnGreen,
            background = Neon.Bg, surface = Neon.Bg,
            onBackground = Color.White, onSurface = Color.White
        ),
        content = content
    )
}

/** Efeito vidro translúcido. */
fun Modifier.glass(radius: Dp = 24.dp): Modifier {
    val s = RoundedCornerShape(radius)
    return this.clip(s).background(Neon.Glass).border(1.dp, Neon.Border, s)
}
