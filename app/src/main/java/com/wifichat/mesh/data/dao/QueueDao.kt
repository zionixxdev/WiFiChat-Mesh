package com.wifichat.mesh.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wifichat.mesh.data.entity.QueueEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface QueueDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: QueueEntity)

    @Query("SELECT * FROM forward_queue ORDER BY nextAttemptAt ASC")
    fun observeAll(): Flow<List<QueueEntity>>

    @Query("SELECT * FROM forward_queue WHERE nextAttemptAt <= :now ORDER BY nextAttemptAt ASC")
    suspend fun due(now: Long): List<QueueEntity>

    @Query("SELECT * FROM forward_queue WHERE messageId = :messageId LIMIT 1")
    suspend fun byId(messageId: String): QueueEntity?

    @Query("DELETE FROM forward_queue WHERE messageId = :messageId")
    suspend fun delete(messageId: String)

    @Query("DELETE FROM forward_queue WHERE expiresAt > 0 AND expiresAt < :now")
    suspend fun deleteExpired(now: Long)

    @Query("SELECT COUNT(*) FROM forward_queue")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM forward_queue")
    fun observeCount(): Flow<Int>

    @Query("DELETE FROM forward_queue")
    suspend fun deleteAll()
}
