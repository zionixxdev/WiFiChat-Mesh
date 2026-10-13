package com.wifichat.mesh.mesh

import com.google.common.truth.Truth.assertThat
import com.wifichat.mesh.util.Constants
import org.junit.Test

class RetryPolicyTest {

    @Test
    fun firstBackoffEqualsBase() {
        assertThat(RetryPolicy.backoff(1, jitter = false)).isEqualTo(Constants.RETRY_BASE_MS)
    }

    @Test
    fun backoffGrowsExponentially() {
        val a = RetryPolicy.backoff(1, jitter = false)
        val b = RetryPolicy.backoff(2, jitter = false)
        val c = RetryPolicy.backoff(3, jitter = false)
        assertThat(b).isEqualTo(a * 2)
        assertThat(c).isEqualTo(a * 4)
    }

    @Test
    fun backoffIsCapped() {
        val huge = RetryPolicy.backoff(20, jitter = false)
        assertThat(huge).isAtMost(Constants.RETRY_MAX_MS)
    }

    @Test
    fun jitterKeepsBackoffWithinTwentyPercent() {
        val base = RetryPolicy.backoff(3, jitter = false)
        repeat(50) {
            val jittered = RetryPolicy.backoff(3, jitter = true)
            assertThat(jittered).isAtLeast((base * 0.79).toLong())
            assertThat(jittered).isAtMost((base * 1.21).toLong())
        }
    }

    @Test
    fun exhaustedAfterMaxRetries() {
        assertThat(RetryPolicy.exhausted(Constants.MAX_RETRIES - 1)).isFalse()
        assertThat(RetryPolicy.exhausted(Constants.MAX_RETRIES)).isTrue()
    }
}
