package com.floatcalc.app

import android.content.Context
import com.floatcalc.app.data.SettingsRepository
import com.floatcalc.app.data.TabsRepository

/** App-level singletons, created once in [FloatCalcApp]. */
class AppContainer(@Suppress("UNUSED_PARAMETER") context: Context) {
    val tabs = TabsRepository()
    val settings = SettingsRepository()
}

val Context.appContainer: AppContainer
    get() = (applicationContext as FloatCalcApp).container
