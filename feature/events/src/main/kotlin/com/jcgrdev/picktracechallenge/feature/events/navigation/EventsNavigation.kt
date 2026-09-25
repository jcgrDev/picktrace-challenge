package com.jcgrdev.picktracechallenge.feature.events.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.jcgrdev.picktracechallenge.feature.events.detail.EventDetailScreenRoute
import com.jcgrdev.picktracechallenge.feature.events.list.EventListScreenRoute
import kotlinx.serialization.Serializable

@Serializable
data object EventListRoute

@Serializable
data class EventDetailRoute(val id: String)

fun NavController.navigateToEventDetail(id: String) = navigate(EventDetailRoute(id))

fun NavGraphBuilder.eventListScreen(onAdd: () -> Unit, onOpen: (String) -> Unit) {
    composable<EventListRoute> { EventListScreenRoute(onAdd = onAdd, onOpen = onOpen) }
}

fun NavGraphBuilder.eventDetailScreen(onBack: () -> Unit) {
    composable<EventDetailRoute> { EventDetailScreenRoute(onBack = onBack) }
}
