package com.example.assignment4_1

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.provider.Settings
import android.util.Log
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.nio.ByteBuffer
import java.nio.ByteOrder

class MainActivity : AppCompatActivity() {

    private val PERMISSION_REQUEST_CODE = 66
    private val TAG = "EddystoneScanner"

    // Eddystone constants
    private val EDDYSTONE_SERVICE_UUID = "0000feaa-0000-1000-8000-00805f9b34fb"
    private val EDDYSTONE_UID_FRAME = 0x00.toByte()
    private val EDDYSTONE_URL_FRAME = 0x10.toByte()
    private val EDDYSTONE_TLM_FRAME = 0x20.toByte()

    // Beacon namespace
    private val EXPECTED_NAMESPACE = byteArrayOf(
        0x21.toByte(), 0x83.toByte(), 0xA8.toByte(), 0x77.toByte(),
        0xA7.toByte(), 0xB7.toByte(), 0x5D.toByte(), 0x07.toByte(),
        0x15.toByte(), 0x13.toByte()
    )

    companion object {
        // Default TX power at 1m for distance estimation (fallback if not in frame)
        private const val DEFAULT_TX_POWER_AT_1M = -65
        // Calibration offset to convert TX power at 0m (from Eddystone frame) to RSSI at 1m.
        // The Eddystone frame provides TX power at 0m (actual transmit power, e.g. -17 dBm).
        // For distance estimation we need RSSI at 1m, which is lower due to path loss.
        // Free space path loss at 1m for 2.4 GHz is ~40 dB, but indoor environments vary.
        // Calibrate by placing a beacon at 1m, noting RSSI, then set offset = TX_Power - RSSI.
        // Example: if TX=-17 and RSSI at 1m=-38, set offset = -17-(-38) = 21.
        private const val TX_POWER_CALIBRATION_OFFSET = 21
    }

    // UI elements
    private lateinit var statusText: TextView
    private lateinit var beaconsContainer: LinearLayout
    private lateinit var btnScan: Button

    // BLE
    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val manager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        manager.adapter
    }

    private var isScanning = false
    private val scanHandler = Handler(Looper.getMainLooper())
    private val SCAN_PERIOD = 10000L // 10 seconds

    // Store beacon data per device address
    private data class BeaconData(
        var namespace: String = "",
        var instance: String = "",
        var url: String = "",
        var voltage: Float = 0f,
        var temperature: Float = 0f,
        var rssi: Int = 0,
        var txPower: Int = DEFAULT_TX_POWER_AT_1M, // Calibrated TX power from frame
        var lastSeen: Long = 0
    )

    private val beaconMap = mutableMapOf<String, BeaconData>()
    private val beaconViews = mutableMapOf<String, TextView>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        beaconsContainer = findViewById(R.id.beaconsContainer)
        btnScan = findViewById(R.id.btnScan)

        btnScan.setOnClickListener {
            if (hasRequiredPermissions()) {
                toggleScan()
            } else {
                requestBlePermissions()
            }
        }
    }

    private fun hasRequiredPermissions(): Boolean {
        val permissions = arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        return permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestBlePermissions() {
        val permissions = arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE &&
            grantResults.all { it == PackageManager.PERMISSION_GRANTED }
        ) {
            toggleScan()
        } else {
            Toast.makeText(this, "Bluetooth permissions required", Toast.LENGTH_LONG).show()
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    private fun toggleScan() {
        val scanner = bluetoothAdapter?.bluetoothLeScanner
        if (scanner == null) {
            statusText.text = "Bluetooth is not available"
            Toast.makeText(this, "Bluetooth is not available on this device", Toast.LENGTH_LONG).show()
            return
        }

        // Check if Bluetooth is enabled
        if (bluetoothAdapter?.isEnabled != true) {
            statusText.text = "Please enable Bluetooth"
            Toast.makeText(this, "Please enable Bluetooth to scan for beacons", Toast.LENGTH_LONG).show()
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivity(enableBtIntent)
            return
        }

        // Check if Location is enabled (required for BLE scanning on Android 10+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            try {
                val isLocationEnabled = locationManager.isLocationEnabled
                if (!isLocationEnabled) {
                    statusText.text = "Please enable Location services"
                    Toast.makeText(this, "Location services must be enabled for BLE scanning", Toast.LENGTH_LONG).show()
                    startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                    return
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not check location enabled state", e)
            }
        }

        if (isScanning) {
            scanner.stopScan(scanCallback)
            isScanning = false
            btnScan.text = "SCAN BEACONS"
            statusText.text = "Scan stopped."
        } else {
            // Clear previous data
            beaconMap.clear()
            beaconsContainer.removeAllViews()
            beaconViews.clear()

            // Use LOW_LATENCY scan mode for more reliable beacon detection
            val scanSettings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()

            @SuppressLint("MissingPermission")
            scanner.startScan(null, scanSettings, scanCallback)
            isScanning = true
            btnScan.text = "STOP SCAN"
            statusText.text = "Scanning for Eddystone beacons..."

            // Auto-stop after SCAN_PERIOD
            scanHandler.postDelayed({
                if (isScanning) {
                    scanner.stopScan(scanCallback)
                    isScanning = false
                    btnScan.text = "SCAN BEACONS"
                    statusText.text = "Scan finished."
                }
            }, SCAN_PERIOD)
        }
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val scanRecord = result.scanRecord ?: run {
                Log.d(TAG, "No scan record for device ${result.device.address}")
                return
            }
            val deviceAddress = result.device.address
            val rssi = result.rssi

            Log.d(TAG, "Scan result from $deviceAddress, RSSI: $rssi")

            // Check for Eddystone service data (Service UUID 0xFEAA)
            val eddystoneUuid = ParcelUuid(java.util.UUID.fromString(EDDYSTONE_SERVICE_UUID))
            val serviceData = scanRecord.getServiceData(eddystoneUuid)

            if (serviceData == null) {
                // Log the service UUIDs this device advertises for debugging
                val serviceUuids = scanRecord.serviceUuids
                if (serviceUuids != null) {
                    Log.d(TAG, "Device $deviceAddress advertises UUIDs: ${serviceUuids.joinToString(", ")}")
                }
                return
            }

            Log.d(TAG, "Found Eddystone service data from $deviceAddress, data size: ${serviceData.size}")

            if (!beaconMap.containsKey(deviceAddress)) {
                beaconMap[deviceAddress] = BeaconData()
            }

            val beacon = beaconMap[deviceAddress]!!
            beacon.rssi = rssi
            beacon.lastSeen = System.currentTimeMillis()

            // Parse Eddystone frame
            parseEddystoneFrame(serviceData, beacon)

            // Update or create view for this beacon
            updateBeaconView(deviceAddress, beacon)
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "Scan failed with error code: $errorCode")
            val errorMsg = when (errorCode) {
                SCAN_FAILED_ALREADY_STARTED -> "Scan failed: already started"
                SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "Scan failed: app registration failed"
                SCAN_FAILED_INTERNAL_ERROR -> "Scan failed: internal error"
                SCAN_FAILED_FEATURE_UNSUPPORTED -> "Scan failed: feature unsupported"
                SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES -> "Scan failed: out of hardware resources"
                SCAN_FAILED_SCANNING_TOO_FREQUENTLY -> "Scan failed: scanning too frequently"
                else -> "Scan failed: unknown error ($errorCode)"
            }
            statusText.text = errorMsg
            isScanning = false
            btnScan.text = "SCAN BEACONS"
        }
    }

    private fun parseEddystoneFrame(data: ByteArray, beacon: BeaconData) {
        if (data.size < 2) return

        val frameType = data[0]

        when (frameType) {
            EDDYSTONE_UID_FRAME -> parseUidFrame(data, beacon)
            EDDYSTONE_URL_FRAME -> parseUrlFrame(data, beacon)
            EDDYSTONE_TLM_FRAME -> parseTlmFrame(data, beacon)
        }
    }

    private fun parseUidFrame(data: ByteArray, beacon: BeaconData) {
        // UID Frame: [FrameType(1) | TX Power(1) | Namespace(10) | Instance(6) | Reserved(2)]
        if (data.size < 20) return

        // Extract TX power at 0m from the frame (byte index 1, signed byte)
        val txPower = data[1].toInt() // signed byte, e.g. -20 dBm
        beacon.txPower = txPower
        Log.d(TAG, "UID frame TX power: $txPower dBm")

        val namespace = data.copyOfRange(2, 12)
        val instance = data.copyOfRange(12, 18)

        beacon.namespace = bytesToHex(namespace)
        beacon.instance = bytesToHex(instance)
    }

    private fun parseUrlFrame(data: ByteArray, beacon: BeaconData) {
        // URL Frame: [FrameType(1) | TX Power(1) | URL Scheme(1) | Encoded URL(...)]
        if (data.size < 4) return

        // Extract TX power at 0m from the frame (byte index 1, signed byte)
        val txPower = data[1].toInt() // signed byte, e.g. -20 dBm
        beacon.txPower = txPower
        Log.d(TAG, "URL frame TX power: $txPower dBm")

        val urlSchemePrefix = when (data[2].toInt()) {
            0x00 -> "http://www."
            0x01 -> "https://www."
            0x02 -> "http://"
            0x03 -> "https://"
            else -> ""
        }

        val encodedUrl = data.copyOfRange(3, data.size)
        val decodedUrl = decodeUrl(encodedUrl)

        beacon.url = urlSchemePrefix + decodedUrl
    }

    private fun decodeUrl(encoded: ByteArray): String {
        val sb = StringBuilder()
        for (byte in encoded) {
            val b = byte.toInt() and 0xFF
            sb.append(when (b) {
                0x00 -> ".com/"
                0x01 -> ".org/"
                0x02 -> ".edu/"
                0x03 -> ".net/"
                0x04 -> ".info/"
                0x05 -> ".biz/"
                0x06 -> ".gov/"
                0x07 -> ".com"
                0x08 -> ".org"
                0x09 -> ".edu"
                0x0A -> ".net"
                0x0B -> ".info"
                0x0C -> ".biz"
                0x0D -> ".gov"
                else -> b.toChar()
            })
        }
        return sb.toString()
    }

    private fun parseTlmFrame(data: ByteArray, beacon: BeaconData) {
        // TLM Frame: [FrameType(1) | Version(1) | VBatt(2) | Temp(2) | AdvCnt(4) | SecCnt(4)]
        if (data.size < 14) return

        val voltageBuf = ByteBuffer.wrap(data.copyOfRange(2, 4)).order(ByteOrder.BIG_ENDIAN)
        beacon.voltage = voltageBuf.short.toFloat() / 1000f

        // Temperature: 2 bytes, signed 8.8 fixed point
        val tempRaw = ByteBuffer.wrap(data.copyOfRange(4, 6)).order(ByteOrder.BIG_ENDIAN).short.toInt()
        beacon.temperature = tempRaw / 256.0f
    }

    private fun estimateDistance(txPower: Int, rssi: Int): Double {
        // Using the log-distance path loss model
        // distance = 10 ^ ((RSSI_AT_1M - RSSI) / (10 * n))
        // n = path loss exponent (2.0 for free space, ~3.0 for typical indoor)
        //
        // The Eddystone frame provides TX power at 0m (actual transmit power).
        // We need to convert this to RSSI at 1m by subtracting the calibration offset
        // (free space path loss at 1m for 2.4 GHz is ~40 dB).
        val n = 3.0
        val rssiAt1m = (txPower - TX_POWER_CALIBRATION_OFFSET).toDouble()
        return Math.pow(10.0, (rssiAt1m - rssi) / (10.0 * n))
    }

    private fun updateBeaconView(address: String, beacon: BeaconData) {
        val distance = estimateDistance(beacon.txPower, beacon.rssi)
        val info = buildString {
            append("Beacon: $address\n")
            if (beacon.namespace.isNotEmpty()) {
                append("Namespace: ${beacon.namespace}\n")
                append("Instance: ${beacon.instance}\n")
            }
            if (beacon.url.isNotEmpty()) {
                append("URL: ${beacon.url}\n")
            }
            if (beacon.voltage > 0) {
                append("Voltage: ${"%.3f".format(beacon.voltage)}V\n")
                append("Temperature: ${"%.1f".format(beacon.temperature)}°C\n")
            }
            append("RSSI: ${beacon.rssi} dBm\n")
            append("TX Power: ${beacon.txPower} dBm\n")
            append("Distance: ${"%.2f".format(distance)} m")
        }

        var view = beaconViews[address]
        if (view == null) {
            view = TextView(this).apply {
                setPadding(12, 12, 12, 12)
                textSize = 14f
                setBackgroundResource(android.R.drawable.list_selector_background)
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                params.setMargins(0, 0, 0, 8)
                layoutParams = params
            }
            beaconsContainer.addView(view)
            beaconViews[address] = view
        }

        view.text = info
        statusText.text = "Found ${beaconMap.size} beacon(s)"
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val sb = StringBuilder()
        for (b in bytes) {
            sb.append(String.format("%02X ", b))
        }
        return sb.toString().trimEnd()
    }

    override fun onDestroy() {
        super.onDestroy()
        bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
    }
}
