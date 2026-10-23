package com.floatcalc.app

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether MainActivity itself is on screen (started). The floating window hides while it is.
 * This follows MainActivity's lifecycle rather than the process, so the transparent trampoline
 * activities never count as "app visible". Main thread only.
 */
object AppVisibility {
    private var startedActivities = 0
    private val _mainActivityVisible = MutableStateFlow(false)
    val mainActivityVisible: StateFlow<Boolean> = _mainActivityVisible.asStateFlow()

    fun onMainActivityStarted() {
        startedActivities++
        _mainActivityVisible.value = true
    }

    fun onMainActivityStopped() {
        startedActivities = (startedActivities - 1).coerceAtLeast(0)
        _mainActivityVisible.value = startedActivities > 0
    }
}
