package com.wifichat.mesh.network

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.wifichat.mesh.model.NetworkStatus
import com.wifichat.mesh.util.Constants
import com.wifichat.mesh.util.MeshLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * Bluetooth Classic (RFCOMM/SPP) transport.
 *
 * Why Bluetooth Classic rather than BLE: RFCOMM gives us a reliable, ordered
 * byte stream with a simple socket API, works on ARMv7 and on Android 12 Go,
 * needs no Google Play Services, and is far easier to get multi-hop forwarding
 * right on than GATT. The trade-offs are documented in docs/ARCHITECTURE.md.
 *
 * One device runs a listening RFCOMM server on [Constants.RFCOMM_SERVICE_UUID]
 * and every neighbour connects as a client to it; this gives a full mesh where
 * any pair of connected devices can exchange frames directly. Routing above
 * this layer decides whether a packet stops here or is forwarded on.
 */
@SuppressLint("MissingPermission")
class BluetoothClassicTransport(
    private val context: Context,
    private val statusManager: NetworkStatusManager,
    parentScope: CoroutineScope
) : MeshTransport {

    private val scope = CoroutineScope(parentScope.coroutineContext + SupervisorJob() + Dispatchers.IO)

    private val adapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    private class Link(
        val address: String,
        val socket: BluetoothSocket,
        val outgoing: Boolean
    ) {
        @Volatile var peerId: String? = null
        val connectedAt: Long = System.currentTimeMillis()
        val output = socket.outputStream
    }

    private val linkMap = ConcurrentHashMap<String, Link>()

    private val _links = MutableStateFlow<List<PeerLink>>(emptyList())
    override val links: StateFlow<List<PeerLink>> = _links.asStateFlow()

    private val _inbound = MutableSharedFlow<InboundFrame>(
        replay = 0,
        extraBufferCapacity = 256,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val inbound: SharedFlow<InboundFrame> = _inbound.asSharedFlow()

    override val status: StateFlow<NetworkStatus> get() = statusManager.state

    private var serverSocket: BluetoothServerSocket? = null
    private var acceptJob: Job? = null

    @Volatile private var running = false

    override fun start() {
        if (running) return
        if (adapter == null || !hasConnectPermission()) {
            MeshLog.w("Transport start skipped: adapter=${adapter != null}, perm=${hasConnectPermission()}")
            statusManager.setBluetoothEnabled(adapter?.isEnabled == true)
            return
        }
        running = true
        statusManager.setBluetoothEnabled(adapter.isEnabled)
        startServer()
    }

    private fun startServer() {
        acceptJob = scope.launch {
            try {
                val name = Constants.SERVICE_NAME
                serverSocket = adapter!!.listenUsingRfcommWithServiceRecord(
                    name, Constants.RFCOMM_SERVICE_UUID
                )
                statusManager.setAdvertising(true)
                MeshLog.i("RFCOMM server listening on ${Constants.RFCOMM_SERVICE_UUID}")
                while (running) {
                    val socket = try {
                        serverSocket?.accept()
                    } catch (io: IOException) {
                        if (running) MeshLog.w("accept() failed: ${io.message}")
                        null
                    } ?: break
                    registerLink(socket, outgoing = false)
                }
            } catch (t: Throwable) {
                MeshLog.e("Server loop error", t)
            } finally {
                statusManager.setAdvertising(false)
            }
        }
    }

    override fun connect(address: String) {
        if (adapter == null || !hasConnectPermission()) return
        if (linkMap.containsKey(address)) return
        scope.launch {
            val socket = try {
                val device = adapter.getRemoteDevice(address)
                device.createRfcommSocketToServiceRecord(Constants.RFCOMM_SERVICE_UUID).also {
                    it.connect()
                }
            } catch (t: Throwable) {
                MeshLog.w("connect($address) failed: ${t.message}")
                null
            }
            if (socket != null) registerLink(socket, outgoing = true)
        }
    }

    private fun registerLink(socket: BluetoothSocket, outgoing: Boolean) {
        val address = socket.remoteDevice.address ?: return
        // Replace any stale link to the same device.
        linkMap.remove(address)?.let { runCatching { it.socket.close() } }
        val link = Link(address, socket, outgoing)
        linkMap[address] = link
        publishLinks()
        MeshLog.i("Link ${if (outgoing) "out" else "in"} established with $address")

        scope.launch { readLoop(link) }
    }

    private suspend fun readLoop(link: Link) {
        try {
            val input = link.socket.inputStream
            while (running && linkMap.containsKey(link.address)) {
                val frame = FrameCodec.readFrame(input, Constants.MAX_PACKET_BYTES) ?: break
                _inbound.tryEmit(InboundFrame(link.address, frame))
            }
        } catch (t: Throwable) {
            MeshLog.w("Read loop ended for ${link.address}: ${t.message}")
        } finally {
            dropLink(link.address)
        }
    }

    private fun dropLink(address: String) {
        val removed = linkMap.remove(address) ?: return
        runCatching { removed.socket.close() }
        publishLinks()
        MeshLog.i("Link dropped with $address")
    }

    override fun disconnect(address: String) = dropLink(address)

    override fun sendTo(address: String, frame: ByteArray): Boolean {
        val link = linkMap[address] ?: return false
        return try {
            synchronized(link) {
                FrameCodec.writeFrame(link.output, frame, Constants.MAX_PACKET_BYTES)
            }
            true
        } catch (t: Throwable) {
            MeshLog.w("sendTo($address) failed: ${t.message}")
            dropLink(address)
            false
        }
    }

    override fun sendToAll(frame: ByteArray): Int {
        var count = 0
        for (address in linkMap.keys.toList()) {
            if (sendTo(address, frame)) count++
        }
        return count
    }

    override fun connectedAddresses(): List<String> = linkMap.keys.toList()

    override fun resolvePeerId(address: String, peerId: String) {
        linkMap[address]?.peerId = peerId
        publishLinks()
    }

    override fun stop() {
        running = false
        runCatching { serverSocket?.close() }
        serverSocket = null
        acceptJob?.cancel()
        acceptJob = null
        for (address in linkMap.keys.toList()) dropLink(address)
        statusManager.setAdvertising(false)
        statusManager.setConnectedPeers(0)
    }

    private fun publishLinks() {
        val snapshot = linkMap.values.map {
            PeerLink(it.address, it.peerId, it.connectedAt, it.outgoing)
        }
        _links.value = snapshot
        statusManager.setConnectedPeers(snapshot.size)
    }

    private fun hasConnectPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
    }
}
