package com.floatcalc.app

import android.content.Context
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStoreFile
import com.floatcalc.app.data.AppSettings
import com.floatcalc.app.data.JsonSerializer
import com.floatcalc.app.data.SettingsRepository
import com.floatcalc.app.data.TabsRepository
import com.floatcalc.app.data.TabsState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json

/** App-level singletons, created once in [FloatCalcApp]. */
class AppContainer(context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    val tabs = TabsRepository(
        store = DataStoreFactory.create(
            serializer = JsonSerializer(TabsState.serializer(), TabsState(), json),
            corruptionHandler = ReplaceFileCorruptionHandler { TabsState() },
            produceFile = { context.dataStoreFile("tabs.json") },
        ),
        scope = scope,
    )

    val settings = SettingsRepository(
        store = DataStoreFactory.create(
            serializer = JsonSerializer(AppSettings.serializer(), AppSettings(), json),
            corruptionHandler = ReplaceFileCorruptionHandler { AppSettings() },
            produceFile = { context.dataStoreFile("settings.json") },
        ),
        scope = scope,
    )

    /** Writes pending changes now (called when the app or the floating window goes away). */
    fun flush() {
        tabs.flush()
        settings.flush()
    }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as FloatCalcApp).container
