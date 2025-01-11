package com.example.bluetooth_connect

import android.bluetooth.BluetoothDevice
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import android.graphics.Color

class BluetoothDeviceAdapter(
    private val devices: List<BluetoothDeviceItem>,
    private val onDeviceClick: (BluetoothDevice) -> Unit
) : RecyclerView.Adapter<BluetoothDeviceAdapter.DeviceViewHolder>() {

    class DeviceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val nameTextView: TextView = itemView.findViewById(R.id.deviceName)
        val stateTextView: TextView = itemView.findViewById(R.id.deviceState)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_bluetooth_device, parent, false)
        return DeviceViewHolder(view)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        val deviceItem = devices[position]
        holder.nameTextView.text = deviceItem.device.name ?: "Unknown Device"
        holder.stateTextView.text = if (deviceItem.isConnected) "Connected" else "Disconnected"

        // Set the status and color
        if (deviceItem.isConnected) {
            holder.stateTextView.setTextColor(Color.GREEN)
        } else {
            holder.stateTextView.setTextColor(Color.RED)
        }

        holder.itemView.setOnClickListener {
            onDeviceClick(deviceItem.device)
        }

    }

    override fun getItemCount(): Int = devices.size
}
