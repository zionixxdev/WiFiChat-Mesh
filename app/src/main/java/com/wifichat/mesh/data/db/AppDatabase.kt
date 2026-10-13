package com.wifichat.mesh.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.wifichat.mesh.data.dao.ConversationDao
import com.wifichat.mesh.data.dao.EventLogDao
import com.wifichat.mesh.data.dao.MessageDao
import com.wifichat.mesh.data.dao.PeerDao
import com.wifichat.mesh.data.dao.QueueDao
import com.wifichat.mesh.data.dao.SeenDao
import com.wifichat.mesh.data.entity.ConversationEntity
import com.wifichat.mesh.data.entity.EventLogEntity
import com.wifichat.mesh.data.entity.MessageEntity
import com.wifichat.mesh.data.entity.PeerEntity
import com.wifichat.mesh.data.entity.QueueEntity
import com.wifichat.mesh.data.entity.SeenMessageEntity

@Database(
    entities = [
        MessageEntity::class,
        ConversationEntity::class,
        PeerEntity::class,
        QueueEntity::class,
        SeenMessageEntity::class,
        EventLogEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun messageDao(): MessageDao
    abstract fun conversationDao(): ConversationDao
    abstract fun peerDao(): PeerDao
    abstract fun queueDao(): QueueDao
    abstract fun seenDao(): SeenDao
    abstract fun eventLogDao(): EventLogDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "wifichat_mesh.db"
                )
                    // All schema changes must ship an explicit Migration; see Migrations.kt.
                    .addMigrations(*Migrations.ALL)
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .build()
                    .also { INSTANCE = it }
            }

        /** Used by tests and by "Clear local data". */
        fun reset() {
            INSTANCE?.close()
            INSTANCE = null
        }
    }
}
