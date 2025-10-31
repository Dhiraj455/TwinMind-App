package com.example.twinmind2.data

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.twinmind2.data.dao.RecordingDao
import com.example.twinmind2.data.dao.SummaryDao
import com.example.twinmind2.data.dao.TranscriptDao
import com.example.twinmind2.data.entity.AudioChunk
import com.example.twinmind2.data.entity.RecordingSession
import com.example.twinmind2.data.entity.Summary
import com.example.twinmind2.data.entity.Transcript

@Database(
    entities = [RecordingSession::class, AudioChunk::class, Transcript::class, Summary::class],
    version = 4,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recordingDao(): RecordingDao
    abstract fun transcriptDao(): TranscriptDao
    abstract fun summaryDao(): SummaryDao
}


