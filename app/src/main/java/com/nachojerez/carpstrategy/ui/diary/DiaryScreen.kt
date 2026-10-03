package com.nachojerez.carpstrategy.ui.diary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.nachojerez.carpstrategy.R
import com.nachojerez.carpstrategy.ui.components.Caption
import com.nachojerez.carpstrategy.ui.components.CarpCard
import com.nachojerez.carpstrategy.ui.components.ScreenHeader
import com.nachojerez.carpstrategy.ui.theme.Spacing

/** Diario de sesiones: llega en la fase 6. */
@Composable
fun DiaryScreen() {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.screen, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        ScreenHeader(stringResource(R.string.diary_title), stringResource(R.string.diary_overline))
        CarpCard {
            Text(stringResource(R.string.diary_coming_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.diary_coming_text))
            Caption(stringResource(R.string.coming_soon))
        }
    }
}
