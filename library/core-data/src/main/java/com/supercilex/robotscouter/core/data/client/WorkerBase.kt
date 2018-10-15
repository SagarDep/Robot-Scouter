package com.supercilex.robotscouter.core.data.client

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.supercilex.robotscouter.core.logCrashLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

internal abstract class WorkerBase(
        context: Context,
        workerParams: WorkerParameters
) : Worker(context, workerParams) {
    override fun doWork() = runBlocking {
        if (runAttemptCount >= MAX_RUN_ATTEMPTS) return@runBlocking Result.FAILURE

        try {
            withContext(Dispatchers.IO) { doBlockingWork() }
        } catch (e: Exception) {
            logCrashLog("$javaClass errored: $e")
            Result.RETRY
        }
    }

    protected abstract suspend fun doBlockingWork(): Result

    private companion object {
        const val MAX_RUN_ATTEMPTS = 7
    }
}
