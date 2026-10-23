package com.floatcalc.app.data

import com.floatcalc.app.engine.CalcKey
import com.floatcalc.app.engine.CalcState
import com.floatcalc.app.engine.Operator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class TabOpsTest {

    private fun withTabs(count: Int): TabsState = (1 until count).fold(TabsState()) { s, _ -> TabOps.add(s) }

    private fun typed(state: TabsState, vararg keys: CalcKey): TabsState =
        keys.fold(state) { s, k -> TabOps.applyKey(s, k, now = 1L) }

    @Test
    fun startsWithOneTab() {
        val state = TabsState()
        assertEquals(1, state.tabs.size)
        assertEquals(1, state.selected.number)
        assertTrue(state.selected.isPristine)
    }

    @Test
    fun addSelectsTheNewTabWithTheNextNumber() {
        val state = TabOps.add(TabsState())
        assertEquals(2, state.tabs.size)
        assertEquals(state.tabs[1].id, state.selectedId)
        assertEquals(2, state.selected.number)
    }

    @Test
    fun atMostTwentyTabs() {
        val full = withTabs(TabsState.MAX_TABS)
        assertEquals(20, full.tabs.size)
        assertFalse(full.canAddTab)
        assertSame(full, TabOps.add(full))
        assertSame(full, TabOps.duplicate(full, full.selectedId))
    }

    @Test
    fun defaultNumbersReuseTheLowestFreeNumber() {
        val three = withTabs(3)
        val (closed, _) = TabOps.close(three, three.tabs[1].id)
        assertEquals(listOf(1, 3), closed.tabs.map { it.number })
        assertEquals(2, TabOps.add(closed).selected.number)
    }

    @Test
    fun closeSelectsTheNeighbour() {
        val three = TabOps.select(withTabs(3), withTabs(3).tabs[1].id)
        val (afterMiddle, info) = TabOps.close(three, three.tabs[1].id)
        assertEquals(three.tabs[2].id, afterMiddle.selectedId)
        assertNotNull(info)
        val (afterLast, _) = TabOps.close(withTabs(3), withTabs(3).tabs[2].id)
        assertEquals(withTabs(3).tabs[1].id, afterLast.selectedId)
    }

    @Test
    fun closingAnUnselectedTabKeepsTheSelection() {
        val three = withTabs(3) // third tab selected
        val (after, _) = TabOps.close(three, three.tabs[0].id)
        assertEquals(three.selectedId, after.selectedId)
    }

    @Test
    fun closingTheLastTabLeavesOneEmptyTab() {
        val used = typed(TabsState(), CalcKey.Digit(4))
        val (after, closed) = TabOps.close(used, used.selectedId)
        assertEquals(1, after.tabs.size)
        assertTrue(after.selected.isPristine)
        assertEquals(1, after.selected.number)
        assertNotEquals(used.selectedId, after.selectedId)
        assertEquals(after.selectedId, closed!!.replacementId)
    }

    @Test
    fun undoRestoresTheClosedTabInPlace() {
        val three = TabOps.select(withTabs(3), withTabs(3).tabs[1].id)
        val middle = three.tabs[1]
        val (after, closed) = TabOps.close(three, middle.id)
        val restored = TabOps.restore(after, closed!!)
        assertEquals(three.tabs.map { it.id }, restored.tabs.map { it.id })
        assertEquals(middle.id, restored.selectedId)
    }

    @Test
    fun undoAfterClosingTheLastTabDropsTheUntouchedReplacement() {
        val used = typed(TabsState(), CalcKey.Digit(4))
        val (after, closed) = TabOps.close(used, used.selectedId)
        val restored = TabOps.restore(after, closed!!)
        assertEquals(listOf(used.selectedId), restored.tabs.map { it.id })
        assertEquals("4", restored.selected.calc.expression)
    }

    @Test
    fun undoKeepsAReplacementThatWasUsed() {
        val used = typed(TabsState(), CalcKey.Digit(4))
        val (after, closed) = TabOps.close(used, used.selectedId)
        val edited = typed(after, CalcKey.Digit(7))
        val restored = TabOps.restore(edited, closed!!)
        assertEquals(2, restored.tabs.size)
        assertEquals(setOf(1, 2), restored.tabs.map { it.number }.toSet())
    }

    @Test
    fun renameTrimsAndBlankRestoresTheDefault() {
        val state = TabsState()
        val id = state.selectedId
        val renamed = TabOps.rename(state, id, "  Groceries  ")
        assertEquals("Groceries", renamed.selected.customName)
        assertNull(TabOps.rename(renamed, id, "   ").selected.customName)
        assertEquals(TabsState.MAX_NAME_LENGTH, TabOps.rename(state, id, "x".repeat(100)).selected.customName!!.length)
    }

    @Test
    fun duplicateCopiesStateAndHistoryRightAfterTheSource() {
        val base = TabOps.select(withTabs(2), withTabs(2).tabs[0].id)
        val typedState = typed(base, CalcKey.Digit(2), CalcKey.Op(Operator.PLUS), CalcKey.Digit(3), CalcKey.Equals)
        val source = typedState.selected
        val dup = TabOps.duplicate(typedState, source.id)
        assertEquals(3, dup.tabs.size)
        assertEquals(dup.tabs[1].id, dup.selectedId)
        assertNotEquals(source.id, dup.selectedId)
        assertEquals(source.calc, dup.selected.calc)
        assertEquals(source.history, dup.selected.history)
        assertEquals(3, dup.selected.number)
    }

    @Test
    fun tabsAreIndependent() {
        val two = withTabs(2)
        val typedSecond = typed(two, CalcKey.Digit(9))
        assertEquals(CalcState(), typedSecond.tabs[0].calc)
        assertEquals("9", typedSecond.tabs[1].calc.expression)
    }

    @Test
    fun equalsAddsHistoryCappedAtOneHundred() {
        var state = TabsState()
        repeat(105) {
            state = typed(state, CalcKey.Digit(1), CalcKey.Op(Operator.PLUS), CalcKey.Digit(1), CalcKey.Equals)
        }
        val history = state.selected.history
        assertEquals(TabsState.MAX_HISTORY, history.size)
        assertEquals(HistoryEntry("1+1", "2", 1L), history.first())
        assertTrue(TabOps.clearHistory(state, state.selectedId).selected.history.isEmpty())
    }

    @Test
    fun insertGoesIntoTheSelectedTab() {
        val state = TabOps.insert(withTabs(2), "12")
        assertEquals("12", state.selected.calc.expression)
        assertEquals("", state.tabs[0].calc.expression)
    }

    @Test
    fun selectRelativeStopsAtTheEnds() {
        val three = withTabs(3)
        assertEquals(three.tabs[1].id, TabOps.selectRelative(three, -1).selectedId)
        assertEquals(three.tabs[2].id, TabOps.selectRelative(three, 1).selectedId)
        assertEquals(three.tabs[0].id, TabOps.selectRelative(three, -5).selectedId)
    }

    @Test
    fun sanitizeRepairsLoadedData() {
        assertEquals(TabsState(), TabOps.sanitize(TabsState(tabs = emptyList(), selectedId = 5, nextId = 1)))
        val tab = CalcTab(id = 7, number = 1)
        val fixed = TabOps.sanitize(TabsState(tabs = listOf(tab, tab), selectedId = 99, nextId = 1))
        assertEquals(1, fixed.tabs.size)
        assertEquals(7, fixed.selectedId)
        assertEquals(8, fixed.nextId)
    }
}
