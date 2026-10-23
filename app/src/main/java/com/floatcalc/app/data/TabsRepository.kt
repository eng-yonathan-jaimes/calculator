package com.floatcalc.app.data

import androidx.datastore.core.DataStore
import com.floatcalc.app.engine.CalcKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

/**
 * Single source of truth for the calculator tabs, shared by the main app and the floating window.
 * All mutations go through [TabOps]; the state is persisted with debounced writes.
 */
class TabsRepository(
    store: DataStore<TabsState>,
    scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val persisted = PersistedState(store, scope, TabsState(), TabOps::sanitize)

    val state: StateFlow<TabsState> = persisted.value
    val loaded: StateFlow<Boolean> = persisted.loaded

    fun press(key: CalcKey) = persisted.update { TabOps.applyKey(it, key, clock()) }

    fun insert(text: String) = persisted.update { TabOps.insert(it, text) }

    /** Returns false when the tab limit is reached. */
    fun addTab(): Boolean {
        if (!state.value.canAddTab) return false
        persisted.update(TabOps::add)
        return true
    }

    fun selectTab(id: Long) = persisted.update { TabOps.select(it, id) }

    fun selectRelative(delta: Int) = persisted.update { TabOps.selectRelative(it, delta) }

    /** Closes a tab and returns what [restoreTab] needs for Undo. */
    fun closeTab(id: Long): ClosedTab? {
        var closed: ClosedTab? = null
        persisted.update { state ->
            val (next, info) = TabOps.close(state, id)
            closed = info
            next
        }
        return closed
    }

    fun restoreTab(closed: ClosedTab) = persisted.update { TabOps.restore(it, closed) }

    fun renameTab(id: Long, name: String) = persisted.update { TabOps.rename(it, id, name) }

    /** Returns false when the tab limit is reached. */
    fun duplicateTab(id: Long): Boolean {
        if (!state.value.canAddTab) return false
        persisted.update { TabOps.duplicate(it, id) }
        return true
    }

    fun clearHistory(id: Long) = persisted.update { TabOps.clearHistory(it, id) }

    fun flush() = persisted.flush()
}
