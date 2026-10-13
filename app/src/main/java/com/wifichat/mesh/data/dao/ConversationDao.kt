package com.wifichat.mesh.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wifichat.mesh.data.entity.ConversationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(conversation: ConversationEntity)

    @Query("SELECT * FROM conversations ORDER BY pinned DESC, lastMessageAt DESC")
    fun observeAll(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE conversationId = :id LIMIT 1")
    suspend fun byId(id: String): ConversationEntity?

    @Query("SELECT * FROM conversations WHERE peerId = :peerId AND isGroup = 0 LIMIT 1")
    suspend fun byPeer(peerId: String): ConversationEntity?

    @Query("UPDATE conversations SET lastMessagePreview = :preview, lastMessageAt = :at WHERE conversationId = :id")
    suspend fun updateLastMessage(id: String, preview: String, at: Long)

    @Query("UPDATE conversations SET unreadCount = unreadCount + 1 WHERE conversationId = :id")
    suspend fun incrementUnread(id: String)

    @Query("UPDATE conversations SET unreadCount = 0 WHERE conversationId = :id")
    suspend fun clearUnread(id: String)

    @Query("UPDATE conversations SET pinned = :pinned WHERE conversationId = :id")
    suspend fun setPinned(id: String, pinned: Boolean)

    @Query("DELETE FROM conversations")
    suspend fun deleteAll()
}
