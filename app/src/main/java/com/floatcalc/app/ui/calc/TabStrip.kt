package com.floatcalc.app.ui.calc

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.floatcalc.app.R
import com.floatcalc.app.data.CalcTab
import com.floatcalc.app.data.TabsState
import com.floatcalc.app.engine.DisplayFormatter
import com.floatcalc.app.engine.TabSummary
import kotlinx.coroutines.delay

/** How long a tab must be held to show its delete button. */
private const val DELETE_HOLD_MILLIS = 1_000L

/** How long the delete button stays up when it is not used. */
private const val DELETE_BUTTON_MILLIS = 4_000L

/** Tab chip widths: what "Calc 1" plus the old close button took, in the app and the floating window. */
private val CHIP_WIDTH = 90.dp
private val DENSE_CHIP_WIDTH = 60.dp

/**
 * Scrollable tab chips plus a "+" button. Long-press on a chip calls [onLongPress]; [anchored]
 * is placed inside each chip's box so callers can anchor a menu to it.
 */
@Composable
fun TabStrip(
    tabs: List<CalcTab>,
    selectedId: Long,
    onSelect: (CalcTab) -> Unit,
    onAdd: () -> Unit,
    onLongPress: (CalcTab) -> Unit,
    onRename: (CalcTab) -> Unit,
    onDuplicate: (CalcTab) -> Unit,
    onClose: (CalcTab) -> Unit,
    modifier: Modifier = Modifier,
    anchored: @Composable (CalcTab) -> Unit = {},
) {
    val listState = rememberLazyListState()
    val formatter = rememberDisplayFormatter()
    KeepSelectedVisible(listState, tabs.indexOfFirst { it.id == selectedId }, tabs.size)
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        LazyRow(
            state = listState,
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            itemsIndexed(tabs, key = { _, tab -> tab.id }) { _, tab ->
                val name = tabName(tab)
                Box {
                    TabChip(
                        name = name,
                        label = tabLabel(tab, name, formatter),
                        selected = tab.id == selectedId,
                        dense = false,
                        onClick = { onSelect(tab) },
                        onLongPress = { onLongPress(tab) },
                        onRename = { onRename(tab) },
                        onDuplicate = { onDuplicate(tab) },
                        onClose = { onClose(tab) },
                    )
                    anchored(tab)
                }
            }
        }
        IconButton(onClick = onAdd) {
            Icon(painterResource(R.drawable.ic_add), stringResource(R.string.action_add_tab))
        }
    }
}

/**
 * Small tab chips stacked vertically, for the floating window (the "+" lives in its header).
 * Holding a chip for a second shows a delete button under it.
 */
@Composable
fun TabColumn(
    tabs: List<CalcTab>,
    selectedId: Long,
    onSelect: (CalcTab) -> Unit,
    onLongPress: (CalcTab) -> Unit,
    onRename: (CalcTab) -> Unit,
    onDuplicate: (CalcTab) -> Unit,
    onClose: (CalcTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val formatter = rememberDisplayFormatter()
    var deleteShownId by rememberDeleteShownId()
    KeepSelectedVisible(listState, tabs.indexOfFirst { it.id == selectedId }, tabs.size)
    LazyColumn(
        state = listState,
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        contentPadding = PaddingValues(vertical = 4.dp),
    ) {
        items(tabs, key = { it.id }) { tab ->
            val name = tabName(tab)
            // Intrinsic width lets the delete button match the chip above it.
            Column(Modifier.width(IntrinsicSize.Max)) {
                TabChip(
                    name = name,
                    label = tabLabel(tab, name, formatter),
                    selected = tab.id == selectedId,
                    dense = true,
                    onClick = {
                        deleteShownId = null
                        onSelect(tab)
                    },
                    onLongPress = {
                        deleteShownId = null
                        onLongPress(tab)
                    },
                    onHoldDelete = { deleteShownId = tab.id },
                    onRename = { onRename(tab) },
                    onDuplicate = { onDuplicate(tab) },
                    onClose = { onClose(tab) },
                )
                AnimatedVisibility(deleteShownId == tab.id) {
                    DeleteTabButton(
                        name = name,
                        dense = true,
                        onClick = {
                            deleteShownId = null
                            onClose(tab)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

/** Scrolls a tab list just enough to show the selected tab after it changes. */
@Composable
private fun KeepSelectedVisible(listState: LazyListState, selectedIndex: Int, count: Int) {
    LaunchedEffect(selectedIndex, count) {
        if (selectedIndex < 0) return@LaunchedEffect
        val info = listState.layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.index == selectedIndex }
        val fullyVisible = item != null && item.offset >= info.viewportStartOffset &&
            item.offset + item.size <= info.viewportEndOffset
        if (!fullyVisible) listState.animateScrollToItem(selectedIndex)
    }
}

/** Which tab's delete button is up (at most one); it goes away by itself after a few seconds. */
@Composable
private fun rememberDeleteShownId() = remember { mutableStateOf<Long?>(null) }.also { state ->
    LaunchedEffect(state.value) {
        if (state.value != null) {
            delay(DELETE_BUTTON_MILLIS)
            state.value = null
        }
    }
}

/**
 * The tab's name while its calculator is empty, otherwise "=" and the current result ("=95"), or
 * the operation being entered ("12+").
 */
@Composable
private fun tabLabel(tab: CalcTab, name: String, formatter: DisplayFormatter): String =
    when (val summary = remember(tab.calc) { TabSummary.of(tab.calc) }) {
        null -> name
        is TabSummary.Result -> "=" + formatter.formatNumber(summary.number)
        is TabSummary.Pending -> formatter.formatNumber(summary.number) + summary.operators
    }

/**
 * Press-and-hold on a chip. Without [onDelete] it calls [onOptions] once the long-press timeout
 * passes. With it, releasing after that timeout calls [onOptions] and holding for
 * [DELETE_HOLD_MILLIS] calls [onDelete] instead. Either way the release is consumed so it does not
 * also select the tab. Moving the finger (e.g. scrolling) cancels it.
 */
private suspend fun PointerInputScope.detectHold(onStage: () -> Unit, onOptions: () -> Unit, onDelete: (() -> Unit)?) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val deadlines = listOfNotNull(
            viewConfiguration.longPressTimeoutMillis,
            DELETE_HOLD_MILLIS.takeIf { onDelete != null },
        )
        var stage = 0
        while (true) {
            val deadline = deadlines.getOrNull(stage)
            val event = if (deadline == null) {
                awaitPointerEvent(PointerEventPass.Initial)
            } else {
                val remaining = deadline - (SystemClock.uptimeMillis() - down.uptimeMillis)
                withTimeoutOrNull(remaining) { awaitPointerEvent(PointerEventPass.Initial) }
            }
            if (event == null) {
                stage++
                onStage()
                when {
                    onDelete == null -> onOptions()
                    stage == deadlines.size -> onDelete()
                }
                continue
            }
            val change = event.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
            if (change.changedToUpIgnoreConsumed()) {
                if (stage > 0) change.consume()
                if (stage == 1 && onDelete != null) onOptions()
                return@awaitEachGesture
            }
            if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) return@awaitEachGesture
        }
    }
}

/** Red trash button that deletes a tab (Undo is still offered), shown after holding the chip. */
@Composable
private fun DeleteTabButton(name: String, dense: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.action_delete_named_tab, name)
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(if (dense) 8.dp else 12.dp),
        color = MaterialTheme.colorScheme.error,
        contentColor = MaterialTheme.colorScheme.onError,
        modifier = modifier.semantics { contentDescription = description },
    ) {
        Box(
            modifier = Modifier.padding(horizontal = if (dense) 8.dp else 12.dp, vertical = if (dense) 4.dp else 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_delete),
                contentDescription = null,
                modifier = Modifier.size(if (dense) 16.dp else 20.dp),
            )
        }
    }
}

/** [label] is what the chip shows; [name] is still what screen readers use. */
@Composable
private fun TabChip(
    name: String,
    label: String,
    selected: Boolean,
    dense: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    /** Set to show a delete button after a one-second hold (the floating window, which has no menu). */
    onHoldDelete: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(if (dense) 8.dp else 12.dp)
    val renameLabel = stringResource(R.string.action_rename_tab)
    val duplicateLabel = stringResource(R.string.action_duplicate_tab)
    val closeLabel = stringResource(R.string.action_close_tab)
    val optionsLabel = stringResource(R.string.tab_options)
    val haptics = LocalHapticFeedback.current
    val latestLongPress by rememberUpdatedState(onLongPress)
    val latestHoldDelete by rememberUpdatedState(onHoldDelete)
    Surface(
        shape = shape,
        color = if (selected) colors.secondaryContainer else Color.Transparent,
        contentColor = if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant,
        border = if (selected) null else BorderStroke(1.dp, colors.outlineVariant),
        modifier = modifier
            .minimumInteractiveComponentSize()
            // Fixed, so the strip doesn't jump around while the label follows the calculation.
            .width(if (dense) DENSE_CHIP_WIDTH else CHIP_WIDTH)
            .clip(shape)
            .semantics {
                this.selected = selected
                contentDescription = name
                if (label != name) stateDescription = label
                onLongClick(optionsLabel) { onLongPress(); true }
                customActions = listOf(
                    CustomAccessibilityAction(renameLabel) { onRename(); true },
                    CustomAccessibilityAction(duplicateLabel) { onDuplicate(); true },
                    CustomAccessibilityAction(closeLabel) { onClose(); true },
                )
            }
            // Before the click handler, so a long hold can consume the release.
            .pointerInput(haptics, onHoldDelete != null) {
                detectHold(
                    onStage = { haptics.performHapticFeedback(HapticFeedbackType.LongPress) },
                    onOptions = { latestLongPress() },
                    onDelete = if (onHoldDelete != null) ({ latestHoldDelete?.invoke() }) else null,
                )
            }
            .clickable(role = Role.Tab, onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = if (dense) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(
                    horizontal = if (dense) 4.dp else 8.dp,
                    vertical = if (dense) 4.dp else 8.dp,
                ),
            )
        }
    }
}

/** Text field state for renaming, with the current name pre-selected. */
@Composable
fun rememberRenameFieldState(initial: String) = remember(initial) {
    mutableStateOf(TextFieldValue(initial, TextRange(0, initial.length)))
}

@Composable
fun RenameTabDialog(
    initialName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by rememberRenameFieldState(initialName)
    val focusRequester = remember { FocusRequester() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.action_rename_tab)) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it.copy(text = it.text.take(TabsState.MAX_NAME_LENGTH)) },
                singleLine = true,
                label = { Text(stringResource(R.string.tab_name)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onConfirm(value.text) }),
                modifier = Modifier.focusRequester(focusRequester),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value.text) }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}
