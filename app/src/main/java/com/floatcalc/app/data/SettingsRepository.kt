package com.floatcalc.app.data

import androidx.datastore.core.DataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

class SettingsRepository(store: DataStore<AppSettings>, scope: CoroutineScope) {
    private val persisted = PersistedState(store, scope, AppSettings()) {
        it.copy(overlayOpacity = it.overlayOpacity.coerceIn(AppSettings.MIN_OPACITY, 1f))
    }

    val settings: StateFlow<AppSettings> = persisted.value
    val loaded: StateFlow<Boolean> = persisted.loaded

    fun update(transform: (AppSettings) -> AppSettings) = persisted.update(transform)

    fun flush() = persisted.flush()
}
