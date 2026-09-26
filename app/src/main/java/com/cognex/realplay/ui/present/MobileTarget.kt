package com.cognex.realplay.ui.present

import androidx.compose.ui.graphics.Color
import com.cognex.realplay.present.Overlay
import com.cognex.realplay.present.RenderModel
import com.cognex.realplay.ui.overlay.OverlayDetection
import com.cognex.realplay.ui.overlay.OverlayZone
import com.cognex.realplay.world.ColorTag

/**
 * The mobile [com.cognex.realplay.present.PresentationTarget] (Architecture §3.6, §27) — adapts a
 * device-independent [RenderModel] into the existing Compose overlay data ([OverlayDetection],
 * [OverlayZone]) drawn over the CameraX feed. This is the ONLY place a `ColorTag` becomes a Compose
 * `Color`; the render model stays pure (§20 invariant 22).
 *
 * It renders only what the engine put in the model (invariant 21): highlights → detection boxes,
 * zones → zone shapes. Skeletons (S8) and the coaching cue track (v3.6-B) are mapped here as they
 * land, all from the same model.
 */
object MobileTarget {

    /** Object highlights → detection boxes (with the target emphasis reflected in a brighter box). */
    fun detections(model: RenderModel): List<OverlayDetection> =
        model.scene.overlays.filterIsInstance<Overlay.Highlight>().map { h ->
            OverlayDetection(
                left = h.box.left,
                top = h.box.top,
                right = h.box.right,
                bottom = h.box.bottom,
                label = h.label,
                color = colorForTag(h.color)
            )
        }

    /** Zone polygons → zone shapes. */
    fun zones(model: RenderModel): List<OverlayZone> =
        model.scene.overlays.filterIsInstance<Overlay.ZoneShape>().map { z ->
            OverlayZone(
                polygon = z.polygon.map { it.x to it.y },
                color = colorForTag(z.color)
            )
        }

    /** Canonical [ColorTag] → display [Color] map for the mobile target. */
    fun colorForTag(tag: ColorTag?): Color = when (tag) {
        ColorTag.RED -> Color(0xFFF87171)
        ColorTag.ORANGE -> Color(0xFFFB923C)
        ColorTag.YELLOW -> Color(0xFFFDE047)
        ColorTag.GREEN -> Color(0xFF4ADE80)
        ColorTag.CYAN -> Color(0xFF22D3EE)
        ColorTag.BLUE -> Color(0xFF60A5FA)
        ColorTag.PURPLE -> Color(0xFFA78BFA)
        ColorTag.PINK -> Color(0xFFF472B6)
        ColorTag.WHITE -> Color(0xFFF1F5F9)
        ColorTag.GRAY -> Color(0xFF94A3B8)
        ColorTag.BLACK -> Color(0xFF334155)
        else -> Color(0xFF22D3EE)
    }
}
