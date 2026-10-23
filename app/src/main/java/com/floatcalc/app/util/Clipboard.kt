package com.floatcalc.app.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import com.floatcalc.app.R

object Clipboard {

    /** Copies [text]. Android 13+ confirms copies itself, so only older versions get a toast. */
    fun copy(context: Context, text: String) {
        val manager = context.getSystemService(ClipboardManager::class.java) ?: return
        manager.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.clip_label), text))
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(context, R.string.copied, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Reads the clipboard as text. On Android 10+ this returns null unless the app has input
     * focus (or is the default IME), which is why the floating window briefly takes focus.
     */
    fun readText(context: Context): String? = try {
        val manager = context.getSystemService(ClipboardManager::class.java)
        val clip = manager?.primaryClip
        if (clip == null || clip.itemCount == 0) null else clip.getItemAt(0).coerceToText(context)?.toString()
    } catch (_: SecurityException) {
        null
    }
}
