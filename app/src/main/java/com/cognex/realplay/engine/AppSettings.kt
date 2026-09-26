package com.cognex.realplay.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * App-wide developer/runtime toggles, held in memory for the session.
 *
 *  - [forceTrackOnly]   : force the §3.5 label-free downgrade, used at S3.5 to test the capability
 *                         downgrade path. Semantic generators drop out; objects are referenced by
 *                         track ID + highlight colour only.
 *  - [muted]            : mute all game sound effects (§S6 SoundManager mute toggle).
 */
object AppSettings {
    private val _forceTrackOnly = MutableStateFlow(false)
    val forceTrackOnly: StateFlow<Boolean> = _forceTrackOnly.asStateFlow()

    private val _muted = MutableStateFlow(false)
    val muted: StateFlow<Boolean> = _muted.asStateFlow()

    fun setForceTrackOnly(value: Boolean) { _forceTrackOnly.value = value }
    fun setMuted(value: Boolean) { _muted.value = value }
}
