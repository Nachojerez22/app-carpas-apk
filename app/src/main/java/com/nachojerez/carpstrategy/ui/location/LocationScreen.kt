package com.nachojerez.carpstrategy.ui.location

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.nachojerez.carpstrategy.R
import com.nachojerez.carpstrategy.domain.model.DefaultLocation
import com.nachojerez.carpstrategy.ui.PlaceholderScreen

@Composable
fun LocationScreen() {
    val location = DefaultLocation.value
    PlaceholderScreen(
        title = stringResource(R.string.nav_location),
        description = stringResource(
            R.string.placeholder_location,
            location.name,
            location.point.latitude,
            location.point.longitude,
        ),
    )
}
