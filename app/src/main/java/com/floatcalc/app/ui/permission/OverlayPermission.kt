package com.floatcalc.app.ui.permission

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.floatcalc.app.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object OverlayPermission {
    fun settingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:${context.packageName}".toUri())

    fun appInfoIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))

    fun open(context: Context, intent: Intent) {
        try {
            context.startActivity(intent)
        } catch (e: RuntimeException) {
            Log.w("OverlayPermission", "No settings screen for $intent", e)
            if (intent.action != Settings.ACTION_APPLICATION_DETAILS_SETTINGS) open(context, appInfoIntent(context))
        }
    }
}

/**
 * Whether the app may draw overlays, re-checked every time the screen resumes and again shortly
 * after, because some devices report a newly granted permission late.
 */
@Composable
fun rememberCanDrawOverlays(): State<Boolean> {
    val context = LocalContext.current
    val state = remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    val scope = rememberCoroutineScope()
    LifecycleResumeEffect(Unit) {
        val job = scope.launch {
            for (wait in RECHECK_DELAYS_MS) {
                delay(wait)
                state.value = Settings.canDrawOverlays(context)
            }
        }
        onPauseOrDispose { job.cancel() }
    }
    return state
}

private val RECHECK_DELAYS_MS = listOf(0L, 300L, 700L, 1_500L)

/** Explains the "Display over other apps" permission, including the restricted-settings fix. */
@Composable
fun OverlayPermissionDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(painterResource(R.drawable.ic_float), contentDescription = null) },
        title = { Text(stringResource(R.string.overlay_permission_title)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                Text(stringResource(R.string.overlay_permission_body))
                Text(
                    text = stringResource(R.string.overlay_permission_restricted),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = { OverlayPermission.open(context, OverlayPermission.appInfoIntent(context)) }) {
                    Text(stringResource(R.string.action_app_info))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { OverlayPermission.open(context, OverlayPermission.settingsIntent(context)) }) {
                Text(stringResource(R.string.action_open_settings))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_not_now)) }
        },
    )
}
