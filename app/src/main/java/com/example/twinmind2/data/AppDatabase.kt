package com.example.twinmind2.data

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.twinmind2.data.dao.RecordingDao
import com.example.twinmind2.data.entity.AudioChunk
import com.example.twinmind2.data.entity.RecordingSession

@Database(
    entities = [RecordingSession::class, AudioChunk::class],
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recordingDao(): RecordingDao
}


