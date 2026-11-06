package com.emduah.personal.ui

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.emduah.personal.data.Profile
import com.emduah.personal.wallpaper.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.isActive

internal data class RotationTiming(val updated: String, val status: String)

@Composable internal fun rememberRotationTiming(profile: Profile, changedAt: Long): RotationTiming {
    val context = LocalContext.current.applicationContext
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val now by produceState(System.currentTimeMillis(), lifecycle, profile.enabled, changedAt) {
        if (!profile.enabled && changedAt <= 0) return@produceState
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) { value = System.currentTimeMillis(); delay(1000) }
        }
    }
    val environmentFlow = remember(context) { RotationNetwork.observe(context) }
    val environment by environmentFlow.collectAsStateWithLifecycle(initialValue = RotationNetwork.snapshot(context))
    val workFlow = remember(context, profile.screen, profile.enabled) {
        if (!profile.enabled) flowOf(RotationWork(known = true)) else {
            val manager = WorkManager.getInstance(context)
            combine(manager.getWorkInfosForUniqueWorkFlow("rotation.${profile.screen.key}"), manager.getWorkInfosForUniqueWorkFlow("next.${profile.screen.key}")) { periodic, immediate ->
                val active = (periodic + immediate).filter { !it.state.isFinished }
                RotationWork(
                    nextAt = active.filter { it.state == WorkInfo.State.ENQUEUED }.map { it.nextScheduleTimeMillis }.filter { it in 1 until Long.MAX_VALUE }.minOrNull(),
                    running = active.any { it.state == WorkInfo.State.RUNNING },
                    known = true
                )
            }
        }
    }
    val work by workFlow.collectAsStateWithLifecycle(initialValue = RotationWork())
    return RotationTiming(RotationStatus.updatedAgo(changedAt, now), RotationStatus.nextUpdate(profile.enabled, profile.wifiOnly, environment, work, now))
}
