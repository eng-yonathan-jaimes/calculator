package com.floatcalc.app.overlay

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.util.Log
import com.floatcalc.app.MainActivity

/**
 * Invisible trampoline used by the Quick Settings tile and the launcher shortcut. Being a
 * visible activity for a moment makes the foreground-service start legal on Android 12+; it
 * lives in its own task so the main app never comes forward.
 */
class FloatLauncherActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        }
        if (FloatingCalculator.canDrawOverlays(this)) {
            try {
                FloatingCalculator.open(this)
            } catch (e: RuntimeException) {
                Log.w("FloatLauncher", "Could not start the floating calculator", e)
                startActivity(MainActivity.launchIntent(this))
            }
        } else {
            startActivity(MainActivity.permissionIntent(this))
        }
        finish()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }
}
