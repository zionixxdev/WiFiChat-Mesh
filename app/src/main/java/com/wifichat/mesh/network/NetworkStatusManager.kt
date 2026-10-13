package com.wifichat.mesh.network

import com.wifichat.mesh.model.NetworkStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Single source of truth for the live networking state shown on the Mesh
 * dashboard. Several components write to it (transport, discovery, router); the
 * UI only reads. Nothing here is ever fabricated — each field reflects a real
 * observed event.
 */
class NetworkStatusManager {

    private val _state = MutableStateFlow(NetworkStatus())
    val state: StateFlow<NetworkStatus> = _state.asStateFlow()

    fun setBluetoothEnabled(enabled: Boolean) = _state.update { it.copy(bluetoothEnabled = enabled) }
    fun setScanning(scanning: Boolean) = _state.update { it.copy(scanning = scanning) }
    fun setAdvertising(advertising: Boolean) = _state.update { it.copy(advertising = advertising) }
    fun setConnectedPeers(count: Int) = _state.update { it.copy(connectedPeerCount = count) }
    fun setKnownPeers(count: Int) = _state.update { it.copy(knownPeerCount = count) }
    fun setPendingMessages(count: Int) = _state.update { it.copy(pendingMessages = count) }
    fun incrementRelayed() = _state.update { it.copy(messagesRelayed = it.messagesRelayed + 1) }
    fun incrementFailed() = _state.update { it.copy(failedDeliveries = it.failedDeliveries + 1) }
}
