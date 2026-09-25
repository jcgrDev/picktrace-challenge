package com.jcgrdev.picktracechallenge.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.jcgrdev.picktracechallenge.feature.capture.navigation.captureScreen
import com.jcgrdev.picktracechallenge.feature.capture.navigation.navigateToCapture
import com.jcgrdev.picktracechallenge.feature.events.navigation.EventListRoute
import com.jcgrdev.picktracechallenge.feature.events.navigation.eventDetailScreen
import com.jcgrdev.picktracechallenge.feature.events.navigation.eventListScreen
import com.jcgrdev.picktracechallenge.feature.events.navigation.navigateToEventDetail

@Composable
fun PicktraceNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = EventListRoute) {
        eventListScreen(onAdd = navController::navigateToCapture, onOpen = navController::navigateToEventDetail)
        eventDetailScreen(onBack = navController::popBackStack)
        captureScreen(onBack = navController::popBackStack)
    }
}
