package com.example.assignment3_1

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


    private val TEMP_CHAR_UUID = UUID.fromString("00002a1c-0000-1000-8000-00805f9b34fb")
    private val HUMID_CHAR_UUID = UUID.fromString("00002a6f-0000-1000-8000-00805f9b34fb")
    private val CCC_DESCRIPTOR_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    private val TAG = "BLE_Weather"
    private val PERMISSION_REQUEST_CODE = 66


    private lateinit var tempText: TextView
    private lateinit var humidityText: TextView
    private lateinit var statusText: TextView

    // Buttons
    private lateinit var btnScan: Button
    private lateinit var btnConnect: Button
    private lateinit var btnReadSensors: Button
    private lateinit var btnDisconnect: Button

    // List View Components
    private lateinit var deviceListView: ListView
    private lateinit var listAdapter: ArrayAdapter<String>
    private val scannedDevicesList = ArrayList<BluetoothDevice>()
    private val displayNamesList = ArrayList<String>()

    private var targetDevice: BluetoothDevice? = null
    private var bluetoothGatt: BluetoothGatt? = null
    private val subscriptionQueue: Queue<BluetoothGattCharacteristic> = LinkedList()

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val manager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        manager.adapter
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tempText = findViewById(R.id.tempText)
        humidityText = findViewById(R.id.humidityText)
        statusText = findViewById(R.id.statusText)

        btnScan = findViewById(R.id.btnScan)
        btnConnect = findViewById(R.id.btnConnect)
        btnReadSensors = findViewById(R.id.btnRead)
        btnDisconnect = findViewById(R.id.btnDisconnect)

        deviceListView = findViewById(R.id.deviceListView)
        listAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, displayNamesList)
        deviceListView.adapter = listAdapter

        setupListeners()
    }

    private fun setupListeners() {
        deviceListView.setOnItemClickListener { _, _, position, _ ->
            try {
                targetDevice = scannedDevicesList[position]
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

            /// other implementation for connection
            """"
            if (targetDevice != null) {
              statusText.text = "Status: Connecting..."
              connectToDevice(targetDevice!!) // Might require the !! depending on property mutability
            } else {
             Toast.makeText(this, "Select a device from the list first!", Toast.LENGTH_SHORT).show()
            }
            """
        }

        btnReadSensors.setOnClickListener {
            if (bluetoothGatt != null) {
                statusText.text = "Status: Subscribing to sensors..."
                startSensorSubscription()
            }
        }

        btnDisconnect.setOnClickListener {
            cleanupGatt()
        }
    }

    private fun hasRequiredPermissions(): Boolean {
        val permissions = arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)

        return permissions.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }
    }

    private fun requestBlePermissions() {
        val permissions = arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)

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

            if (deviceName.contains("IPVS", ignoreCase = true)) {

                if (scannedDevicesList.none { it.address == deviceAddress }) {
                    scannedDevicesList.add(result.device)
                    displayNamesList.add("$deviceName\n$deviceAddress")

                    runOnUiThread {
                        listAdapter.notifyDataSetChanged()
                    }
                }
            }
        }
    }

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
                    //btnReadSensors.isEnabled = true
                } else {
                    statusText.text = "Status: Disconnected"
                    statusText.setTextColor(Color.parseColor("#D32F2F"))
                    btnReadSensors.isEnabled = false
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                runOnUiThread {
                    statusText.text = "Status: Connected. Ready to read."
                    btnReadSensors.isEnabled = true
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            subscribeNext(gatt)
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            handleCharacteristicUpdate(characteristic, value)
        }

        private fun handleCharacteristicUpdate(characteristic: BluetoothGattCharacteristic, data: ByteArray) {
            if (data.isEmpty()) return
            runOnUiThread {
                when (characteristic.uuid) {
                    TEMP_CHAR_UUID -> {
                        val temp = parseTemperature(characteristic, data)
                        tempText.text = String.format(Locale.US, "%.1f °C", temp)
                    }
                    HUMID_CHAR_UUID -> {
                        val humidity = parseHumidity(characteristic, data)
                        humidityText.text = String.format(Locale.US, "%.1f %%", humidity)
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startSensorSubscription() {
        val gatt = bluetoothGatt ?: return
        var targetService: BluetoothGattService? = null

        for (service in gatt.services) {
            if (service.getCharacteristic(TEMP_CHAR_UUID) != null) {
                targetService = service
                break
            }
        }

        if (targetService == null) {
            runOnUiThread { statusText.text = "Status: Characteristics not found" }
            return
        }

        targetService.characteristics?.let { characteristics ->
            subscriptionQueue.clear()
            for (char in characteristics) {
                if (char.uuid == TEMP_CHAR_UUID || char.uuid == HUMID_CHAR_UUID) {
                    subscriptionQueue.add(char)
                }
            }
            runOnUiThread { statusText.text = "Status: Reading Data..." }
            subscribeNext(gatt)
        }
    }

    @SuppressLint("MissingPermission")
    private fun subscribeNext(gatt: BluetoothGatt) {
        if (subscriptionQueue.isEmpty()) return

        val char = subscriptionQueue.poll()
        gatt.setCharacteristicNotification(char, true)

        val descriptor = char.getDescriptor(CCC_DESCRIPTOR_UUID)
        if (descriptor != null) {
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            gatt.writeDescriptor(descriptor)
        } else {
            subscribeNext(gatt)
        }
    }

    private fun parseTemperature(characteristic: BluetoothGattCharacteristic, data: ByteArray): Double {
        if (data.size < 2) return (data[0].toInt() and 0xFF).toDouble()
        val tempFloat = characteristic.getFloatValue(BluetoothGattCharacteristic.FORMAT_FLOAT, 1)
        if (tempFloat != null) {
            val isFahrenheit = (data[0].toInt() and 0xFF and 0x01) != 0
            return if (isFahrenheit) (tempFloat.toDouble() - 32.0) / 1.8 else tempFloat.toDouble()
        }
        return 0.0
    }

    private fun parseHumidity(characteristic: BluetoothGattCharacteristic, data: ByteArray): Double {

        val rawHumidity = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, 0)
        return if (rawHumidity != null) rawHumidity * 0.01 else 0.0
    }

    @SuppressLint("MissingPermission")
    private fun cleanupGatt() {
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        targetDevice = null

        runOnUiThread {
            statusText.text = "Status: Disconnected"
            tempText.text = "-- °C"
            humidityText.text = "-- %"
            btnConnect.isEnabled = false
            btnReadSensors.isEnabled = false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cleanupGatt()
    }
}
