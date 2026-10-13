package com.wifichat.mesh.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "event_log")
data class EventLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val type: String,          // EventType.name
    val timestamp: Long,
    val detail: String
)
