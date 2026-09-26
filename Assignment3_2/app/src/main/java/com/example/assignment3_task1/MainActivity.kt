package com.example.assignment3_task1

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.*
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.*
import kotlin.collections.ArrayList

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "BLE Motor App"
        //private val CCC_DESCRIPTOR_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        private const val PERMISSION_REQUEST_CODE = 101
        private val INTENSITY_CHAR_UUID = UUID.fromString("10000001-0000-0000-FDFD-FDFDFDFDFDFD")
    }

    private lateinit var statusText: TextView

    // Buttons
    private lateinit var btnScan: Button
    private lateinit var btnConnect: Button
    private lateinit var btnDisconnect: Button
    private lateinit var btnSendIntensity: Button

    // List View Components
    private lateinit var deviceListView: ListView
    private lateinit var listAdapter: ArrayAdapter<String>
    private val scannedDevicesList = ArrayList<BluetoothDevice>()
    private val displayNamesList = ArrayList<String>()

    // Intensity slider
    private lateinit var intensitySeekBar: SeekBar
    private lateinit var intensityValueText: TextView

    private var targetDevice: BluetoothDevice? = null
    private var bluetoothGatt: BluetoothGatt? = null
    private var intensityCharacteristic: BluetoothGattCharacteristic? = null

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val manager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        manager.adapter
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)

        // Set top margin to 10dp
        val params = statusText.layoutParams as LinearLayout.LayoutParams
        params.topMargin = (10 * resources.displayMetrics.density).toInt()
        statusText.layoutParams = params

        btnScan = findViewById(R.id.btnScan)
        btnConnect = findViewById(R.id.btnConnect)
        btnDisconnect = findViewById(R.id.btnDisconnect)
        btnSendIntensity = findViewById(R.id.btnSendIntensity)

        deviceListView = findViewById(R.id.deviceListView)
        listAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, displayNamesList)
        deviceListView.adapter = listAdapter

        // Intensity slider
        intensitySeekBar = findViewById(R.id.intensitySeekBar)
        intensityValueText = findViewById(R.id.intensityValueText)

        setupListeners()
    }

    private fun setupListeners() {
        deviceListView.setOnItemClickListener { _, _, position, _ ->
            try {// Set the target device based on what the user clicked
                targetDevice = scannedDevicesList[position]

                // Stop scanning to save battery and reduce interference
                bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)

                statusText.text = "Status: Selected ${targetDevice?.name}. Ready to connect."
                btnConnect.isEnabled = true}
            catch(e: SecurityException) {
                Log.e(TAG, "Missing permission to read device name", e)
            }

            Toast.makeText(this, "Selected ${targetDevice?.address}", Toast.LENGTH_SHORT).show()
        }

        btnScan.setOnClickListener {
            if (hasRequiredPermissions()) startBleScan() else requestBlePermissions()
        }

        btnConnect.setOnClickListener {
            targetDevice?.let { device ->
                statusText.text = "Status: Connecting..."
                connectToDevice(device)
            } ?: Toast.makeText(this, "Select a device from the list first!", Toast.LENGTH_SHORT).show()
        }


        btnDisconnect.setOnClickListener {
            cleanupGatt()
        }

        // Intensity slider listener - update the displayed value as user drags
        intensitySeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                intensityValueText.text = progress.toString()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                // No action needed
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                // No action needed
            }
        })

        // Send intensity button
        btnSendIntensity.setOnClickListener {
            sendIntensity(intensitySeekBar.progress)
        }
    }

    private fun hasRequiredPermissions(): Boolean {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        return permissions.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }
    }

    private fun requestBlePermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            startBleScan()
        }
    }

    @SuppressLint("MissingPermission")
    private fun startBleScan() {
        val scanner = bluetoothAdapter?.bluetoothLeScanner ?: return

        // Reset the lists for a fresh scan
        scannedDevicesList.clear()
        displayNamesList.clear()
        listAdapter.notifyDataSetChanged()
        targetDevice = null
        btnConnect.isEnabled = false

        statusText.text = "Status: Scanning for IPVS devices..."
        statusText.setTextColor(Color.parseColor("#757575"))

        scanner.startScan(null, ScanSettings.Builder().build(), scanCallback)
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val deviceName = result.device.name ?: result.scanRecord?.deviceName ?: "Unknown"
            val deviceAddress = result.device.address

            // Filter: Only show IPVS devices
            if (deviceName.contains("IPVS", ignoreCase = true)) {
                // Prevent duplicate entries in the list
                if (scannedDevicesList.none { it.address == deviceAddress }) {
                    scannedDevicesList.add(result.device)
                    // Add to display list in a clean format
                    displayNamesList.add("$deviceName\n$deviceAddress")

                    runOnUiThread {
                        listAdapter.notifyDataSetChanged()
                    }
                }
            }
        }
    }

    // --- CONNECTING ---
    @SuppressLint("MissingPermission")
    private fun connectToDevice(device: BluetoothDevice) {
        bluetoothGatt = device.connectGatt(this, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            runOnUiThread {
                if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                    statusText.text = "Status: Connected (Discovering Services...)"
                    statusText.setTextColor(Color.parseColor("#2E7D32"))
                    gatt.discoverServices()
                } else {
                    statusText.text = "Status: Disconnected"
                    statusText.setTextColor(Color.parseColor("#D32F2F"))
                    runOnUiThread {
                        btnSendIntensity.isEnabled = false
                    }
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                // Search all services for the intensity characteristic
                var found = false
                for (service in gatt.services) {
                    val char = service.getCharacteristic(INTENSITY_CHAR_UUID)
                    if (char != null) {
                        intensityCharacteristic = char
                        found = true
                        Log.d(TAG, "Found intensity characteristic in service: ${service.uuid}")
                        break
                    }
                }

                runOnUiThread {
                    if (found) {
                        statusText.text = "Status: Connected. Intensity control ready."
                        btnSendIntensity.isEnabled = true
                    } else {
                        statusText.text = "Status: Connected. Intensity characteristic not found!"
                        btnSendIntensity.isEnabled = false
                    }
                }
            } else {
                runOnUiThread {
                    statusText.text = "Status: Service discovery failed"
                    statusText.setTextColor(Color.parseColor("#D32F2F"))
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            //subscribeNext(gatt)
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            handleCharacteristicUpdate(characteristic, value)
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            val data = characteristic.value ?: byteArrayOf()
            handleCharacteristicUpdate(characteristic, data)
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            runOnUiThread {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    statusText.text = "Status: Intensity value sent successfully"
                    statusText.setTextColor(Color.parseColor("#2E7D32"))
                } else {
                    statusText.text = "Status: Failed to write intensity (status=$status)"
                    statusText.setTextColor(Color.parseColor("#D32F2F"))
                }
            }
        }
    }

    /**
     * Send the intensity value to the BLE device.
     * The value is written as a uint16 in little-endian format.
     */
    @SuppressLint("MissingPermission")
    private fun sendIntensity(value: Int) {
        val characteristic = intensityCharacteristic
        if (characteristic == null) {
            Toast.makeText(this, "Not connected or characteristic not found", Toast.LENGTH_SHORT).show()
            return
        }

        // Convert to uint16 little-endian byte array
        val bytes = ByteArray(2)
        bytes[0] = (value and 0xFF).toByte()
        bytes[1] = ((value shr 8) and 0xFF).toByte()

        characteristic.value = bytes
        val success = bluetoothGatt?.writeCharacteristic(characteristic)

        if (success == true) {
            Log.d(TAG, "Write initiated for intensity value: $value")
            statusText.text = "Status: Sending intensity $value..."
        } else {
            Log.e(TAG, "Failed to initiate write for intensity value: $value")
            Toast.makeText(this, "Failed to send intensity", Toast.LENGTH_SHORT).show()
        }
    }

    
    @SuppressLint("MissingPermission")
    private fun cleanupGatt() {
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        targetDevice = null
        intensityCharacteristic = null

        runOnUiThread {
            statusText.text = "Status: Disconnected"
            btnConnect.isEnabled = false
            btnSendIntensity.isEnabled = false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cleanupGatt()
    }
}
