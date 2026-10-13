package com.wifichat.mesh.mesh

import com.wifichat.mesh.util.Constants
import kotlin.random.Random

/**
 * Retry scheduling. Exponential backoff with +/-20% jitter, capped at
 * [Constants.RETRY_MAX_MS]. Kept as a pure object so it is trivially testable
 * and reusable by both the queue and any future transport.
 */
object RetryPolicy {

    fun backoff(attempt: Int, jitter: Boolean = true): Long {
        val safeAttempt = attempt.coerceIn(1, 16)
        val base = (Constants.RETRY_BASE_MS shl (safeAttempt - 1))
            .coerceAtMost(Constants.RETRY_MAX_MS)
        if (!jitter) return base
        val j = (base * 0.2).toLong()
        return base + Random.nextLong(-j, j + 1)
    }

    /** True once retries should stop and the message be marked failed. */
    fun exhausted(attempts: Int): Boolean = attempts >= Constants.MAX_RETRIES
}
