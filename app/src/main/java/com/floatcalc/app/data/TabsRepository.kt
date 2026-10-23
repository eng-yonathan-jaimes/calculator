package com.floatcalc.app.data

import com.floatcalc.app.engine.CalcKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Single source of truth for the calculator tabs, shared by the main app and the floating window.
 * All mutations go through [TabOps].
 */
class TabsRepository(
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val _state = MutableStateFlow(TabsState())
    val state: StateFlow<TabsState> = _state.asStateFlow()

    fun press(key: CalcKey) = update { TabOps.applyKey(it, key, clock()) }

    fun insert(text: String) = update { TabOps.insert(it, text) }

    fun clearHistory(id: Long) = update { TabOps.clearHistory(it, id) }

    private inline fun update(transform: (TabsState) -> TabsState) {
        _state.update(transform)
    }
}
