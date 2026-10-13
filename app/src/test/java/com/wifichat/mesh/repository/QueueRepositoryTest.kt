package com.wifichat.mesh.repository

import com.google.common.truth.Truth.assertThat
import com.wifichat.mesh.data.entity.QueueEntity
import com.wifichat.mesh.fakes.FakeQueueDao
import com.wifichat.mesh.util.Constants
import kotlinx.coroutines.runBlocking
import org.junit.Test

class QueueRepositoryTest {

    private fun item(id: String, nextAttemptAt: Long = 0, expiresAt: Long = 0) = QueueEntity(
        messageId = id,
        packetBlob = ByteArray(4),
        targetPeerId = "peer",
        nextAttemptAt = nextAttemptAt,
        attempts = 0,
        createdAt = 0,
        expiresAt = expiresAt
    )

    @Test
    fun enqueueThenDue(): Unit = runBlocking {
        val repo = QueueRepository(FakeQueueDao())
        assertThat(repo.enqueue(item("a", nextAttemptAt = 100))).isTrue()
        assertThat(repo.count()).isEqualTo(1)

        assertThat(repo.due(now = 50)).isEmpty()
        assertThat(repo.due(now = 150).map { it.messageId }).containsExactly("a")
    }

    @Test
    fun removeDeletesEntry(): Unit = runBlocking {
        val repo = QueueRepository(FakeQueueDao())
        repo.enqueue(item("a"))
        repo.remove("a")
        assertThat(repo.count()).isEqualTo(0)
    }

    @Test
    fun pruneExpiredRemovesStaleEntries(): Unit = runBlocking {
        val repo = QueueRepository(FakeQueueDao())
        repo.enqueue(item("live", expiresAt = 0))
        repo.enqueue(item("stale", expiresAt = 500))
        val removed = repo.pruneExpired(now = 1000)
        assertThat(removed).isEqualTo(1)
        assertThat(repo.byId("stale")).isNull()
        assertThat(repo.byId("live")).isNotNull()
    }

    @Test
    fun queueIsBounded(): Unit = runBlocking {
        val repo = QueueRepository(FakeQueueDao())
        // Fill to capacity.
        for (i in 0 until Constants.MAX_FORWARD_QUEUE) {
            repo.enqueue(item("m$i", expiresAt = 10_000L + i))
        }
        assertThat(repo.count()).isEqualTo(Constants.MAX_FORWARD_QUEUE)

        // One more must evict the closest-to-expiry entry, not grow unbounded.
        repo.enqueue(item("overflow", expiresAt = 999_999L))
        assertThat(repo.count()).isEqualTo(Constants.MAX_FORWARD_QUEUE)
        assertThat(repo.byId("overflow")).isNotNull()
    }
}
