package com.cognex.realplay.world

/**
 * A single pose landmark in NORMALIZED analysis-image space (Architecture §4). Pure JVM.
 * [visibility] is the detector's 0..1 confidence that the joint is actually visible.
 * (Populated by the pose detector in S4; declared here so [TrackedPlayer] can compile.)
 */
data class Landmark(val point: NormPoint, val visibility: Float)
