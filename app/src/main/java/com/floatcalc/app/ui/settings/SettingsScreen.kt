package com.floatcalc.app.ui.settings

import android.Manifest
import android.app.StatusBarManager
import android.app.UiModeManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon as MaterialIcon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.floatcalc.app.AppContainer
import com.floatcalc.app.R
import com.floatcalc.app.data.AppSettings
import com.floatcalc.app.data.OverlaySize
import com.floatcalc.app.data.ThemeMode
import com.floatcalc.app.tile.FloatTileService
import com.floatcalc.app.ui.permission.OverlayPermissionDialog
import com.floatcalc.app.ui.permission.rememberCanDrawOverlays
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(container: AppContainer, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val settings by container.settings.settings.collectAsStateWithLifecycle()
    val canDrawOverlays by rememberCanDrawOverlays()
    val notificationsAllowed = rememberNotificationsAllowed()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showPermissionDialog by rememberSaveable { mutableStateOf(false) }

    fun update(transform: (AppSettings) -> AppSettings) = container.settings.update(transform)

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        MaterialIcon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 640.dp).fillMaxWidth()) {
                SectionHeader(R.string.settings_section_appearance)
                SettingLabel(R.string.settings_theme)
                ChoiceRow(
                    options = ThemeMode.entries,
                    selected = settings.theme,
                    label = { stringResource(it.labelRes()) },
                    onSelect = { mode ->
                        update { it.copy(theme = mode) }
                        applyNightMode(context, mode)
                    },
                )
                SwitchItem(
                    title = stringResource(R.string.settings_haptics),
                    summary = stringResource(R.string.settings_haptics_summary),
                    checked = settings.haptics,
                    onCheckedChange = { checked -> update { it.copy(haptics = checked) } },
                )

                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                SectionHeader(R.string.settings_section_floating)
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_overlay_permission)) },
                    supportingContent = {
                        Text(
                            stringResource(
                                if (canDrawOverlays) R.string.settings_permission_allowed else R.string.settings_overlay_permission_missing,
                            ),
                            color = if (canDrawOverlays) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                        )
                    },
                    trailingContent = {
                        if (!canDrawOverlays) {
                            Button(onClick = { showPermissionDialog = true }) { Text(stringResource(R.string.action_fix)) }
                        }
                    },
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.settings_notifications)) },
                        supportingContent = {
                            Text(
                                stringResource(
                                    if (notificationsAllowed) R.string.settings_permission_allowed else R.string.settings_notifications_missing,
                                ),
                            )
                        },
                        trailingContent = {
                            if (!notificationsAllowed) {
                                OutlinedButton(onClick = { openNotificationSettings(context) }) {
                                    Text(stringResource(R.string.action_allow))
                                }
                            }
                        },
                    )
                }

                val opacityPercent = (settings.overlayOpacity * 100).roundToInt()
                SettingLabel(R.string.settings_opacity, valueText = stringResource(R.string.percent_value, opacityPercent))
                Slider(
                    value = settings.overlayOpacity,
                    onValueChange = { value -> update { it.copy(overlayOpacity = (value * 20).roundToInt() / 20f) } },
                    valueRange = AppSettings.MIN_OPACITY..1f,
                    steps = 9,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .semantics { stateDescription = resources.getString(R.string.percent_value, opacityPercent) },
                )

                SettingLabel(R.string.settings_size)
                ChoiceRow(
                    options = OverlaySize.entries,
                    selected = settings.overlaySize,
                    label = { stringResource(it.labelRes()) },
                    onSelect = { size -> update { it.copy(overlaySize = size) } },
                )

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.settings_tile)) },
                        supportingContent = { Text(stringResource(R.string.settings_tile_summary)) },
                        trailingContent = {
                            OutlinedButton(
                                onClick = {
                                    requestAddTile(context) { messageRes ->
                                        scope.launch { snackbar.showSnackbar(resources.getString(messageRes)) }
                                    }
                                },
                            ) { Text(stringResource(R.string.action_add_tile)) }
                        },
                    )
                } else {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.settings_tile)) },
                        supportingContent = { Text(stringResource(R.string.settings_tile_manual)) },
                    )
                }

                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                SectionHeader(R.string.settings_section_about)
                ListItem(
                    headlineContent = { Text(stringResource(R.string.app_name)) },
                    supportingContent = { Text(stringResource(R.string.settings_version, appVersion(context))) },
                )
            }
        }
    }

    if (showPermissionDialog) {
        OverlayPermissionDialog(onDismiss = { showPermissionDialog = false })
    }
}

@Composable
private fun SectionHeader(@StringRes title: Int) {
    Text(
        text = stringResource(title),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun SettingLabel(@StringRes title: Int, valueText: String? = null) {
    ListItem(
        headlineContent = { Text(stringResource(title)) },
        trailingContent = valueText?.let { { Text(it, style = MaterialTheme.typography.labelLarge) } },
        colors = ListItemDefaults.colors(),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> ChoiceRow(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    SingleChoiceSegmentedButtonRow(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
            ) { Text(label(option), maxLines = 1) }
        }
    }
}

@Composable
private fun SwitchItem(title: String, summary: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        modifier = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
    )
}

@StringRes
private fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.theme_system
    ThemeMode.LIGHT -> R.string.theme_light
    ThemeMode.DARK -> R.string.theme_dark
}

@StringRes
private fun OverlaySize.labelRes(): Int = when (this) {
    OverlaySize.SMALL -> R.string.size_small
    OverlaySize.MEDIUM -> R.string.size_medium
    OverlaySize.LARGE -> R.string.size_large
}

/**
 * Android 12+ can apply the theme choice to the whole app process, so the window background
 * before the first frame (and the splash screen) match it too.
 */
private fun applyNightMode(context: Context, mode: ThemeMode) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val uiMode = context.getSystemService(UiModeManager::class.java) ?: return
    uiMode.setApplicationNightMode(
        when (mode) {
            ThemeMode.SYSTEM -> UiModeManager.MODE_NIGHT_AUTO
            ThemeMode.LIGHT -> UiModeManager.MODE_NIGHT_NO
            ThemeMode.DARK -> UiModeManager.MODE_NIGHT_YES
        },
    )
}

@Composable
private fun rememberNotificationsAllowed(): Boolean {
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(notificationsAllowed(context)) }
    LifecycleResumeEffect(Unit) {
        allowed = notificationsAllowed(context)
        onPauseOrDispose { }
    }
    return allowed
}

private fun notificationsAllowed(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

private fun openNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    try {
        context.startActivity(intent)
    } catch (_: RuntimeException) {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
        )
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun requestAddTile(context: Context, onResult: (Int) -> Unit) {
    val statusBar = context.getSystemService(StatusBarManager::class.java) ?: return
    statusBar.requestAddTileService(
        ComponentName(context, FloatTileService::class.java),
        context.getString(R.string.tile_label),
        Icon.createWithResource(context, R.drawable.ic_calculate),
        context.mainExecutor,
    ) { result ->
        onResult(
            when (result) {
                StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> R.string.tile_added
                StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> R.string.tile_already_added
                StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED -> R.string.tile_not_added
                else -> R.string.tile_add_failed
            },
        )
    }
}

private fun appVersion(context: Context): String = try {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
} catch (_: PackageManager.NameNotFoundException) {
    ""
}
