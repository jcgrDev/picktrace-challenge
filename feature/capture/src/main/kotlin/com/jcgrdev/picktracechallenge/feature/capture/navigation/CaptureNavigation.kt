package com.jcgrdev.picktracechallenge.feature.capture.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.jcgrdev.picktracechallenge.feature.capture.CaptureScreenRoute
import kotlinx.serialization.Serializable

@Serializable
data object CaptureRoute

fun NavController.navigateToCapture() = navigate(CaptureRoute)

fun NavGraphBuilder.captureScreen(onBack: () -> Unit) {
    composable<CaptureRoute> { CaptureScreenRoute(onBack = onBack) }
}
