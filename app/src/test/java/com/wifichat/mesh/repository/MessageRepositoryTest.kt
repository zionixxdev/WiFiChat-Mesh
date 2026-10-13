package com.wifichat.mesh.repository

import com.google.common.truth.Truth.assertThat
import com.wifichat.mesh.fakes.FakeMessageDao
import com.wifichat.mesh.fakes.FakeSeenDao
import com.wifichat.mesh.model.DeliveryState
import com.wifichat.mesh.model.Message
import kotlinx.coroutines.runBlocking
import org.junit.Test

class MessageRepositoryTest {

    private fun message(id: String, expiresAt: Long = 0) = Message(
        messageId = id,
        conversationId = "c1",
        senderId = "me",
        recipientId = "you",
        body = "hi",
        createdAt = 1,
        status = DeliveryState.PENDING,
        outgoing = true,
        expiresAt = expiresAt
    )

    @Test
    fun duplicateSuppressionWithinInstance() = runBlocking {
        val repo = MessageRepository(FakeMessageDao(), FakeSeenDao())
        assertThat(repo.isDuplicate("m1")).isFalse()
        repo.markSeen("m1")
        assertThat(repo.isDuplicate("m1")).isTrue()
    }

    @Test
    fun duplicateSuppressionSurvivesRestart() = runBlocking {
        val seen = FakeSeenDao()
        val repo1 = MessageRepository(FakeMessageDao(), seen)
        repo1.markSeen("persisted-id")

        // A fresh repository over the same persistent store must still dedupe.
        val repo2 = MessageRepository(FakeMessageDao(), seen)
        assertThat(repo2.isDuplicate("persisted-id")).isTrue()
    }

    @Test
    fun saveAndUpdateStatus() = runBlocking {
        val repo = MessageRepository(FakeMessageDao(), FakeSeenDao())
        repo.save(message("m2"))
        assertThat(repo.byId("m2")?.status).isEqualTo(DeliveryState.PENDING)

        repo.updateStatus("m2", DeliveryState.DELIVERED_TO_DEVICE)
        assertThat(repo.byId("m2")?.status).isEqualTo(DeliveryState.DELIVERED_TO_DEVICE)
    }

    @Test
    fun pruneExpiredRemovesOnlyExpired() = runBlocking {
        val repo = MessageRepository(FakeMessageDao(), FakeSeenDao())
        repo.save(message("keep"))
        repo.save(message("gone", expiresAt = 500))

        val removed = repo.pruneExpired(now = 1000)
        assertThat(removed).isEqualTo(1)
        assertThat(repo.byId("keep")).isNotNull()
        assertThat(repo.byId("gone")).isNull()
    }

    @Test
    fun statusTransitionsAreRecordedFaithfully() = runBlocking {
        val dao = FakeMessageDao()
        val repo = MessageRepository(dao, FakeSeenDao())
        repo.save(message("m3"))

        val path = listOf(
            DeliveryState.QUEUED,
            DeliveryState.TRANSMITTED,
            DeliveryState.DELIVERED_TO_DEVICE,
            DeliveryState.READ
        )
        for (state in path) {
            repo.updateStatus("m3", state)
            assertThat(dao.byId("m3")?.status).isEqualTo(state.name)
        }
    }
}
