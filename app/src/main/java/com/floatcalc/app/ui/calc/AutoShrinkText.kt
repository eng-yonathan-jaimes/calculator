package com.floatcalc.app.ui.calc

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * Single-line text that shrinks from [maxFontSize] to [minFontSize] to fit. If it still does not
 * fit, it wraps at the minimum size and scrolls to show the end (where typing happens).
 */
@Composable
fun AutoShrinkText(
    text: String,
    style: TextStyle,
    maxFontSize: TextUnit,
    minFontSize: TextUnit,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
) {
    val measurer = rememberTextMeasurer(cacheSize = 16)
    BoxWithConstraints(modifier, contentAlignment = Alignment.BottomEnd) {
        val maxWidth = constraints.maxWidth
        val maxHeight = constraints.maxHeight
        val fitting = remember(text, style, maxWidth, maxHeight, maxFontSize, minFontSize) {
            fittingFontSize(measurer, text, style, maxWidth, maxHeight, maxFontSize, minFontSize)
        }
        if (fitting != null) {
            Text(
                text = text,
                style = style.copy(fontSize = fitting, textAlign = TextAlign.End),
                color = color,
                maxLines = 1,
                softWrap = false,
            )
        } else {
            val scroll = rememberScrollState()
            LaunchedEffect(text, scroll.maxValue) { scroll.scrollTo(scroll.maxValue) }
            Text(
                text = text,
                style = style.copy(fontSize = minFontSize, textAlign = TextAlign.End),
                color = color,
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scroll),
            )
        }
    }
}

private const val STEP_SP = 2f

private fun fittingFontSize(
    measurer: TextMeasurer,
    text: String,
    style: TextStyle,
    maxWidth: Int,
    maxHeight: Int,
    maxFontSize: TextUnit,
    minFontSize: TextUnit,
): TextUnit? {
    var size = maxFontSize.value
    while (true) {
        val layout = measurer.measure(
            text = text,
            style = style.copy(fontSize = size.sp),
            maxLines = 1,
            softWrap = false,
            constraints = Constraints(),
        )
        val fitsHeight = maxHeight == Constraints.Infinity || layout.size.height <= maxHeight
        if (layout.size.width <= maxWidth && fitsHeight) return size.sp
        if (size <= minFontSize.value) return null
        size = maxOf(size - STEP_SP, minFontSize.value)
    }
}
