package com.nachojerez.carpstrategy

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nachojerez.carpstrategy.ui.AppViewModel
import com.nachojerez.carpstrategy.ui.CrashReportDialog
import com.nachojerez.carpstrategy.ui.UpdateDialog
import com.nachojerez.carpstrategy.ui.guided.GuidedNotifier
import com.nachojerez.carpstrategy.ui.guided.GuidedSessionManager
import com.nachojerez.carpstrategy.ui.navigation.CarpStrategyNavHost
import com.nachojerez.carpstrategy.ui.theme.CarpTheme
import com.nachojerez.carpstrategy.ui.theme.toThemePrefs
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val appViewModel: AppViewModel by viewModels()

    @Inject lateinit var guided: GuidedSessionManager

    /** La notificación de la sesión guiada pide abrir su pantalla. */
    private var openGuided by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        openGuided = savedInstanceState == null && intent.wantsGuided()
        // Si hay sesión guiada en curso, se vuelve a programar el aviso (p. ej. tras reiniciar).
        lifecycleScope.launch { guided.restore() }
        setContent {
            val appearance by appViewModel.appearance.collectAsStateWithLifecycle()
            val lastCrash by appViewModel.lastCrash.collectAsStateWithLifecycle()
            CarpTheme(appearance.toThemePrefs()) {
                CarpStrategyNavHost(openGuided = openGuided, onGuidedOpened = { openGuided = false })
                lastCrash?.let { CrashReportDialog(it, onDismiss = appViewModel::dismissCrash) }
                val update by appViewModel.update.collectAsStateWithLifecycle()
                val syncEnabled by appViewModel.syncEnabled.collectAsStateWithLifecycle()
                if (lastCrash == null) {
                    update?.let { UpdateDialog(it, syncEnabled, onOpened = appViewModel::updateOpened, onLater = appViewModel::dismissUpdate) }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.wantsGuided()) openGuided = true
    }

    private fun Intent.wantsGuided(): Boolean = getBooleanExtra(GuidedNotifier.EXTRA_OPEN_GUIDED, false)
}
