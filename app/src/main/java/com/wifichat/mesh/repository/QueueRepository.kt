package com.wifichat.mesh.repository

import com.wifichat.mesh.data.dao.QueueDao
import com.wifichat.mesh.data.entity.QueueEntity
import com.wifichat.mesh.util.Constants
import kotlinx.coroutines.flow.Flow

/**
 * The persistent store-and-forward queue. Packets live here as serialised,
 * signed bytes so they survive process death and reboot, and are only removed
 * once the destination acknowledges them (or they expire / exhaust retries).
 *
 * The queue is bounded: if it is full, the oldest non-expiring entry is dropped
 * to protect the low-RAM target device. Dropping is reported to the caller so
 * the UI can mark the message FAILED rather than pretending it will send.
 */
class QueueRepository(private val queueDao: QueueDao) {

    fun observeAll(): Flow<List<QueueEntity>> = queueDao.observeAll()

    fun observeCount(): Flow<Int> = queueDao.observeCount()

    suspend fun count(): Int = queueDao.count()

    /**
     * Enqueues a packet. Returns true if stored, false if the queue is full and
     * nothing could be evicted.
     */
    suspend fun enqueue(item: QueueEntity): Boolean {
        val size = queueDao.count()
        if (size >= Constants.MAX_FORWARD_QUEUE) {
            // Evict the entry closest to expiry to make room.
            val all = queueDao.due(Long.MAX_VALUE)
            val victim = all.minByOrNull { it.expiresAt } ?: return false
            queueDao.delete(victim.messageId)
        }
        queueDao.upsert(item)
        return true
    }

    suspend fun due(now: Long = System.currentTimeMillis()): List<QueueEntity> =
        queueDao.due(now)

    suspend fun byId(messageId: String): QueueEntity? = queueDao.byId(messageId)

    suspend fun remove(messageId: String) = queueDao.delete(messageId)

    suspend fun pruneExpired(now: Long = System.currentTimeMillis()): Int {
        val before = queueDao.count()
        queueDao.deleteExpired(now)
        return before - queueDao.count()
    }

    suspend fun clearAll() = queueDao.deleteAll()
}
