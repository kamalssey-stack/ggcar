package com.cartunepro.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cartunepro.app.data.Telemetry
import kotlin.math.hypot
import kotlin.math.max

private const val G_RANGE = 0.8f

@Composable
fun GForceScreen(t: Telemetry, landscape: Boolean, onCalibrate: () -> Unit) {
    val sx by animateFloatAsState(t.gx, spring(dampingRatio = 0.75f, stiffness = 220f), label = "gx")
    val sy by animateFloatAsState(t.gy, spring(dampingRatio = 0.75f, stiffness = 220f), label = "gy")
    val trail = remember { mutableStateListOf<Offset>() }
    LaunchedEffect(sx, sy) {
        trail.add(Offset(sx, sy))
        while (trail.size > 36) trail.removeAt(0)
    }
    val tm = rememberTextMeasurer()
    val mag = hypot(sx, sy)

    val readout: @Composable () -> Unit = {
        Text(
            String.format("%.2f g", mag),
            style = TextStyle(
                fontFamily = DigitFont, fontSize = 52.sp, color = Neon.Text,
                shadow = Shadow(lerp(Neon.Cyan, Neon.Red, (mag / G_RANGE).coerceIn(0f, 1f)), Offset.Zero, 30f),
            ),
        )
        Text(
            "ПЕРЕГРУЗКА",
            style = TextStyle(
                color = Neon.Dim, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp,
            ),
        )
    }
    val tiles: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PeakTile("РАЗГОН", t.maxAccel, Neon.Green, Modifier.weight(1f))
            PeakTile("ТОРМОЗ", t.maxBrake, Neon.Red, Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PeakTile("ЛЕВО", t.maxLeft, Neon.Cyan, Modifier.weight(1f))
            PeakTile("ПРАВО", t.maxRight, Neon.Amber, Modifier.weight(1f))
        }
        Spacer(Modifier.height(14.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .glass(18.dp, Neon.Cyan, 0.15f)
                .clickable { onCalibrate() }
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "КАЛИБРОВКА · СБРОС ПИКОВ",
                style = TextStyle(
                    color = Neon.Cyan, fontSize = 13.sp, fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                ),
            )
        }
        Text(
            if (t.hasAccel) {
                "Закрепите телефон неподвижно и нажмите калибровку на ровной дороге. Ось «вперёд» определяется автоматически."
            } else {
                "Датчика ускорения в этом устройстве нет: разгон и торможение считаются по изменению GPS-скорости, боковая g недоступна."
            },
            modifier = Modifier.padding(top = 10.dp, bottom = 16.dp),
            style = TextStyle(color = Neon.Dim, fontSize = 11.sp),
        )
    }

    if (landscape) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                GCircle(sx, sy, mag, trail, tm, Modifier.fillMaxHeight().aspectRatio(1f).padding(4.dp))
            }
            Column(
                Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                readout()
                Spacer(Modifier.height(10.dp))
                tiles()
            }
        }
    } else {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            readout()
            Spacer(Modifier.height(8.dp))
            GCircle(sx, sy, mag, trail, tm, Modifier.fillMaxWidth().aspectRatio(1f).padding(8.dp))
            Spacer(Modifier.height(8.dp))
            tiles()
        }
    }
}

@Composable
private fun GCircle(
    sx: Float,
    sy: Float,
    mag: Float,
    trail: List<Offset>,
    tm: androidx.compose.ui.text.TextMeasurer,
    modifier: Modifier,
) {
    Canvas(modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val r = size.minDimension / 2f * 0.92f

        drawCircle(
            Brush.radialGradient(listOf(Color(0xFF0E1218), Color(0xFF040507)), center, r),
            r, center,
        )
        for (i in 1..4) {
            drawCircle(
                Neon.Cyan, r * i / 4f, center,
                alpha = if (i == 4) 0.55f else 0.16f,
                style = Stroke(if (i == 4) 2.5f.dp.toPx() else 1.dp.toPx()),
            )
            val layout = tm.measure(
                String.format("%.1f", G_RANGE * i / 4f),
                TextStyle(color = Neon.Dim, fontSize = (size.minDimension * 0.028f).toSp()),
            )
            drawText(layout, topLeft = center + Offset(r * i / 4f + 4f, 2f))
        }
        drawLine(Neon.Cyan.copy(alpha = 0.22f), Offset(center.x - r, center.y), Offset(center.x + r, center.y), 1.dp.toPx())
        drawLine(Neon.Cyan.copy(alpha = 0.22f), Offset(center.x, center.y - r), Offset(center.x, center.y + r), 1.dp.toPx())

        fun label(text: String, pos: Offset) {
            val l = tm.measure(
                text,
                TextStyle(
                    color = Neon.Dim, fontSize = (size.minDimension * 0.03f).toSp(),
                    fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
                ),
            )
            drawText(l, topLeft = pos - Offset(l.size.width / 2f, l.size.height / 2f))
        }
        label("РАЗГОН", Offset(center.x, center.y - r - 2f + 22f))
        label("ТОРМОЗ", Offset(center.x, center.y + r - 22f))
        label("ЛЕВО", Offset(center.x - r + 34f, center.y - 16f))
        label("ПРАВО", Offset(center.x + r - 34f, center.y - 16f))

        fun toScreen(g: Offset): Offset {
            var p = Offset(g.x / G_RANGE * r, -g.y / G_RANGE * r)
            val len = p.getDistance()
            if (len > r) p = p * (r / len)
            return center + p
        }

        val col = lerp(Neon.Cyan, Neon.Red, (mag / G_RANGE).coerceIn(0f, 1f))
            .let { if (mag / G_RANGE in 0.35f..0.7f) lerp(Neon.Cyan, Neon.Amber, (mag / G_RANGE - 0.35f) / 0.35f) else it }

        // шлейф
        val pts = trail.toList()
        for (i in 1 until pts.size) {
            val f = i / pts.size.toFloat()
            drawLine(
                col.copy(alpha = f * 0.6f), toScreen(pts[i - 1]), toScreen(pts[i]),
                strokeWidth = 1.dp.toPx() + 5.dp.toPx() * f, cap = StrokeCap.Round,
            )
        }

        // светящаяся точка
        val p = toScreen(Offset(sx, sy))
        drawCircle(
            Brush.radialGradient(listOf(col.copy(alpha = 0.85f), Color.Transparent), p, r * 0.24f),
            r * 0.24f, p,
        )
        drawCircle(col, r * 0.05f, p)
        drawCircle(Color.White, r * 0.022f, p)
    }
}

@Composable
private fun PeakTile(title: String, value: Float, color: Color, modifier: Modifier) {
    val glow = (max(0f, value) / G_RANGE).coerceIn(0f, 1f)
    Column(modifier.glass(20.dp, color, glow * 0.6f).padding(14.dp)) {
        Text(
            title,
            style = TextStyle(
                color = color, fontSize = 11.sp, fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
            ),
        )
        Text(
            String.format("%.2f", value),
            style = TextStyle(fontFamily = DigitFont, fontSize = 30.sp, color = Neon.Text),
        )
        Text("g", style = TextStyle(color = Neon.Dim, fontSize = 11.sp))
    }
}
