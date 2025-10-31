package com.example.twinmind2.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.twinmind2.data.entity.Summary
import kotlinx.coroutines.flow.Flow

@Dao
interface SummaryDao {
    @Query("SELECT * FROM summaries WHERE sessionId = :sessionId LIMIT 1")
    fun observeSummary(sessionId: Long): Flow<Summary?>

    @Query("SELECT * FROM summaries WHERE sessionId = :sessionId LIMIT 1")
    suspend fun getSummary(sessionId: Long): Summary?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(summary: Summary)

    @Update
    suspend fun update(summary: Summary)
}

