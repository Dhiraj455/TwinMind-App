package com.example.twinmind2.summary

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

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

        private fun uniqueWorkName(sessionId: Long): String = "summary_work_$sessionId"

        fun enqueue(context: Context, sessionId: Long, replaceExisting: Boolean = true) {
            if (sessionId <= 0L) return

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val input = Data.Builder()
                .putLong(KEY_SESSION_ID, sessionId)
                .build()

            val request = OneTimeWorkRequestBuilder<SummaryWorker>()
                .setInputData(input)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
                .addTag("summary_worker")
                .addTag("summary_session_$sessionId")
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                uniqueWorkName(sessionId),
                if (replaceExisting) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
                request
            )
        }
    }
}

