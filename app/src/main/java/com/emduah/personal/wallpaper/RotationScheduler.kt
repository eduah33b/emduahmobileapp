package com.emduah.personal.wallpaper

import android.content.Context
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.work.*
import com.emduah.personal.data.Profile
import com.emduah.personal.data.Screen
import java.util.concurrent.TimeUnit

object RotationScheduler {
    internal fun constraints(p: Profile): Constraints = Constraints.Builder().apply {
        if (p.wifiOnly) setRequiredNetworkRequest(
            NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(),
            NetworkType.CONNECTED
        ) else setRequiredNetworkType(NetworkType.CONNECTED)
        setRequiresBatteryNotLow(true)
    }.build()
    private fun request(p: Profile): PeriodicWorkRequest = PeriodicWorkRequestBuilder<RotationWorker>(p.minutes, TimeUnit.MINUTES)
            .setInitialDelay(p.minutes, TimeUnit.MINUTES)
            .setConstraints(constraints(p))
            .setInputData(workDataOf("screen" to p.screen.key))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES).build()
    fun schedule(context: Context, p: Profile) {
        val manager = WorkManager.getInstance(context)
        if (!p.enabled) { manager.cancelUniqueWork("rotation.${p.screen.key}"); return }
        manager.enqueueUniquePeriodicWork("rotation.${p.screen.key}", ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE, request(p))
    }
    fun refreshConstraints(context: Context, p: Profile) {
        if (p.enabled) WorkManager.getInstance(context).enqueueUniquePeriodicWork("rotation.${p.screen.key}", ExistingPeriodicWorkPolicy.UPDATE, request(p))
    }
    fun next(context: Context, p: Profile, manual: Boolean = false) {
        // A manual update starts a fresh interval for this screen's automatic rotation.
        schedule(context, p)
        val request = OneTimeWorkRequestBuilder<RotationWorker>().setConstraints(constraints(p))
            .setInputData(workDataOf("screen" to p.screen.key, "manual" to manual)).setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context).enqueueUniqueWork("next.${p.screen.key}", if (manual) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP, request)
    }
    fun cancelAll(context: Context) {
        Screen.entries.forEach {
            WorkManager.getInstance(context).cancelUniqueWork("rotation.${it.key}")
            WorkManager.getInstance(context).cancelUniqueWork("next.${it.key}")
        }
    }
}
