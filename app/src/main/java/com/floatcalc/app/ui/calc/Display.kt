package com.floatcalc.app.ui.calc

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
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
    val preview: TextUnit,
    val previous: TextUnit,
    val previewLineHeight: Dp,
) {
    companion object {
        val Regular = DisplayStyle(64.sp, 28.sp, 30.sp, 18.sp, 44.dp)
        val Compact = DisplayStyle(36.sp, 18.sp, 18.sp, 12.sp, 26.dp)
    }
}

private val NumberTextStyle = TextStyle(textDirection = TextDirection.Ltr)

/**
 * Expression (auto-shrinking) with the live result underneath. Tapping the result copies it,
 * long-pressing the expression copies the expression.
 */
@Composable
fun CalculatorDisplay(
    calc: CalcState,
    formatter: DisplayFormatter,
    onCopyResult: (number: String) -> Unit,
    onCopyExpression: (text: String) -> Unit,
    modifier: Modifier = Modifier,
    style: DisplayStyle = DisplayStyle.Regular,
) {
    val preview = remember(calc) { CalcEditor.preview(calc) }
    val colors = MaterialTheme.colorScheme
    val copyResultLabel = stringResource(R.string.action_copy_result)
    val copyExpressionLabel = stringResource(R.string.action_copy_expression)

    // Numbers and operators always read left to right, also in RTL locales.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(modifier, horizontalAlignment = Alignment.End) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.Bottom,
                horizontalAlignment = Alignment.End,
            ) {
                if (calc.evaluated && calc.previousExpression.isNotEmpty()) {
                    Text(
                        text = formatter.formatExpression(calc.previousExpression),
                        style = NumberTextStyle,
                        fontSize = style.previous,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.StartEllipsis,
                    )
                }
                val expressionText = formatter.formatExpression(calc.expression)
                AutoShrinkText(
                    text = expressionText.ifEmpty { "0" },
                    style = NumberTextStyle.copy(fontWeight = FontWeight.Normal),
                    maxFontSize = style.expressionMax,
                    minFontSize = style.expressionMin,
                    color = if (expressionText.isEmpty()) colors.onSurfaceVariant else colors.onSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            interactionSource = null,
                            indication = null,
                            onClickLabel = if (calc.evaluated) copyResultLabel else null,
                            onLongClickLabel = copyExpressionLabel,
                            onLongClick = { if (expressionText.isNotEmpty()) onCopyExpression(expressionText) },
                            onClick = { if (calc.evaluated) onCopyResult(calc.expression) },
                        ),
                )
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
                        style = NumberTextStyle,
                        maxFontSize = style.preview,
                        minFontSize = style.preview * 0.5f,
                        color = colors.onSurfaceVariant,
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
