package com.wifichat.mesh.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.wifichat.mesh.data.entity.EventLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EventLogDao {

    @Insert
    suspend fun insert(event: EventLogEntity): Long

    @Query("SELECT * FROM event_log ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<EventLogEntity>>

    @Query("DELETE FROM event_log")
    suspend fun clear()
}
