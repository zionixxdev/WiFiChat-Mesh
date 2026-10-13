package com.wifichat.mesh.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wifichat.mesh.AppContainer
import com.wifichat.mesh.BuildConfig
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val container: AppContainer,
    private val appContext: Context
) : ViewModel() {

    val displayName: StateFlow<String> = container.settings.displayName
    val themeMode: StateFlow<Int> = container.settings.themeMode
    val notifications: StateFlow<Boolean> = container.settings.notifications
    val discoveryEnabled: StateFlow<Boolean> = container.settings.discoveryEnabled
    val readReceipts: StateFlow<Boolean> = container.settings.readReceipts
    val maxHops: StateFlow<Int> = container.settings.maxHops
    val messageTtlMs: StateFlow<Long> = container.settings.messageTtlMs

    val peerId: String = container.settings.peerId
    val versionName: String = BuildConfig.VERSION_NAME
    val keystoreHardwareBacked: Boolean = container.keyStore.isHardwareBacked

    fun setDisplayName(name: String) = container.settings.setDisplayName(name)
    fun setThemeMode(mode: Int) = container.settings.setThemeMode(mode)
    fun setNotifications(enabled: Boolean) = container.settings.setNotifications(enabled)
    fun setDiscoveryEnabled(enabled: Boolean) = container.settings.setDiscoveryEnabled(enabled)
    fun setReadReceipts(enabled: Boolean) = container.settings.setReadReceipts(enabled)
    fun setMaxHops(hops: Int) = container.settings.setMaxHops(hops)
    fun setMessageTtlMs(ttl: Long) = container.settings.setMessageTtlMs(ttl)

    fun clearLocalData(onDone: () -> Unit) {
        viewModelScope.launch {
            container.clearAllData()
            onDone()
        }
    }

    /**
     * A redacted diagnostics blob for bug reports. It contains NO plaintext
     * messages and NO key material — only counts, ids and configuration.
     */
    fun exportDiagnostics(): String = buildString {
        appendLine("WiFiChat Mesh diagnostics")
        appendLine("version: $versionName")
        appendLine("peerId: $peerId")
        appendLine("maxHops: ${container.settings.maxHops.value}")
        appendLine("ttlMs: ${container.settings.messageTtlMs.value}")
        appendLine("discovery: ${container.settings.discoveryEnabled.value}")
        appendLine("keystoreHardwareBacked: $keystoreHardwareBacked")
        appendLine("status: ${container.status.state.value}")
        appendLine("queued: ${container.status.state.value.pendingMessages}")
        appendLine("knownPeers: ${container.router.knownPeers.value.size}")
    }
}
