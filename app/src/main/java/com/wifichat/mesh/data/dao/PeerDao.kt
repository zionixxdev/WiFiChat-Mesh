package com.wifichat.mesh.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wifichat.mesh.data.entity.PeerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PeerDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(peer: PeerEntity)

    @Query("SELECT * FROM peers ORDER BY lastSeen DESC")
    fun observeAll(): Flow<List<PeerEntity>>

    @Query("SELECT * FROM peers WHERE peerId = :peerId LIMIT 1")
    suspend fun byId(peerId: String): PeerEntity?

    @Query("DELETE FROM peers WHERE peerId = :peerId")
    suspend fun delete(peerId: String)

    @Query("DELETE FROM peers WHERE lastSeen < :cutoff")
    suspend fun deleteStale(cutoff: Long)

    @Query("SELECT COUNT(*) FROM peers")
    suspend fun count(): Int

    @Query("DELETE FROM peers")
    suspend fun deleteAll()
}
