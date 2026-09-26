package com.example.assignment4_task2

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.app.ActivityCompat


class MainActivity : AppCompatActivity() {

    private val PERMISSION_REQUEST_CODE = 66

    private var locationService: LocationService.LocalBinder? = null
    private var isBound = false

    private lateinit var tvStats: TextView
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button

    @RequiresApi(Build.VERSION_CODES.O)
    private val createDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/gpx+xml")
    ) { uri: Uri? ->
        if (uri != null) {
            // Persist the URI permissions as required by the assignment
            val takeFlags = Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION
            contentResolver.takePersistableUriPermission(uri, takeFlags)

            startAndBindService(uri)
        } else {
            Toast.makeText(this, "You must select a file to start tracking.", Toast.LENGTH_LONG).show()
        }
    }
 
    private val uiHandler = Handler(Looper.getMainLooper())
    private val updateUI = object : Runnable {
        override fun run() {
            if (isBound && locationService != null) {
                val lat = locationService!!.getLatitude()
                val lon = locationService!!.getLongitude()
                val dist = locationService!!.getDistance()
                val speed = locationService!!.getAverageSpeed()
                tvStats.text = "Lat: $lat\nLon: $lon\nDistance: ${dist.toInt()}m\nSpeed: ${"%.2f".format(speed)} km/h"
            }
            uiHandler.postDelayed(this, 1000)
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as LocationService.LocalBinder
            locationService = binder
            isBound = true
        }
        override fun onServiceDisconnected(arg0: ComponentName) {
            isBound = false
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStats = findViewById(R.id.tvStats)
        btnStart = findViewById(R.id.btnStart)
        btnStop = findViewById(R.id.btnStop)

        btnStart.setOnClickListener {
            if (hasRequiredPermissions()) {
                createDocumentLauncher.launch("MyTrack_${System.currentTimeMillis()}.gpx")
            } else {
                requestPermissions()
            }
        }

        btnStop.setOnClickListener {
            val serviceIntent = Intent(this, LocationService::class.java)
            if (isBound) {
                unbindService(connection)
                isBound = false
            }
            stopService(serviceIntent)
            tvStats.text = "Service Stopped."
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun startAndBindService(fileUri: Uri) {
        val serviceIntent = Intent(this, LocationService::class.java).apply {
            putExtra("GPX_URI", fileUri.toString()) // Pass URI to the service
        }
        startForegroundService(serviceIntent)

        bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE)
    }
    private fun hasRequiredPermissions(): Boolean {
        val permissions = arrayOf(Manifest.permission.FOREGROUND_SERVICE, Manifest.permission.FOREGROUND_SERVICE_LOCATION,
            Manifest.permission.POST_NOTIFICATIONS, Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION)

        return permissions.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }
    }

    private fun requestPermissions() {
        val permissions = arrayOf(Manifest.permission.FOREGROUND_SERVICE, Manifest.permission.FOREGROUND_SERVICE_LOCATION,
            Manifest.permission.POST_NOTIFICATIONS, Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION)

        ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE)
    }

    override fun onStart() {
        super.onStart()
        val intent = Intent(this, LocationService::class.java)
        // Bind automatically on app open (if service is running)
        bindService(intent, connection, Context.BIND_AUTO_CREATE)
        uiHandler.post(updateUI)
    }

    override fun onStop() {
        super.onStop()
        if (isBound) {
            unbindService(connection)
            isBound = false
        }
        uiHandler.removeCallbacks(updateUI)
    }
}



