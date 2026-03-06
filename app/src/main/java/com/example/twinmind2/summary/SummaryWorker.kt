package com.example.twinmind2.summary

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class SummaryWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val summaryRepository: SummaryRepository
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val sessionId = inputData.getLong(KEY_SESSION_ID, -1L)
        if (sessionId <= 0L) {
            return Result.failure()
        }

        return try {
            summaryRepository.runSummaryGeneration(sessionId)
            Result.success()
        } catch (e: Exception) {
            // Let WorkManager decide when to retry based on backoff policy
            Result.retry()
        }
    }

    companion object {
        const val KEY_SESSION_ID = "sessionId"
    }
}

