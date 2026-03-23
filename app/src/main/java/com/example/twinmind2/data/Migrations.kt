package com.example.twinmind2.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * All Room migrations live here. Rules:
 *  - Never delete a migration once the app is in production.
 *  - For every DB version bump, add a new MIGRATION_X_Y object.
 *  - Pass every object to AppModule's addMigrations() call.
 *
 * How to add a migration when you change the schema:
 *  1. Make your entity change.
 *  2. Bump @Database(version = N+1).
 *  3. Add MIGRATION_N_(N+1) below describing the SQL change.
 *  4. Add it to ALL_MIGRATIONS at the bottom.
 *  5. Build — Room will write schemas/<version>.json automatically.
 */

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS chat_sessions (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                title TEXT NOT NULL,
                type TEXT NOT NULL,
                recordingSessionId INTEGER,
                createdAt INTEGER NOT NULL,
                lastMessageAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS chat_messages (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                chatSessionId INTEGER NOT NULL,
                role TEXT NOT NULL,
                content TEXT NOT NULL,
                createdAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }
}

val ALL_MIGRATIONS: Array<Migration> = arrayOf(
    MIGRATION_6_7
)
