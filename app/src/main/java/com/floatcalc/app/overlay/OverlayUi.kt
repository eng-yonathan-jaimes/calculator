package com.floatcalc.app.overlay

import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.floatcalc.app.AppContainer
import com.floatcalc.app.R
import com.floatcalc.app.data.CalcTab
import com.floatcalc.app.data.TabsState
import com.floatcalc.app.engine.CalcKey
import com.floatcalc.app.ui.calc.AnimatedTabDisplay
import com.floatcalc.app.ui.calc.KeypadLayout
import com.floatcalc.app.ui.calc.DisplayStyle
import com.floatcalc.app.ui.calc.HistoryList
import com.floatcalc.app.ui.calc.Keypad
import com.floatcalc.app.ui.calc.LocalHapticsEnabled
import com.floatcalc.app.ui.calc.TabColumn
import com.floatcalc.app.ui.calc.detectHorizontalSwipe
import com.floatcalc.app.ui.calc.rememberDisplayFormatter
import com.floatcalc.app.ui.calc.rememberRenameFieldState
import com.floatcalc.app.ui.calc.tabName
import com.floatcalc.app.ui.theme.FloatCalcTheme
import kotlin.math.hypot

/** A short message shown at the bottom of the panel, optionally with an action (e.g. Undo). */
data class OverlayMessage(val text: String, val actionLabel: String? = null, val action: (() -> Unit)? = null)

/** Service-owned state the overlay composables observe. */
class OverlayUiState {
    var message by mutableStateOf<OverlayMessage?>(null)
    var bubbleOverDismissTarget by mutableStateOf(false)

    /** Whether the panel window currently has input focus (only while pasting or renaming). */
    var panelFocused by mutableStateOf(false)

    /** Incremented when typing must end (back key, touch outside the panel). */
    var textInputCancelSignal by mutableIntStateOf(0)
}

interface DragCallbacks {
    fun onDragStart()

    /** Total movement since the finger went down, in screen pixels. */
    fun onDrag(dx: Float, dy: Float)
    fun onDragEnd()
    fun onTap() = Unit
}

interface PanelActions {
    val drag: DragCallbacks
    fun onMinimize()
    fun onOpenApp()
    fun onClose()
    fun onPaste()
    fun onCopy(text: String)

    /** The rename field is shown (true) or gone (false): the window must take focus for typing. */
    fun onTextInput(active: Boolean)
    fun showMessage(message: OverlayMessage)
}

/**
 * Tracks a drag using raw screen coordinates. The window moves under the finger, so positions
 * local to the view would feed back into the movement.
 */
class DragTracker(private val touchSlop: Float) {
    var callbacks: DragCallbacks? = null
    private var downX = 0f
    private var downY = 0f
    private var dragging = false

    fun onTouch(event: MotionEvent): Boolean {
        val target = callbacks ?: return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                downY = event.rawY
                dragging = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - downX
                val dy = event.rawY - downY
                if (!dragging && hypot(dx, dy) > touchSlop) {
                    dragging = true
                    target.onDragStart()
                }
                if (dragging) target.onDrag(dx, dy)
            }
            MotionEvent.ACTION_UP -> if (dragging) target.onDragEnd() else target.onTap()
            MotionEvent.ACTION_CANCEL -> if (dragging) target.onDragEnd()
        }
        return true
    }
}

@Composable
private fun rememberDragTracker(callbacks: DragCallbacks): DragTracker {
    val context = LocalContext.current
    val tracker = remember { DragTracker(ViewConfiguration.get(context).scaledTouchSlop.toFloat()) }
    tracker.callbacks = callbacks
    return tracker
}

@OptIn(ExperimentalComposeUiApi::class)
private fun Modifier.dragWith(tracker: DragTracker): Modifier = pointerInteropFilter { tracker.onTouch(it) }

/** Theme, haptics and compact touch targets for overlay content. */
@Composable
private fun OverlayTheme(container: AppContainer, content: @Composable () -> Unit) {
    val settings by container.settings.settings.collectAsStateWithLifecycle()
    FloatCalcTheme(settings.theme) {
        CompositionLocalProvider(
            LocalHapticsEnabled provides settings.haptics,
            // Compact like the rest of the panel; Compose still extends small touch targets.
            LocalMinimumInteractiveComponentSize provides 0.dp,
            content = content,
        )
    }
}

@Composable
fun OverlayPanel(container: AppContainer, ui: OverlayUiState, actions: PanelActions) {
    OverlayTheme(container) {
        val tabs = container.tabs
        val tabsState by tabs.state.collectAsStateWithLifecycle()
        val tab = tabsState.selected
        val formatter = rememberDisplayFormatter()
        val dragTracker = rememberDragTracker(actions.drag)
        var showHistory by remember { mutableStateOf(false) }
        var actionsTabId by remember { mutableStateOf<Long?>(null) }
        var renameTabId by remember { mutableStateOf<Long?>(null) }
        val tooManyTabs = pluralStringResource(R.plurals.max_tabs_reached, TabsState.MAX_TABS, TabsState.MAX_TABS)
        val resources = LocalResources.current
        val undoLabel = stringResource(R.string.action_undo)
        val cancelSignal = ui.textInputCancelSignal
        val initialCancelSignal = remember { cancelSignal }
        LaunchedEffect(cancelSignal) {
            if (cancelSignal != initialCancelSignal) renameTabId = null
        }

        fun closeTab(target: CalcTab) {
            val name = resources.tabName(target)
            val closed = tabs.closeTab(target.id) ?: return
            actions.showMessage(OverlayMessage(resources.getString(R.string.tab_closed, name), undoLabel) { tabs.restoreTab(closed) })
        }

        fun duplicateTab(target: CalcTab) {
            if (!tabs.duplicateTab(target.id)) actions.showMessage(OverlayMessage(tooManyTabs))
        }

        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        ) {
            Box(Modifier.fillMaxSize()) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(start = 6.dp, end = 6.dp, bottom = 6.dp),
                ) {
                    PanelHeader(
                        dragTracker = dragTracker,
                        showHistory = showHistory,
                        onToggleHistory = { showHistory = !showHistory },
                        onAddTab = { if (!tabs.addTab()) actions.showMessage(OverlayMessage(tooManyTabs)) },
                        actions = actions,
                    )
                    // Tabs stacked on the left, display on the right (or the tab options / rename row).
                    Box(
                        Modifier
                            .weight(0.5f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        val renaming = renameTabId?.let { id -> tabsState.tabs.firstOrNull { it.id == id } }
                        val actionTab = actionsTabId?.let { id -> tabsState.tabs.firstOrNull { it.id == id } }
                        when {
                            renaming != null -> RenameRow(
                                initialName = tabName(renaming),
                                focused = ui.panelFocused,
                                onConfirm = {
                                    tabs.renameTab(renaming.id, it)
                                    renameTabId = null
                                },
                                onCancel = { renameTabId = null },
                                actions = actions,
                            )
                            actionTab != null -> TabActionsRow(
                                onRename = {
                                    actionsTabId = null
                                    renameTabId = actionTab.id
                                },
                                onDuplicate = {
                                    actionsTabId = null
                                    duplicateTab(actionTab)
                                },
                                onClose = {
                                    actionsTabId = null
                                    closeTab(actionTab)
                                },
                                onDismiss = { actionsTabId = null },
                            )
                            else -> Row(Modifier.fillMaxSize()) {
                                TabColumn(
                                    tabs = tabsState.tabs,
                                    selectedId = tabsState.selectedId,
                                    onSelect = { tabs.selectTab(it.id) },
                                    onLongPress = { actionsTabId = it.id },
                                    onRename = { renameTabId = it.id },
                                    onDuplicate = ::duplicateTab,
                                    onClose = ::closeTab,
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .widthIn(max = 92.dp),
                                )
                                AnimatedTabDisplay(
                                    tabId = tab.id,
                                    tabIndex = tabsState.selectedIndex,
                                    calc = tab.calc,
                                    formatter = formatter,
                                    onCopyResult = { actions.onCopy(formatter.toClipboardNumber(it)) },
                                    onCopyExpression = { actions.onCopy(it) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .padding(start = 8.dp, end = 4.dp)
                                        .pointerInput(tabs) { detectHorizontalSwipe { tabs.selectRelative(it) } },
                                    style = DisplayStyle.Compact,
                                    onMoveCursor = tabs::moveCursor,
                                    onEditPrevious = tabs::editPrevious,
                                )
                            }
                        }
                    }
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                    ) {
                        if (showHistory) {
                            HistoryList(
                                entries = tab.history,
                                formatter = formatter,
                                onReuse = {
                                    tabs.insert(it.result)
                                    showHistory = false
                                },
                                onCopy = { actions.onCopy(formatter.toClipboardNumber(it.result)) },
                                onClear = { tabs.clearHistory(tab.id) },
                                modifier = Modifier.fillMaxSize(),
                                compact = true,
                            )
                        } else {
                            Keypad(
                                onKey = tabs::press,
                                modifier = Modifier.fillMaxSize(),
                                spacing = 4.dp,
                                layout = KeypadLayout.Compact,
                            )
                        }
                    }
                }
                MessageBanner(ui.message, Modifier.align(Alignment.BottomCenter))
            }
        }
    }
}

/** ⠿ + [history] [paste] ··· _ ⧉ ✕ — the handle and the empty middle drag the window. */
@Composable
private fun PanelHeader(
    dragTracker: DragTracker,
    showHistory: Boolean,
    onToggleHistory: () -> Unit,
    onAddTab: () -> Unit,
    actions: PanelActions,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(32.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(28.dp)
                .fillMaxHeight()
                .dragWith(dragTracker),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_drag),
                contentDescription = stringResource(R.string.overlay_drag_handle),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
        HeaderButton(R.drawable.ic_add, stringResource(R.string.action_add_tab), onAddTab)
        HeaderButton(
            icon = if (showHistory) R.drawable.ic_dialpad else R.drawable.ic_history,
            description = stringResource(if (showHistory) R.string.overlay_show_keypad else R.string.overlay_show_history),
            onClick = onToggleHistory,
        )
        HeaderButton(R.drawable.ic_paste, stringResource(R.string.action_paste), actions::onPaste)
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .dragWith(dragTracker),
        )
        HeaderButton(R.drawable.ic_minimize, stringResource(R.string.overlay_minimize), actions::onMinimize)
        HeaderButton(R.drawable.ic_open_app, stringResource(R.string.overlay_open_app), actions::onOpenApp)
        HeaderButton(R.drawable.ic_close, stringResource(R.string.action_close), actions::onClose)
    }
}

@Composable
private fun HeaderButton(icon: Int, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(30.dp)) {
        Icon(painterResource(icon), contentDescription = description, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun TabActionsRow(onRename: () -> Unit, onDuplicate: () -> Unit, onClose: () -> Unit, onDismiss: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        CompactTextButton(stringResource(R.string.action_rename_tab), onRename)
        CompactTextButton(stringResource(R.string.action_duplicate_tab), onDuplicate)
        CompactTextButton(stringResource(R.string.action_close_tab), onClose)
        Box(Modifier.weight(1f))
        IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
            Icon(painterResource(R.drawable.ic_close), stringResource(R.string.action_cancel), Modifier.size(18.dp))
        }
    }
}

@Composable
private fun CompactTextButton(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 8.dp)) {
        Text(text, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

/**
 * Inline rename field. While it is shown the panel window is made focusable so the keyboard can
 * type into it; the field takes focus once the window has it.
 */
@Composable
private fun RenameRow(
    initialName: String,
    focused: Boolean,
    onConfirm: (String) -> Unit,
    onCancel: () -> Unit,
    actions: PanelActions,
) {
    var value by rememberRenameFieldState(initialName)
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val renameLabel = stringResource(R.string.tab_name)
    val latestActions by rememberUpdatedState(actions)
    DisposableEffect(Unit) {
        latestActions.onTextInput(true)
        onDispose { latestActions.onTextInput(false) }
    }
    LaunchedEffect(focused) {
        if (focused) {
            focusRequester.requestFocus()
            keyboard?.show()
        }
    }
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = value,
            onValueChange = { value = it.copy(text = it.text.take(TabsState.MAX_NAME_LENGTH)) },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.onSurface),
            cursorBrush = SolidColor(colors.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onConfirm(value.text) }),
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester)
                .semantics { contentDescription = renameLabel }
                .background(colors.surfaceContainerHighest, RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp),
        )
        IconButton(onClick = { onConfirm(value.text) }, modifier = Modifier.size(36.dp)) {
            Icon(painterResource(R.drawable.ic_check), stringResource(R.string.action_save), Modifier.size(20.dp))
        }
        IconButton(onClick = onCancel, modifier = Modifier.size(36.dp)) {
            Icon(painterResource(R.drawable.ic_close), stringResource(R.string.action_cancel), Modifier.size(20.dp))
        }
    }
}

@Composable
private fun MessageBanner(message: OverlayMessage?, modifier: Modifier = Modifier) {
    // Crossfade keeps the outgoing message on screen while it fades out.
    Crossfade(targetState = message, modifier = modifier, label = "message") { current ->
        if (current == null) return@Crossfade
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            modifier = Modifier.padding(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 12.dp, end = 4.dp)) {
                Text(
                    text = current.text,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .padding(vertical = 10.dp),
                )
                if (current.actionLabel != null && current.action != null) {
                    TextButton(onClick = current.action) {
                        Text(current.actionLabel, color = MaterialTheme.colorScheme.inversePrimary)
                    }
                } else {
                    Box(Modifier.size(8.dp))
                }
            }
        }
    }
}

/** The minimized bubble: drag it around, tap to expand. */
@Composable
fun OverlayBubble(container: AppContainer, drag: DragCallbacks) {
    OverlayTheme(container) {
        val tracker = rememberDragTracker(drag)
        val description = stringResource(R.string.overlay_bubble_description)
        val expandLabel = stringResource(R.string.overlay_expand)
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .fillMaxSize()
                .semantics {
                    contentDescription = description
                    role = Role.Button
                    onClick(label = expandLabel) {
                        drag.onTap()
                        true
                    }
                }
                .dragWith(tracker),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_calculate), contentDescription = null, modifier = Modifier.size(28.dp))
            }
        }
    }
}

/** Drop target at the bottom of the screen; dropping the bubble here closes the calculator. */
@Composable
fun DismissTarget(container: AppContainer, ui: OverlayUiState) {
    OverlayTheme(container) {
        val active = ui.bubbleOverDismissTarget
        val scale by animateFloatAsState(if (active) 1.25f else 1f, label = "dismissScale")
        val colors = MaterialTheme.colorScheme
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Surface(
                shape = CircleShape,
                color = if (active) colors.error else colors.inverseSurface,
                contentColor = if (active) colors.onError else colors.inverseOnSurface,
                modifier = Modifier
                    .size(56.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painterResource(R.drawable.ic_close),
                        contentDescription = stringResource(R.string.overlay_dismiss_target),
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
        }
    }
}
