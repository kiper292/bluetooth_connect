package com.example.bluetooth_connect

import android.bluetooth.BluetoothDevice

data class BluetoothDeviceItem(
    val device: BluetoothDevice,
    var isConnected: Boolean
)
