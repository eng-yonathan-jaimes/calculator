package com.floatcalc.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.floatcalc.app.ui.calc.CalculatorScreen
import com.floatcalc.app.ui.calc.LocalHapticsEnabled
import com.floatcalc.app.ui.theme.FloatCalcTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = appContainer
        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle()
            FloatCalcTheme(settings.theme) {
                CompositionLocalProvider(LocalHapticsEnabled provides settings.haptics) {
                    CalculatorScreen(container)
                }
            }
        }
    }
}
