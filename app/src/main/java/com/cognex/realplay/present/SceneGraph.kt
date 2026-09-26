package com.cognex.realplay.present

import com.cognex.realplay.world.WorldState

/**
 * Which real-world backdrop the overlays are drawn over (Architecture §3.6, §27). Pure JVM.
 * [MobileTarget] uses [CAMERA] (the CameraX feed); a future `VrTarget` would use [VR_PASSTHROUGH]
 * (headset passthrough). The engine states the intent; each target realises it.
 */
enum class PassthroughLayer { CAMERA, VR_PASSTHROUGH, NONE }

/**
 * The device-independent scene description (Architecture §3.6, §27): the backdrop layer plus the
 * ordered [overlays] to draw over it. Pure JVM — the single source of scene truth both targets
 * render (§20 invariant 22). Draw order is list order (earlier = underneath).
 */
data class SceneGraph(
    val passthrough: PassthroughLayer,
    val overlays: List<Overlay>
)

/**
 * The perception-derived overlays for one frame (Architecture §3.6). Pure JVM — built directly from
 * the pure [WorldState], with zero Android. This is the ONLY thing [SceneComposer] needs from the
 * world model to build a scene graph, keeping the composer independent of the camera/detector.
 */
data class ScenePerception(
    val highlights: List<Overlay.Highlight>,
    val zones: List<Overlay.ZoneShape>,
    val skeletons: List<Overlay.Skeleton> = emptyList(),
    val players: List<Overlay.PlayerHalo> = emptyList()
) {
    companion object {
        val EMPTY = ScenePerception(emptyList(), emptyList())

        /**
         * Projects a [WorldState] into overlays. Objects become [Overlay.Highlight]s (those whose
         * trackId is in [targetTrackIds] are flagged [Emphasis.TARGET]); zones become
         * [Overlay.ZoneShape]s; confirmed players become [Overlay.PlayerHalo]s (S8). Pure — no Android.
         */
        fun fromWorld(world: WorldState, targetTrackIds: Set<Int> = emptySet()): ScenePerception {
            val highlights = world.objects.map { o ->
                Overlay.Highlight(
                    box = o.box,
                    label = "#${o.trackId} ${o.label}".trim(),
                    color = o.color,
                    emphasis = if (o.trackId in targetTrackIds) Emphasis.TARGET else Emphasis.NORMAL,
                    trackId = o.trackId
                )
            }
            val zones = world.zones.map { z -> Overlay.ZoneShape(z.polygon, z.color, z.zoneId) }
            val players = world.players.map { p ->
                Overlay.PlayerHalo(
                    box = p.torsoBox,
                    playerId = p.playerId,
                    color = p.colorBand,
                    ambiguous = p.ambiguous
                )
            }
            return ScenePerception(highlights, zones, players = players)
        }
    }
}
