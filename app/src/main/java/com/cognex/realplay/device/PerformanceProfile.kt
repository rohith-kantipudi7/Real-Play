package com.cognex.realplay.device

import com.cognex.realplay.util.RpLog

/**
 * Coarse performance bucket that later stages use to scale detection cadence,
 * resolution and optional AI features.
 */
enum class PerformanceProfile {
    LOW, MEDIUM, HIGH;

    companion object {
        /**
         * Selects a profile from the device probe. Deliberately simple and deterministic —
         * RAM and core count are the strongest cheap signals for on-device ML throughput.
         */
        fun select(caps: DeviceCapabilities): PerformanceProfile {
            val profile = when {
                caps.totalRamMb >= 8_000 && caps.availableProcessors >= 8 -> HIGH
                caps.totalRamMb >= 4_000 && caps.availableProcessors >= 4 -> MEDIUM
                else -> LOW
            }
            RpLog.i(
                RpLog.Tag.DEVICE,
                "PerformanceProfile=$profile (ram=${caps.totalRamMb}MB, cores=${caps.availableProcessors})"
            )
            return profile
        }
    }
}
