package com.emduah.personal.wallpaper

import android.content.Context
import androidx.work.*
import com.emduah.personal.data.Profile
import com.emduah.personal.data.Screen
import java.util.concurrent.TimeUnit

object RotationScheduler {
    private fun constraints(p: Profile) = Constraints.Builder().setRequiredNetworkType(if (p.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED).setRequiresBatteryNotLow(true).build()
    fun schedule(context: Context, p: Profile) {
        val manager = WorkManager.getInstance(context)
        if (!p.enabled) { manager.cancelUniqueWork("rotation.${p.screen.key}"); return }
        val request = PeriodicWorkRequestBuilder<RotationWorker>(p.minutes, TimeUnit.MINUTES)
            .setInitialDelay(p.minutes, TimeUnit.MINUTES)
            .setConstraints(constraints(p))
            .setInputData(workDataOf("screen" to p.screen.key))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES).build()
        manager.enqueueUniquePeriodicWork("rotation.${p.screen.key}", ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE, request)
    }
    fun next(context: Context, p: Profile) {
        val request = OneTimeWorkRequestBuilder<RotationWorker>().setConstraints(constraints(p))
            .setInputData(workDataOf("screen" to p.screen.key)).setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context).enqueueUniqueWork("next.${p.screen.key}", ExistingWorkPolicy.KEEP, request)
    }
    fun cancelAll(context: Context) {
        Screen.entries.forEach {
            WorkManager.getInstance(context).cancelUniqueWork("rotation.${it.key}")
            WorkManager.getInstance(context).cancelUniqueWork("next.${it.key}")
        }
    }
}
