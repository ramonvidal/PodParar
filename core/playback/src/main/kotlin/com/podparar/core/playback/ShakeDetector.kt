package com.podparar.core.playback

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import kotlin.math.sqrt

/**
 * Calls [onShake] when the phone is shaken: the acceleration's magnitude minus gravity goes
 * over [THRESHOLD] twice within [WINDOW_MS], at most once every [COOLDOWN_MS].
 * [start] and [stop] are called on the main thread; events arrive there too.
 */
internal class ShakeDetector(context: Context, private val onShake: () -> Unit) : SensorEventListener {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val accelerometer: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private var listening = false
    private var lastPeakAt = 0L
    private var lastShakeAt = 0L

    fun start() {
        if (listening) return
        val manager = sensorManager ?: return
        val sensor = accelerometer ?: return
        lastPeakAt = 0L
        listening = manager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
    }

    fun stop() {
        if (!listening) return
        sensorManager?.unregisterListener(this)
        listening = false
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.values.size < 3) return
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        val magnitude = sqrt(x * x + y * y + z * z) - SensorManager.GRAVITY_EARTH
        if (magnitude <= THRESHOLD) return
        val now = SystemClock.elapsedRealtime()
        val previous = lastPeakAt
        lastPeakAt = now
        if (previous == 0L || now - previous > WINDOW_MS) return
        if (lastShakeAt != 0L && now - lastShakeAt < COOLDOWN_MS) return
        lastShakeAt = now
        lastPeakAt = 0L
        onShake()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private companion object {
        const val THRESHOLD = 12f
        const val WINDOW_MS = 500L
        const val COOLDOWN_MS = 2_000L
    }
}
