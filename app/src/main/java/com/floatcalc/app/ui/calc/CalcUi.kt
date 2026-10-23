package com.floatcalc.app.ui.calc

import android.content.res.Resources
import android.view.HapticFeedbackConstants
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import com.floatcalc.app.R
import com.floatcalc.app.data.CalcTab
import com.floatcalc.app.engine.CalcError
import com.floatcalc.app.engine.DisplayFormatter

/** Whether key presses give haptic feedback (the app setting; the system setting still applies). */
val LocalHapticsEnabled = compositionLocalOf { true }

/** Returns a function that performs a keyboard-tap haptic, honoring the app and system settings. */
@Composable
fun rememberKeyHaptic(): () -> Unit {
    val view = LocalView.current
    val enabled = LocalHapticsEnabled.current
    return remember(view, enabled) {
        { if (enabled) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP) }
    }
}

@Composable
fun rememberDisplayFormatter(): DisplayFormatter {
    val locale = LocalConfiguration.current.locales[0]
    return remember(locale) { DisplayFormatter.forLocale(locale) }
}

@StringRes
fun CalcError.messageRes(): Int = when (this) {
    CalcError.INCOMPLETE -> R.string.error_incomplete
    CalcError.MALFORMED -> R.string.error_malformed
    CalcError.DIVIDE_BY_ZERO -> R.string.error_divide_by_zero
    CalcError.OVERFLOW -> R.string.error_overflow
}

@Composable
fun tabName(tab: CalcTab): String = tab.customName ?: stringResource(R.string.tab_default_name, tab.number)

fun Resources.tabName(tab: CalcTab): String = tab.customName ?: getString(R.string.tab_default_name, tab.number)
