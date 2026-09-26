package com.cognex.realplay.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * App-wide developer/runtime toggles, held in memory for the session.
 *
 *  - [useFakeDetector]  : replay scripted detections so the whole app runs with no camera (§S2).
 *  - [forceTrackOnly]   : force the §3.5 label-free downgrade, used at S3.5 to test the capability
 *                         downgrade path. Semantic generators drop out; objects are referenced by
 *                         track ID + highlight colour only.
 */
object AppSettings {
    private val _useFakeDetector = MutableStateFlow(false)
    val useFakeDetector: StateFlow<Boolean> = _useFakeDetector.asStateFlow()

    private val _forceTrackOnly = MutableStateFlow(false)
    val forceTrackOnly: StateFlow<Boolean> = _forceTrackOnly.asStateFlow()

    fun setUseFakeDetector(value: Boolean) { _useFakeDetector.value = value }
    fun setForceTrackOnly(value: Boolean) { _forceTrackOnly.value = value }
}
