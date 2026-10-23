package com.floatcalc.app.data

import kotlinx.serialization.Serializable

@Serializable
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Floating panel size presets, in dp. */
@Serializable
enum class OverlaySize(val widthDp: Int, val heightDp: Int) {
    SMALL(256, 384),
    MEDIUM(280, 420),
    LARGE(320, 480),
}

/** Where the floating window was last shown, in screen pixels (-1 = not placed yet). */
@Serializable
data class OverlayLayout(
    val panelX: Int = -1,
    val panelY: Int = -1,
    val bubbleX: Int = -1,
    val bubbleY: Int = -1,
    val minimized: Boolean = false,
)

@Serializable
data class AppSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val haptics: Boolean = true,
    /** Floating window opacity, [MIN_OPACITY]..1. */
    val overlayOpacity: Float = 1f,
    val overlaySize: OverlaySize = OverlaySize.MEDIUM,
    val overlayLayout: OverlayLayout = OverlayLayout(),
) {
    companion object {
        const val MIN_OPACITY = 0.5f
    }
}
