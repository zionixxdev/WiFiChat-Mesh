package com.wifichat.mesh.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wifichat.mesh.data.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(message: MessageEntity)

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    fun observeConversation(conversationId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE messageId = :messageId LIMIT 1")
    suspend fun byId(messageId: String): MessageEntity?

    @Query("UPDATE messages SET status = :status WHERE messageId = :messageId")
    suspend fun updateStatus(messageId: String, status: String)

    @Query("DELETE FROM messages WHERE messageId = :messageId")
    suspend fun delete(messageId: String)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun deleteConversation(conversationId: String)

    @Query("SELECT * FROM messages WHERE expiresAt > 0 AND expiresAt < :now")
    suspend fun expired(now: Long): List<MessageEntity>

    @Query("DELETE FROM messages WHERE expiresAt > 0 AND expiresAt < :now")
    suspend fun deleteExpired(now: Long)

    @Query("SELECT COUNT(*) FROM messages")
    suspend fun count(): Int

    @Query("DELETE FROM messages")
    suspend fun deleteAll()
}
