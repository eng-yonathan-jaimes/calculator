package com.floatcalc.app.overlay

import android.animation.ValueAnimator
import android.app.ActivityOptions
import android.app.AppOpsManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.Display
import android.view.HapticFeedbackConstants
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import androidx.core.animation.doOnEnd
import androidx.core.app.ServiceCompat
import com.floatcalc.app.AppContainer
import com.floatcalc.app.AppVisibility
import com.floatcalc.app.MainActivity
import com.floatcalc.app.R
import com.floatcalc.app.appContainer
import com.floatcalc.app.data.AppSettings
import com.floatcalc.app.data.OverlayLayout
import com.floatcalc.app.engine.DisplayFormatter
import com.floatcalc.app.engine.PasteParser
import com.floatcalc.app.util.Clipboard
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Foreground service that owns the floating calculator windows: the expanded panel, the
 * minimized bubble and the dismiss target shown while the bubble is dragged.
 */
class FloatingCalculatorService : Service() {

    private lateinit var container: AppContainer
    private lateinit var overlayContext: Context
    private lateinit var windowManager: WindowManager
    private lateinit var geometry: OverlayGeometry

    private val scope = MainScope()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val ui = OverlayUiState()

    private var panel: OverlayWindow? = null
    private var bubble: OverlayWindow? = null
    private var dismissTarget: OverlayWindow? = null

    private val userHidden = MutableStateFlow(false)
    private val minimized = MutableStateFlow(false)
    private var initialized = false
    private var openRequestedBeforeInit = false
    private var destroyed = false

    private var dragStartX = 0
    private var dragStartY = 0
    private var snapAnimator: ValueAnimator? = null
    private var messageJob: Job? = null

    private enum class FocusReason { PASTE, TEXT_INPUT }

    private val focusReasons = mutableSetOf<FocusReason>()
    private var pendingPaste = false
    private var pasteTimeoutJob: Job? = null
    private var yBeforeTextInput: Int? = null

    private var appOps: AppOpsManager? = null
    private val opChangedListener = AppOpsManager.OnOpChangedListener { _, packageName ->
        if (packageName == this.packageName) {
            // Some devices report the new value late, so check again after a moment.
            mainHandler.postDelayed({ if (!Settings.canDrawOverlays(this)) stopForPermissionLoss() }, 500)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        container = appContainer
        overlayContext = createOverlayContext()
        windowManager = overlayContext.getSystemService(WindowManager::class.java)
        geometry = OverlayGeometry.of(overlayContext)

        if (!startInForeground()) return
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        FloatingCalculator.setRunning(true)
        appOps = getSystemService(AppOpsManager::class.java)?.also {
            it.startWatchingMode(AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW, packageName, opChangedListener)
        }

        scope.launch {
            container.settings.loaded.first { it }
            container.tabs.loaded.first { it }
            minimized.value = !openRequestedBeforeInit && container.settings.settings.value.overlayLayout.minimized
            initialized = true
            var hiddenForMainActivity = false
            combine(AppVisibility.mainActivityVisible, userHidden, minimized) { mainVisible, hidden, min ->
                Triple(mainVisible, hidden, min)
            }.collectLatest { (mainVisible, hidden, min) ->
                // Coming back from the app: wait a moment so a configuration change or the
                // close animation doesn't make the window flicker.
                if (!mainVisible && hiddenForMainActivity) delay(SHOW_AFTER_APP_DELAY_MS)
                hiddenForMainActivity = mainVisible
                render(show = !mainVisible && !hidden, minimized = min)
            }
        }
        scope.launch {
            container.settings.settings
                .map { it.overlaySize to it.overlayOpacity }
                .distinctUntilChanged()
                .drop(1)
                .collect { applySizeAndOpacity(container.settings.settings.value) }
        }
        scope.launch { FloatingCalculator.clipboardTexts.collect { handlePastedText(it) } }
        scope.launch { userHidden.drop(1).collect { OverlayNotifications.update(this@FloatingCalculatorService, it) } }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            FloatingCalculator.ACTION_OPEN -> {
                userHidden.value = false
                if (!initialized) openRequestedBeforeInit = true else if (minimized.value) expand()
            }
            FloatingCalculator.ACTION_SHOW -> userHidden.value = false
            FloatingCalculator.ACTION_HIDE -> userHidden.value = true
            FloatingCalculator.ACTION_CLOSE -> stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Let the window metrics settle before re-measuring.
        mainHandler.post { onScreenChanged() }
    }

    override fun onDestroy() {
        destroyed = true
        snapAnimator?.cancel()
        mainHandler.removeCallbacksAndMessages(null)
        appOps?.stopWatchingMode(opChangedListener)
        panel?.hide()
        bubble?.hide()
        dismissTarget?.hide()
        scope.cancel()
        FloatingCalculator.setRunning(false)
        if (::container.isInitialized) container.flush()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    // region Setup

    /** On Android 11+ overlay windows should come from a window context, not the Service itself. */
    private fun createOverlayContext(): Context {
        val base = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val display = getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
            createDisplayContext(display).createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
        } else {
            this
        }
        return ContextThemeWrapper(base, R.style.Theme_FloatCalc)
    }

    private fun startInForeground(): Boolean {
        OverlayNotifications.ensureChannel(this)
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        return try {
            ServiceCompat.startForeground(
                this,
                OverlayNotifications.NOTIFICATION_ID,
                OverlayNotifications.build(this, hidden = false),
                type,
            )
            true
        } catch (e: RuntimeException) {
            // E.g. ForegroundServiceStartNotAllowedException if started from the background.
            Log.w(TAG, "Could not start in the foreground", e)
            stopSelf()
            false
        }
    }

    // endregion

    // region Windows

    private fun render(show: Boolean, minimized: Boolean) {
        if (destroyed) return
        if (!show) {
            hideAllWindows()
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            stopForPermissionLoss()
            return
        }
        if (minimized) {
            clearFocusReasons()
            panel?.hide()
            bubbleWindow().show()
        } else {
            bubble?.hide()
            hideDismissTarget()
            panelWindow().show()
        }
    }

    private fun hideAllWindows() {
        snapAnimator?.cancel()
        clearFocusReasons()
        panel?.hide()
        bubble?.hide()
        hideDismissTarget()
    }

    private fun panelWindow(): OverlayWindow = panel ?: createPanel().also { panel = it }

    private fun bubbleWindow(): OverlayWindow = bubble ?: createBubble().also { bubble = it }

    private fun panelSize(settings: AppSettings = container.settings.settings.value): Pair<Int, Int> =
        geometry.fitSize(geometry.dp(settings.overlaySize.widthDp), geometry.dp(settings.overlaySize.heightDp))

    private fun createPanel(): OverlayWindow {
        val settings = container.settings.settings.value
        val layout = settings.overlayLayout
        val (width, height) = panelSize(settings)
        val x = if (layout.panelX >= 0) geometry.clampX(layout.panelX, width) else geometry.edgeX(onRight = true, width)
        val y = if (layout.panelY >= 0) {
            geometry.clampY(layout.panelY, height)
        } else {
            geometry.clampY(geometry.safe.top + geometry.safe.height() / 6, height)
        }
        val params = OverlayWindow.layoutParams(width, height, x, y).apply {
            alpha = settings.overlayOpacity
            title = "FloatCalc"
        }
        return OverlayWindow(overlayContext, windowManager, params, ::onWindowError, panelRootCallbacks) {
            OverlayPanel(container, ui, panelActions)
        }
    }

    private fun createBubble(): OverlayWindow {
        val settings = container.settings.settings.value
        val layout = settings.overlayLayout
        val size = geometry.dp(BUBBLE_DP)
        val onRight = layout.bubbleX < 0 || geometry.isOnRightHalf(layout.bubbleX, size)
        val y = if (layout.bubbleY >= 0) layout.bubbleY else geometry.safe.top + geometry.safe.height() / 3
        val params = OverlayWindow.layoutParams(size, size, geometry.edgeX(onRight, size), geometry.clampY(y, size))
            .apply {
                alpha = settings.overlayOpacity
                title = "FloatCalc bubble"
            }
        return OverlayWindow(overlayContext, windowManager, params, ::onWindowError) {
            OverlayBubble(container, bubbleDrag)
        }
    }

    private fun showDismissTarget() {
        val size = geometry.dp(DISMISS_TARGET_DP)
        val x = (geometry.safe.left + geometry.safe.right) / 2 - size / 2
        val y = geometry.safe.bottom - size - geometry.dp(DISMISS_TARGET_BOTTOM_MARGIN_DP)
        val target = dismissTarget ?: OverlayWindow(
            overlayContext,
            windowManager,
            OverlayWindow.layoutParams(size, size, x, y).apply {
                flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                // Touches pass through this window, so it must stay at or below the system's
                // maximum obscuring opacity (0.8) or Android 12+ would block them.
                alpha = 0.8f
                title = "FloatCalc dismiss target"
            },
            ::onWindowError,
        ) { DismissTarget(container, ui) }.also { dismissTarget = it }
        target.params.x = x
        target.params.y = y
        if (target.isShown) target.update() else target.show()
    }

    private fun hideDismissTarget() {
        ui.bubbleOverDismissTarget = false
        dismissTarget?.hide()
    }

    private fun dismissTargetCenter(): Pair<Int, Int>? {
        val params = dismissTarget?.takeIf { it.isShown }?.params ?: return null
        return (params.x + params.width / 2) to (params.y + params.height / 2)
    }

    private fun applySizeAndOpacity(settings: AppSettings) {
        panel?.let { window ->
            val (width, height) = panelSize(settings)
            window.params.apply {
                this.width = width
                this.height = height
                x = geometry.clampX(x, width)
                y = geometry.clampY(y, height)
                alpha = settings.overlayOpacity
            }
            window.update()
        }
        bubble?.let { window ->
            window.params.alpha = settings.overlayOpacity
            window.update()
        }
    }

    /** Rotation, resolution or display size changed: keep every window on screen. */
    private fun onScreenChanged() {
        if (destroyed) return
        val old = geometry
        val new = OverlayGeometry.of(overlayContext)
        if (new == old) return
        geometry = new
        snapAnimator?.cancel()
        hideDismissTarget()
        panel?.let { window ->
            val p = window.params
            val onRight = old.isOnRightHalf(p.x, p.width)
            val distanceFromEdge = if (onRight) old.safe.right - (p.x + p.width) else p.x - old.safe.left
            val (width, height) = panelSize()
            p.width = width
            p.height = height
            p.x = new.clampX(if (onRight) new.safe.right - width - distanceFromEdge else new.safe.left + distanceFromEdge, width)
            p.y = new.clampY(p.y, height)
            window.update()
        }
        bubble?.let { window ->
            val p = window.params
            val onRight = old.isOnRightHalf(p.x, p.width)
            p.x = new.edgeX(onRight, p.width)
            p.y = new.clampY(p.y, p.height)
            window.update()
        }
        saveCurrentPositions()
    }

    private fun onWindowError(@Suppress("UNUSED_PARAMETER") error: RuntimeException) {
        if (!Settings.canDrawOverlays(this)) stopForPermissionLoss()
    }

    private fun stopForPermissionLoss() {
        if (destroyed) return
        Log.i(TAG, "Overlay permission revoked; stopping")
        hideAllWindows()
        stopSelf()
    }

    // endregion

    // region Expand / minimize / drag

    private fun minimize() {
        val p = panel?.params
        if (p != null) {
            val size = geometry.dp(BUBBLE_DP)
            val onRight = geometry.isOnRightHalf(p.x, p.width)
            val bubbleX = geometry.edgeX(onRight, size)
            val bubbleY = geometry.clampY(p.y, size)
            bubble?.params?.apply {
                x = bubbleX
                y = bubbleY
            }
            saveLayout { it.copy(panelX = p.x, panelY = p.y, bubbleX = bubbleX, bubbleY = bubbleY, minimized = true) }
        } else {
            saveLayout { it.copy(minimized = true) }
        }
        minimized.value = true
    }

    private fun expand() {
        val b = bubble?.params
        if (b != null) {
            val (width, height) = panelSize()
            val onRight = geometry.isOnRightHalf(b.x, b.width)
            val panelX = geometry.edgeX(onRight, width)
            val panelY = geometry.clampY(b.y, height)
            panel?.params?.apply {
                x = panelX
                y = panelY
            }
            saveLayout { it.copy(panelX = panelX, panelY = panelY, minimized = false) }
        } else {
            saveLayout { it.copy(minimized = false) }
        }
        minimized.value = false
    }

    private val panelDrag = object : DragCallbacks {
        override fun onDragStart() {
            val p = panel?.params ?: return
            dragStartX = p.x
            dragStartY = p.y
            yBeforeTextInput = null
        }

        override fun onDrag(dx: Float, dy: Float) {
            val window = panel ?: return
            val p = window.params
            p.x = geometry.clampX(dragStartX + dx.roundToInt(), p.width)
            p.y = geometry.clampY(dragStartY + dy.roundToInt(), p.height)
            window.update()
        }

        override fun onDragEnd() = saveCurrentPositions()
    }

    private val bubbleDrag = object : DragCallbacks {
        override fun onDragStart() {
            snapAnimator?.cancel()
            val p = bubble?.params ?: return
            dragStartX = p.x
            dragStartY = p.y
            showDismissTarget()
        }

        override fun onDrag(dx: Float, dy: Float) {
            val window = bubble ?: return
            val p = window.params
            var x = geometry.clampX(dragStartX + dx.roundToInt(), p.width)
            var y = geometry.clampY(dragStartY + dy.roundToInt(), p.height)
            val center = dismissTargetCenter()
            val over = center != null &&
                hypot((x + p.width / 2 - center.first).toFloat(), (y + p.height / 2 - center.second).toFloat()) <
                geometry.dp(DISMISS_RADIUS_DP)
            if (over) {
                // Pull the bubble onto the target so it is clear that letting go will close it.
                x = center.first - p.width / 2
                y = center.second - p.height / 2
            }
            if (over != ui.bubbleOverDismissTarget) {
                ui.bubbleOverDismissTarget = over
                if (over && container.settings.settings.value.haptics) {
                    window.view?.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                }
            }
            p.x = x
            p.y = y
            window.update()
        }

        override fun onDragEnd() {
            val close = ui.bubbleOverDismissTarget
            hideDismissTarget()
            if (close) stopSelf() else snapBubbleToEdge()
        }

        override fun onTap() = expand()
    }

    private fun snapBubbleToEdge() {
        val window = bubble ?: return
        val p = window.params
        val startX = p.x
        val startY = p.y
        val endX = geometry.edgeX(geometry.isOnRightHalf(p.x, p.width), p.width)
        val endY = geometry.clampY(p.y, p.height)
        snapAnimator?.cancel()
        snapAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = SNAP_DURATION_MS
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                val f = it.animatedValue as Float
                p.x = (startX + (endX - startX) * f).roundToInt()
                p.y = (startY + (endY - startY) * f).roundToInt()
                window.update()
            }
            doOnEnd { saveCurrentPositions() }
            start()
        }
    }

    private fun saveCurrentPositions() {
        val p = panel?.params
        val b = bubble?.params
        saveLayout {
            it.copy(
                panelX = p?.x ?: it.panelX,
                panelY = yBeforeTextInput ?: p?.y ?: it.panelY,
                bubbleX = b?.x ?: it.bubbleX,
                bubbleY = b?.y ?: it.bubbleY,
            )
        }
    }

    private fun saveLayout(transform: (OverlayLayout) -> OverlayLayout) {
        container.settings.update { it.copy(overlayLayout = transform(it.overlayLayout)) }
    }

    // endregion

    // region Focus, paste, rename

    private val panelRootCallbacks = object : OverlayRootView.Callbacks {
        override fun onWindowFocusChanged(hasFocus: Boolean) {
            ui.panelFocused = hasFocus
            if (hasFocus && pendingPaste) {
                pendingPaste = false
                pasteTimeoutJob?.cancel()
                scope.launch { readClipboardWithFocus() }
            }
        }

        override fun onBackPressed(): Boolean {
            if (FocusReason.TEXT_INPUT !in focusReasons) return false
            ui.textInputCancelSignal++
            return true
        }

        override fun onOutsideTouch() {
            // Typing in the panel ends when the user goes back to the other app.
            if (FocusReason.TEXT_INPUT in focusReasons) ui.textInputCancelSignal++
        }
    }

    /**
     * Toggles FLAG_NOT_FOCUSABLE. The panel is normally not focusable so the app underneath keeps
     * its keyboard and back button; it takes focus only to read the clipboard (Android 10+ allows
     * that only for the focused app) or to type a tab name.
     */
    private fun setFocusable(reason: FocusReason, focusable: Boolean) {
        if (focusable) focusReasons += reason else focusReasons -= reason
        val window = panel ?: return
        val p = window.params
        val wantFocus = focusReasons.isNotEmpty()
        val focusFlags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        val newFlags = if (wantFocus) {
            (p.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()) or focusFlags
        } else {
            (p.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) and focusFlags.inv()
        }
        val newSoftInput = if (FocusReason.TEXT_INPUT in focusReasons) {
            WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
        } else {
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
        }
        if (newFlags != p.flags || newSoftInput != p.softInputMode) {
            p.flags = newFlags
            p.softInputMode = newSoftInput
            window.update()
        }
        if (!wantFocus) ui.panelFocused = false
    }

    private fun clearFocusReasons() {
        pendingPaste = false
        pasteTimeoutJob?.cancel()
        FocusReason.entries.forEach { setFocusable(it, false) }
    }

    private fun paste() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || ui.panelFocused) {
            handlePastedText(Clipboard.readText(this))
            return
        }
        pendingPaste = true
        setFocusable(FocusReason.PASTE, true)
        pasteTimeoutJob?.cancel()
        pasteTimeoutJob = scope.launch {
            delay(PASTE_FOCUS_TIMEOUT_MS)
            if (pendingPaste) {
                // This device didn't give the overlay focus: read through a transparent activity.
                pendingPaste = false
                setFocusable(FocusReason.PASTE, false)
                launchActivity(Intent(this@FloatingCalculatorService, ClipboardReaderActivity::class.java))
            }
        }
    }

    private suspend fun readClipboardWithFocus() {
        delay(CLIPBOARD_READ_DELAY_MS)
        var text = Clipboard.readText(this)
        if (text == null) {
            delay(CLIPBOARD_RETRY_DELAY_MS)
            text = Clipboard.readText(this)
        }
        setFocusable(FocusReason.PASTE, false)
        handlePastedText(text)
    }

    private fun handlePastedText(text: String?) {
        val formatter = DisplayFormatter.forLocale(resources.configuration.locales[0])
        val parsed = text?.let { PasteParser.parse(it, formatter.decimalSeparator, formatter.groupingSeparator) }
        when {
            parsed != null -> container.tabs.insert(parsed)
            text.isNullOrBlank() -> showMessage(OverlayMessage(getString(R.string.paste_empty)))
            else -> showMessage(OverlayMessage(getString(R.string.paste_invalid)))
        }
    }

    private fun onTextInput(active: Boolean) {
        val window = panel
        if (active && window != null) {
            // Keep the field above the keyboard: move a panel in the lower half to the top.
            val p = window.params
            if (p.y + p.height / 2 > geometry.safe.centerY()) {
                yBeforeTextInput = p.y
                p.y = geometry.clampY(geometry.safe.top + geometry.dp(8), p.height)
                window.update()
            }
        } else if (!active && window != null) {
            yBeforeTextInput?.let {
                window.params.y = geometry.clampY(it, window.params.height)
                window.update()
            }
            yBeforeTextInput = null
        }
        setFocusable(FocusReason.TEXT_INPUT, active)
    }

    // endregion

    // region Panel actions

    private val panelActions = object : PanelActions {
        override val drag: DragCallbacks = panelDrag
        override fun onMinimize() = minimize()
        override fun onOpenApp() = launchActivity(MainActivity.launchIntent(this@FloatingCalculatorService))
        override fun onClose() = stopSelf()
        override fun onPaste() = paste()
        override fun onTextInput(active: Boolean) = this@FloatingCalculatorService.onTextInput(active)
        override fun showMessage(message: OverlayMessage) = this@FloatingCalculatorService.showMessage(message)

        override fun onCopy(text: String) {
            Clipboard.copy(this@FloatingCalculatorService, text, showToast = false)
            // Android 13+ shows its own confirmation for copies.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                showMessage(OverlayMessage(getString(R.string.copied)))
            }
        }
    }

    private fun showMessage(message: OverlayMessage) {
        val shown = message.action?.let { action ->
            message.copy(action = {
                action()
                ui.message = null
            })
        } ?: message
        ui.message = shown
        messageJob?.cancel()
        messageJob = scope.launch {
            delay(if (message.action != null) MESSAGE_WITH_ACTION_MS else MESSAGE_MS)
            if (ui.message === shown) ui.message = null
        }
    }

    /**
     * Starts an activity from the overlay. On Android 16+ the launch goes through a PendingIntent
     * with MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE (our overlay window is visible); older
     * versions allow it directly because the app holds the overlay permission.
     */
    private fun launchActivity(intent: Intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            val pending = PendingIntent.getActivity(
                this,
                intent.component?.className.hashCode(),
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val options = ActivityOptions.makeBasic().setPendingIntentBackgroundActivityStartMode(
                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE,
            )
            try {
                pending.send(options.toBundle())
                return
            } catch (e: PendingIntent.CanceledException) {
                Log.w(TAG, "PendingIntent launch failed; trying a direct start", e)
            }
        }
        try {
            startActivity(intent)
        } catch (e: RuntimeException) {
            Log.w(TAG, "Could not start activity", e)
        }
    }

    // endregion

    private companion object {
        const val TAG = "FloatingCalculator"
        const val BUBBLE_DP = 56
        const val DISMISS_TARGET_DP = 96
        const val DISMISS_TARGET_BOTTOM_MARGIN_DP = 24
        const val DISMISS_RADIUS_DP = 64
        const val SNAP_DURATION_MS = 220L
        const val SHOW_AFTER_APP_DELAY_MS = 250L
        const val PASTE_FOCUS_TIMEOUT_MS = 700L
        const val CLIPBOARD_READ_DELAY_MS = 50L
        const val CLIPBOARD_RETRY_DELAY_MS = 150L
        const val MESSAGE_MS = 2_500L
        const val MESSAGE_WITH_ACTION_MS = 4_000L
    }
}
