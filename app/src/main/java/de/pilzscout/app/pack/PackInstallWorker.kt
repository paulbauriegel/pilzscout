package de.pilzscout.app.pack

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import de.pilzscout.app.R
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.takeWhile
import java.util.concurrent.TimeUnit

/**
 * Keeps the process alive (foreground notification with progress) while PackRepository installs the pack.
 * The actual work is done by PackRepository so the UI can observe progress from the same flow. For remote
 * packs the worker waits for a (Wi-Fi, if requested) connection and retries with backoff after transient
 * failures such as a dropped connection; partial downloads resume.
 */
@HiltWorker
class PackInstallWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val packRepository: PackRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        runCatching { setForeground(foregroundInfo(null)) }
        packRepository.state.first { it.loaded }
        packRepository.installAll().join()
        // Update the notification while anything installs (the job above may have queued behind a UI install).
        packRepository.state.takeWhile { it.installing.isNotEmpty() }.collect { s ->
            val fraction = s.installing.values.average().toFloat()
            runCatching { setForeground(foregroundInfo(fraction)) }
        }
        val s = packRepository.state.value
        val transient = s.errors.values.any { it.transient } || s.manifestError?.transient == true
        return when {
            transient && s.remote && runAttemptCount < MAX_ATTEMPTS -> Result.retry()
            s.errors.isEmpty() -> Result.success()
            else -> Result.failure()
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = foregroundInfo(null)

    private fun foregroundInfo(fraction: Float?): ForegroundInfo {
        val notification = notification(fraction)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun notification(fraction: Float?): Notification {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, applicationContext.getString(R.string.pack_notification_channel), NotificationManager.IMPORTANCE_LOW),
        )
        return NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(applicationContext.getString(R.string.pack_notification_title))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .apply { if (fraction == null) setProgress(0, 0, true) else setProgress(1000, (fraction * 1000).toInt(), false) }
            .build()
    }

    companion object {
        const val CHANNEL = "pack_install"
        const val NOTIFICATION_ID = 41
        const val UNIQUE_NAME = "pack-install"
        private const val MAX_ATTEMPTS = 8

        /** Queues the install; for remote packs it starts once a suitable network is available. */
        fun enqueue(context: Context, remote: Boolean, wifiOnly: Boolean) {
            val builder = OneTimeWorkRequestBuilder<PackInstallWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            if (remote) {
                builder
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED).build())
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            }
            WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.REPLACE, builder.build())
        }
    }
}
