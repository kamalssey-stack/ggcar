package com.cartunepro.app.ui

import android.graphics.Typeface
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object Neon {
    val Bg = Color(0xFF020203)
    val Cyan = Color(0xFF00E5FF)
    val Green = Color(0xFF00FF9C)
    val Amber = Color(0xFFFFB300)
    val Red = Color(0xFFFF1744)
    val Text = Color(0xFFEAF6FF)
    val Dim = Color(0xFF6C7A89)
    val Steel = Color(0xFFB8C4D0)
}

val DigitFont = FontFamily(Typeface.create("sans-serif-condensed", Typeface.BOLD))

fun speedColor(f: Float): Color = when {
    f < 0.4f -> lerp(Neon.Cyan, Neon.Green, f / 0.4f)
    f < 0.7f -> lerp(Neon.Green, Neon.Amber, (f - 0.4f) / 0.3f)
    else -> lerp(Neon.Amber, Neon.Red, ((f - 0.7f) / 0.3f).coerceIn(0f, 1f))
}

/** Карбоновая текстура: маленький плиточный шейдер, повторяется по всему фону. */
@Composable
fun carbonBrush(): Brush = remember {
    val n = 12
    val bmp = ImageBitmap(n, n)
    val c = Canvas(bmp)
    val p = Paint()
    p.color = Color(0xFF07070A)
    c.drawRect(0f, 0f, n.toFloat(), n.toFloat(), p)
    p.color = Color(0xFF121218)
    c.drawRect(0f, 0f, 6f, 6f, p)
    c.drawRect(6f, 6f, 12f, 12f, p)
    p.color = Color(0xFF0C0C10)
    c.drawRect(6f, 0f, 12f, 6f, p)
    p.color = Color(0xFF191A22)
    c.drawRect(0f, 0f, 6f, 1f, p)
    c.drawRect(6f, 6f, 12f, 7f, p)
    ShaderBrush(ImageShader(bmp, TileMode.Repeated, TileMode.Repeated))
}

/** Стеклянная карточка (glassmorphism) с опциональным неоновым свечением. */
fun Modifier.glass(
    radius: Dp = 20.dp,
    glow: Color = Color.Transparent,
    glowAlpha: Float = 0f,
): Modifier {
    val shape = RoundedCornerShape(radius)
    val ga = glowAlpha.coerceIn(0f, 1f)
    return this
        .shadow((20f * ga).dp, shape, clip = false, ambientColor = glow, spotColor = glow)
        .clip(shape)
        .background(Brush.verticalGradient(listOf(Color(0x26FFFFFF), Color(0x0AFFFFFF))))
        .background(glow.copy(alpha = 0.16f * ga))
        .border(
            1.dp,
            Brush.verticalGradient(listOf(lerp(Color(0x33FFFFFF), glow, ga), Color(0x14FFFFFF))),
            shape,
        )
}

@Composable
fun CarTuneTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Neon.Bg,
            surface = Neon.Bg,
            primary = Neon.Cyan,
            onPrimary = Color.Black,
            onBackground = Neon.Text,
            onSurface = Neon.Text,
        ),
        content = content,
    )
}
