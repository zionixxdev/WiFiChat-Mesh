package com.wifichat.mesh.repository

import android.content.Context
import com.wifichat.mesh.util.Constants
import com.wifichat.mesh.util.Ids
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** App-wide user preferences. Backed by SharedPreferences, exposed as flows. */
class SettingsStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)

    private val _displayName = MutableStateFlow(prefs.getString(KEY_DISPLAY_NAME, "") ?: "")
    val displayName: StateFlow<String> = _displayName.asStateFlow()

    private val _themeMode = MutableStateFlow(prefs.getInt(KEY_THEME, THEME_DARK))
    val themeMode: StateFlow<Int> = _themeMode.asStateFlow()

    private val _notifications = MutableStateFlow(prefs.getBoolean(KEY_NOTIFICATIONS, true))
    val notifications: StateFlow<Boolean> = _notifications.asStateFlow()

    private val _discoveryEnabled = MutableStateFlow(prefs.getBoolean(KEY_DISCOVERY, true))
    val discoveryEnabled: StateFlow<Boolean> = _discoveryEnabled.asStateFlow()

    private val _readReceipts = MutableStateFlow(prefs.getBoolean(KEY_READ_RECEIPTS, true))
    val readReceipts: StateFlow<Boolean> = _readReceipts.asStateFlow()

    private val _maxHops = MutableStateFlow(prefs.getInt(KEY_MAX_HOPS, Constants.DEFAULT_MAX_HOPS))
    val maxHops: StateFlow<Int> = _maxHops.asStateFlow()

    private val _messageTtlMs = MutableStateFlow(
        prefs.getLong(KEY_TTL, Constants.DEFAULT_MESSAGE_TTL_MS)
    )
    val messageTtlMs: StateFlow<Long> = _messageTtlMs.asStateFlow()

    /** The stable, locally generated peer id. Created once, never MAC-derived. */
    val peerId: String
        get() {
            val existing = prefs.getString(KEY_PEER_ID, null)
            if (existing != null) return existing
            val created = Ids.newPeerId()
            prefs.edit().putString(KEY_PEER_ID, created).apply()
            return created
        }

    val onboardingDone: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_DONE, false)

    fun setDisplayName(name: String) {
        prefs.edit().putString(KEY_DISPLAY_NAME, name).apply()
        _displayName.value = name
    }

    fun setThemeMode(mode: Int) {
        prefs.edit().putInt(KEY_THEME, mode).apply()
        _themeMode.value = mode
    }

    fun setNotifications(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NOTIFICATIONS, enabled).apply()
        _notifications.value = enabled
    }

    fun setDiscoveryEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DISCOVERY, enabled).apply()
        _discoveryEnabled.value = enabled
    }

    fun setReadReceipts(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_READ_RECEIPTS, enabled).apply()
        _readReceipts.value = enabled
    }

    fun setMaxHops(hops: Int) {
        val clamped = hops.coerceIn(1, Constants.MAX_HOP_LIMIT)
        prefs.edit().putInt(KEY_MAX_HOPS, clamped).apply()
        _maxHops.value = clamped
    }

    fun setMessageTtlMs(ttl: Long) {
        prefs.edit().putLong(KEY_TTL, ttl).apply()
        _messageTtlMs.value = ttl
    }

    fun setOnboardingDone() = prefs.edit().putBoolean(KEY_ONBOARDING_DONE, true).apply()

    /** Full reset of preferences (identity is regenerated on next launch). */
    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        const val THEME_DARK = 0
        const val THEME_LIGHT = 1
        const val THEME_SYSTEM = 2

        private const val KEY_DISPLAY_NAME = "display_name"
        private const val KEY_PEER_ID = "peer_id"
        private const val KEY_THEME = "theme_mode"
        private const val KEY_NOTIFICATIONS = "notifications"
        private const val KEY_DISCOVERY = "discovery_enabled"
        private const val KEY_READ_RECEIPTS = "read_receipts"
        private const val KEY_MAX_HOPS = "max_hops"
        private const val KEY_TTL = "message_ttl"
        private const val KEY_ONBOARDING_DONE = "onboarding_done"
    }
}
