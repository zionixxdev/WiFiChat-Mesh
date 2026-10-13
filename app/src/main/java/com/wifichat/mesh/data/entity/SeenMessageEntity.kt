package com.wifichat.mesh.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Duplicate-suppression ledger. A packet whose messageId is present here has
 * already been handled and must not be displayed or forwarded again.
 * Rows are pruned to a bounded size (see MessageRepository).
 */
@Entity(tableName = "seen_ids")
data class SeenMessageEntity(
    @PrimaryKey val messageId: String,
    val seenAt: Long
)
