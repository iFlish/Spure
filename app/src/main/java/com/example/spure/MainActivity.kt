package com.example.bluetoothhc05classic

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.*

class MainActivity : ComponentActivity() {

    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var bluetoothSocket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    private val handler = Handler(Looper.getMainLooper())

    private val HC05_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request permissions if necessary
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.BLUETOOTH_CONNECT),
                1
            )
        }

        setContent {
            BluetoothScreen()
        }
    }

    @Composable
    fun BluetoothScreen() {
        var isConnected by remember { mutableStateOf(false) }
        var receivedText by remember { mutableStateOf("") }
        var connectionStatus by remember { mutableStateOf("Not connected") }
        var showDeviceDialog by remember { mutableStateOf(false) }
        var deviceList by remember { mutableStateOf(listOf<BluetoothDevice>()) }

        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = connectionStatus, color = Color.Blue)
            Spacer(modifier = Modifier.height(8.dp))

            Button(onClick = {
                if (isConnected) {
                    disconnectBluetooth()
                    isConnected = false
                    connectionStatus = "Disconnected"
                } else {
                    deviceList = bluetoothAdapter?.bondedDevices?.toList() ?: listOf()
                    showDeviceDialog = true
                }
            }) {
                Text(if (isConnected) "Disconnect" else "Select Device")
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text("Received:", color = Color.Gray)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = receivedText, color = Color.Black)

            if (showDeviceDialog) {
                AlertDialog(
                    onDismissRequest = { showDeviceDialog = false },
                    confirmButton = {},
                    title = { Text("Select Device") },
                    text = {
                        Column {
                            deviceList.forEach { device ->
                                TextButton(onClick = {
                                    showDeviceDialog = false
                                    connectionStatus = "Connecting to ${device.name}"
                                    connectToDevice(device, {
                                        isConnected = true
                                        connectionStatus = "Connected to ${device.name}"
                                    }, {
                                        connectionStatus = "Failed to connect: $it"
                                    }) {
                                        receivedText = it
                                    }
                                }) {
                                    Text("${device.name} (${device.address})")
                                }
                            }
                        }
                    }
                )
            }
        }
    }

    private fun connectToDevice(
        device: BluetoothDevice,
        onConnected: () -> Unit,
        onError: (String) -> Unit,
        onDataReceived: (String) -> Unit
    ) {
        Thread {
            try {
                bluetoothSocket = device.createRfcommSocketToServiceRecord(HC05_UUID)
                bluetoothAdapter?.cancelDiscovery()
                bluetoothSocket?.connect()

                inputStream = bluetoothSocket?.inputStream
                outputStream = bluetoothSocket?.outputStream

                handler.post { onConnected() }

                val buffer = ByteArray(1024)
                var bytes: Int

                while (true) {
                    bytes = inputStream?.read(buffer) ?: break
                    val readMessage = String(buffer, 0, bytes)
                    handler.post {
                        onDataReceived(readMessage)
                    }
                }
            } catch (e: IOException) {
                handler.post { onError(e.message ?: "Unknown error") }
                disconnectBluetooth()
            }
        }.start()
    }

    private fun disconnectBluetooth() {
        try {
            inputStream?.close()
            outputStream?.close()
            bluetoothSocket?.close()
        } catch (_: IOException) { }
    }
}
