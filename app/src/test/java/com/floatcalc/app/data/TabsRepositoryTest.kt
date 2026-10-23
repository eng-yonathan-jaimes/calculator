package com.floatcalc.app.data

import androidx.datastore.core.DataStore
import com.floatcalc.app.engine.CalcKey
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TabsRepositoryTest {

    private class FakeStore<T>(initial: T) : DataStore<T> {
        val writes = mutableListOf<T>()
        private val flow = MutableStateFlow(initial)
        override val data: Flow<T> = flow
        override suspend fun updateData(transform: suspend (t: T) -> T): T {
            val value = transform(flow.value)
            flow.value = value
            writes += value
            return value
        }
    }

    @Test
    fun loadsStoredTabs() = runTest {
        val stored = TabOps.rename(TabOps.add(TabsState()), 2, "Rent")
        val repo = TabsRepository(FakeStore(stored), backgroundScope, clock = { 0L })
        assertFalse(repo.loaded.value)
        runCurrent()
        assertTrue(repo.loaded.value)
        assertEquals(stored, repo.state.value)
    }

    @Test
    fun changesBeforeLoadingAreIgnored() = runTest {
        val repo = TabsRepository(FakeStore(TabsState()), backgroundScope, clock = { 0L })
        repo.press(CalcKey.Digit(5))
        runCurrent()
        assertEquals("", repo.state.value.selected.calc.expression)
    }

    @Test
    fun rapidChangesAreWrittenOnceAfterTheDebounce() = runTest {
        val store = FakeStore(TabsState())
        val repo = TabsRepository(store, backgroundScope, clock = { 0L })
        runCurrent()
        repeat(5) { repo.press(CalcKey.Digit(1)) }
        advanceTimeBy(300)
        assertTrue(store.writes.isEmpty())
        advanceTimeBy(200)
        assertEquals(1, store.writes.size)
        assertEquals("11111", store.writes.last().selected.calc.expression)
    }

    @Test
    fun flushWritesImmediately() = runTest {
        val store = FakeStore(TabsState())
        val repo = TabsRepository(store, backgroundScope, clock = { 0L })
        runCurrent()
        repo.addTab()
        repo.flush()
        runCurrent()
        assertEquals(2, store.writes.last().tabs.size)
    }

    @Test
    fun addAndDuplicateReportTheTabLimit() = runTest {
        val repo = TabsRepository(FakeStore(TabsState()), backgroundScope, clock = { 0L })
        runCurrent()
        repeat(TabsState.MAX_TABS - 1) { assertTrue(repo.addTab()) }
        assertFalse(repo.addTab())
        assertFalse(repo.duplicateTab(repo.state.value.selectedId))
    }

    @Test
    fun closeAndUndoThroughTheRepository() = runTest {
        val repo = TabsRepository(FakeStore(TabsState()), backgroundScope, clock = { 0L })
        runCurrent()
        repo.addTab()
        val second = repo.state.value.selectedId
        val closed = repo.closeTab(second)!!
        assertEquals(1, repo.state.value.tabs.size)
        repo.restoreTab(closed)
        assertEquals(second, repo.state.value.selectedId)
    }
}
