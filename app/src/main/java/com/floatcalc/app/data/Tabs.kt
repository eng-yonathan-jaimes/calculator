package com.floatcalc.app.data

import com.floatcalc.app.engine.CalcEditor
import com.floatcalc.app.engine.CalcKey
import com.floatcalc.app.engine.CalcState
import kotlinx.serialization.Serializable

@Serializable
data class HistoryEntry(
    val expression: String,
    val result: String,
    val timestamp: Long,
)

@Serializable
data class CalcTab(
    val id: Long,
    /** Number used for the default name ("Calc 1"); the name itself comes from strings.xml. */
    val number: Int,
    val customName: String? = null,
    val calc: CalcState = CalcState(),
    /** Newest first, at most [TabsState.MAX_HISTORY] entries. */
    val history: List<HistoryEntry> = emptyList(),
) {
    val isPristine: Boolean get() = customName == null && calc == CalcState() && history.isEmpty()
}

@Serializable
data class TabsState(
    val tabs: List<CalcTab> = listOf(CalcTab(id = 1, number = 1)),
    val selectedId: Long = 1,
    val nextId: Long = 2,
) {
    val selected: CalcTab get() = tabs.firstOrNull { it.id == selectedId } ?: tabs.first()
    val selectedIndex: Int get() = tabs.indexOf(selected)
    val canAddTab: Boolean get() = tabs.size < MAX_TABS

    companion object {
        const val MAX_TABS = 20
        const val MAX_HISTORY = 100
        const val MAX_NAME_LENGTH = 30
    }
}

/** What Undo needs to put a closed tab back. */
data class ClosedTab(
    val tab: CalcTab,
    val index: Int,
    val wasSelected: Boolean,
    /** Id of the empty tab created because the last tab was closed. */
    val replacementId: Long?,
)

/** Pure tab operations shared by the main app and the floating window (via the repository). */
object TabOps {

    fun add(state: TabsState): TabsState {
        if (!state.canAddTab) return state
        val tab = CalcTab(id = state.nextId, number = lowestFreeNumber(state.tabs))
        return state.copy(tabs = state.tabs + tab, selectedId = tab.id, nextId = state.nextId + 1)
    }

    fun select(state: TabsState, id: Long): TabsState =
        if (state.tabs.any { it.id == id }) state.copy(selectedId = id) else state

    /** Selects the tab [delta] positions away from the current one (no wrap-around). */
    fun selectRelative(state: TabsState, delta: Int): TabsState {
        val index = (state.selectedIndex + delta).coerceIn(0, state.tabs.lastIndex)
        return state.copy(selectedId = state.tabs[index].id)
    }

    /** Closes [id]. Closing the last tab leaves one fresh empty tab. */
    fun close(state: TabsState, id: Long): Pair<TabsState, ClosedTab?> {
        val index = state.tabs.indexOfFirst { it.id == id }
        if (index < 0) return state to null
        val tab = state.tabs[index]
        val wasSelected = state.selectedId == id
        val remaining = state.tabs - tab
        if (remaining.isEmpty()) {
            val fresh = CalcTab(id = state.nextId, number = 1)
            val newState = TabsState(tabs = listOf(fresh), selectedId = fresh.id, nextId = state.nextId + 1)
            return newState to ClosedTab(tab, index, wasSelected, replacementId = fresh.id)
        }
        val selectedId = if (wasSelected) remaining[index.coerceAtMost(remaining.lastIndex)].id else state.selectedId
        return state.copy(tabs = remaining, selectedId = selectedId) to ClosedTab(tab, index, wasSelected, null)
    }

    /** Undo for [close]. Drops the replacement tab if it is still untouched. */
    fun restore(state: TabsState, closed: ClosedTab): TabsState {
        if (state.tabs.any { it.id == closed.tab.id }) return state
        val tabs = state.tabs.filterNot { it.id == closed.replacementId && it.isPristine }.toMutableList()
        if (tabs.size >= TabsState.MAX_TABS) return state
        val tab = if (tabs.any { it.number == closed.tab.number }) {
            closed.tab.copy(number = lowestFreeNumber(tabs))
        } else {
            closed.tab
        }
        tabs.add(closed.index.coerceIn(0, tabs.size), tab)
        val selectedId = when {
            closed.wasSelected || tabs.none { it.id == state.selectedId } -> tab.id
            else -> state.selectedId
        }
        return state.copy(tabs = tabs, selectedId = selectedId)
    }

    /** Renames [id]; a blank name restores the default name. */
    fun rename(state: TabsState, id: Long, name: String): TabsState {
        val clean = name.trim().take(TabsState.MAX_NAME_LENGTH).ifEmpty { null }
        return updateTab(state, id) { it.copy(customName = clean) }
    }

    /** Copies [id] (expression, result and history) into a new tab right after it. */
    fun duplicate(state: TabsState, id: Long): TabsState {
        if (!state.canAddTab) return state
        val index = state.tabs.indexOfFirst { it.id == id }
        if (index < 0) return state
        val copy = state.tabs[index].copy(id = state.nextId, number = lowestFreeNumber(state.tabs), customName = null)
        val tabs = state.tabs.toMutableList().apply { add(index + 1, copy) }
        return state.copy(tabs = tabs, selectedId = copy.id, nextId = state.nextId + 1)
    }

    fun updateTab(state: TabsState, id: Long, transform: (CalcTab) -> CalcTab): TabsState =
        state.copy(tabs = state.tabs.map { if (it.id == id) transform(it) else it })

    /** Applies a key to the selected tab and records finished calculations in its history. */
    fun applyKey(state: TabsState, key: CalcKey, now: Long): TabsState =
        updateTab(state, state.selected.id) { tab ->
            val result = CalcEditor.apply(tab.calc, key)
            val history = result.evaluation?.let {
                addToHistory(tab.history, HistoryEntry(it.expression, it.result, now))
            } ?: tab.history
            tab.copy(calc = result.state, history = history)
        }

    /** Inserts pasted text or a reused history result into the selected tab. */
    fun insert(state: TabsState, text: String): TabsState =
        updateTab(state, state.selected.id) { it.copy(calc = CalcEditor.insert(it.calc, text)) }

    /** Moves the caret of the selected tab (null = end of the expression). */
    fun moveCursor(state: TabsState, index: Int?): TabsState =
        updateTab(state, state.selected.id) { it.copy(calc = CalcEditor.moveCursor(it.calc, index)) }

    /** After "=", brings the selected tab's previous expression back for editing. */
    fun editPrevious(state: TabsState): TabsState =
        updateTab(state, state.selected.id) { it.copy(calc = CalcEditor.editPrevious(it.calc)) }

    fun clearHistory(state: TabsState, id: Long): TabsState = updateTab(state, id) { it.copy(history = emptyList()) }

    fun addToHistory(history: List<HistoryEntry>, entry: HistoryEntry): List<HistoryEntry> =
        (listOf(entry) + history).take(TabsState.MAX_HISTORY)

    /** Makes loaded data safe to use (non-empty, valid selection, unique ids). */
    fun sanitize(state: TabsState): TabsState {
        val tabs = state.tabs.distinctBy { it.id }.take(TabsState.MAX_TABS)
            .map { it.copy(history = it.history.take(TabsState.MAX_HISTORY)) }
        if (tabs.isEmpty()) return TabsState()
        val nextId = maxOf(state.nextId, tabs.maxOf { it.id } + 1)
        val selectedId = if (tabs.any { it.id == state.selectedId }) state.selectedId else tabs.first().id
        return TabsState(tabs, selectedId, nextId)
    }

    private fun lowestFreeNumber(tabs: List<CalcTab>): Int {
        val used = tabs.mapTo(HashSet()) { it.number }
        return generateSequence(1) { it + 1 }.first { it !in used }
    }
}
