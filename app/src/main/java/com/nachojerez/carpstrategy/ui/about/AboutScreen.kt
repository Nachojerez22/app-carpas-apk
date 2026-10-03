package com.nachojerez.carpstrategy.ui.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nachojerez.carpstrategy.BuildConfig
import com.nachojerez.carpstrategy.R

@Composable
fun AboutScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.about_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.about_description), style = MaterialTheme.typography.bodyLarge)

        Text(stringResource(R.string.about_sources_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.about_source_openmeteo))
        Text(stringResource(R.string.about_source_aemet))

        Text(stringResource(R.string.about_disclaimers_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.about_disclaimer_water))
        Text(stringResource(R.string.about_disclaimer_rules))
        Text(
            stringResource(
                if (BuildConfig.AEMET_API_KEY.isBlank()) {
                    R.string.about_aemet_key_missing
                } else {
                    R.string.about_aemet_key_present
                },
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text(
            stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
