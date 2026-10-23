package com.floatcalc.app.ui.calc

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
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

private enum class KeyRole { Number, Operator, Clear, Equals }

private data class KeyDef(
    val label: String,
    val key: CalcKey,
    val role: KeyRole,
    @param:StringRes val description: Int? = null,
)

private fun keypadRows(clearLabel: String, parenthesesLabel: String, decimalLabel: String): List<List<KeyDef>> {
    fun digit(d: Int) = KeyDef(d.toString(), CalcKey.Digit(d), KeyRole.Number)
    fun op(o: Operator, description: Int) = KeyDef(o.symbol.toString(), CalcKey.Op(o), KeyRole.Operator, description)
    return listOf(
        listOf(
            KeyDef(clearLabel, CalcKey.Clear, KeyRole.Clear, R.string.key_all_clear),
            KeyDef(parenthesesLabel, CalcKey.Parentheses, KeyRole.Operator, R.string.key_parentheses),
            KeyDef("%", CalcKey.Percent, KeyRole.Operator, R.string.key_percent),
            op(Operator.DIVIDE, R.string.key_divide),
        ),
        listOf(digit(7), digit(8), digit(9), op(Operator.TIMES, R.string.key_multiply)),
        listOf(digit(4), digit(5), digit(6), op(Operator.MINUS, R.string.key_minus)),
        listOf(digit(1), digit(2), digit(3), op(Operator.PLUS, R.string.key_plus)),
        listOf(
            KeyDef("±", CalcKey.ToggleSign, KeyRole.Number, R.string.key_toggle_sign),
            digit(0),
            KeyDef(decimalLabel, CalcKey.Decimal, KeyRole.Number, R.string.key_decimal),
            KeyDef("=", CalcKey.Equals, KeyRole.Equals, R.string.key_equals),
        ),
    )
}

/**
 * The 5×4 keypad: AC ( ) % ÷ | 7 8 9 × | 4 5 6 − | 1 2 3 + | ± 0 . =
 * Keys fill the available space; labels scale with the key size.
 */
@Composable
fun Keypad(
    onKey: (CalcKey) -> Unit,
    modifier: Modifier = Modifier,
    spacing: Dp = 8.dp,
) {
    val decimalLabel = rememberDisplayFormatter().decimalSeparator.toString()
    val clearLabel = stringResource(R.string.key_label_all_clear)
    val parenthesesLabel = stringResource(R.string.key_label_parentheses)
    val rows = remember(clearLabel, parenthesesLabel, decimalLabel) {
        keypadRows(clearLabel, parenthesesLabel, decimalLabel)
    }
    val haptic = rememberKeyHaptic()
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        BoxWithConstraints(modifier) {
            val keyHeight = (maxHeight - spacing * (rows.size - 1)) / rows.size
            val keyWidth = (maxWidth - spacing * 3) / 4
            val smallest = min(keyHeight, keyWidth)
            val fontSize = (smallest.value * 0.42f).coerceIn(14f, 34f).sp
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
                                onClick = {
                                    haptic()
                                    onKey(def.key)
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
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val (container, content) = when (def.role) {
        KeyRole.Number -> colors.surfaceContainerHighest to colors.onSurface
        KeyRole.Operator -> colors.secondaryContainer to colors.onSecondaryContainer
        KeyRole.Clear -> colors.tertiaryContainer to colors.onTertiaryContainer
        KeyRole.Equals -> colors.primary to colors.onPrimary
    }
    val description = def.description?.let { stringResource(it) } ?: def.label
    Surface(
        onClick = onClick,
        shape = shape,
        color = container,
        contentColor = content,
        modifier = modifier.semantics { contentDescription = description },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = def.label,
                fontSize = fontSize,
                maxLines = 1,
                modifier = Modifier.clearAndSetSemantics { },
            )
        }
    }
}
