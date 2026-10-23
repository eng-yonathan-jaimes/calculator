package com.floatcalc.app.ui.calc

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.floatcalc.app.R
import com.floatcalc.app.engine.CalcKey
import com.floatcalc.app.engine.Operator

enum class KeypadLayout {
    /** Main app, 5 rows × 4: AC ( ) % ÷ | 7 8 9 × | 4 5 6 − | 1 2 3 + | ± 0 . = */
    Standard,

    /** Floating window, 4 rows × 5: 7 8 9 % ( ) | 4 5 6 − ÷ | 1 2 3 + × | ⌫ 0 . = AC */
    Compact,
}

private enum class KeyRole { Number, Operator, Clear, Equals }

private data class KeyDef(
    val label: String,
    val key: CalcKey,
    val role: KeyRole,
    @param:StringRes val description: Int? = null,
    @param:DrawableRes val icon: Int? = null,
    /** What a long press does (backspace: clear everything). */
    val longPressKey: CalcKey? = null,
)

private data class KeyLabels(val clear: String, val parentheses: String, val decimal: String)

private fun digit(d: Int) = KeyDef(d.toString(), CalcKey.Digit(d), KeyRole.Number)

private fun op(o: Operator, description: Int) =
    KeyDef(o.symbol.toString(), CalcKey.Op(o), KeyRole.Operator, description)

private fun keypadRows(layout: KeypadLayout, labels: KeyLabels): List<List<KeyDef>> {
    val clear = KeyDef(labels.clear, CalcKey.Clear, KeyRole.Clear, R.string.key_all_clear)
    val parentheses = KeyDef(labels.parentheses, CalcKey.Parentheses, KeyRole.Operator, R.string.key_parentheses)
    val percent = KeyDef("%", CalcKey.Percent, KeyRole.Operator, R.string.key_percent)
    val decimal = KeyDef(labels.decimal, CalcKey.Decimal, KeyRole.Number, R.string.key_decimal)
    val equals = KeyDef("=", CalcKey.Equals, KeyRole.Equals, R.string.key_equals)
    return when (layout) {
        KeypadLayout.Standard -> listOf(
            listOf(clear, parentheses, percent, op(Operator.DIVIDE, R.string.key_divide)),
            listOf(digit(7), digit(8), digit(9), op(Operator.TIMES, R.string.key_multiply)),
            listOf(digit(4), digit(5), digit(6), op(Operator.MINUS, R.string.key_minus)),
            listOf(digit(1), digit(2), digit(3), op(Operator.PLUS, R.string.key_plus)),
            listOf(
                KeyDef("±", CalcKey.ToggleSign, KeyRole.Number, R.string.key_toggle_sign),
                digit(0),
                decimal,
                equals,
            ),
        )
        KeypadLayout.Compact -> listOf(
            listOf(digit(7), digit(8), digit(9), percent, parentheses),
            listOf(digit(4), digit(5), digit(6), op(Operator.MINUS, R.string.key_minus), op(Operator.DIVIDE, R.string.key_divide)),
            listOf(digit(1), digit(2), digit(3), op(Operator.PLUS, R.string.key_plus), op(Operator.TIMES, R.string.key_multiply)),
            listOf(
                KeyDef(
                    "",
                    CalcKey.Backspace,
                    KeyRole.Number,
                    R.string.backspace,
                    icon = R.drawable.ic_backspace,
                    longPressKey = CalcKey.Clear,
                ),
                digit(0),
                decimal,
                equals,
                clear,
            ),
        )
    }
}

/** Keys fill the available space; labels scale with the key size. */
@Composable
fun Keypad(
    onKey: (CalcKey) -> Unit,
    modifier: Modifier = Modifier,
    spacing: Dp = 8.dp,
    layout: KeypadLayout = KeypadLayout.Standard,
) {
    val labels = KeyLabels(
        clear = stringResource(R.string.key_label_all_clear),
        parentheses = stringResource(R.string.key_label_parentheses),
        decimal = rememberDisplayFormatter().decimalSeparator.toString(),
    )
    val rows = remember(layout, labels) { keypadRows(layout, labels) }
    val haptic = rememberKeyHaptic()
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        BoxWithConstraints(modifier) {
            val columns = rows.maxOf { it.size }
            val keyHeight = (maxHeight - spacing * (rows.size - 1)) / rows.size
            val keyWidth = (maxWidth - spacing * (columns - 1)) / columns
            val smallest = min(keyHeight, keyWidth)
            val scale = if (layout == KeypadLayout.Compact) 0.52f else 0.42f
            val fontSize = (smallest.value * scale).coerceIn(12f, 34f).sp
            val shape = RoundedCornerShape(smallest * 0.4f)
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(spacing)) {
                rows.forEach { row ->
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(spacing)) {
                        row.forEach { def ->
                            KeyButton(
                                def = def,
                                fontSize = fontSize,
                                shape = shape,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                onPress = {
                                    haptic()
                                    onKey(it)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KeyButton(
    def: KeyDef,
    fontSize: TextUnit,
    shape: Shape,
    modifier: Modifier,
    onPress: (CalcKey) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val (container, content) = when (def.role) {
        KeyRole.Number -> colors.surfaceContainerHighest to colors.onSurface
        KeyRole.Operator -> colors.secondaryContainer to colors.onSecondaryContainer
        KeyRole.Clear -> colors.tertiaryContainer to colors.onTertiaryContainer
        KeyRole.Equals -> colors.primary to colors.onPrimary
    }
    val description = def.description?.let { stringResource(it) } ?: def.label
    val longPressLabel = def.longPressKey?.let { stringResource(R.string.action_clear_all) }
    Box(
        modifier = modifier
            .clip(shape)
            .background(container)
            .combinedClickable(
                role = Role.Button,
                onLongClickLabel = longPressLabel,
                onLongClick = def.longPressKey?.let { key -> { onPress(key) } },
                onClick = { onPress(def.key) },
            )
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        if (def.icon != null) {
            val iconSize = with(LocalDensity.current) { fontSize.toDp() } * 1.1f
            Icon(painterResource(def.icon), contentDescription = null, tint = content, modifier = Modifier.size(iconSize))
        } else {
            Text(
                text = def.label,
                fontSize = fontSize,
                color = content,
                maxLines = 1,
                modifier = Modifier.clearAndSetSemantics { },
            )
        }
    }
}
