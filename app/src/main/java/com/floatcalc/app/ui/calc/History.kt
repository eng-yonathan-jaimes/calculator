package com.floatcalc.app.ui.calc

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.floatcalc.app.R
import com.floatcalc.app.data.HistoryEntry
import com.floatcalc.app.engine.DisplayFormatter

/** History of one tab, newest first. Tap reuses a result, long-press copies it. */
@Composable
fun HistoryList(
    entries: List<HistoryEntry>,
    formatter: DisplayFormatter,
    onReuse: (HistoryEntry) -> Unit,
    onCopy: (HistoryEntry) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val typography = MaterialTheme.typography
    Column(modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = if (compact) 12.dp else 24.dp, end = if (compact) 4.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.history),
                style = if (compact) typography.titleSmall else typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onClear, enabled = entries.isNotEmpty()) {
                Text(stringResource(R.string.history_clear))
            }
        }
        if (entries.isEmpty()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = if (compact) 16.dp else 32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.history_empty),
                    style = typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            val reuseLabel = stringResource(R.string.action_reuse_result)
            val copyLabel = stringResource(R.string.action_copy_result)
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                LazyColumn(contentPadding = PaddingValues(bottom = 8.dp)) {
                    itemsIndexed(entries) { index, entry ->
                        if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClickLabel = reuseLabel,
                                    onLongClickLabel = copyLabel,
                                    onLongClick = { onCopy(entry) },
                                    onClick = { onReuse(entry) },
                                )
                                .padding(
                                    horizontal = if (compact) 12.dp else 24.dp,
                                    vertical = if (compact) 6.dp else 12.dp,
                                ),
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = formatter.formatExpression(entry.expression),
                                style = if (compact) typography.bodySmall else typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "= " + formatter.formatNumber(entry.result),
                                style = if (compact) typography.titleMedium else typography.headlineSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistorySheet(
    entries: List<HistoryEntry>,
    formatter: DisplayFormatter,
    onReuse: (HistoryEntry) -> Unit,
    onCopy: (HistoryEntry) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        HistoryList(
            entries = entries,
            formatter = formatter,
            onReuse = onReuse,
            onCopy = onCopy,
            onClear = onClear,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
