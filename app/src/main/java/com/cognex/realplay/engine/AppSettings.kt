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
 *  - [aiComposerEnabled]: allow the composer chain (cloud Azure model, then on-device Gemma) to
 *                         arrange registered, verifiable skills into the mission + reword
 *                         instructions (§6.5, §3.1/§3.2, v3.7). Fully validator-gated; never on
 *                         the truth path; the deterministic composer is always the fallback. On by
 *                         default — falls back silently to on-device, then deterministic, if the
 *                         cloud is unreachable or no Tier-B model is present.
 *  - [devOverlayEnabled]: shows raw internal numbers (scene richness terms, generator scores,
 *                         difficulty "why" line) on top of the camera preview. Off by default —
 *                         these are debugging aids, not something a player should see.
 */
object AppSettings {
    private val _forceTrackOnly = MutableStateFlow(false)
    val forceTrackOnly: StateFlow<Boolean> = _forceTrackOnly.asStateFlow()

    private val _muted = MutableStateFlow(false)
    val muted: StateFlow<Boolean> = _muted.asStateFlow()

    private val _aiComposerEnabled = MutableStateFlow(true)
    val aiComposerEnabled: StateFlow<Boolean> = _aiComposerEnabled.asStateFlow()

    private val _devOverlayEnabled = MutableStateFlow(false)
    val devOverlayEnabled: StateFlow<Boolean> = _devOverlayEnabled.asStateFlow()

    fun setForceTrackOnly(value: Boolean) { _forceTrackOnly.value = value }
    fun setMuted(value: Boolean) { _muted.value = value }
    fun setAiComposerEnabled(value: Boolean) { _aiComposerEnabled.value = value }
    fun setDevOverlayEnabled(value: Boolean) { _devOverlayEnabled.value = value }
}
