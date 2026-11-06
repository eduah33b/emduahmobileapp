package com.emduah.personal

import android.net.NetworkCapabilities
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.NetworkType
import com.emduah.personal.data.Profile
import com.emduah.personal.data.Screen
import com.emduah.personal.wallpaper.RotationScheduler
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RotationConstraintsDeviceTest {
    @Test fun wifiOnlyRequiresWifiAndAlsoAllowsMeteredWifi() {
        val constraints = RotationScheduler.constraints(Profile(Screen.HOME, wifiOnly = true))
        val request = constraints.requiredNetworkRequest!!
        assertTrue(request.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))
        assertFalse(request.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR))
        assertTrue(request.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
        assertFalse(request.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED))
        assertTrue(constraints.requiresBatteryNotLow())
    }
    @Test fun anyNetworkDoesNotRequireWifi() {
        val constraints = RotationScheduler.constraints(Profile(Screen.HOME, wifiOnly = false))
        assertNull(constraints.requiredNetworkRequest)
        assertEquals(NetworkType.CONNECTED, constraints.requiredNetworkType)
    }
}
