package com.emduah.personal.wallpaper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.BatteryManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.concurrent.atomic.AtomicBoolean

object RotationNetwork {
    fun snapshot(context: Context, batteryLow: Boolean = false): RotationEnvironment {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork)
        return RotationEnvironment(
            internet = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
            wifi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true,
            batteryLow = batteryLow
        )
    }

    fun allowed(context: Context, wifiOnly: Boolean): Boolean = snapshot(context).let { it.internet && (!wifiOnly || it.wifi) }

    fun observe(context: Context): Flow<RotationEnvironment> = callbackFlow {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val batteryLow = AtomicBoolean(false)
        fun publish() { trySend(snapshot(context, batteryLow.get())) }
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { publish() }
            override fun onLost(network: Network) { publish() }
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) { publish() }
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                    val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
                    batteryLow.set(status != BatteryManager.BATTERY_STATUS_UNKNOWN && level >= 0 && scale > 0 && level.toFloat() / scale <= .15f)
                }
                publish()
            }
        }
        manager.registerDefaultNetworkCallback(callback)
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), Context.RECEIVER_NOT_EXPORTED)
        publish()
        awaitClose { manager.unregisterNetworkCallback(callback); context.unregisterReceiver(receiver) }
    }
}
