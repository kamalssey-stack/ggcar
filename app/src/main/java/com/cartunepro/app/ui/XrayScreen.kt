package com.cartunepro.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cartunepro.app.data.Telemetry
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

// ------------------------------------------------------------------ модель

private val RATIOS = floatArrayOf(0f, 4.16f, 2.66f, 1.80f, 1.39f, 1.00f, 0.77f) // примерные, 6-АКПП
private const val FINAL_DRIVE = 3.51f
private const val WHEEL_R_M = 0.316f
private const val TWO_PI = (2.0 * PI).toFloat()

private class XraySim {
    var wheel = 0f      // фаза колеса, рад
    var cycle = 0f      // угол рабочего цикла 0..720°
    var road = 0f       // смещение разметки
    var flow = 0f
    var exh = 0f
    var time = 0f
    var gear = 1
    var rpm = 800f
}

private fun stepSim(sim: XraySim, speedKmh: Float, slow: Float, dt: Float) {
    val vms = speedKmh / 3.6f
    val wheelRps = vms / (TWO_PI * WHEEL_R_M)
    var rpmNow = wheelRps * 60f * RATIOS[sim.gear] * FINAL_DRIVE
    if (rpmNow > 3400f && sim.gear < 6) sim.gear++
    else if (rpmNow < 1400f && sim.gear > 1) sim.gear--
    rpmNow = wheelRps * 60f * RATIOS[sim.gear] * FINAL_DRIVE
    sim.rpm = max(800f, min(6500f, rpmNow))

    sim.time += dt
    sim.wheel = (sim.wheel + wheelRps * TWO_PI * dt * slow) % TWO_PI
    sim.cycle = (sim.cycle + sim.rpm / 60f * 360f * dt * slow) % 720f
    sim.road = (sim.road + wheelRps * TWO_PI * 66f * dt * slow) % 100000f
    sim.flow = (sim.flow + dt * (0.25f + sim.rpm / 2800f)) % 1f
    sim.exh = (sim.exh + dt * (0.2f + sim.rpm / 3500f)) % 1f
}

// ------------------------------------------------------------------ геометрия Kia Soul (вид сбоку, нос справа, 1000×440)

private object CarGeometry {
    val body: Path by lazy {
        val b = Path().apply {
            moveTo(945f, 326f)
            lineTo(951f, 262f)
            cubicTo(958f, 230f, 952f, 206f, 932f, 198f)
            lineTo(700f, 172f)
            lineTo(588f, 60f)
            lineTo(215f, 52f)
            cubicTo(160f, 52f, 128f, 64f, 118f, 86f)
            lineTo(98f, 215f)
            lineTo(95f, 300f)
            cubicTo(95f, 318f, 105f, 326f, 122f, 326f)
            close()
        }
        val arches = Path().apply {
            addOval(Rect(Offset(780f, 315f), 80f))
            addOval(Rect(Offset(255f, 315f), 80f))
        }
        Path.combine(PathOperation.Difference, b, arches)
    }

    val windows: Path by lazy {
        Path().apply {
            // передняя дверь
            moveTo(676f, 160f); lineTo(572f, 72f); lineTo(442f, 72f); lineTo(442f, 160f); close()
            // задняя часть
            moveTo(424f, 160f); lineTo(424f, 72f); lineTo(235f, 70f)
            lineTo(165f, 100f); lineTo(150f, 160f); close()
        }
    }

    val flowPath = listOf(Offset(872f, 262f), Offset(800f, 262f), Offset(784f, 292f), Offset(780f, 315f))
    val exhaustPath = listOf(
        Offset(925f, 304f), Offset(900f, 334f), Offset(300f, 334f), Offset(190f, 336f), Offset(105f, 338f),
    )
}

private fun lerpPoly(p: List<Offset>, t: Float): Offset {
    var total = 0f
    val lens = FloatArray(p.size - 1) { (p[it + 1] - p[it]).getDistance().also { d -> total += d } }
    var d = t * total
    for (i in lens.indices) {
        if (d <= lens[i]) return lerp(p[i], p[i + 1], if (lens[i] == 0f) 0f else d / lens[i])
        d -= lens[i]
    }
    return p.last()
}

// ------------------------------------------------------------------ экран

@Composable
fun XrayScreen(t: Telemetry, landscape: Boolean) {
    var manual by remember { mutableStateOf<Float?>(55f) }
    var slow by remember { mutableFloatStateOf(0.04f) }
    val target = manual ?: t.speed
    val speed by animateFloatAsState(target, tween(450), label = "xspeed")

    val sim = remember { XraySim() }
    var tick by remember { mutableIntStateOf(0) }
    var rpmUi by remember { mutableIntStateOf(800) }
    var gearUi by remember { mutableIntStateOf(1) }
    val speedNow by rememberUpdatedState(speed)
    val slowNow by rememberUpdatedState(slow)
    val tm = rememberTextMeasurer()

    LaunchedEffect(Unit) {
        var last = 0L
        var n = 0
        while (true) {
            androidx.compose.runtime.withFrameNanos { now ->
                if (last != 0L) {
                    val dt = ((now - last) / 1e9f).coerceAtMost(0.05f)
                    stepSim(sim, speedNow, slowNow, dt)
                    tick++
                    n++
                    if (n % 5 == 0) {
                        rpmUi = (sim.rpm / 10f).roundToInt() * 10
                        gearUi = sim.gear
                    }
                }
                last = now
            }
        }
    }

    val carBlock: @Composable () -> Unit = {
        Text(
            "X-RAY · KIA SOUL",
            style = TextStyle(
                color = Neon.Cyan, fontSize = 13.sp, fontWeight = FontWeight.Black,
                letterSpacing = 3.sp,
            ),
        )
        Text(
            "Кузов, привод на передние колёса и двигатель в разрезе",
            style = TextStyle(color = Neon.Dim, fontSize = 11.sp),
            modifier = Modifier.padding(bottom = 10.dp),
        )
        Box(Modifier.fillMaxWidth().glass(22.dp, Neon.Cyan, 0.15f).padding(6.dp)) {
            Canvas(Modifier.fillMaxWidth().aspectRatio(1000f / 440f)) {
                val frame = tick
                if (frame >= 0) drawCar(sim, speed, tm)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("СКОРОСТЬ", "${speed.roundToInt()}", "км/ч", Neon.Cyan, Modifier.weight(1f))
            StatTile(
                "ОБОРОТЫ", "$rpmUi", "об/мин",
                lerpColor(Neon.Green, Neon.Red, ((rpmUi - 800) / 5200f).coerceIn(0f, 1f)),
                Modifier.weight(1f),
            )
            StatTile("ПЕРЕДАЧА", "$gearUi", "из 6", Neon.Amber, Modifier.weight(1f))
        }
    }

    val engineBlock: @Composable () -> Unit = {
        Text(
            "ДВИГАТЕЛЬ В РАЗРЕЗЕ · 1.6 · ПОРЯДОК РАБОТЫ 1-3-4-2",
            style = TextStyle(
                color = Neon.Amber, fontSize = 12.sp, fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
            ),
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Box(Modifier.fillMaxWidth().glass(22.dp, Neon.Amber, 0.12f).padding(6.dp)) {
            Canvas(Modifier.fillMaxWidth().aspectRatio(2f)) {
                val frame = tick
                if (frame >= 0) drawEngine(sim, tm)
            }
        }
    }

    val controlsBlock: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth().glass(20.dp).padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (manual == null) "СКОРОСТЬ ПО GPS" else "РУЧНАЯ СКОРОСТЬ",
                    style = TextStyle(
                        color = Neon.Text, fontSize = 12.sp, fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp,
                    ),
                )
                Spacer(Modifier.weight(1f))
                Chip("GPS", manual == null) { manual = null }
            }
            Slider(
                value = (manual ?: t.speed).coerceIn(0f, 160f),
                onValueChange = { manual = it },
                valueRange = 0f..160f,
                colors = SliderDefaults.colors(
                    thumbColor = Neon.Cyan,
                    activeTrackColor = Neon.Cyan,
                    inactiveTrackColor = Color(0x33FFFFFF),
                ),
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "ЗАМЕДЛЕНИЕ",
                    style = TextStyle(color = Neon.Dim, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp),
                )
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("1/10", slow == 0.1f) { slow = 0.1f }
                    Chip("1/25", slow == 0.04f) { slow = 0.04f }
                    Chip("1/50", slow == 0.02f) { slow = 0.02f }
                }
            }
        }
        Text(
            "Реальные обороты слишком быстрые для глаза, поэтому вращение показано в замедлении. Передаточные числа АКПП — приблизительные.",
            style = TextStyle(color = Neon.Dim, fontSize = 11.sp),
            modifier = Modifier.padding(top = 10.dp, bottom = 16.dp),
        )
    }

    if (landscape) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
                carBlock()
            }
            Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
                engineBlock()
                Spacer(Modifier.height(12.dp))
                controlsBlock()
            }
        }
    } else {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            carBlock()
            Spacer(Modifier.height(14.dp))
            engineBlock()
            Spacer(Modifier.height(14.dp))
            controlsBlock()
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, unit: String, color: Color, modifier: Modifier) {
    Column(modifier.glass(18.dp, color, 0.18f).padding(12.dp)) {
        Text(
            label,
            style = TextStyle(color = color, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp),
        )
        Text(value, style = TextStyle(fontFamily = DigitFont, fontSize = 28.sp, color = Neon.Text))
        Text(unit, style = TextStyle(color = Neon.Dim, fontSize = 10.sp))
    }
}

@Composable
private fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    val c = if (selected) Neon.Cyan else Neon.Dim
    Box(
        Modifier
            .glass(14.dp, c, if (selected) 0.4f else 0f)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = TextStyle(color = c, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp),
        )
    }
}

// ------------------------------------------------------------------ рисование машины

private fun DrawScope.drawWheel(c: Offset, phase: Float, flowGlow: Float, driven: Boolean) {
    drawCircle(Neon.Steel, 66f, c, alpha = 0.12f, style = Stroke(16f))
    drawCircle(Neon.Cyan, 66f, c, alpha = 0.85f, style = Stroke(2.2f))
    drawCircle(Neon.Cyan, 53f, c, alpha = 0.40f, style = Stroke(1.5f))
    drawCircle(Neon.Steel, 44f, c, alpha = 0.85f, style = Stroke(3f))
    for (k in 0 until 5) {
        val a = phase + k * TWO_PI / 5f
        val dir = Offset(cos(a), sin(a))
        drawLine(Neon.Steel, c + dir * 12f, c + dir * 43f, 6f, StrokeCap.Round, alpha = 0.30f)
        drawLine(Neon.Cyan, c + dir * 12f, c + dir * 43f, 1.6f, StrokeCap.Round, alpha = 0.9f)
    }
    val va = Offset(cos(phase), sin(phase)) * 58f
    drawCircle(Neon.Amber, 3.5f, c + va)
    drawCircle(Color(0xFF0A0A0C), 12f, c)
    drawCircle(Neon.Cyan, 12f, c, style = Stroke(2f))
    if (driven) {
        for (k in 0 until 2) {
            val p = (flowGlow * 2f + k * 0.5f) % 1f
            drawCircle(Neon.Green, 14f + 40f * p, c, alpha = (1f - p) * 0.55f, style = Stroke(2.2f))
        }
    }
}

private fun DrawScope.drawCar(sim: XraySim, speed: Float, tm: TextMeasurer) {
    val s = size.width / 1000f
    val heat = ((sim.rpm - 800f) / 5200f).coerceIn(0f, 1f)
    val heatColor = lerpColor(Neon.Cyan, Neon.Red, heat)
    val bob = sin(sim.time * 9f) * 1.3f * min(1f, speed / 40f)
    val fire = 1f - (sim.cycle % 180f) / 180f
    val cyan = Neon.Cyan

    withTransform({ scale(s, s, Offset.Zero) }) {
        // дорога
        drawRect(Color(0xFF07080B), Offset(0f, 380f), Size(1000f, 70f))
        drawLine(cyan, Offset(0f, 380f), Offset(1000f, 380f), 2f, alpha = 0.5f)
        val period = 130f
        var x = -(sim.road % period)
        while (x < 1000f) {
            drawLine(Color.White, Offset(x, 412f), Offset(x + 70f, 412f), 4f, StrokeCap.Round, alpha = 0.5f)
            x += period
        }
        drawOval(Color.Black, Offset(110f, 372f), Size(820f, 16f), alpha = 0.55f)

        translate(0f, bob) {
            // кузов
            drawPath(
                CarGeometry.body,
                Brush.verticalGradient(listOf(cyan.copy(alpha = 0.16f), cyan.copy(alpha = 0.03f)), 52f, 326f),
            )
            drawPath(CarGeometry.body, cyan, alpha = 0.16f, style = Stroke(9f, join = StrokeJoin.Round))
            drawPath(CarGeometry.body, cyan, alpha = 0.95f, style = Stroke(2.4f, join = StrokeJoin.Round))
            drawPath(CarGeometry.windows, cyan, alpha = 0.08f)
            drawPath(CarGeometry.windows, cyan, alpha = 0.55f, style = Stroke(1.6f, join = StrokeJoin.Round))

            // силовой каркас: рельсы, ферма, стойки
            for (y in listOf(168f, 312f)) {
                drawLine(cyan, Offset(120f, y), Offset(930f, y), 1.2f, alpha = 0.22f)
            }
            for (seg in listOf(130f to 420f, 456f to 690f)) {
                var xx = seg.first
                var k = 0
                while (xx + 29f <= seg.second) {
                    val y1 = if (k % 2 == 0) 168f else 312f
                    val y2 = if (k % 2 == 0) 312f else 168f
                    drawLine(cyan, Offset(xx, y1), Offset(xx + 29f, y2), 1.2f, alpha = 0.18f)
                    xx += 29f
                    k++
                }
            }
            drawLine(cyan, Offset(433f, 70f), Offset(433f, 326f), 1.6f, alpha = 0.4f)
            drawLine(cyan, Offset(690f, 172f), Offset(690f, 326f), 1.2f, alpha = 0.25f)
            drawLine(cyan, Offset(175f, 120f), Offset(175f, 326f), 1.2f, alpha = 0.25f)

            // двигатель (поперечный, спереди)
            val eng = Offset(812f, 205f)
            drawRoundRect(heatColor, eng, Size(120f, 99f), CornerRadius(10f), alpha = 0.10f + 0.20f * heat)
            drawRoundRect(heatColor, eng, Size(120f, 99f), CornerRadius(10f), alpha = 0.9f, style = Stroke(2.2f))
            val cr = Offset(872f, 262f)
            drawCircle(Neon.Amber, 36f * (0.6f + 0.4f * fire), cr, alpha = 0.30f * fire * fire)
            drawCircle(Neon.Steel, 24f, cr, alpha = 0.8f, style = Stroke(2f))
            val ca = sim.cycle * (PI.toFloat() / 180f)
            val pin = cr + Offset(cos(ca), sin(ca)) * 15f
            val cw = cr - Offset(cos(ca), sin(ca)) * 12f
            drawLine(Neon.Steel, cr, pin, 4f, StrokeCap.Round)
            drawCircle(Neon.Amber, 5f, pin)
            drawCircle(Neon.Steel, 8f, cw, alpha = 0.55f)
            drawCircle(Neon.Cyan, 4f, cr)

            // АКПП (две шестерни)
            val gA = Offset(766f, 258f)
            val gB = Offset(802f, 288f)
            drawCircle(Neon.Steel, 20f, gA, alpha = 0.8f, style = Stroke(2f))
            drawCircle(Neon.Steel, 15f, gB, alpha = 0.8f, style = Stroke(2f))
            for (k in 0 until 8) {
                val a1 = ca + k * TWO_PI / 8f
                val d1 = Offset(cos(a1), sin(a1))
                drawLine(Neon.Steel, gA + d1 * 20f, gA + d1 * 25f, 3f, alpha = 0.85f)
                val a2 = -ca * RATIOS[sim.gear].coerceAtLeast(0.5f) * 0.7f + k * TWO_PI / 8f
                val d2 = Offset(cos(a2), sin(a2))
                drawLine(Neon.Steel, gB + d2 * 15f, gB + d2 * 19.5f, 3f, alpha = 0.85f)
            }

            // поток мощности: коленвал → АКПП → дифференциал → ШРУС → колесо
            val fp = CarGeometry.flowPath
            for (i in 0 until fp.size - 1) {
                drawLine(Neon.Green, fp[i], fp[i + 1], 3f, StrokeCap.Round, alpha = 0.28f)
            }
            for (k in 0 until 6) {
                val p = lerpPoly(fp, (sim.flow + k / 6f) % 1f)
                drawCircle(Neon.Green, 8f + 3f * heat, p, alpha = 0.25f)
                drawCircle(Color(0xFFD8FFEE), 3.2f, p)
            }

            // выхлоп
            val ex = CarGeometry.exhaustPath
            for (i in 0 until ex.size - 1) {
                drawLine(Neon.Steel, ex[i], ex[i + 1], 4f, StrokeCap.Round, alpha = 0.22f)
            }
            drawRoundRect(Neon.Steel, Offset(190f, 324f), Size(110f, 24f), CornerRadius(10f), alpha = 0.8f, style = Stroke(2f))
            for (k in 0 until 7) {
                val tt = (sim.exh + k / 7f) % 1f
                val p = lerpPoly(ex, tt)
                drawCircle(Neon.Amber, 3.5f + 4f * heat, p, alpha = sin(tt * PI.toFloat()) * 0.85f)
            }
            for (k in 0 until 5) {
                val ph = (sim.exh * 0.7f + k / 5f) % 1f
                drawCircle(Color(0xFFB8C4D0), 4f + ph * 11f, Offset(100f - ph * 70f, 338f - ph * 16f), alpha = (1f - ph) * 0.30f)
            }
        }

        // колёса (передние ведущие)
        drawWheel(Offset(780f, 315f), sim.wheel, sim.flow, speed > 1f)
        drawWheel(Offset(255f, 315f), sim.wheel, sim.flow, false)
    }

    // подписи (в экранных координатах, чтобы шрифт не масштабировался)
    fun label(text: String, x: Float, y: Float, color: Color) {
        val l = tm.measure(
            text,
            TextStyle(color = color, fontSize = (size.width * 0.021f).toSp(), fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
        )
        drawText(l, topLeft = Offset(x * s, y * s))
    }
    withTransform({ scale(s, s, Offset.Zero) }) {
        drawLine(cyan, Offset(872f, 205f), Offset(872f, 150f), 1.2f, alpha = 0.6f)
        drawLine(cyan, Offset(700f, 232f), Offset(745f, 262f), 1.2f, alpha = 0.6f)
    }
    label("ДВИГАТЕЛЬ 1.6", 780f, 128f, Neon.Amber)
    label("6-АКПП · ${sim.gear}-я", 548f, 218f, Neon.Cyan)
    label("ПРИВОД: ПЕРЕДНИЙ (FWD)", 640f, 392f, Neon.Green)
    label("ВЫХЛОП", 205f, 352f, Neon.Steel)
}

// ------------------------------------------------------------------ рисование двигателя в разрезе

private val FIRE_OFFSET = floatArrayOf(0f, 540f, 180f, 360f) // цилиндры 1,2,3,4 -> порядок 1-3-4-2
private val STROKE_NAMES = arrayOf("РАБОЧИЙ ХОД", "ВЫПУСК", "ВПУСК", "СЖАТИЕ")

private fun strokeColor(i: Int): Color = when (i) {
    0 -> Neon.Amber
    1 -> Color(0xFF8A94A0)
    2 -> Neon.Cyan
    else -> Color(0xFFFFD54F)
}

private fun DrawScope.drawEngine(sim: XraySim, tm: TextMeasurer) {
    val cw = size.width / 4f
    val bw = cw * 0.46f
    val headY = cw * 0.34f
    val r = cw * 0.17f
    val l = r * 3.4f
    val ph = bw * 0.40f
    val gap = cw * 0.03f
    val cy = headY + gap + 0.55f * ph + r + l
    val rad = PI.toFloat() / 180f
    var firingIdx = -1

    // главная ось коленвала
    drawLine(Neon.Steel, Offset(cw * 0.5f, cy), Offset(cw * 3.5f, cy), cw * 0.04f, StrokeCap.Round, alpha = 0.35f)

    for (idx in 0 until 4) {
        val cx = cw * (idx + 0.5f)
        val phase = (sim.cycle - FIRE_OFFSET[idx] + 720f) % 720f
        val stroke = (phase / 180f).toInt().coerceIn(0, 3)
        val theta = (phase % 360f) * rad
        if (stroke == 0 && phase < 90f) firingIdx = idx

        val cpx = cx + r * sin(theta)
        val cpy = cy - r * cos(theta)
        val pinY = cpy - sqrt(l * l - (r * sin(theta)).let { it * it })
        val pistonTop = pinY - 0.55f * ph
        val bdcPin = cy - (l - r)
        val cylBottom = bdcPin + 0.45f * ph + cw * 0.02f
        val left = cx - bw / 2f

        // камера сгорания
        val sc = strokeColor(stroke)
        val a = when (stroke) {
            0 -> 0.20f + 0.65f * (1f - phase / 180f)
            1 -> 0.14f
            2 -> 0.22f
            else -> 0.18f + 0.40f * ((phase - 540f) / 180f)
        }
        drawRect(sc, Offset(left, headY), Size(bw, max(0f, pistonTop - headY)), alpha = a)
        if (stroke == 0 && phase < 60f) {
            drawCircle(
                Brush.radialGradient(
                    listOf(Neon.Amber.copy(alpha = 0.55f * (1f - phase / 60f)), Color.Transparent),
                    Offset(cx, headY + (pistonTop - headY) / 2f), bw,
                ),
                bw, Offset(cx, headY + (pistonTop - headY) / 2f),
            )
        }

        // стенки цилиндра и головка
        drawLine(Neon.Steel, Offset(left, headY), Offset(left, cylBottom), 3f, alpha = 0.7f)
        drawLine(Neon.Steel, Offset(left + bw, headY), Offset(left + bw, cylBottom), 3f, alpha = 0.7f)
        drawLine(Neon.Steel, Offset(left - 4f, headY), Offset(left + bw + 4f, headY), 5f, alpha = 0.9f)

        // клапаны
        val inOpen = if (stroke == 2) sin(PI.toFloat() * ((phase - 360f) / 180f)) else 0f
        val exOpen = if (stroke == 1) sin(PI.toFloat() * ((phase - 180f) / 180f)) else 0f
        for ((vx, open, col) in listOf(
            Triple(cx - bw * 0.22f, inOpen, Neon.Cyan),
            Triple(cx + bw * 0.22f, exOpen, Color(0xFF8A94A0)),
        )) {
            val vy = headY + open * cw * 0.035f
            drawLine(col, Offset(vx, headY - cw * 0.09f), Offset(vx, vy), 3f, alpha = 0.9f)
            drawLine(col, Offset(vx - bw * 0.1f, vy), Offset(vx + bw * 0.1f, vy), 4f, StrokeCap.Round)
        }

        // свеча и искра
        drawLine(Neon.Steel, Offset(cx, headY - cw * 0.07f), Offset(cx, headY + 2f), 3f, alpha = 0.8f)
        if (phase < 12f || phase > 708f) {
            for (k in 0 until 6) {
                val sa = k * TWO_PI / 6f
                drawLine(
                    Color(0xFFFFF3B0), Offset(cx, headY + 4f),
                    Offset(cx + cos(sa) * cw * 0.09f, headY + 4f + sin(sa) * cw * 0.09f), 2f, StrokeCap.Round,
                )
            }
        }

        // шатун и кривошип
        val rw = r * 1.35f
        val opp = theta / rad + 90f
        drawArc(Neon.Steel, opp - 70f, 140f, true, Offset(cx - rw, cy - rw), Size(2 * rw, 2 * rw), alpha = 0.45f)
        drawCircle(Neon.Steel, r, Offset(cx, cy), alpha = 0.35f, style = Stroke(2f))
        drawLine(
            Color(0xFFD0DAE4), Offset(cx, pinY), Offset(cpx, cpy), cw * 0.035f, StrokeCap.Round, alpha = 0.85f,
        )
        drawCircle(Neon.Amber, cw * 0.028f, Offset(cpx, cpy))
        drawCircle(Neon.Cyan, cw * 0.03f, Offset(cx, cy))

        // поршень
        drawRect(Neon.Steel, Offset(left + 3f, pistonTop), Size(bw - 6f, ph), alpha = 0.26f)
        drawRect(Neon.Cyan, Offset(left + 3f, pistonTop), Size(bw - 6f, ph), alpha = 0.95f, style = Stroke(2f))
        drawLine(Neon.Cyan, Offset(left + 3f, pistonTop + ph * 0.2f), Offset(left + bw - 3f, pistonTop + ph * 0.2f), 1.5f, alpha = 0.8f)
        drawLine(Neon.Cyan, Offset(left + 3f, pistonTop + ph * 0.34f), Offset(left + bw - 3f, pistonTop + ph * 0.34f), 1.5f, alpha = 0.8f)
        drawCircle(Color.White, cw * 0.016f, Offset(cx, pinY))

        // подписи цилиндра
        val t1 = tm.measure("ЦИЛ ${idx + 1}", TextStyle(color = Neon.Dim, fontSize = (cw * 0.085f).toSp(), fontWeight = FontWeight.Bold))
        drawText(t1, topLeft = Offset(cx - t1.size.width / 2f, cw * 0.02f))
        val t2 = tm.measure(STROKE_NAMES[stroke], TextStyle(color = sc, fontSize = (cw * 0.07f).toSp(), fontWeight = FontWeight.Black))
        drawText(t2, topLeft = Offset(cx - t2.size.width / 2f, cw * 0.14f))
    }

    // полоса порядка работы 1-3-4-2
    val order = intArrayOf(1, 3, 4, 2)
    val by = cy + r * 1.5f + cw * 0.12f
    for (k in 0 until 4) {
        val cxk = cw * (k + 0.5f)
        val active = firingIdx + 1 == order[k]
        val col = if (active) Neon.Amber else Neon.Dim
        val bwid = cw * 0.5f
        val bh = cw * 0.22f
        drawRoundRect(col, Offset(cxk - bwid / 2f, by), Size(bwid, bh), CornerRadius(bh / 3f), alpha = if (active) 0.40f else 0.08f)
        drawRoundRect(col, Offset(cxk - bwid / 2f, by), Size(bwid, bh), CornerRadius(bh / 3f), alpha = 0.9f, style = Stroke(2f))
        val tl = tm.measure("${order[k]}", TextStyle(color = if (active) Neon.Text else Neon.Dim, fontSize = (cw * 0.13f).toSp(), fontWeight = FontWeight.Black))
        drawText(tl, topLeft = Offset(cxk - tl.size.width / 2f, by + (bh - tl.size.height) / 2f))
    }
}
