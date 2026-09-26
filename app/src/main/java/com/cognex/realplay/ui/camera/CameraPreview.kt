package com.cognex.realplay.ui.camera

import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.cognex.realplay.camera.CameraController

/**
 * Hosts a CameraX [PreviewView] and keeps [controller] bound to the current lifecycle: it binds
 * on RESUME and unbinds on PAUSE, so returning from the background rebinds cleanly instead of
 * leaving a frozen surface.
 *
 * The PreviewView uses FILL_CENTER to match [com.cognex.realplay.camera.CoordinateMapper]'s
 * default scale type, so overlay boxes line up with the visible image.
 */
@Composable
fun CameraPreview(
    controller: CameraController,
    modifier: Modifier = Modifier
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    DisposableEffect(lifecycleOwner, previewView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> controller.bind(lifecycleOwner, previewView.surfaceProvider)
                Lifecycle.Event.ON_PAUSE -> controller.unbind()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            controller.unbind()
        }
    }

    AndroidView(factory = { previewView }, modifier = modifier)
}
