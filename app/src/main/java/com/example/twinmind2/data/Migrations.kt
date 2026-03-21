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
 *
 * Common SQL patterns:
 *   ADD column:    ALTER TABLE foo ADD COLUMN bar TEXT
 *   ADD NOT NULL:  ALTER TABLE foo ADD COLUMN bar INTEGER NOT NULL DEFAULT 0
 *   NEW table:     CREATE TABLE IF NOT EXISTS ...
 *   DROP column:   requires create-copy-drop approach (SQLite has no DROP COLUMN pre-API 35)
 */

// ─── Example: next migration when you bump from 6 → 7 ────────────────────────
//
// val MIGRATION_6_7 = object : Migration(6, 7) {
//     override fun migrate(db: SupportSQLiteDatabase) {
//         // Example: add a nullable column to recording_sessions
//         db.execSQL("ALTER TABLE recording_sessions ADD COLUMN location TEXT")
//     }
// }

// ─── Register every migration in this array ───────────────────────────────────
//
// val ALL_MIGRATIONS = arrayOf(
//     MIGRATION_6_7,
// )

/**
 * Empty placeholder until the first post-launch migration is needed.
 * Replace with the real array once you have migrations.
 */
val ALL_MIGRATIONS: Array<Migration> = emptyArray()
