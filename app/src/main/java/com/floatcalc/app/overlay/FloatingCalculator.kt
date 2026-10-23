package com.floatcalc.app.overlay

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/** Entry points for starting and controlling the floating calculator service. */
object FloatingCalculator {
    const val ACTION_OPEN = "com.floatcalc.app.action.OPEN_FLOATING"
    const val ACTION_SHOW = "com.floatcalc.app.action.SHOW_FLOATING"
    const val ACTION_HIDE = "com.floatcalc.app.action.HIDE_FLOATING"
    const val ACTION_CLOSE = "com.floatcalc.app.action.CLOSE_FLOATING"

    private val _running = MutableStateFlow(false)

    /** True while the service is alive (the Quick Settings tile shows this). */
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val _clipboardTexts = MutableSharedFlow<String?>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** Clipboard text read by [ClipboardReaderActivity] (the paste fallback). */
    internal val clipboardTexts: SharedFlow<String?> = _clipboardTexts.asSharedFlow()

    private val _closeRequests = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    internal val closeRequests: SharedFlow<Unit> = _closeRequests.asSharedFlow()

    internal fun setRunning(running: Boolean) {
        _running.value = running
    }

    /** Closes a running floating calculator (it lives in this process, so no intent is needed). */
    fun close() {
        _closeRequests.tryEmit(Unit)
    }

    internal fun deliverClipboardText(text: String?) {
        _clipboardTexts.tryEmit(text)
    }

    fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

    /**
     * Starts the service (or brings an existing window back, expanded). Must be called from a
     * visible activity: Android 12+ blocks foreground-service starts from the background.
     */
    fun open(context: Context) {
        ContextCompat.startForegroundService(context, intent(context, ACTION_OPEN))
    }

    internal fun intent(context: Context, action: String): Intent =
        Intent(context, FloatingCalculatorService::class.java).setAction(action)
}
