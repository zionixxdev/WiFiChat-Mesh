package com.wifichat.mesh.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wifichat.mesh.data.entity.SeenMessageEntity

@Dao
interface SeenDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun markSeen(row: SeenMessageEntity): Long

    @Query("SELECT COUNT(*) FROM seen_ids WHERE messageId = :messageId")
    suspend fun isSeen(messageId: String): Int

    @Query("SELECT COUNT(*) FROM seen_ids")
    suspend fun count(): Int

    /** Keep only the newest [keep] rows; bounds the ledger on low-storage devices. */
    @Query("DELETE FROM seen_ids WHERE messageId NOT IN (SELECT messageId FROM seen_ids ORDER BY seenAt DESC LIMIT :keep)")
    suspend fun prune(keep: Int)

    @Query("DELETE FROM seen_ids")
    suspend fun clear()
}
