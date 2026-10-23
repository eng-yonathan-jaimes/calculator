package com.floatcalc.app.ui.calc

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Single-line text that shrinks from [maxFontSize] to [minFontSize] to fit. If it still does not
 * fit, it wraps at the minimum size and scrolls to show the end (where typing happens).
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
    caretOffset: Int? = null,
    caretColor: Color = Color.Unspecified,
    onTapOffset: ((Int) -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
) {
    val measurer = rememberTextMeasurer(cacheSize = 16)
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val tapHandler by rememberUpdatedState(onTapOffset)
    val longPressHandler by rememberUpdatedState(onLongPress)
    val caretAlpha = if (caretOffset != null) rememberBlinkingAlpha() else null

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
            val alpha = caretAlpha?.value ?: return@drawWithContent
            if (offset > textLayout.layoutInput.text.length || alpha <= 0f) return@drawWithContent
            val rect = textLayout.getCursorRect(offset)
            drawLine(
                color = caretColor.copy(alpha = caretColor.alpha * alpha),
                start = Offset(rect.left, rect.top),
                end = Offset(rect.left, rect.bottom),
                strokeWidth = 2.dp.toPx(),
            )
        }

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
                // Lays out across the full width (it fits on one line) so End alignment holds.
                softWrap = true,
                onTextLayout = { layout = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .then(interactive),
            )
        } else {
            val scroll = rememberScrollState()
            LaunchedEffect(text, scroll.maxValue) { if (caretOffset == null) scroll.scrollTo(scroll.maxValue) }
            Text(
                text = text,
                style = style.copy(fontSize = minFontSize, textAlign = TextAlign.End),
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

/** 1 → 0 → 1 every second, like a text cursor. */
@Composable
private fun rememberBlinkingAlpha(): State<Float> {
    val transition = rememberInfiniteTransition(label = "caret")
    return transition.animateFloat(
        initialValue = 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = 1_000
                1f at 0
                1f at 499
                0f at 500
                0f at 999
            },
        ),
        label = "caretAlpha",
    )
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
