package com.floatcalc.app.ui.calc

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.floatcalc.app.AppContainer
import com.floatcalc.app.R
import com.floatcalc.app.engine.CalcKey
import com.floatcalc.app.engine.CalcState
import com.floatcalc.app.engine.DisplayFormatter
import com.floatcalc.app.util.Clipboard

@Composable
fun CalculatorScreen(container: AppContainer) {
    val context = LocalContext.current
    val tabsState by container.tabs.state.collectAsStateWithLifecycle()
    val tab = tabsState.selected
    val formatter = rememberDisplayFormatter()
    var showHistory by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
                horizontalArrangement = Arrangement.End,
            ) {
                IconButton(onClick = { showHistory = true }) {
                    Icon(painterResource(R.drawable.ic_history), stringResource(R.string.action_history))
                }
            }
        },
    ) { padding ->
        CalculatorContent(
            calc = tab.calc,
            formatter = formatter,
            onKey = container.tabs::press,
            onCopyResult = { Clipboard.copy(context, formatter.toClipboardNumber(it)) },
            onCopyExpression = { Clipboard.copy(context, it) },
            modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding),
        )
    }

    if (showHistory) {
        HistorySheet(
            entries = tab.history,
            formatter = formatter,
            onReuse = {
                container.tabs.insert(it.result)
                showHistory = false
            },
            onCopy = { Clipboard.copy(context, formatter.toClipboardNumber(it.result)) },
            onClear = { container.tabs.clearHistory(tab.id) },
            onDismiss = { showHistory = false },
        )
    }
}

/**
 * Display + backspace + keypad. Stacked in portrait and in small windows; side by side when
 * the window is clearly wider than tall (landscape, wide split screen).
 */
@Composable
fun CalculatorContent(
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
            CalculatorDisplay(
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
