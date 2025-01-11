package com.example.bluetooth_connect

import android.Manifest
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.pm.PackageManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.Toast
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.IOException
import java.util.*

class MainActivity : AppCompatActivity() {

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action
            if (action == BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED) {
                val state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, BluetoothProfile.STATE_DISCONNECTED)
                val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)

                if (device != null) {
                    when (state) {
                        BluetoothProfile.STATE_CONNECTED -> {
                            Toast.makeText(this@MainActivity, "Connected to ${device.name}", Toast.LENGTH_SHORT).show()
                        }
                        BluetoothProfile.STATE_DISCONNECTED -> {
                            Toast.makeText(this@MainActivity, "Disconnected from ${device.name}", Toast.LENGTH_SHORT).show()
                        }
                    }
                    // update list
                    refreshDeviceList()
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
        registerReceiver(bluetoothReceiver, filter)
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(bluetoothReceiver)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // check permissions
        checkAndRequestPermissions()

        // update button
        val refreshButton = findViewById<Button>(R.id.btn_refresh)
        refreshButton.setOnClickListener {
            refreshDeviceList()
        }
    }

    private fun updateDeviceList(devices: List<BluetoothDeviceItem>) {
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        val adapter = BluetoothDeviceAdapter(devices) { device ->
            handleDeviceClick(device)
        }

        recyclerView.adapter = adapter
    }

    private fun handleDeviceClick(device: BluetoothDevice) {
        isA2dpDeviceConnected(device) { isConnected ->
            if (isConnected) {
                disconnectA2dpDevice(device)
            } else {
                connectA2dpDevice(device)
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissions = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            // For Android 12 (API 31) and above
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            )
        } else {
            // For Android 10 (API 29) and below
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN
            )
        }
        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missingPermissions.toTypedArray(), 1)
        } else {
            // If all permissions are already there, launch Bluetooth
            refreshDeviceList()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == 1) {
            // Checking if all requested permissions have been granted
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                refreshDeviceList()
            } else {
                Toast.makeText(
                    this,
                    "The app requires permissions to work with Bluetooth",
                    Toast.LENGTH_LONG
                ).show()
                finishAndRemoveTask() // exit application
            }
        }
    }

    public fun getPairedDevices(callback: (List<BluetoothDeviceItem>) -> Unit) {
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        val pairedDevices = bluetoothAdapter.bondedDevices

        val deviceItems = mutableListOf<BluetoothDeviceItem>()

        // async
        val totalDevices = pairedDevices.size
        var checkedDevices = 0

        pairedDevices.forEach { device ->
            isA2dpDeviceConnected(device) { isConnected ->
                deviceItems.add(BluetoothDeviceItem(device, isConnected))
                checkedDevices++

                // callback
                if (checkedDevices == totalDevices) {
                    runOnUiThread {
                        callback(deviceItems)
                    }
                }
            }
        }

        // empty list
        if (totalDevices == 0) {
            callback(deviceItems)
        }
    }

    private fun refreshDeviceList() {
        getPairedDevices { deviceItems ->
            runOnUiThread {
                updateDeviceList(deviceItems)
                //Toast.makeText(this, "List updated", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun isA2dpDeviceConnected(device: BluetoothDevice, callback: (Boolean) -> Unit) {
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not supported", Toast.LENGTH_SHORT).show()
            callback(false)
            return
        }

        bluetoothAdapter.getProfileProxy(this, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
                if (profile == BluetoothProfile.A2DP) {
                    val bluetoothA2dp = proxy as BluetoothA2dp
                    val connectedDevices = bluetoothA2dp.connectedDevices

                    val isConnected = connectedDevices.any { it.address == device.address }

                    // release recource
                    bluetoothAdapter.closeProfileProxy(BluetoothProfile.A2DP, proxy)

                    // callback
                    callback(isConnected)
                }
            }

            override fun onServiceDisconnected(profile: Int) {
                // A2DP service disconnected
                callback(false)
            }
        }, BluetoothProfile.A2DP)
    }

    private fun connectA2dpDevice(device: BluetoothDevice) {
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        bluetoothAdapter.getProfileProxy(this, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
                if (profile == BluetoothProfile.A2DP) {
                    val a2dp = proxy as BluetoothA2dp
                    try {
                        val method = BluetoothA2dp::class.java.getMethod("connect", BluetoothDevice::class.java)
                        method.invoke(a2dp, device)

                        bluetoothAdapter.closeProfileProxy(BluetoothProfile.A2DP, proxy)
                        Toast.makeText(this@MainActivity, "Connecting to ${device.name}...", Toast.LENGTH_SHORT).show()

                    } catch (e: Exception) {
                        e.printStackTrace()
                        Toast.makeText(this@MainActivity, "Connect error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            override fun onServiceDisconnected(profile: Int) {
                // A2DP service disconnected
            }
        }, BluetoothProfile.A2DP)
    }

    private fun disconnectA2dpDevice(device: BluetoothDevice) {
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        bluetoothAdapter.getProfileProxy(this, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
                if (profile == BluetoothProfile.A2DP) {
                    val a2dp = proxy as BluetoothA2dp
                    try {
                        val method = BluetoothA2dp::class.java.getMethod("disconnect", BluetoothDevice::class.java)
                        method.invoke(a2dp, device)
                        Toast.makeText(this@MainActivity, "Disconnecting from ${device.name}", Toast.LENGTH_SHORT).show()

                    } catch (e: Exception) {
                        e.printStackTrace()
                        Toast.makeText(this@MainActivity, "Disconnect error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            override fun onServiceDisconnected(profile: Int) {
                // A2DP service disconnected
            }
        }, BluetoothProfile.A2DP)
    }

}
