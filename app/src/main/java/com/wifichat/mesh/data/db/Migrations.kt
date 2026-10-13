package com.wifichat.mesh.data.db

import androidx.room.migration.Migration

/**
 * Versioned migrations. Every schema change increments the Room version and
 * adds a Migration here — destructive fallback is NOT the default for upgrades
 * (only for downgrades), so user history is preserved across updates.
 *
 * Example for a future v2:
 *
 *   val MIGRATION_1_2 = object : Migration(1, 2) {
 *       override fun migrate(db: SupportSQLiteDatabase) {
 *           db.execSQL("ALTER TABLE messages ADD COLUMN starred INTEGER NOT NULL DEFAULT 0")
 *       }
 *   }
 */
object Migrations {
    val ALL: Array<Migration> = emptyArray()
}
