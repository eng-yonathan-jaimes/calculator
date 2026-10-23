package com.floatcalc.app.data

import android.util.Log
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/** DataStore serializer that stores a value as JSON with kotlinx.serialization. */
class JsonSerializer<T>(
    private val serializer: KSerializer<T>,
    override val defaultValue: T,
    private val json: Json,
) : Serializer<T> {
    override suspend fun readFrom(input: InputStream): T = try {
        json.decodeFromString(serializer, input.readBytes().decodeToString())
    } catch (e: SerializationException) {
        throw CorruptionException("Unreadable data", e)
    } catch (e: IllegalArgumentException) {
        throw CorruptionException("Unreadable data", e)
    }

    override suspend fun writeTo(t: T, output: OutputStream) {
        output.write(json.encodeToString(serializer, t).encodeToByteArray())
    }
}

/**
 * An in-memory [StateFlow] that is loaded from a [DataStore] once and written back with a debounce,
 * so rapid key presses cause one write. [flush] writes immediately (e.g. when the app goes to the
 * background).
 */
class PersistedState<T>(
    private val store: DataStore<T>,
    private val scope: CoroutineScope,
    initial: T,
    sanitize: (T) -> T = { it },
) {
    private val _value = MutableStateFlow(initial)
    val value: StateFlow<T> = _value.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    init {
        scope.launch {
            val stored = try {
                store.data.first()
            } catch (e: IOException) {
                Log.w(TAG, "Could not read stored data", e)
                initial
            }
            _value.value = sanitize(stored)
            _loaded.value = true
            @OptIn(FlowPreview::class)
            _value.drop(1).debounce(WRITE_DEBOUNCE_MS).collect { write(it) }
        }
    }

    /** Changes are ignored until the stored value has been loaded (the UI waits for [loaded]). */
    fun update(transform: (T) -> T) {
        if (_loaded.value) _value.update(transform)
    }

    fun flush() {
        if (_loaded.value) scope.launch { write(_value.value) }
    }

    private suspend fun write(value: T) {
        try {
            store.updateData { value }
        } catch (e: IOException) {
            Log.w(TAG, "Could not save data", e)
        }
    }

    private companion object {
        const val TAG = "PersistedState"
        const val WRITE_DEBOUNCE_MS = 400L
    }
}
