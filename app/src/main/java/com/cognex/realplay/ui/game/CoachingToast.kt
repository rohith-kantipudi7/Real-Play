package com.cognex.realplay.ui.game

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.cognex.realplay.ui.common.RpToast

/**
 * The coaching toast for an Unsure outcome (Architecture §13 S6, §8). A deliberately CALM styling —
 * never red, never anything that reads as failure — because Unsure means "I can't tell yet, let me
 * help", not "you failed". Delegates to the shared [RpToast] so every "here's what to do" banner in
 * the app looks identical.
 */
@Composable
fun CoachingToast(
    hint: String?,
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    RpToast(text = hint.orEmpty(), visible = visible && hint != null, modifier = modifier)
}
