package com.floatcalc.app.tile

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import com.floatcalc.app.MainActivity
import com.floatcalc.app.R
import com.floatcalc.app.overlay.FloatLauncherActivity
import com.floatcalc.app.overlay.FloatingCalculator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Quick Settings tile "FloatCalc": toggles the floating calculator and shows whether it is on. */
class FloatTileService : TileService() {
    private var listeningScope: CoroutineScope? = null

    override fun onStartListening() {
        super.onStartListening()
        listeningScope?.cancel()
        listeningScope = MainScope().also { scope ->
            scope.launch { FloatingCalculator.running.collect { updateTile(it) } }
        }
    }

    override fun onStopListening() {
        listeningScope?.cancel()
        listeningScope = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        if (FloatingCalculator.running.value) {
            FloatingCalculator.close()
            updateTile(active = false)
        } else if (isLocked) {
            unlockAndRun { openFloating() }
        } else {
            openFloating()
        }
    }

    /**
     * Starting the service needs a foreground context, so go through the transparent trampoline
     * (or the app's permission screen when the overlay permission is missing).
     */
    private fun openFloating() {
        val intent = if (FloatingCalculator.canDrawOverlays(this)) {
            Intent(this, FloatLauncherActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } else {
            MainActivity.permissionIntent(this)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startActivityAndCollapse(
                    PendingIntent.getActivity(
                        this,
                        intent.component?.className.hashCode(),
                        intent,
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    ),
                )
            } else {
                startActivityAndCollapseLegacy(intent)
            }
        } catch (e: RuntimeException) {
            Log.w(TAG, "Could not open the floating calculator from the tile", e)
        }
    }

    /** The PendingIntent overload only exists on API 34+; older versions need the Intent one. */
    @SuppressLint("StartActivityAndCollapseDeprecated")
    @Suppress("DEPRECATION")
    private fun startActivityAndCollapseLegacy(intent: Intent) = startActivityAndCollapse(intent)

    private fun updateTile(active: Boolean) {
        val tile = qsTile ?: return
        tile.state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.tile_label)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = getString(if (active) R.string.tile_on else R.string.tile_off)
        }
        tile.updateTile()
    }

    companion object {
        private const val TAG = "FloatTileService"

        /** Asks the system to refresh the tile (e.g. after the service started or stopped). */
        fun requestUpdate(context: Context) {
            try {
                requestListeningState(context, ComponentName(context, FloatTileService::class.java))
            } catch (e: RuntimeException) {
                Log.w(TAG, "Could not request a tile update", e)
            }
        }
    }
}
