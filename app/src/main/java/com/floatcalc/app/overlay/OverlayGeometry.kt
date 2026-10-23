package com.floatcalc.app.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Point
import android.graphics.Rect
import android.os.Build
import android.view.WindowInsets
import android.view.WindowManager
import kotlin.math.roundToInt

/**
 * The part of the screen overlay windows may use, in absolute screen pixels: the display minus
 * status bar, navigation bar and display cutouts.
 */
data class OverlayGeometry(
    val screenWidth: Int,
    val screenHeight: Int,
    val safe: Rect,
    val density: Float,
) {
    fun dp(value: Int): Int = (value * density).roundToInt()

    /** Fits a requested window size inside the safe area (with a margin). */
    fun fitSize(width: Int, height: Int): Pair<Int, Int> {
        val margin = dp(MARGIN_DP)
        return width.coerceAtMost(safe.width() - 2 * margin).coerceAtLeast(dp(MIN_SIZE_DP)) to
            height.coerceAtMost(safe.height() - 2 * margin).coerceAtLeast(dp(MIN_SIZE_DP))
    }

    fun clampX(x: Int, width: Int): Int = x.coerceIn(safe.left, maxOf(safe.left, safe.right - width))

    fun clampY(y: Int, height: Int): Int = y.coerceIn(safe.top, maxOf(safe.top, safe.bottom - height))

    /** x of a window of [width] resting against the left or right edge. */
    fun edgeX(onRight: Boolean, width: Int): Int {
        val margin = dp(EDGE_MARGIN_DP)
        return if (onRight) clampX(safe.right - width - margin, width) else clampX(safe.left + margin, width)
    }

    fun isOnRightHalf(x: Int, width: Int): Boolean = x + width / 2 > (safe.left + safe.right) / 2

    companion object {
        private const val MARGIN_DP = 8
        private const val EDGE_MARGIN_DP = 4
        private const val MIN_SIZE_DP = 48

        fun of(context: Context): OverlayGeometry {
            val wm = context.getSystemService(WindowManager::class.java)
            val density = context.resources.displayMetrics.density
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val metrics = wm.maximumWindowMetrics
                val bounds = metrics.bounds
                val insets = metrics.windowInsets.getInsetsIgnoringVisibility(
                    WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout(),
                )
                OverlayGeometry(
                    screenWidth = bounds.width(),
                    screenHeight = bounds.height(),
                    safe = Rect(insets.left, insets.top, bounds.width() - insets.right, bounds.height() - insets.bottom),
                    density = density,
                )
            } else {
                legacy(context, wm, density)
            }
        }

        /**
         * Android 8–10: Display.getSize() excludes the navigation bar; the status bar height comes
         * from the framework dimension (24dp if unavailable).
         */
        @Suppress("DEPRECATION")
        private fun legacy(context: Context, wm: WindowManager, density: Float): OverlayGeometry {
            val real = Point().also { wm.defaultDisplay.getRealSize(it) }
            val app = Point().also { wm.defaultDisplay.getSize(it) }
            var top = statusBarHeight(context).takeIf { it > 0 } ?: (24 * density).roundToInt()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                wm.defaultDisplay.cutout?.let { top = maxOf(top, it.safeInsetTop) }
            }
            return OverlayGeometry(real.x, real.y, Rect(0, top, app.x, app.y), density)
        }

        @SuppressLint("DiscouragedApi", "InternalInsetResource")
        private fun statusBarHeight(context: Context): Int {
            val id = context.resources.getIdentifier("status_bar_height", "dimen", "android")
            return if (id > 0) context.resources.getDimensionPixelSize(id) else 0
        }
    }
}
