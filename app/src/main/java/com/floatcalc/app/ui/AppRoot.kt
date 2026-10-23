package com.floatcalc.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.floatcalc.app.AppContainer
import com.floatcalc.app.ui.calc.CalculatorScreen
import com.floatcalc.app.ui.permission.OverlayPermissionDialog
import com.floatcalc.app.ui.permission.rememberCanDrawOverlays

/**
 * The main activity's content. Handles the Float flow: overlay permission (with a dialog and
 * re-check on resume), then the notification permission on Android 13+ (optional: the floating
 * window works without it), then [onStartFloating].
 */
@Composable
fun AppRoot(
    container: AppContainer,
    permissionRequested: Boolean,
    onPermissionRequestHandled: () -> Unit,
    onStartFloating: () -> Unit,
) {
    val context = LocalContext.current
    val canDrawOverlays by rememberCanDrawOverlays()
    var showPermissionDialog by rememberSaveable { mutableStateOf(false) }
    var floatPending by rememberSaveable { mutableStateOf(false) }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        onStartFloating()
    }

    fun float() {
        if (!Settings.canDrawOverlays(context)) {
            floatPending = true
            showPermissionDialog = true
            return
        }
        val needsNotificationPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        if (needsNotificationPermission) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            onStartFloating()
        }
    }

    LaunchedEffect(permissionRequested) {
        if (permissionRequested) {
            onPermissionRequestHandled()
            float()
        }
    }
    LaunchedEffect(canDrawOverlays, floatPending) {
        if (canDrawOverlays && floatPending) {
            floatPending = false
            showPermissionDialog = false
            float()
        }
    }

    CalculatorScreen(container = container, onFloat = ::float)

    if (showPermissionDialog) {
        OverlayPermissionDialog(
            onDismiss = {
                showPermissionDialog = false
                floatPending = false
            },
        )
    }
}
