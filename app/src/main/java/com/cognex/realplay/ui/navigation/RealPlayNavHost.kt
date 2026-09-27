package com.cognex.realplay.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.cognex.realplay.device.DeviceCapabilities
import com.cognex.realplay.device.PerformanceProfile
import com.cognex.realplay.engine.PartyRuntime
import com.cognex.realplay.engine.PlayMode
import com.cognex.realplay.engine.SessionConfig
import com.cognex.realplay.engine.Audience
import com.cognex.realplay.challenge.AgeBand
import com.cognex.realplay.ui.audience.AudienceScreen
import com.cognex.realplay.ui.calibration.CalibrationScreen
import com.cognex.realplay.ui.game.GameScreen
import com.cognex.realplay.ui.home.HomeScreen
import com.cognex.realplay.ui.modeselect.ModeSelectScreen
import com.cognex.realplay.ui.party.PartyHandoffScreen
import com.cognex.realplay.ui.party.PartyPodiumScreen
import com.cognex.realplay.ui.party.PartyRosterScreen
import com.cognex.realplay.ui.present.VrPreviewScreen
import com.cognex.realplay.ui.result.ResultScreen
import com.cognex.realplay.ui.settings.SettingsScreen

/**
 * Single navigation graph for the app. Screens are placeholders in S0; later stages fill
 * them in without changing these routes.
 */
@Composable
fun RealPlayNavHost(
    deviceCapabilities: DeviceCapabilities,
    performanceProfile: PerformanceProfile,
    navController: NavHostController = rememberNavController()
) {
    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onPlay = {
                    PartyRuntime.clear()
                    // Point & Play (§1): zero-config — objects by default so a body-pose game only
                    // appears when a human is explicitly chosen (Home → Advanced → Body/Mixed).
                    SessionConfig.mode = PlayMode.OBJECTS
                    SessionConfig.audience = Audience.PLAYER
                    SessionConfig.playerCount = 1
                    // Difficulty is chosen on its own screen BEFORE the camera opens.
                    navController.navigate(Routes.AUDIENCE)
                },
                onParty = { navController.navigate(Routes.PARTY_ROSTER) },
                onVrPreview = { navController.navigate(Routes.VR_PREVIEW) },
                onSettings = { navController.navigate(Routes.SETTINGS) },
                onAdvanced = {
                    PartyRuntime.clear()
                    navController.navigate(Routes.MODE_SELECT)
                }
            )
        }
        composable(Routes.AUDIENCE) {
            AudienceScreen(
                onContinue = { navController.navigate(Routes.CALIBRATION) },
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.MODE_SELECT) {
            ModeSelectScreen(
                onContinue = { navController.navigate(Routes.CALIBRATION) },
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.CALIBRATION) {
            CalibrationScreen(
                onReady = { navController.navigate(Routes.GAME) },
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.GAME) {
            GameScreen(
                onFinish = {
                    val party = PartyRuntime.active
                    when {
                        party == null -> navController.navigate(Routes.RESULT)
                        party.sessionOver -> navController.navigate(Routes.PARTY_PODIUM) {
                            popUpTo(Routes.PARTY_ROSTER)
                        }
                        else -> navController.navigate(Routes.PARTY_HANDOFF) {
                            popUpTo(Routes.PARTY_HANDOFF) { inclusive = true }
                        }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.RESULT) {
            ResultScreen(
                onPlayAgain = { navController.popBackStack(Routes.MODE_SELECT, inclusive = false) },
                onHome = { navController.popBackStack(Routes.HOME, inclusive = false) }
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                deviceCapabilities = deviceCapabilities,
                performanceProfile = performanceProfile,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.PARTY_ROSTER) {
            PartyRosterScreen(
                onStart = { navController.navigate(Routes.PARTY_HANDOFF) },
                onBack = { navController.popBackStack() }
            )
        }
        // PARTY skips Calibration between turns — fast, energetic pacing (§25) over a per-turn
        // capability re-read; each round plays against whatever the camera already sees.
        composable(Routes.PARTY_HANDOFF) {
            PartyHandoffScreen(
                onReady = { navController.navigate(Routes.GAME) },
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.PARTY_PODIUM) {
            PartyPodiumScreen(
                onPlayAgain = {
                    PartyRuntime.clear()
                    navController.navigate(Routes.PARTY_ROSTER) { popUpTo(Routes.HOME) }
                },
                onHome = {
                    PartyRuntime.clear()
                    navController.popBackStack(Routes.HOME, inclusive = false)
                }
            )
        }
        composable(Routes.VR_PREVIEW) {
            VrPreviewScreen(onBack = { navController.popBackStack() })
        }
    }
}
