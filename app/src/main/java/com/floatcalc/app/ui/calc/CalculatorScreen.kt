package com.floatcalc.app.ui.calc

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.floatcalc.app.AppContainer
import com.floatcalc.app.R
import com.floatcalc.app.data.CalcTab
import com.floatcalc.app.data.TabsState
import com.floatcalc.app.engine.CalcKey
import com.floatcalc.app.engine.CalcState
import com.floatcalc.app.engine.DisplayFormatter
import com.floatcalc.app.util.Clipboard
import kotlinx.coroutines.launch

@Composable
fun CalculatorScreen(container: AppContainer, onFloat: () -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val tabs = container.tabs
    val tabsState by tabs.state.collectAsStateWithLifecycle()
    val tab = tabsState.selected
    val formatter = rememberDisplayFormatter()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var showHistory by rememberSaveable { mutableStateOf(false) }
    var menuTabId by rememberSaveable { mutableStateOf<Long?>(null) }
    var renameTabId by rememberSaveable { mutableStateOf<Long?>(null) }
    val tooManyTabs = pluralStringResource(R.plurals.max_tabs_reached, TabsState.MAX_TABS, TabsState.MAX_TABS)

    fun showMessage(text: String) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(text)
        }
    }

    fun closeTab(target: CalcTab) {
        val name = resources.tabName(target)
        val closed = tabs.closeTab(target.id) ?: return
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar(
                message = resources.getString(R.string.tab_closed, name),
                actionLabel = resources.getString(R.string.action_undo),
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) tabs.restoreTab(closed)
        }
    }

    fun duplicateTab(target: CalcTab) {
        if (!tabs.duplicateTab(target.id)) showMessage(tooManyTabs)
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TabStrip(
                    tabs = tabsState.tabs,
                    selectedId = tabsState.selectedId,
                    onSelect = { tabs.selectTab(it.id) },
                    onAdd = { if (!tabs.addTab()) showMessage(tooManyTabs) },
                    onLongPress = { menuTabId = it.id },
                    onRename = { renameTabId = it.id },
                    onDuplicate = ::duplicateTab,
                    onClose = ::closeTab,
                    modifier = Modifier.weight(1f),
                    anchored = { t ->
                        DropdownMenu(expanded = menuTabId == t.id, onDismissRequest = { menuTabId = null }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_rename_tab)) },
                                onClick = {
                                    menuTabId = null
                                    renameTabId = t.id
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_duplicate_tab)) },
                                onClick = {
                                    menuTabId = null
                                    duplicateTab(t)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_close_tab)) },
                                onClick = {
                                    menuTabId = null
                                    closeTab(t)
                                },
                            )
                        }
                    },
                )
                IconButton(onClick = { showHistory = true }) {
                    Icon(painterResource(R.drawable.ic_history), stringResource(R.string.action_history))
                }
                IconButton(onClick = onFloat) {
                    Icon(painterResource(R.drawable.ic_float), stringResource(R.string.action_float))
                }
            }
        },
    ) { padding ->
        CalculatorContent(
            tabId = tab.id,
            tabIndex = tabsState.selectedIndex,
            calc = tab.calc,
            formatter = formatter,
            onKey = tabs::press,
            onCopyResult = { Clipboard.copy(context, formatter.toClipboardNumber(it)) },
            onCopyExpression = { Clipboard.copy(context, it) },
            modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding),
            displayModifier = Modifier.pointerInput(tabs) { detectHorizontalSwipe { tabs.selectRelative(it) } },
        )
    }

    if (showHistory) {
        HistorySheet(
            entries = tab.history,
            formatter = formatter,
            onReuse = {
                tabs.insert(it.result)
                showHistory = false
            },
            onCopy = { Clipboard.copy(context, formatter.toClipboardNumber(it.result)) },
            onClear = { tabs.clearHistory(tab.id) },
            onDismiss = { showHistory = false },
        )
    }

    val renaming = renameTabId?.let { id -> tabsState.tabs.firstOrNull { it.id == id } }
    if (renaming != null) {
        RenameTabDialog(
            initialName = tabName(renaming),
            onConfirm = {
                tabs.renameTab(renaming.id, it)
                renameTabId = null
            },
            onDismiss = { renameTabId = null },
        )
    }
}

/** What the display shows for one tab; the key drives the slide animation between tabs. */
private data class DisplayPage(val tabId: Long, val index: Int, val calc: CalcState)

/** The display of the selected tab, sliding left or right when the tab changes. */
@Composable
fun AnimatedTabDisplay(
    tabId: Long,
    tabIndex: Int,
    calc: CalcState,
    formatter: DisplayFormatter,
    onCopyResult: (String) -> Unit,
    onCopyExpression: (String) -> Unit,
    modifier: Modifier = Modifier,
    style: DisplayStyle = DisplayStyle.Regular,
) {
    AnimatedContent(
        targetState = DisplayPage(tabId, tabIndex, calc),
        contentKey = { it.tabId },
        transitionSpec = {
            val direction = if (targetState.index >= initialState.index) 1 else -1
            (slideInHorizontally { it * direction / 4 } + fadeIn()) togetherWith
                (slideOutHorizontally { -it * direction / 4 } + fadeOut())
        },
        modifier = modifier,
    ) { page ->
        CalculatorDisplay(
            calc = page.calc,
            formatter = formatter,
            onCopyResult = onCopyResult,
            onCopyExpression = onCopyExpression,
            modifier = Modifier.fillMaxSize(),
            style = style,
        )
    }
}

/**
 * Display + backspace + keypad. Stacked in portrait and in small windows; side by side when
 * the window is clearly wider than tall (landscape, wide split screen).
 */
@Composable
fun CalculatorContent(
    tabId: Long,
    tabIndex: Int,
    calc: CalcState,
    formatter: DisplayFormatter,
    onKey: (CalcKey) -> Unit,
    onCopyResult: (String) -> Unit,
    onCopyExpression: (String) -> Unit,
    modifier: Modifier = Modifier,
    displayModifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val sideBySide = maxWidth > maxHeight * 1.15f
        val display: @Composable (Modifier) -> Unit = { m ->
            AnimatedTabDisplay(
                tabId = tabId,
                tabIndex = tabIndex,
                calc = calc,
                formatter = formatter,
                onCopyResult = onCopyResult,
                onCopyExpression = onCopyExpression,
                modifier = m.then(displayModifier),
            )
        }
        val backspace: @Composable () -> Unit = {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                BackspaceButton(
                    onBackspace = { onKey(CalcKey.Backspace) },
                    onClear = { onKey(CalcKey.Clear) },
                )
            }
        }
        if (sideBySide) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                ) {
                    display(Modifier.weight(1f).fillMaxWidth())
                    backspace()
                }
                Keypad(
                    onKey = onKey,
                    modifier = Modifier
                        .weight(1.2f)
                        .fillMaxHeight(),
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(max = 640.dp)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                display(Modifier.weight(1f).fillMaxWidth())
                backspace()
                Keypad(
                    onKey = onKey,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1.6f),
                )
            }
        }
    }
}
