package com.cartunepro.app.data

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin

enum class RunState { IDLE, ARMED, RUNNING, DONE }

data class Telemetry(
    val speed: Float = 0f,          // км/ч
    val gx: Float = 0f,             // боковая перегрузка, g (вправо +)
    val gy: Float = 0f,             // продольная перегрузка, g (разгон +)
    val gpsFix: Boolean = false,
    val hasAccel: Boolean = true,   // false на большинстве магнитол: g считается по GPS
    val demo: Boolean = false,
    val state: RunState = RunState.IDLE,
    val elapsed: Float = 0f,        // секунды
    val t50: Float? = null,
    val t100: Float? = null,
    val maxAccel: Float = 0f,
    val maxBrake: Float = 0f,
    val maxLeft: Float = 0f,
    val maxRight: Float = 0f,
)

class TelemetryViewModel(app: Application) : AndroidViewModel(app) {

    private val _ui = MutableStateFlow(Telemetry())
    val ui: StateFlow<Telemetry> = _ui.asStateFlow()

    private val lm = app.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val sm = app.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private var demoJob: Job? = null
    private var gpsOn = false
    private var sensorsOn = false

    private var lastV = 0f
    private var lastT = 0L
    private var startT = 0L
    private var lastFixMs = 0L

    private var fx = 0f
    private var fy = 0f
    private var offX = 0f
    private var offY = 0f

    private val grav = FloatArray(3)

    private val linAcc = sm.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
    private val gravSensor = sm.getDefaultSensor(Sensor.TYPE_GRAVITY)

    private var prevLoc: Location? = null

    init {
        _ui.update { it.copy(hasAccel = linAcc != null) }
        viewModelScope.launch {
            while (true) {
                delay(33)
                tick()
            }
        }
    }

    // ---------------------------------------------------------------- управление

    fun resume(gpsAllowed: Boolean) {
        if (gpsAllowed) startGps()
        startSensors()
    }

    fun pause() {
        if (gpsOn) {
            lm.removeUpdates(locationListener)
            gpsOn = false
        }
        if (sensorsOn) {
            sm.unregisterListener(sensorListener)
            sensorsOn = false
        }
    }

    fun toggleRun() {
        val s = _ui.value
        when (s.state) {
            RunState.IDLE -> _ui.update {
                it.copy(state = RunState.ARMED, t50 = null, t100 = null, elapsed = 0f)
            }
            RunState.ARMED -> _ui.update { it.copy(state = RunState.IDLE) }
            RunState.RUNNING, RunState.DONE -> _ui.update {
                it.copy(state = RunState.IDLE, t50 = null, t100 = null, elapsed = 0f)
            }
        }
    }

    fun setDemo(on: Boolean) {
        lastV = 0f
        lastT = 0L
        fx = 0f
        fy = 0f
        _ui.update {
            it.copy(
                demo = on, speed = 0f, gx = 0f, gy = 0f,
                state = RunState.IDLE, t50 = null, t100 = null, elapsed = 0f,
            )
        }
        if (on) {
            startDemo()
        } else {
            demoJob?.cancel()
            demoJob = null
        }
    }

    fun calibrate() {
        offX = fx
        offY = fy
        _ui.update {
            it.copy(gx = 0f, gy = 0f, maxAccel = 0f, maxBrake = 0f, maxLeft = 0f, maxRight = 0f)
        }
    }

    // ---------------------------------------------------------------- GPS

    @SuppressLint("MissingPermission")
    private fun startGps() {
        if (gpsOn) return
        try {
            lm.requestLocationUpdates(
                LocationManager.GPS_PROVIDER, 0L, 0f, locationListener, Looper.getMainLooper(),
            )
            gpsOn = true
        } catch (_: SecurityException) {
        } catch (_: IllegalArgumentException) {
        }
    }

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            if (_ui.value.demo) return
            lastFixMs = SystemClock.elapsedRealtime()
            val prev = prevLoc
            prevLoc = location
            // у части магнитол GPS отдаёт только координаты — тогда скорость считаем по перемещению
            val ms = when {
                location.hasSpeed() -> location.speed
                prev != null && location.elapsedRealtimeNanos > prev.elapsedRealtimeNanos ->
                    prev.distanceTo(location) /
                        ((location.elapsedRealtimeNanos - prev.elapsedRealtimeNanos) / 1e9f)
                else -> return
            }
            onSample(ms * 3.6f, location.elapsedRealtimeNanos)
        }

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    // ---------------------------------------------------------------- датчики

    private fun startSensors() {
        if (sensorsOn) return
        gravSensor?.let { sm.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_GAME) }
        linAcc?.let { sm.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_GAME) }
        sensorsOn = true
    }

    private val sensorListener = object : SensorEventListener {
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

        override fun onSensorChanged(e: SensorEvent) {
            if (_ui.value.demo) return
            when (e.sensor.type) {
                Sensor.TYPE_GRAVITY -> System.arraycopy(e.values, 0, grav, 0, 3)
                Sensor.TYPE_LINEAR_ACCELERATION -> {
                    // телефон лежит плашмя -> вперёд = +Y, стоит в держателе экраном к водителю -> вперёд = -Z
                    val flat = abs(grav[2]) > abs(grav[1])
                    val lat = e.values[0] / 9.81f
                    val lon = (if (flat) e.values[1] else -e.values[2]) / 9.81f
                    pushG(lat, lon, 0.18f)
                }
            }
        }
    }

    private fun pushG(rx: Float, ry: Float, alpha: Float) {
        fx += alpha * (rx - fx)
        fy += alpha * (ry - fy)
        val gx = fx - offX
        val gy = fy - offY
        _ui.update {
            it.copy(
                gx = gx, gy = gy,
                maxAccel = max(it.maxAccel, gy),
                maxBrake = max(it.maxBrake, -gy),
                maxLeft = max(it.maxLeft, -gx),
                maxRight = max(it.maxRight, gx),
            )
        }
    }

    // ---------------------------------------------------------------- замер

    private fun onSample(v: Float, tNs: Long) {
        val s = _ui.value
        var st = s.state
        var t50 = s.t50
        var t100 = s.t100
        var el = s.elapsed

        if (lastT != 0L && tNs > lastT) {
            if (st == RunState.ARMED && lastV < START_KMH && v >= START_KMH) {
                val f = if (v > lastV) ((START_KMH - lastV) / (v - lastV)).coerceIn(0f, 1f) else 1f
                startT = lastT + ((tNs - lastT) * f).toLong()
                st = RunState.RUNNING
            }
            if (st == RunState.RUNNING) {
                val pv = lastV
                val pt = lastT
                fun cross(th: Float): Float? {
                    if (pv < th && v >= th) {
                        val f = (th - pv) / (v - pv)
                        return (pt + ((tNs - pt) * f).toLong() - startT) / 1e9f
                    }
                    return null
                }
                if (t50 == null) cross(50f)?.let { t50 = it }
                if (t100 == null) {
                    cross(100f)?.let {
                        t100 = it
                        el = it
                        st = RunState.DONE
                    }
                }
            }
        }
        // нет акселерометра — продольную g берём из изменения GPS-скорости
        if (linAcc == null && !s.demo && lastT != 0L && tNs > lastT) {
            val a = (v - lastV) / 3.6f / ((tNs - lastT) / 1e9f) / 9.81f
            pushG(0f, a.coerceIn(-1.5f, 1.5f), 0.5f)
        }
        lastV = v
        lastT = tNs
        val finalT100 = t100
        _ui.update {
            it.copy(
                speed = v, state = st, t50 = t50, t100 = finalT100,
                elapsed = if (st == RunState.DONE) el else it.elapsed,
            )
        }
    }

    private fun tick() {
        val s = _ui.value
        var elapsed = s.elapsed
        var state = s.state
        if (state == RunState.RUNNING) {
            elapsed = (SystemClock.elapsedRealtimeNanos() - startT) / 1e9f
            if (elapsed > 60f) {
                state = RunState.IDLE
                elapsed = 0f
            }
        }
        val fix = s.demo || (SystemClock.elapsedRealtime() - lastFixMs < 3000L && lastFixMs != 0L)
        if (elapsed != s.elapsed || fix != s.gpsFix || state != s.state) {
            _ui.update { it.copy(elapsed = elapsed, gpsFix = fix, state = state) }
        }
    }

    // ---------------------------------------------------------------- демо-режим

    private fun startDemo() {
        demoJob?.cancel()
        demoJob = viewModelScope.launch {
            var v = 0f
            var phase = 0
            var timer = 0f
            var total = 0f
            val dt = 0.02f
            while (true) {
                delay(20)
                timer += dt
                total += dt
                var a = 0f // км/ч в секунду
                when (phase) {
                    0 -> if (timer > 2.5f) { phase = 1; timer = 0f }
                    1 -> {
                        a = 11.5f - 0.045f * v
                        if (v >= 125f) { phase = 2; timer = 0f }
                    }
                    else -> {
                        a = -28f
                        if (v <= 0.2f) { v = 0f; phase = 0; timer = 0f }
                    }
                }
                v = (v + a * dt).coerceAtLeast(0f)
                val gy = a / 3.6f / 9.81f
                val gx = if (v > 15f) 0.32f * sin(total * 0.9f) else 0f
                pushG(gx, gy, 0.35f)
                onSample(v, SystemClock.elapsedRealtimeNanos())
            }
        }
    }

    override fun onCleared() {
        pause()
        super.onCleared()
    }

    companion object {
        const val START_KMH = 1.5f
    }
}
