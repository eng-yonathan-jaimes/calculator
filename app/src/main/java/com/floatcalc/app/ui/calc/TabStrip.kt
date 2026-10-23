package com.floatcalc.app.ui.calc

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.floatcalc.app.R
import com.floatcalc.app.data.CalcTab
import com.floatcalc.app.data.TabsState

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
    compact: Boolean = false,
    anchored: @Composable (CalcTab) -> Unit = {},
) {
    val listState = rememberLazyListState()
    val selectedIndex = tabs.indexOfFirst { it.id == selectedId }
    LaunchedEffect(selectedIndex, tabs.size) {
        if (selectedIndex < 0) return@LaunchedEffect
        val info = listState.layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.index == selectedIndex }
        val fullyVisible = item != null && item.offset >= info.viewportStartOffset &&
            item.offset + item.size <= info.viewportEndOffset
        if (!fullyVisible) listState.animateScrollToItem(selectedIndex)
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        LazyRow(
            state = listState,
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 8.dp),
            contentPadding = PaddingValues(horizontal = if (compact) 4.dp else 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            itemsIndexed(tabs, key = { _, tab -> tab.id }) { _, tab ->
                Box {
                    TabChip(
                        name = tabName(tab),
                        selected = tab.id == selectedId,
                        compact = compact,
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
        IconButton(onClick = onAdd, modifier = if (compact) Modifier.size(40.dp) else Modifier) {
            Icon(painterResource(R.drawable.ic_add), stringResource(R.string.action_add_tab))
        }
    }
}

@Composable
private fun TabChip(
    name: String,
    selected: Boolean,
    compact: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onClose: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(if (compact) 8.dp else 12.dp)
    val renameLabel = stringResource(R.string.action_rename_tab)
    val duplicateLabel = stringResource(R.string.action_duplicate_tab)
    val closeLabel = stringResource(R.string.action_close_tab)
    Surface(
        shape = shape,
        color = if (selected) colors.secondaryContainer else Color.Transparent,
        contentColor = if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant,
        border = if (selected) null else BorderStroke(1.dp, colors.outlineVariant),
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clip(shape)
            .semantics {
                this.selected = selected
                customActions = listOf(
                    CustomAccessibilityAction(renameLabel) { onRename(); true },
                    CustomAccessibilityAction(duplicateLabel) { onDuplicate(); true },
                    CustomAccessibilityAction(closeLabel) { onClose(); true },
                )
            }
            .combinedClickable(
                role = Role.Tab,
                onLongClickLabel = stringResource(R.string.tab_options),
                onLongClick = onLongPress,
                onClick = onClick,
            ),
    ) {
        Text(
            text = name,
            style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .widthIn(max = if (compact) 120.dp else 180.dp)
                .padding(
                    horizontal = if (compact) 10.dp else 14.dp,
                    vertical = if (compact) 6.dp else 8.dp,
                ),
        )
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
