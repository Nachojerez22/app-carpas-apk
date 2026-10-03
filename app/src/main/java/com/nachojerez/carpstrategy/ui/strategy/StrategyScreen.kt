package com.nachojerez.carpstrategy.ui.strategy

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.nachojerez.carpstrategy.R
import com.nachojerez.carpstrategy.ui.PlaceholderScreen

@Composable
fun StrategyScreen() {
    PlaceholderScreen(
        title = stringResource(R.string.nav_strategy),
        description = stringResource(R.string.placeholder_strategy),
    )
}
