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
 *  - [aiComposerEnabled]: allow the optional ON-DEVICE Gemma composer to arrange registered,
 *                         verifiable skills into the mission + reword instructions (§6.5,
 *                         §3.1/§3.2). Fully offline; never on the truth path; the deterministic
 *                         composer is always the fallback. Off unless a Tier-B model is present.
 */
object AppSettings {
    private val _forceTrackOnly = MutableStateFlow(false)
    val forceTrackOnly: StateFlow<Boolean> = _forceTrackOnly.asStateFlow()

    private val _muted = MutableStateFlow(false)
    val muted: StateFlow<Boolean> = _muted.asStateFlow()

    private val _aiComposerEnabled = MutableStateFlow(false)
    val aiComposerEnabled: StateFlow<Boolean> = _aiComposerEnabled.asStateFlow()

    fun setForceTrackOnly(value: Boolean) { _forceTrackOnly.value = value }
    fun setMuted(value: Boolean) { _muted.value = value }
    fun setAiComposerEnabled(value: Boolean) { _aiComposerEnabled.value = value }
}
