package com.floatcalc.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.floatcalc.app.overlay.FloatingCalculator
import com.floatcalc.app.ui.AppRoot
import com.floatcalc.app.ui.calc.LocalHapticsEnabled
import com.floatcalc.app.ui.theme.FloatCalcTheme

class MainActivity : ComponentActivity() {
    /** Set when opened by the tile or shortcut without the overlay permission. */
    private val permissionRequested = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIntent(intent)
        val container = appContainer
        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle()
            val tabsLoaded by container.tabs.loaded.collectAsStateWithLifecycle()
            val settingsLoaded by container.settings.loaded.collectAsStateWithLifecycle()
            FloatCalcTheme(settings.theme) {
                CompositionLocalProvider(LocalHapticsEnabled provides settings.haptics) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        if (tabsLoaded && settingsLoaded) {
                            AppRoot(
                                container = container,
                                permissionRequested = permissionRequested.value,
                                onPermissionRequestHandled = { permissionRequested.value = false },
                                onStartFloating = ::startFloating,
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        AppVisibility.onMainActivityStarted()
    }

    override fun onStop() {
        super.onStop()
        AppVisibility.onMainActivityStopped()
        appContainer.flush()
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_REQUEST_OVERLAY_PERMISSION, false) == true) {
            permissionRequested.value = true
        }
    }

    /** Opens the floating window and sends the app to the background. */
    private fun startFloating() {
        FloatingCalculator.open(this)
        moveTaskToBack(true)
    }

    companion object {
        private const val EXTRA_REQUEST_OVERLAY_PERMISSION = "com.floatcalc.app.extra.REQUEST_OVERLAY_PERMISSION"

        /** Brings the app to the front like the launcher does. */
        fun launchIntent(context: Context): Intent =
            Intent(context, MainActivity::class.java)
                .setAction(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        /** Opens the app and asks for the overlay permission, then starts floating. */
        fun permissionIntent(context: Context): Intent =
            launchIntent(context).putExtra(EXTRA_REQUEST_OVERLAY_PERMISSION, true)
    }
}
