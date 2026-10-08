package com.example.android.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.android.AegisApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Background WorkManager worker responsible for draining and synchronizing
 * local offline detection telemetry to the Phase 7 FastAPI / PostgreSQL backend.
 */
class ThreatSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val app = applicationContext as? AegisApplication ?: return@withContext Result.failure()
        val syncRepo = app.container.syncRepository

        try {
            val summary = syncRepo.processSyncQueue()
            if (summary.retryCount > 0) {
                Result.retry()
            } else {
                Result.success()
            }
        } catch (e: Exception) {
            if (runAttemptCount < 5) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }
}
