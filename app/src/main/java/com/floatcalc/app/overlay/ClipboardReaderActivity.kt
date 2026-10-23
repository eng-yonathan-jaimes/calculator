package com.floatcalc.app.overlay

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.floatcalc.app.util.Clipboard

/**
 * Paste fallback for devices that never give the overlay window focus: a transparent activity
 * gets focus, reads the clipboard, hands the text to the service and finishes.
 */
class ClipboardReaderActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private var delivered = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        }
        handler.postDelayed({ deliver(null) }, GIVE_UP_MS)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) deliver(Clipboard.readText(this))
    }

    private fun deliver(text: String?) {
        if (delivered) return
        delivered = true
        handler.removeCallbacksAndMessages(null)
        FloatingCalculator.deliverClipboardText(text)
        finish()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private companion object {
        const val GIVE_UP_MS = 1_500L
    }
}
