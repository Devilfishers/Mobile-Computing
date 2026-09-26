package com.example.myapplication

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import kotlin.math.abs

data class SensorData(val x: Float, val y: Float, val z: Float)

class ExampleService : Service(), SensorEventListener {
    private var startMode: Int = 0
    private lateinit var sensorManager: SensorManager
    private var mSensor: Sensor? = null
    private val binder = SensorBinder()
    var onSensorDataChanged: ((SensorData) -> Unit)? = null

    // Task 2c Implementation
    var broadcastEnabled: Boolean = false
    var currentThreshold: Int = 0
    private var wasAboveThreshold = false

    fun setBroadcastEnabledState(enabled: Boolean) {
        broadcastEnabled = enabled
    }

    private fun checkThresholdAndBroadcast(data: SensorData) {
        // Use consistent abs() logic without rounding for the broadcast
        val isAboveThreshold = abs(data.x) > currentThreshold

        if (broadcastEnabled && isAboveThreshold && !wasAboveThreshold) {
            val intent = Intent("THRESHOLD_EXCEEDED").apply {
                putExtra("sensor_x", data.x)
                putExtra("sensor_y", data.y)
                putExtra("sensor_z", data.z)
                // Set package to ensure it only goes to our own app
                setPackage(packageName)
            }
            sendBroadcast(intent)
        }
        wasAboveThreshold = isAboveThreshold
    }

    inner class SensorBinder : Binder() {
        fun getService(): ExampleService = this@ExampleService
    }

    override fun onBind(p0: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        mSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        mSensor?.also { sensor ->
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onAccuracyChanged(p0: Sensor?, p1: Int) {}

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let { sensorEvent ->
            if (sensorEvent.sensor == mSensor) {
                val sensorData = SensorData(event.values[0], event.values[1], event.values[2])
                checkThresholdAndBroadcast(sensorData)
                onSensorDataChanged?.invoke(sensorData)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = startMode

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
    }
}
