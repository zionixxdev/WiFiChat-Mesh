package com.wifichat.mesh.model

/**
 * Live networking state surfaced to the UI. "Known" peers are devices we have
 * heard an ANNOUNCE from (directly or via relay); "connected" peers are the
 * subset with a live RFCOMM socket. They are deliberately separate numbers.
 */
data class NetworkStatus(
    val bluetoothEnabled: Boolean = false,
    val scanning: Boolean = false,
    val advertising: Boolean = false,
    val connectedPeerCount: Int = 0,
    val knownPeerCount: Int = 0,
    val messagesRelayed: Int = 0,
    val pendingMessages: Int = 0,
    val failedDeliveries: Int = 0
)
