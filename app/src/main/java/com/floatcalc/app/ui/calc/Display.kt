package com.floatcalc.app.ui.calc

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.floatcalc.app.R
import com.floatcalc.app.engine.CalcEditor
import com.floatcalc.app.engine.CalcState
import com.floatcalc.app.engine.DisplayFormatter
import com.floatcalc.app.engine.Preview
import kotlin.math.abs

/** Text sizes for the main app and the scaled-down floating window. */
data class DisplayStyle(
    val expressionMax: TextUnit,
    val expressionMin: TextUnit,
    /** Below this size a long expression wraps onto more lines instead of shrinking further. */
    val expressionWrap: TextUnit,
    val preview: TextUnit,
    val previous: TextUnit,
    val previewLineHeight: Dp,
    /** The floating window shows the live result bold and at full contrast. */
    val previewEmphasized: Boolean = false,
) {
    companion object {
        val Regular = DisplayStyle(64.sp, 28.sp, 40.sp, 30.sp, 18.sp, 44.dp)
        val Compact = DisplayStyle(32.sp, 14.sp, 18.sp, 20.sp, 11.sp, 28.dp, previewEmphasized = true)
    }
}

private val NumberTextStyle = TextStyle(textDirection = TextDirection.Ltr)

/**
 * Expression with the live result right below it, both at the top of the display; a long
 * expression narrows, then wraps downward. Tapping the expression places the caret
 * ([onMoveCursor], an index into the internal expression); tapping a result copies it;
 * long-pressing the expression copies the expression, or, when [onPaste] is set, opens a
 * Copy / Paste menu. After "=", tapping the small previous expression brings it back for editing
 * ([onEditPrevious]).
 */
@Composable
fun CalculatorDisplay(
    calc: CalcState,
    formatter: DisplayFormatter,
    onCopyResult: (number: String) -> Unit,
    onCopyExpression: (text: String) -> Unit,
    modifier: Modifier = Modifier,
    style: DisplayStyle = DisplayStyle.Regular,
    onMoveCursor: (Int?) -> Unit = {},
    onEditPrevious: () -> Unit = {},
    /** Null in the floating window: popup menus need focus, so it pastes from its header instead. */
    onPaste: (() -> Unit)? = null,
) {
    val preview = remember(calc) { CalcEditor.preview(calc) }
    val formatted = remember(calc.expression, formatter) { formatter.formatExpressionMapped(calc.expression) }
    val colors = MaterialTheme.colorScheme
    val copyResultLabel = stringResource(R.string.action_copy_result)
    val copyExpressionLabel = stringResource(R.string.action_copy_expression)
    val editLabel = stringResource(R.string.action_edit_calculation)
    val pasteLabel = stringResource(R.string.action_paste)

    // Numbers and operators always read left to right, also in RTL locales.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(modifier, horizontalAlignment = Alignment.End) {
            if (calc.evaluated && calc.previousExpression.isNotEmpty()) {
                Text(
                    text = formatter.formatExpression(calc.previousExpression),
                    style = NumberTextStyle,
                    fontSize = style.previous,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.StartEllipsis,
                    modifier = Modifier.combinedClickable(
                        interactionSource = null,
                        indication = null,
                        onClickLabel = editLabel,
                        onClick = onEditPrevious,
                    ),
                )
            }
            val expressionText = formatted.text
            // While editing, the caret is always shown: where it was placed, or at the end.
            val caret = if (!calc.evaluated && expressionText.isNotEmpty()) {
                calc.cursor ?: calc.expression.length
            } else {
                null
            }
            var menuOpen by remember { mutableStateOf(false) }
            // Takes only the height it needs, up to what's left above the result line.
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
            ) {
                AutoShrinkText(
                    text = expressionText.ifEmpty { "0" },
                    style = NumberTextStyle.copy(fontWeight = FontWeight.Normal),
                    maxFontSize = style.expressionMax,
                    minFontSize = style.expressionMin,
                    wrapFontSize = style.expressionWrap,
                    color = if (expressionText.isEmpty()) colors.onSurfaceVariant else colors.onSurface,
                    caretOffset = caret?.let { formatted.displayOffset(it) },
                    caretColor = colors.primary,
                    onTapOffset = { offset ->
                        when {
                            calc.evaluated -> onCopyResult(calc.expression)
                            expressionText.isNotEmpty() -> onMoveCursor(formatted.internalOffset(offset))
                        }
                    },
                    onLongPress = {
                        when {
                            onPaste != null -> menuOpen = true
                            expressionText.isNotEmpty() -> onCopyExpression(expressionText)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics {
                            if (calc.evaluated) {
                                onClick(label = copyResultLabel) {
                                    onCopyResult(calc.expression)
                                    true
                                }
                            }
                            if (expressionText.isNotEmpty()) {
                                onLongClick(label = copyExpressionLabel) {
                                    onCopyExpression(expressionText)
                                    true
                                }
                            }
                            if (onPaste != null) {
                                customActions = listOf(CustomAccessibilityAction(pasteLabel) { onPaste(); true })
                            }
                        },
                )
                // Anchored at the end, under the numbers, since they are right-aligned.
                if (onPaste != null) {
                    Box(Modifier.align(Alignment.BottomEnd)) {
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            if (expressionText.isNotEmpty()) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_copy)) },
                                    onClick = {
                                        menuOpen = false
                                        onCopyExpression(expressionText)
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(pasteLabel) },
                                onClick = {
                                    menuOpen = false
                                    onPaste()
                                },
                            )
                        }
                    }
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(style.previewLineHeight),
                contentAlignment = Alignment.CenterEnd,
            ) {
                when (preview) {
                    is Preview.Value -> AutoShrinkText(
                        text = formatter.formatNumber(preview.number),
                        style = if (style.previewEmphasized) {
                            NumberTextStyle.copy(fontWeight = FontWeight.Bold)
                        } else {
                            NumberTextStyle
                        },
                        maxFontSize = style.preview,
                        minFontSize = style.preview * 0.5f,
                        color = if (style.previewEmphasized) colors.onSurface else colors.onSurfaceVariant,
                        modifier = Modifier.combinedClickable(
                            interactionSource = null,
                            indication = null,
                            onClickLabel = copyResultLabel,
                            onClick = { onCopyResult(preview.number) },
                        ),
                    )
                    is Preview.Error -> Text(
                        text = stringResource(preview.error.messageRes()),
                        fontSize = style.preview * 0.7f,
                        color = colors.error,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Preview.None -> Unit
                }
            }
        }
    }
}

/**
 * Detects a horizontal swipe and reports +1 (swipe left: next tab) or −1 (swipe right: previous).
 */
suspend fun PointerInputScope.detectHorizontalSwipe(onSwipe: (Int) -> Unit) {
    val threshold = 56.dp.toPx()
    var total = 0f
    detectHorizontalDragGestures(
        onDragStart = { total = 0f },
        onDragCancel = { total = 0f },
        onDragEnd = { if (abs(total) > threshold) onSwipe(if (total < 0) 1 else -1) },
    ) { change, dragAmount ->
        total += dragAmount
        change.consume()
    }
}
