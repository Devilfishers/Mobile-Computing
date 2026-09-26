package com.example.myapplication

import androidx.activity.ComponentActivity
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.widget.Button
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs
import kotlin.math.round

class MainActivity : ComponentActivity() {
    private lateinit var square: TextView
    private lateinit var btn: Button
    private lateinit var thresholdBar: SeekBar
    private lateinit var thresholdLabel: TextView
    private lateinit var broadcastToggle: Switch

    private var mService: ExampleService? = null
    private var isBound = false
    private var threshold = 0
    private var isReceiverRegistered = false

    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            // Check if context is valid and show toast
            val x = intent?.getFloatExtra("sensor_x", 0f) ?: 0f
            Toast.makeText(this@MainActivity, "Threshold exceeded! X = $x", Toast.LENGTH_SHORT).show()
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(p0: ComponentName?, p1: IBinder?) {
            val binder = p1 as ExampleService.SensorBinder
            mService = binder.getService()
            isBound = true

            // Sync initial states when service connects
            mService?.currentThreshold = threshold
            mService?.setBroadcastEnabledState(broadcastToggle.isChecked)

            mService?.onSensorDataChanged = { sensorData ->
                // Use runOnUiThread to ensure UI updates happen on main thread
                runOnUiThread {
                    updateUI(sensorData)
                }
            }
        }

        override fun onServiceDisconnected(p0: ComponentName?) {
            isBound = false
            mService = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main)
        square = findViewById(R.id.tv_square)
        btn = findViewById(R.id.btn)
        thresholdBar = findViewById(R.id.sb_threshold)
        thresholdLabel = findViewById(R.id.tv_threshold)
        broadcastToggle = findViewById(R.id.switch_broadcast)
        // Initial setup

        thresholdBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                threshold = progress - 15
                thresholdLabel.text = "Threshold: $threshold"
                mService?.currentThreshold = threshold
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        broadcastToggle.setOnCheckedChangeListener { _, isChecked ->
            mService?.setBroadcastEnabledState(isChecked)
        }

        btn.setOnClickListener {
            if (isBound) unbind() else binding()
        }

        registerThresholdReceiver()
    }

    private fun registerThresholdReceiver() {
        if (!isReceiverRegistered) {
            val filter = IntentFilter("THRESHOLD_EXCEEDED")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(broadcastReceiver, filter, RECEIVER_NOT_EXPORTED) //only ExampleService is allowed to send broadcasts to this receiver
            } else {
                registerReceiver(broadcastReceiver, filter)
            }
            isReceiverRegistered = true
        }
    }

    private fun updateUI(data: SensorData) {
        val dominantAxis = data.x
        // Check threshold
        val isVisible = abs(round(dominantAxis)) <= threshold
        square.visibility = if (isVisible) View.VISIBLE else View.GONE

        if (isVisible) {
            square.apply {
                translationX = data.x * 5f
                translationY = data.y * 5f
                translationZ = data.z * 5f
                rotationX = data.x * 5f
                rotationY = data.y * 5f
                rotation = data.z * 5f

                val color = if (round(dominantAxis) == 0f) Color.GREEN else Color.RED
                setTextColor(color)
                text = "X: ${String.format("%.2f", data.x)}"
            }
        }
    }

    fun binding() {
        val intent = Intent(this, ExampleService::class.java)
        bindService(intent, connection, BIND_AUTO_CREATE)
        btn.text = "Unbind"
    }

    fun unbind() {
        if (isBound) {
            mService?.onSensorDataChanged = null // Clear callback
            unbindService(connection)
            isBound = false
            mService = null
            btn.text = "Bind"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unbind()
        if (isReceiverRegistered) {
            unregisterReceiver(broadcastReceiver)
            isReceiverRegistered = false
        }
    }
}
