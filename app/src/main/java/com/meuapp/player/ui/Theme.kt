package com.meuapp.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** TOKENS DE DESIGN: para trocar as cores do app inteiro, mude só aqui. */
object Neon {
    val Bg = Color(0xFF040806)
    val Glow = Color(0xFF0B3D22)
    val Surface = Color(0xFF0D1813)          // diálogos e menus
    val Green = Color(0xFF2BFF88)            // cor principal (verde tóxico)
    val Lime = Color(0xFFC4FF3D)             // acento do degradê da marca
    val Teal = Color(0xFF18D8C0)             // acento frio, só em brilhos de fundo
    val OnGreen = Color(0xFF03130A)
    val Danger = Color(0xFFFF5C6C)
    val Glass = Color.White.copy(alpha = 0.06f)
    val Border = Color.White.copy(alpha = 0.12f)
    val Dim = Color.White.copy(alpha = 0.65f)
    val Faint = Color.White.copy(alpha = 0.38f)

    /** Degradê da marca (verde → limão), usado em botões principais e destaques. */
    val Brand: Brush = Brush.linearGradient(listOf(Green, Lime))
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Neon.Green, onPrimary = Neon.OnGreen,
            secondary = Neon.Lime, onSecondary = Neon.OnGreen,
            background = Neon.Bg, surface = Neon.Bg,
            onBackground = Color.White, onSurface = Color.White,
            surfaceVariant = Neon.Surface, onSurfaceVariant = Neon.Dim,
            outline = Color.White.copy(alpha = 0.28f),
            outlineVariant = Color.White.copy(alpha = 0.12f),
            error = Neon.Danger
        ),
        content = content
    )
}

/** Efeito vidro: preenchimento em degradê translúcido + borda com brilho no canto superior. */
fun Modifier.glass(radius: Dp = 24.dp, strong: Boolean = false): Modifier {
    val s = RoundedCornerShape(radius)
    val top = if (strong) 0.16f else 0.10f
    val bottom = if (strong) 0.07f else 0.035f
    return this.clip(s)
        .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = top), Color.White.copy(alpha = bottom))), s)
        .border(
            1.dp,
            Brush.linearGradient(listOf(Color.White.copy(alpha = 0.32f), Color.White.copy(alpha = 0.04f), Color.White.copy(alpha = 0.14f))),
            s
        )
}

/** Fundo "aurora": brilhos suaves de cor sobre o preto. [glow] normalmente vem da capa da música. */
fun Modifier.aurora(glow: Color): Modifier = this
    .background(Neon.Bg)
    .drawBehind {
        drawRect(
            Brush.radialGradient(
                listOf(glow.copy(alpha = 0.85f), Color.Transparent),
                center = Offset(size.width * 0.12f, 0f), radius = size.width * 1.25f
            )
        )
        drawRect(
            Brush.radialGradient(
                listOf(Neon.Teal.copy(alpha = 0.14f), Color.Transparent),
                center = Offset(size.width * 1.0f, size.height * 0.72f), radius = size.width * 1.0f
            )
        )
        drawRect(
            Brush.radialGradient(
                listOf(Neon.Lime.copy(alpha = 0.06f), Color.Transparent),
                center = Offset(size.width * 0.05f, size.height), radius = size.width * 0.8f
            )
        )
    }

/** Diálogo no estilo do app (mesmos parâmetros do AlertDialog padrão). */
@Composable
internal fun GlassDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        dismissButton = dismissButton,
        title = title,
        text = text,
        shape = RoundedCornerShape(28.dp),
        containerColor = Neon.Surface,
        titleContentColor = Color.White,
        textContentColor = Neon.Dim
    )
}

/** Menu suspenso no estilo do app. */
@Composable
internal fun GlassMenu(expanded: Boolean, onDismissRequest: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = Modifier.background(Neon.Surface),
        content = content
    )
}
