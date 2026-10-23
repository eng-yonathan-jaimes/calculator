package com.floatcalc.app.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy

/**
 * Root view of an overlay window. Reports window focus (needed for the clipboard and text
 * input), the back key while the window is focusable, and touches outside the window.
 */
@SuppressLint("ViewConstructor")
class OverlayRootView(context: Context, private val callbacks: Callbacks) : FrameLayout(context) {
    interface Callbacks {
        fun onWindowFocusChanged(hasFocus: Boolean) = Unit
        fun onBackPressed(): Boolean = false
        fun onOutsideTouch() = Unit
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        callbacks.onWindowFocusChanged(hasWindowFocus)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_UP && callbacks.onBackPressed()) return true
            if (event.action == KeyEvent.ACTION_DOWN) return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) {
            callbacks.onOutsideTouch()
            return true
        }
        return super.dispatchTouchEvent(event)
    }
}

/**
 * One TYPE_APPLICATION_OVERLAY window holding Compose content. [show] adds a fresh view tree
 * (owner RESUMED), [hide] removes it (owner DESTROYED). WindowManager failures, e.g. after the
 * overlay permission was revoked, are reported to [onError] instead of crashing.
 */
class OverlayWindow(
    private val context: Context,
    private val windowManager: WindowManager,
    val params: WindowManager.LayoutParams,
    private val onError: (RuntimeException) -> Unit,
    private val callbacks: OverlayRootView.Callbacks = object : OverlayRootView.Callbacks {},
    private val content: @Composable () -> Unit,
) {
    private var root: OverlayRootView? = null
    private var owner: OverlayViewOwner? = null

    val isShown: Boolean get() = root != null

    fun show(): Boolean {
        if (root != null) return true
        val newOwner = OverlayViewOwner()
        val newRoot = OverlayRootView(context, callbacks).apply {
            addView(
                ComposeView(context).apply {
                    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                    setContent(content)
                },
            )
        }
        newOwner.attachTo(newRoot)
        return try {
            windowManager.addView(newRoot, params)
            newOwner.resume()
            root = newRoot
            owner = newOwner
            true
        } catch (e: RuntimeException) {
            Log.w(TAG, "Could not add overlay window", e)
            newOwner.destroy()
            onError(e)
            false
        }
    }

    fun hide() {
        val current = root ?: return
        root = null
        try {
            windowManager.removeViewImmediate(current)
        } catch (e: RuntimeException) {
            Log.w(TAG, "Overlay window was already removed", e)
        }
        owner?.destroy()
        owner = null
    }

    /** Pushes changed [params] (position, size, flags, alpha) to the window if it is shown. */
    fun update() {
        val current = root ?: return
        try {
            windowManager.updateViewLayout(current, params)
        } catch (e: RuntimeException) {
            Log.w(TAG, "Could not update overlay window", e)
            onError(e)
        }
    }

    val view: View? get() = root

    companion object {
        private const val TAG = "OverlayWindow"

        /** Base parameters: an overlay that never takes focus, positioned in screen pixels. */
        fun layoutParams(width: Int, height: Int, x: Int, y: Int): WindowManager.LayoutParams =
            WindowManager.LayoutParams(
                width,
                height,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                this.x = x
                this.y = y
                // Coordinates are absolute screen pixels; OverlayGeometry keeps windows clear of
                // system bars and cutouts.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    fitInsetsTypes = 0
                    layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
                softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
            }
    }
}
