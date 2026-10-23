package com.floatcalc.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.floatcalc.app.AppContainer
import com.floatcalc.app.ui.calc.CalculatorScreen
import com.floatcalc.app.ui.permission.OverlayPermissionDialog
import com.floatcalc.app.ui.permission.rememberCanDrawOverlays
import com.floatcalc.app.ui.settings.SettingsScreen
import kotlin.coroutines.cancellation.CancellationException

private enum class Screen { Calculator, Settings }

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

    var screen by rememberSaveable { mutableStateOf(Screen.Calculator) }
    var backProgress by remember { mutableFloatStateOf(0f) }
    PredictiveBackHandler(enabled = screen == Screen.Settings) { events ->
        try {
            events.collect { backProgress = it.progress }
            screen = Screen.Calculator
        } catch (e: CancellationException) {
            backProgress = 0f
            throw e
        }
    }

    AnimatedContent(
        targetState = screen,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "screen",
    ) { target ->
        when (target) {
            Screen.Calculator -> CalculatorScreen(
                container = container,
                onFloat = ::float,
                onOpenSettings = {
                    backProgress = 0f
                    screen = Screen.Settings
                },
            )
            Screen.Settings -> SettingsScreen(
                container = container,
                onBack = { screen = Screen.Calculator },
                modifier = Modifier.graphicsLayer {
                    // Predictive back: the page shrinks and slides with the gesture.
                    val scale = 1f - 0.1f * backProgress
                    scaleX = scale
                    scaleY = scale
                    translationX = backProgress * 32.dp.toPx()
                    shape = RoundedCornerShape(28.dp * backProgress)
                    clip = backProgress > 0f
                },
            )
        }
    }

    if (showPermissionDialog) {
        OverlayPermissionDialog(
            onDismiss = {
                showPermissionDialog = false
                floatPending = false
            },
        )
    }
}
