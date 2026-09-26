package com.example.assignment4_task2

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.location.Location
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class LocationService : Service() {

    private val binder = LocalBinder()

    // Tracking Math Variables
    private var startLocation: Location? = null
    private var lastLocation: Location? = null
    private var currentPosition: Location? = null
    private var travelledDistance: Float = 0f
    private var startTimeMillis: Long = 0L

    // Google API Variables
    private lateinit var locationProviderClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    // File System Buffer Variables
    private var gpxUriString: String? = null
    private val locationBuffer = mutableListOf<Location>()
    private val BATCH_SIZE = 5 // Low I/O constraint: Write every 5 updates

    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    inner class LocalBinder : Binder() {
        fun getLongitude(): Double = currentPosition?.longitude ?: 0.0
        fun getLatitude(): Double = currentPosition?.latitude ?: 0.0
        fun getDistance(): Float = travelledDistance
        fun getAverageSpeed(): Float {
            if (startTimeMillis == 0L || currentPosition == null) return 0f
            val hours = (System.currentTimeMillis() - startTimeMillis) / 3600000.0f
            if (hours == 0f) return 0f
            return (travelledDistance / 1000f) / hours
        }
    }

    override fun onCreate() {
        super.onCreate()
        locationProviderClient = LocationServices.getFusedLocationProviderClient(this)

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                for (location in result.locations) {
                    processNewLocation(location)
                }
            }
        }
    }

    private fun processNewLocation(location: Location) {
        currentPosition = location

        if (startLocation == null) {
            startLocation = location
            lastLocation = location
            startTimeMillis = System.currentTimeMillis()
        } else {
            travelledDistance += lastLocation!!.distanceTo(location)
            lastLocation = location
        }

        locationBuffer.add(location)

        if (locationBuffer.size >= BATCH_SIZE) {
            flushBufferToFile()
        }

        val directDistanceToStart = startLocation?.distanceTo(location) ?: 0f
        updateNotification("Distance to start: ${directDistanceToStart.toInt()} meters")
    }

    private fun flushBufferToFile() {
        if (locationBuffer.isEmpty() || gpxUriString == null) return

        val sb = java.lang.StringBuilder()
        for (location in locationBuffer) {
            val timeString = dateFormatter.format(Date(location.time))
            sb.append("""            <trkpt lat="${location.latitude}" lon="${location.longitude}">
                <time>$timeString</time>
            </trkpt>
""")
        }

        appendStringToFile(sb.toString())
        locationBuffer.clear()
    }

    private fun appendStringToFile(data: String) {
        if (gpxUriString == null) return
        try {
            val uri = Uri.parse(gpxUriString)
            // "wa" = Write + Append. Essential for keeping the file intact!
            contentResolver.openOutputStream(uri, "wa")?.use { outputStream ->
                outputStream.write(data.toByteArray())
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @SuppressLint("MissingPermission")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        startForeground(1, buildNotification("Waiting for GPS..."))

        // Extract the URI sent from the Activity
        gpxUriString = intent?.getStringExtra("GPX_URI")


        val header = """<?xml version="1.0" encoding="UTF-8"?>
<gpx version="1.1" creator="MyAndroidTracker"
    xmlns="http://www.topografix.com/GPX/1/1"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd">
    <trk>
        <name>Assignment Track</name>
        <trkseg>
"""
        appendStringToFile(header)

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000L).apply {
            setMinUpdateIntervalMillis(5000L)
            setMinUpdateDistanceMeters(3f)
        }.build()

        locationProviderClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        locationProviderClient.removeLocationUpdates(locationCallback)

        flushBufferToFile()

        val footer = """        </trkseg>
    </trk>
</gpx>"""
        appendStringToFile(footer)
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    private fun buildNotification(text: String) = NotificationCompat.Builder(this, "LOCATION_CHANNEL")
        .setContentTitle("Tracking Location")
        .setContentText(text)
        .setSmallIcon(android.R.drawable.ic_menu_mylocation)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        .build()

    private fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(1, buildNotification(text))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("LOCATION_CHANNEL", "Location Tracking", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}

