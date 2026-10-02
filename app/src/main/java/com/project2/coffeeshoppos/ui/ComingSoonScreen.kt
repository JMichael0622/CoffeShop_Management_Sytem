package com.project2.coffeeshoppos.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.project2.coffeeshoppos.ui.components.CoffeeCard
import com.project2.coffeeshoppos.ui.components.EmptyState
import com.project2.coffeeshoppos.ui.navigation.TopLevelDestination

/** Temporary screen for sections that are built in later phases. */
@Composable
fun ComingSoonScreen(destination: TopLevelDestination, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        CoffeeCard(modifier = Modifier.fillMaxWidth()) {
            EmptyState(
                icon = destination.icon,
                title = "${destination.label} is on the way",
                message = destination.comingSoonMessage
            )
        }
    }
}
