package com.wifichat.mesh.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class IdsTest {

    @Test
    fun messageIdsAreUnique() {
        val ids = (0 until 5000).map { Ids.newMessageId() }.toHashSet()
        assertThat(ids).hasSize(5000)
    }

    @Test
    fun randomBytesHaveRequestedLength() {
        assertThat(Ids.randomBytes(12)).hasLength(12)
        assertThat(Ids.randomBytes(32)).hasLength(32)
    }

    @Test
    fun randomBytesDiffer() {
        assertThat(Ids.randomBytes(16)).isNotEqualTo(Ids.randomBytes(16))
    }

    @Test
    fun shortTagIsSixUppercaseChars() {
        val tag = Ids.shortTag("6d5a1e90-9b6f-4c2a-8f1d-2f7c9a0b4e11")
        assertThat(tag).hasLength(6)
        assertThat(tag).isEqualTo(tag.uppercase())
    }
}
