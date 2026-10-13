package com.wifichat.mesh.util

import java.util.UUID

/** Compile-time constants shared across the mesh. */
object Constants {

    /**
     * Bump when the wire format changes incompatibly. Peers advertising a
     * different major version are ignored (see DiscoveryManager).
     */
    const val PROTOCOL_VERSION = 1

    /**
     * The RFCOMM (Bluetooth Classic) service UUID. Every WiFiChat Mesh device
     * listens on this SPP channel. It is a random v4 UUID, not a registered one.
     */
    val RFCOMM_SERVICE_UUID: UUID =
        UUID.fromString("6d5a1e90-9b6f-4c2a-8f1d-2f7c9a0b4e11")

    const val SERVICE_NAME = "WiFiChat-Mesh"

    // ---- Mesh routing limits ----
    const val DEFAULT_MAX_HOPS = 5
    const val MAX_HOP_LIMIT = 10          // hard ceiling regardless of setting
    const val DEFAULT_MESSAGE_TTL_MS = 24L * 60 * 60 * 1000   // 24h

    // ---- Queue limits (protect the low-RAM target device) ----
    const val MAX_FORWARD_QUEUE = 200
    const val MAX_SEEN_IDS = 5000
    const val MAX_PACKET_BYTES = 64 * 1024   // 64 KiB frame ceiling
    const val MAX_MESSAGE_CHARS = 4000

    // ---- Retry policy (exponential backoff with jitter) ----
    const val RETRY_BASE_MS = 2_000L
    const val RETRY_MAX_MS = 5 * 60 * 1000L
    const val MAX_RETRIES = 8

    // ---- Discovery pacing (battery conscious) ----
    const val DISCOVERY_WINDOW_MS = 12_000L
    const val DISCOVERY_INTERVAL_MS = 30_000L

    const val PREFS_NAME = "wifichat_prefs"
}
