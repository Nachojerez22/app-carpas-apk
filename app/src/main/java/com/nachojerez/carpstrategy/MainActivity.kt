package com.nachojerez.carpstrategy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.nachojerez.carpstrategy.ui.navigation.CarpStrategyNavHost
import com.nachojerez.carpstrategy.ui.theme.CarpStrategyTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CarpStrategyTheme {
                CarpStrategyNavHost()
            }
        }
    }
}
