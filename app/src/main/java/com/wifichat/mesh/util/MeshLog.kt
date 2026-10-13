package com.wifichat.mesh.util

import android.util.Log
import com.wifichat.mesh.BuildConfig

/**
 * Thin logging wrapper. The rule for this project: NEVER log message plaintext,
 * private keys, or shared secrets. Callers pass only structural metadata
 * (ids, sizes, states). In release builds verbose/debug calls are dropped.
 */
object MeshLog {

    private const val TAG = "WiFiChatMesh"

    fun d(msg: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, msg)
    }

    fun i(msg: String) = Log.i(TAG, msg)

    fun w(msg: String) = Log.w(TAG, msg)

    fun e(msg: String, t: Throwable? = null) {
        if (t == null) Log.e(TAG, msg) else Log.e(TAG, msg, t)
    }
}
