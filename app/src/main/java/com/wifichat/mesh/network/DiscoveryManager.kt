package com.wifichat.mesh.network

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** A Bluetooth device seen during discovery. */
data class DiscoveredDevice(
    val address: String,
    val name: String?,
    val bonded: Boolean,
    val lastSeen: Long
)

/**
 * Bluetooth Classic discovery.
 *
 * Important honesty note: Bluetooth Classic discovery only reveals devices in
 * *discoverable* mode. We therefore also surface already-paired (bonded)
 * devices, and we never invent entries. If nothing real is found, the list is
 * empty and the UI shows troubleshooting rather than fake peers.
 *
 * Classic discovery cannot tell us whether a found device actually runs
 * WiFiChat Mesh. Membership is only confirmed when an ANNOUNCE packet is
 * exchanged after connecting — see MeshRouter. The Nearby screen labels peers
 * accordingly.
 */
@SuppressLint("MissingPermission")
class DiscoveryManager(
    private val context: Context,
    private val statusManager: NetworkStatusManager,
    private val onDeviceFound: (DiscoveredDevice) -> Unit
) {

    private val adapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    private val _devices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val devices: StateFlow<List<DiscoveredDevice>> = _devices.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private var receiver: BroadcastReceiver? = null

    private val receiverImpl = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        }
                    val address = device?.address ?: return
                    val name = runCatching { device.name }.getOrNull()
                    val dd = DiscoveredDevice(address, name, device.bondState == BluetoothDevice.BOND_BONDED, System.currentTimeMillis())
                    upsert(dd)
                    onDeviceFound(dd)
                }
                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> setScanning(true)
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> setScanning(false)
            }
        }
    }

    fun start() {
        if (receiver != null) return
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiverImpl, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiverImpl, filter)
        }
        receiver = receiverImpl
        loadBonded()
    }

    fun stop() {
        receiver?.let { runCatching { context.unregisterReceiver(it) } }
        receiver = null
        stopScan()
    }

    /** Loads already-paired devices so the user can reconnect without scanning. */
    fun loadBonded() {
        if (!hasScanPermission()) return
        val bonded = runCatching { adapter?.bondedDevices }.getOrNull() ?: return
        for (device in bonded) {
            val address = device.address ?: continue
            val name = runCatching { device.name }.getOrNull()
            upsert(DiscoveredDevice(address, name, bonded = true, lastSeen = System.currentTimeMillis()))
        }
    }

    fun startScan(): Boolean {
        if (adapter == null || !adapter.isEnabled) return false
        if (!hasScanPermission()) return false
        if (_scanning.value) return true
        val started = runCatching { adapter.startDiscovery() }.getOrDefault(false)
        if (started) setScanning(true)
        return started
    }

    fun stopScan() {
        if (!hasScanPermission()) return
        runCatching { adapter?.cancelDiscovery() }
        setScanning(false)
    }

    private fun setScanning(value: Boolean) {
        _scanning.value = value
        statusManager.setScanning(value)
    }

    private fun upsert(device: DiscoveredDevice) {
        _devices.update { current ->
            val without = current.filterNot { it.address == device.address }
            (without + device).sortedWith(
                compareByDescending<DiscoveredDevice> { it.bonded }.thenByDescending { it.lastSeen }
            )
        }
    }

    fun clear() {
        _devices.value = emptyList()
    }

    private fun hasScanPermission(): Boolean {
        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Manifest.permission.BLUETOOTH_SCAN
        } else {
            Manifest.permission.ACCESS_FINE_LOCATION
        }
        return ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
    }

    /** Permissions the UI should request before discovery can work. */
    fun requiredPermissions(): List<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE
            )
        } else {
            listOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
}
