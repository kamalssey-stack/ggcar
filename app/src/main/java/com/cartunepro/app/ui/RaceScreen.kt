package com.cartunepro.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cartunepro.app.data.RunState
import com.cartunepro.app.data.Telemetry
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private const val MAX_SPEED = 200f

/** Паспортный разгон 0–100 для Kia Soul 1.6 — поправьте под свою комплектацию. */
private const val FACTORY_0_100 = 11.0f

@Composable
fun RaceScreen(t: Telemetry, sweep: Float, landscape: Boolean, onStart: () -> Unit) {
    val shown by animateFloatAsState(t.speed, tween(700, easing = LinearOutSlowInEasing), label = "speed")
    val ledFrac = max((shown / MAX_SPEED).coerceIn(0f, 1f), sweep)

    if (landscape) {
        // широкий экран магнитолы: слева спидометр с диодами, справа кнопка и карточки
        Row(
            Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(Modifier.weight(1.15f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                LedBar(ledFrac, t.state == RunState.DONE, Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                Speedometer(
                    speed = shown, sweep = sweep, timer = t.elapsed, state = t.state,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            }
            Column(
                Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                StartButton(t.state, onStart)
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ResultCard("0–50", 50f, t.t50, shown, t.state, Neon.Cyan, null, Modifier.weight(1f))
                    ResultCard("0–100", 100f, t.t100, shown, t.state, Neon.Green, FACTORY_0_100, Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
            }
        }
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LedBar(ledFrac, t.state == RunState.DONE, Modifier.fillMaxWidth().padding(top = 4.dp))
        Spacer(Modifier.height(8.dp))
        Speedometer(
            speed = shown,
            sweep = sweep,
            timer = t.elapsed,
            state = t.state,
            modifier = Modifier.fillMaxWidth().aspectRatio(1.04f),
        )
        StartButton(t.state, onStart)
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ResultCard("0–50", 50f, t.t50, shown, t.state, Neon.Cyan, null, Modifier.weight(1f))
            ResultCard("0–100", 100f, t.t100, shown, t.state, Neon.Green, FACTORY_0_100, Modifier.weight(1f))
        }
        Spacer(Modifier.height(24.dp))
    }
}

// ------------------------------------------------------------------ LED как на руле F1

@Composable
fun LedBar(frac: Float, flash: Boolean, modifier: Modifier = Modifier) {
    val inf = rememberInfiniteTransition(label = "led")
    val blink by inf.animateFloat(
        1f, 0.12f,
        infiniteRepeatable(tween(260), RepeatMode.Reverse),
        label = "blink",
    )
    Canvas(modifier.height(24.dp)) {
        val n = 14
        val gap = 6.dp.toPx()
        val w = (size.width - gap * (n - 1)) / n
        val corner = androidx.compose.ui.geometry.CornerRadius(w * 0.35f)
        for (i in 0 until n) {
            val x = i * (w + gap)
            val base = when {
                i < 5 -> Neon.Green
                i < 10 -> Neon.Amber
                else -> Neon.Red
            }
            val lit = frac * n > i + 0.01f || flash
            if (lit) {
                val a = if (flash) blink else 1f
                val c = if (flash) Neon.Green else base
                drawRoundRect(
                    c, Offset(x - 3f, -3f), Size(w + 6f, size.height + 6f), corner,
                    alpha = 0.22f * a,
                )
                drawRoundRect(c, Offset(x, 0f), Size(w, size.height), corner, alpha = a)
            } else {
                drawRoundRect(Color(0xFF14171C), Offset(x, 0f), Size(w, size.height), corner)
                drawRoundRect(
                    base, Offset(x, 0f), Size(w, size.height), corner,
                    alpha = 0.10f, style = Stroke(1f),
                )
            }
        }
    }
}

// ------------------------------------------------------------------ спидометр

@Composable
fun Speedometer(
    speed: Float,
    sweep: Float,
    timer: Float,
    state: RunState,
    modifier: Modifier = Modifier,
) {
    val frac = max((speed / MAX_SPEED).coerceIn(0f, 1f), sweep)
    val tm = rememberTextMeasurer()
    val col = speedColor(frac)

    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val digitSize = (min(maxWidth.value, maxHeight.value) * 0.27f).sp
        Canvas(Modifier.fillMaxSize()) {
            val stroke = size.minDimension * 0.05f
            val pad = stroke * 2.2f
            val d = size.minDimension - 2 * pad
            val arcSize = Size(d, d)
            val topLeft = Offset((size.width - d) / 2f, (size.height - d) / 2f)
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = d / 2f

            // фоновое свечение
            drawCircle(
                Brush.radialGradient(
                    listOf(col.copy(alpha = 0.10f + 0.12f * frac), Color.Transparent),
                    center = center, radius = radius * 1.1f,
                ),
                radius = radius * 1.1f, center = center,
            )

            // трек
            drawArc(
                Color(0xFF14171C), 135f, 270f, false, topLeft, arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )

            // риски и подписи
            val shownSpeed = frac * MAX_SPEED
            for (v in 0..200 step 10) {
                val major = v % 50 == 0
                val ang = (135f + 270f * v / MAX_SPEED) * (PI.toFloat() / 180f)
                val outer = radius - stroke * 1.15f
                val inner = outer - (if (major) 16.dp.toPx() else 8.dp.toPx())
                val dir = Offset(cos(ang), sin(ang))
                val lit = v <= shownSpeed
                drawLine(
                    if (lit) speedColor(v / MAX_SPEED) else Color(0xFF39424D),
                    center + dir * inner, center + dir * outer,
                    strokeWidth = if (major) 3.dp.toPx() else 1.5f.dp.toPx(),
                    cap = StrokeCap.Round,
                )
                if (major) {
                    val r = inner - 15.dp.toPx()
                    val layout = tm.measure(
                        v.toString(),
                        TextStyle(
                            color = if (lit) Neon.Text else Neon.Dim,
                            fontSize = (size.minDimension * 0.034f).toSp(),
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                    drawText(
                        layout,
                        topLeft = center + dir * r -
                            Offset(layout.size.width / 2f, layout.size.height / 2f),
                    )
                }
            }

            // активная неоновая дуга
            val sweepDeg = 270f * frac
            if (sweepDeg > 0.5f) {
                rotate(135f, center) {
                    val br = Brush.sweepGradient(
                        0f to Neon.Cyan, 0.25f to Neon.Green, 0.45f to Neon.Amber,
                        0.65f to Neon.Red, 1f to Neon.Red,
                        center = center,
                    )
                    drawArc(br, 0f, sweepDeg, false, topLeft, arcSize, alpha = 0.07f,
                        style = Stroke(stroke * 3.4f, cap = StrokeCap.Round))
                    drawArc(br, 0f, sweepDeg, false, topLeft, arcSize, alpha = 0.13f,
                        style = Stroke(stroke * 2.3f, cap = StrokeCap.Round))
                    drawArc(br, 0f, sweepDeg, false, topLeft, arcSize, alpha = 0.22f,
                        style = Stroke(stroke * 1.55f, cap = StrokeCap.Round))
                    drawArc(br, 0f, sweepDeg, false, topLeft, arcSize,
                        style = Stroke(stroke, cap = StrokeCap.Round))
                }
                // светящийся «наконечник»
                val tipAng = (135f + sweepDeg) * (PI.toFloat() / 180f)
                val tip = center + Offset(cos(tipAng), sin(tipAng)) * radius
                drawCircle(col, stroke * 1.9f, tip, alpha = 0.20f)
                drawCircle(col, stroke * 1.1f, tip, alpha = 0.55f)
                drawCircle(Color.White, stroke * 0.5f, tip)
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "${(frac * MAX_SPEED).roundToInt()}",
                style = TextStyle(
                    fontFamily = DigitFont,
                    fontSize = digitSize,
                    color = Neon.Text,
                    shadow = Shadow(col, Offset.Zero, 36f),
                ),
            )
            Text(
                "КМ/Ч",
                style = TextStyle(
                    color = Neon.Dim, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    letterSpacing = 4.sp,
                ),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                String.format("%.2f с", timer),
                style = TextStyle(
                    fontFamily = DigitFont,
                    fontSize = 26.sp,
                    color = if (state == RunState.RUNNING) Neon.Amber else Neon.Text,
                    shadow = Shadow(
                        if (state == RunState.RUNNING) Neon.Amber else Neon.Cyan,
                        Offset.Zero, 18f,
                    ),
                ),
            )
            Text(
                when (state) {
                    RunState.IDLE -> "ГОТОВ К СТАРТУ"
                    RunState.ARMED -> "ТРОНЬСЯ С МЕСТА"
                    RunState.RUNNING -> "ЗАМЕР ИДЁТ"
                    RunState.DONE -> "ЗАЕЗД ЗАВЕРШЁН"
                },
                style = TextStyle(
                    color = Neon.Dim, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                ),
            )
        }
    }
}

// ------------------------------------------------------------------ кнопка СТАРТ

@Composable
fun StartButton(state: RunState, onClick: () -> Unit) {
    val label = when (state) {
        RunState.IDLE -> "СТАРТ"
        RunState.ARMED -> "ЖДУ"
        RunState.RUNNING -> "СТОП"
        RunState.DONE -> "СБРОС"
    }
    val color = when (state) {
        RunState.IDLE -> Neon.Green
        RunState.ARMED -> Neon.Amber
        RunState.RUNNING -> Neon.Red
        RunState.DONE -> Neon.Cyan
    }
    val pulsing = state == RunState.ARMED || state == RunState.RUNNING
    val inf = rememberInfiniteTransition(label = "pulse")
    val p1 by inf.animateFloat(
        0f, 1f, infiniteRepeatable(tween(1500, easing = LinearEasing)), label = "p1",
    )
    val p2 by inf.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(1500, easing = LinearEasing), initialStartOffset = StartOffset(750)),
        label = "p2",
    )
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val sc by animateFloatAsState(
        if (pressed) 0.9f else 1f, spring(dampingRatio = 0.45f, stiffness = 500f), label = "press",
    )

    Box(Modifier.size(190.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val base = size.minDimension * 0.29f
            if (pulsing) {
                for (p in listOf(p1, p2)) {
                    drawCircle(
                        color, base * (1f + 0.62f * p), alpha = 0.55f * (1f - p),
                        style = Stroke(4.dp.toPx()),
                    )
                }
            }
            drawCircle(color, base * 1.2f, alpha = if (pulsing) 0.16f else 0.08f)
            drawCircle(color, base * 1.2f, alpha = 0.45f, style = Stroke(1.5f.dp.toPx()))
        }
        Box(
            Modifier
                .size(112.dp)
                .scale(sc)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(color.copy(alpha = 0.38f), Color(0xFF0A0A0C))))
                .border(2.dp, color, CircleShape)
                .clickable(interactionSource = src, indication = null) { onClick() },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label,
                style = TextStyle(
                    color = Neon.Text, fontSize = 22.sp, fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp, shadow = Shadow(color, Offset.Zero, 24f),
                ),
            )
        }
    }
}

// ------------------------------------------------------------------ карточки результатов

@Composable
fun ResultCard(
    title: String,
    target: Float,
    time: Float?,
    speed: Float,
    state: RunState,
    accent: Color,
    factory: Float?,
    modifier: Modifier = Modifier,
) {
    val reached = time != null
    val flash = remember { Animatable(0f) }
    val tick = remember { Animatable(0f) }
    LaunchedEffect(reached) {
        if (reached) {
            flash.snapTo(1f)
            launch { tick.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 320f)) }
            flash.animateTo(0f, tween(1200))
        } else {
            tick.snapTo(0f)
            flash.snapTo(0f)
        }
    }
    val running = state == RunState.RUNNING
    val progress by animateFloatAsState(
        if (reached) 1f else if (running) (speed / target).coerceIn(0f, 1f) else 0f,
        tween(500), label = "prog",
    )
    val glowA = max(flash.value, if (reached) 0.28f else if (running) 0.12f else 0f)

    Column(modifier.glass(22.dp, accent, glowA).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                style = TextStyle(
                    color = accent, fontSize = 15.sp, fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                ),
            )
            Spacer(Modifier.weight(1f))
            Text(
                "✓",
                style = TextStyle(color = accent, fontSize = 22.sp, fontWeight = FontWeight.Black),
                modifier = Modifier.graphicsLayer {
                    scaleX = tick.value
                    scaleY = tick.value
                    alpha = tick.value.coerceIn(0f, 1f)
                },
            )
        }
        Text(
            time?.let { String.format("%.2f", it) } ?: "–.––",
            style = TextStyle(
                fontFamily = DigitFont,
                fontSize = 42.sp,
                color = if (reached) Neon.Text else Neon.Dim,
                shadow = if (reached) Shadow(accent, Offset.Zero, 26f) else null,
            ),
        )
        Text(
            "СЕКУНД",
            style = TextStyle(
                color = Neon.Dim, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                letterSpacing = 3.sp,
            ),
        )
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color(0x22FFFFFF)),
        ) {
            Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(accent))
        }
        if (factory != null) {
            Spacer(Modifier.height(8.dp))
            val delta = time?.let { it - factory }
            Text(
                if (delta == null) {
                    String.format("паспорт ≈ %.1f с", factory)
                } else {
                    String.format("%+.1f с к паспорту (%.1f)", delta, factory)
                },
                style = TextStyle(
                    color = if (delta == null) Neon.Dim else if (delta <= 0f) Neon.Green else Neon.Amber,
                    fontSize = 11.sp, fontWeight = FontWeight.Medium,
                ),
            )
        }
    }
}
