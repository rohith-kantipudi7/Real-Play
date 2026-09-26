package com.cognex.realplay.device

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import com.cognex.realplay.util.RpLog

/**
 * Immutable snapshot of the device's runtime capabilities. Probed once at startup and
 * shown in Settings. Values here drive the [PerformanceProfile] selection.
 */
data class DeviceCapabilities(
    val androidSdk: Int,
    val androidRelease: String,
    val manufacturer: String,
    val model: String,
    val totalRamMb: Long,
    val availableProcessors: Int,
    val batteryPercent: Int,
    val thermalStatus: ThermalStatus
) {
    companion object {
        fun probe(context: Context): DeviceCapabilities {
            val activityManager =
                context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memInfo)
            val totalRamMb = memInfo.totalMem / (1024L * 1024L)

            val batteryPercent = readBatteryPercent(context)
            val thermal = readThermalStatus(context)

            return DeviceCapabilities(
                androidSdk = Build.VERSION.SDK_INT,
                androidRelease = Build.VERSION.RELEASE ?: "unknown",
                manufacturer = Build.MANUFACTURER ?: "unknown",
                model = Build.MODEL ?: "unknown",
                totalRamMb = totalRamMb,
                availableProcessors = Runtime.getRuntime().availableProcessors(),
                batteryPercent = batteryPercent,
                thermalStatus = thermal
            ).also {
                RpLog.i(RpLog.Tag.DEVICE, "Device probe: $it")
            }
        }

        private fun readBatteryPercent(context: Context): Int {
            val bm = context.getSystemService(Context.BATTERY_SERVICE)
                as? android.os.BatteryManager ?: return -1
            return bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
        }

        private fun readThermalStatus(context: Context): ThermalStatus {
            // PowerManager.getCurrentThermalStatus requires API 29 (guarded).
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return ThermalStatus.UNKNOWN
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                ?: return ThermalStatus.UNKNOWN
            return ThermalStatus.fromAndroid(pm.currentThermalStatus)
        }
    }
}

/** Thin, API-safe wrapper over [PowerManager]'s thermal status constants. */
enum class ThermalStatus {
    NONE, LIGHT, MODERATE, SEVERE, CRITICAL, EMERGENCY, SHUTDOWN, UNKNOWN;

    companion object {
        fun fromAndroid(value: Int): ThermalStatus = when (value) {
            PowerManager.THERMAL_STATUS_NONE -> NONE
            PowerManager.THERMAL_STATUS_LIGHT -> LIGHT
            PowerManager.THERMAL_STATUS_MODERATE -> MODERATE
            PowerManager.THERMAL_STATUS_SEVERE -> SEVERE
            PowerManager.THERMAL_STATUS_CRITICAL -> CRITICAL
            PowerManager.THERMAL_STATUS_EMERGENCY -> EMERGENCY
            PowerManager.THERMAL_STATUS_SHUTDOWN -> SHUTDOWN
            else -> UNKNOWN
        }
    }
}
