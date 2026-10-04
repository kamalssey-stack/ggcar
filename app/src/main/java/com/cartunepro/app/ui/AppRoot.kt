package com.cartunepro.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.cartunepro.app.data.TelemetryViewModel
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val TABS = listOf("ГОНКА", "G-СИЛА", "X-RAY")

@Composable
fun AppRoot(vm: TelemetryViewModel) {
    val t by vm.ui.collectAsState()
    val ctx = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }

    // разрешение на геолокацию
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) vm.resume(true)
    }
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            ctx, Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) vm.resume(true) else launcher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    // стартовый «тест диодов» как на руле болида + плавное появление
    val sweep = remember { Animatable(0f) }
    val intro = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        intro.animateTo(1f, tween(500))
    }
    LaunchedEffect(Unit) {
        sweep.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
        sweep.animateTo(0f, tween(700, easing = FastOutSlowInEasing))
    }

    val carbon = carbonBrush()
    Box(
        Modifier
            .fillMaxSize()
            .background(carbon)
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        listOf(Color(0x3300E5FF), Color.Transparent),
                        center = Offset(size.width / 2f, 0f), radius = size.width,
                    ),
                )
                drawRect(
                    Brush.radialGradient(
                        listOf(Color.Transparent, Color(0xDD000000)),
                        center = Offset(size.width / 2f, size.height / 2f),
                        radius = size.maxDimension * 0.75f,
                    ),
                )
            },
    ) {
        val landscape = LocalConfiguration.current.let { it.screenWidthDp > it.screenHeightDp }
        val pages: @Composable () -> Unit = {
            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    val dir = if (targetState > initialState) 1 else -1
                    (slideInHorizontally(tween(380, easing = FastOutSlowInEasing)) { it / 4 * dir } +
                        fadeIn(tween(380)))
                        .togetherWith(
                            slideOutHorizontally(tween(380, easing = FastOutSlowInEasing)) { -it / 4 * dir } +
                                fadeOut(tween(200)),
                        )
                },
                label = "tabs",
            ) { page ->
                when (page) {
                    0 -> RaceScreen(t, sweep.value, landscape) { vm.toggleRun() }
                    1 -> GForceScreen(t, landscape) { vm.calibrate() }
                    else -> XrayScreen(t, landscape)
                }
            }
        }
        if (landscape) {
            Row(Modifier.fillMaxSize().systemBarsPadding().alpha(intro.value)) {
                SideRail(tab) { tab = it }
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    Header(demo = t.demo, gps = t.gpsFix, onDemo = { vm.setDemo(!t.demo) }, compact = true)
                    Box(Modifier.weight(1f).fillMaxWidth()) { pages() }
                }
            }
        } else {
            Column(Modifier.fillMaxSize().systemBarsPadding().alpha(intro.value)) {
                Header(demo = t.demo, gps = t.gpsFix, onDemo = { vm.setDemo(!t.demo) }, compact = false)
                Box(Modifier.weight(1f).fillMaxWidth()) { pages() }
                BottomBar(tab) { tab = it }
            }
        }
    }
}

/** Боковая панель навигации для широкого экрана магнитолы: крупные зоны нажатия. */
@Composable
private fun SideRail(sel: Int, onSel: (Int) -> Unit) {
    Column(
        Modifier
            .padding(start = 10.dp, top = 10.dp, bottom = 10.dp)
            .width(92.dp)
            .fillMaxHeight()
            .glass(26.dp),
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TABS.forEachIndexed { i, name ->
            val active = i == sel
            val color by animateColorAsState(if (active) Neon.Cyan else Neon.Dim, tween(300), label = "rc")
            val sc by animateFloatAsState(
                if (active) 1.15f else 1f,
                spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "rs",
            )
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSel(i) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                NavIcon(i, color, Modifier.size(34.dp).scale(sc))
                Spacer(Modifier.height(4.dp))
                Text(
                    name,
                    style = TextStyle(color = color, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp),
                )
            }
        }
    }
}

@Composable
private fun Header(demo: Boolean, gps: Boolean, onDemo: () -> Unit, compact: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = if (compact) 6.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "CARTUNE",
                    style = TextStyle(
                        color = Neon.Text, fontSize = 20.sp, fontWeight = FontWeight.Black,
                        letterSpacing = 3.sp,
                    ),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "PRO",
                    style = TextStyle(
                        color = Neon.Cyan, fontSize = 20.sp, fontWeight = FontWeight.Black,
                        letterSpacing = 3.sp,
                    ),
                )
            }
            Text(
                "KIA SOUL",
                style = TextStyle(color = Neon.Dim, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp),
            )
        }
        Spacer(Modifier.weight(1f))

        val gpsColor by animateColorAsState(if (gps) Neon.Green else Neon.Amber, tween(300), label = "gpsc")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(gpsColor))
            Spacer(Modifier.width(6.dp))
            Text(
                if (gps) "GPS" else "ПОИСК GPS",
                style = TextStyle(color = gpsColor, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp),
            )
        }
        Spacer(Modifier.width(12.dp))
        val c = if (demo) Neon.Amber else Neon.Dim
        Box(
            Modifier
                .glass(14.dp, c, if (demo) 0.5f else 0f)
                .clickable { onDemo() }
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Text(
                "DEMO",
                style = TextStyle(color = c, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp),
            )
        }
    }
}

@Composable
private fun BottomBar(sel: Int, onSel: (Int) -> Unit) {
    Row(
        Modifier
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .fillMaxWidth()
            .height(66.dp)
            .glass(26.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TABS.forEachIndexed { i, name ->
            val active = i == sel
            val color by animateColorAsState(if (active) Neon.Cyan else Neon.Dim, tween(300), label = "tc")
            val sc by animateFloatAsState(
                if (active) 1.15f else 1f,
                spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "ts",
            )
            val lineW by animateDpAsState(if (active) 24.dp else 0.dp, tween(300), label = "tl")
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSel(i) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
            ) {
                NavIcon(i, color, Modifier.size(26.dp).scale(sc))
                Spacer(Modifier.height(3.dp))
                Text(
                    name,
                    style = TextStyle(color = color, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp),
                )
                Spacer(Modifier.height(3.dp))
                Box(Modifier.width(lineW).height(2.dp).clip(CircleShape).background(color))
            }
        }
    }
}

@Composable
private fun NavIcon(kind: Int, color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val r = size.minDimension / 2f * 0.9f
        val sw = 2.2f.dp.toPx()
        val rad = (PI / 180.0).toFloat()
        when (kind) {
            0 -> { // спидометр
                drawArc(color, 150f, 240f, false, Offset(c.x - r, c.y - r), androidx.compose.ui.geometry.Size(2 * r, 2 * r), style = Stroke(sw, cap = StrokeCap.Round))
                val a = 300f * rad
                drawLine(color, c, c + Offset(cos(a), sin(a)) * r * 0.7f, sw, StrokeCap.Round)
                drawCircle(color, sw * 1.1f, c)
            }
            1 -> { // G-круг
                drawCircle(color, r, c, style = Stroke(sw))
                drawCircle(color, r * 0.5f, c, alpha = 0.5f, style = Stroke(sw * 0.6f))
                drawCircle(color, sw * 1.4f, c + Offset(r * 0.25f, -r * 0.2f))
            }
            else -> { // шестерня
                drawCircle(color, r * 0.55f, c, style = Stroke(sw))
                drawCircle(color, r * 0.2f, c)
                for (k in 0 until 8) {
                    val a = k * 45f * rad
                    val d = Offset(cos(a), sin(a))
                    drawLine(color, c + d * r * 0.65f, c + d * r, sw * 1.4f, StrokeCap.Round)
                }
            }
        }
    }
}
