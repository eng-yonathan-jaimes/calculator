package com.floatcalc.app.ui.calc

import android.graphics.Typeface
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** The system's condensed sans-serif (Roboto Condensed): narrower, taller-looking digits. */
private val CondensedFamily = FontFamily(Typeface.create("sans-serif-condensed", Typeface.NORMAL))

private data class TextFit(val fontSize: TextUnit, val condensed: Boolean, val wrap: Boolean)

/**
 * Text that fits itself into the available space, in this order:
 * 1. one line at [maxFontSize];
 * 2. one line in the condensed font, shrinking down to [wrapFontSize] (or [minFontSize]);
 * 3. if [wrapFontSize] is set: several lines, condensed, shrinking down to [minFontSize];
 * 4. otherwise the smallest size, scrolled to the end.
 *
 * Optionally draws a blinking caret before character [caretOffset] and reports taps as the
 * character offset under the finger ([onTapOffset]).
 */
@Composable
fun AutoShrinkText(
    text: String,
    style: TextStyle,
    maxFontSize: TextUnit,
    minFontSize: TextUnit,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    wrapFontSize: TextUnit? = null,
    caretOffset: Int? = null,
    caretColor: Color = Color.Unspecified,
    onTapOffset: ((Int) -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
) {
    val measurer = rememberTextMeasurer(cacheSize = 32)
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val tapHandler by rememberUpdatedState(onTapOffset)
    val longPressHandler by rememberUpdatedState(onLongPress)
    val caretVisible = if (caretOffset != null) rememberCaretBlink(text, caretOffset) else null

    val interactive = Modifier
        .pointerInput(onTapOffset != null, onLongPress != null) {
            if (tapHandler == null && longPressHandler == null) return@pointerInput
            detectTapGestures(
                onLongPress = { longPressHandler?.invoke() },
                onTap = { position -> layout?.let { tapHandler?.invoke(it.getOffsetForPosition(position)) } },
            )
        }
        .drawWithContent {
            drawContent()
            val textLayout = layout ?: return@drawWithContent
            val offset = caretOffset ?: return@drawWithContent
            if (caretVisible?.value != true || offset > textLayout.layoutInput.text.length) return@drawWithContent
            val rect = textLayout.getCursorRect(offset)
            drawLine(
                color = caretColor,
                start = Offset(rect.left, rect.top),
                end = Offset(rect.left, rect.bottom),
                strokeWidth = 2.dp.toPx(),
            )
        }

    BoxWithConstraints(modifier, contentAlignment = Alignment.TopEnd) {
        val maxWidth = constraints.maxWidth
        val maxHeight = constraints.maxHeight
        val fit = remember(text, style, maxWidth, maxHeight, maxFontSize, minFontSize, wrapFontSize) {
            fitText(measurer, text, style, maxWidth, maxHeight, maxFontSize, minFontSize, wrapFontSize)
        }
        val fittedStyle = style.copy(
            fontSize = fit.fontSize,
            fontFamily = if (fit.condensed) CondensedFamily else style.fontFamily,
            textAlign = TextAlign.End,
        )
        if (!fit.wrap) {
            Text(
                text = text,
                style = fittedStyle,
                color = color,
                maxLines = 1,
                onTextLayout = { layout = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .then(interactive),
            )
        } else {
            val scroll = rememberScrollState()
            val caretAtEnd = caretOffset == null || caretOffset >= text.length
            LaunchedEffect(text, scroll.maxValue) { if (caretAtEnd) scroll.scrollTo(scroll.maxValue) }
            Text(
                text = text,
                style = fittedStyle,
                color = color,
                onTextLayout = { layout = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scroll)
                    .then(interactive),
            )
        }
    }
}

/** Blinks every 0.5 s, starting visible again whenever the text or caret position changes. */
@Composable
private fun rememberCaretBlink(text: String, offset: Int): State<Boolean> {
    val visible = remember { mutableStateOf(true) }
    LaunchedEffect(text, offset) {
        visible.value = true
        while (true) {
            delay(CARET_BLINK_MS)
            visible.value = !visible.value
        }
    }
    return visible
}

private const val CARET_BLINK_MS = 500L
private const val STEP_SP = 2f

private fun fitText(
    measurer: TextMeasurer,
    text: String,
    style: TextStyle,
    maxWidth: Int,
    maxHeight: Int,
    maxFontSize: TextUnit,
    minFontSize: TextUnit,
    wrapFontSize: TextUnit?,
): TextFit {
    fun fitsOneLine(size: Float, condensed: Boolean): Boolean {
        val result = measurer.measure(
            text = text,
            style = style.copy(fontSize = size.sp, fontFamily = if (condensed) CondensedFamily else style.fontFamily),
            maxLines = 1,
            softWrap = false,
            constraints = Constraints(),
        )
        return result.size.width <= maxWidth && fitsHeight(result.size.height, maxHeight)
    }

    fun fitsWrapped(size: Float): Boolean {
        val result = measurer.measure(
            text = text,
            style = style.copy(fontSize = size.sp, fontFamily = CondensedFamily),
            softWrap = true,
            constraints = Constraints(maxWidth = maxWidth),
        )
        return fitsHeight(result.size.height, maxHeight)
    }

    val max = maxFontSize.value
    val min = minFontSize.value
    if (fitsOneLine(max, condensed = false)) return TextFit(maxFontSize, condensed = false, wrap = false)

    val oneLineFloor = wrapFontSize?.value?.coerceIn(min, max) ?: min
    var size = max
    while (true) {
        if (fitsOneLine(size, condensed = true)) return TextFit(size.sp, condensed = true, wrap = false)
        if (size <= oneLineFloor) break
        size = maxOf(size - STEP_SP, oneLineFloor)
    }
    if (wrapFontSize == null) return TextFit(minFontSize, condensed = true, wrap = false)

    size = oneLineFloor
    while (true) {
        if (fitsWrapped(size)) return TextFit(size.sp, condensed = true, wrap = true)
        if (size <= min) break
        size = maxOf(size - STEP_SP, min)
    }
    return TextFit(minFontSize, condensed = true, wrap = true)
}

private fun fitsHeight(height: Int, maxHeight: Int) = maxHeight == Constraints.Infinity || height <= maxHeight
