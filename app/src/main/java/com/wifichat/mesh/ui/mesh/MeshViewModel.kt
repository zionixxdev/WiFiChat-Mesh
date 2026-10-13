package com.wifichat.mesh.ui.mesh

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wifichat.mesh.AppContainer
import com.wifichat.mesh.model.MeshEvent
import com.wifichat.mesh.model.NetworkStatus
import com.wifichat.mesh.network.PeerLink
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MeshViewModel(private val container: AppContainer) : ViewModel() {

    val status: StateFlow<NetworkStatus> = container.status.state

    val links: StateFlow<List<PeerLink>> = container.transport.links

    val knownPeers: StateFlow<Set<String>> = container.router.knownPeers

    val events: StateFlow<List<MeshEvent>> =
        container.events.observeRecent(200)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun clearLog() {
        viewModelScope.launch { container.events.clear() }
    }
}
