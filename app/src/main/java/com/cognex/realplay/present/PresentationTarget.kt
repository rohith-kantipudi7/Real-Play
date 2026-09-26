package com.cognex.realplay.present

/**
 * The presentation seam (Architecture §3.6, §27). A target turns a device-independent [RenderModel]
 * into pixels for the current device and routes pointer input back to the engine. Pure interface —
 * no Android — so it can be referenced from anywhere without coupling the truth line to a renderer.
 *
 * The whole VR-scalability story lives here: the engine depends only on this contract, so a new
 * display is a new implementation, not a new product.
 *
 * INVARIANT 21: an implementation may render ONLY what the engine put in [RenderModel]; it can never
 * read perception, invoke a verifier, or alter a verdict. `MobileTarget` (ships now) and any future
 * `VrTarget` consume the identical model.
 */
interface PresentationTarget {

    /** Renders one frame's [model]. Called on the main/render thread of the concrete target. */
    fun render(model: RenderModel)

    /**
     * A pointer/selection at NORMALIZED analysis-image coordinates (0..1), routed back to the engine
     * as input. Default no-op — most targets are display-only. Never used to fabricate a verdict.
     */
    fun onPointer(xNorm: Float, yNorm: Float) {}
}
