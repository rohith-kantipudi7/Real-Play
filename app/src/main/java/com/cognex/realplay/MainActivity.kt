package com.cognex.realplay

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.cognex.realplay.device.DeviceCapabilities
import com.cognex.realplay.device.PerformanceProfile
import com.cognex.realplay.perception.AssetModelResolver
import com.cognex.realplay.ui.navigation.RealPlayNavHost
import com.cognex.realplay.ui.theme.RealPlayTheme
import com.cognex.realplay.util.RpLog

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        RpLog.i(RpLog.Tag.APP, "RealPlay starting (S0 foundation)")

        // Confirm bundled Tier-A model assets are actually in the APK and log their sizes.
        AssetModelResolver(applicationContext).inspectRequired()

        val capabilities = DeviceCapabilities.probe(applicationContext)
        val profile = PerformanceProfile.select(capabilities)

        setContent {
            RealPlayTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    RealPlayNavHost(
                        deviceCapabilities = capabilities,
                        performanceProfile = profile
                    )
                }
            }
        }
    }
}
