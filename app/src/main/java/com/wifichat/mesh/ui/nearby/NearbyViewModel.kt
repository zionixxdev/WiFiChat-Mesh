package com.wifichat.mesh.ui.nearby

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wifichat.mesh.AppContainer
import com.wifichat.mesh.model.Peer
import com.wifichat.mesh.network.DiscoveredDevice
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NearbyViewModel(private val container: AppContainer) : ViewModel() {

    val devices: StateFlow<List<DiscoveredDevice>> = container.discovery.devices
    val scanning: StateFlow<Boolean> = container.discovery.scanning

    val peers: StateFlow<List<Peer>> =
        container.peers.observeAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Peers we have actually exchanged an ANNOUNCE with (mesh members). */
    val knownPeerIds: StateFlow<Set<String>> = container.router.knownPeers

    fun startScan() = container.discovery.startScan()

    fun stopScan() = container.discovery.stopScan()

    fun connect(address: String) = container.transport.connect(address)

    fun requiredPermissions(): List<String> = container.discovery.requiredPermissions()

    /** Opens (or creates) a 1:1 conversation and returns its id for navigation. */
    fun openConversation(peerId: String, name: String, onReady: (String) -> Unit) {
        viewModelScope.launch {
            val convo = container.conversations.getOrCreateDirect(peerId, name)
            onReady(convo.conversationId)
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Discovery itself is owned by the service; do not tear it down here.
    }
}
