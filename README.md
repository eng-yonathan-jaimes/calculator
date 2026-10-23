# FloatCalc

A native Android calculator with tabs and a floating window that stays on top of other apps, so
you can calculate (and copy/paste numbers) while using any app.

- **Package:** `com.floatcalc.app` (debug build: `com.floatcalc.app.debug`, installs side by side)
- **Android:** 8.0+ (minSdk 26), targets Android 17 (API 37)
- **No internet permission, no analytics, no ads.**

## Features

**Calculator**
- `+ − × ÷`, parentheses, implicit multiplication (`2(3+4)`), unary minus, decimals and `%`.
- Exact decimal math (`BigDecimal`, 34 significant digits): `0.1 + 0.2 = 0.3`, `1 ÷ 3 × 3 = 1`.
- Percent: `a + b%` = a + a·b/100, `a − b%` = a − a·b/100, `a × b%` = a·b/100, `a ÷ b%` = a ÷ (b/100), `b%` = b/100.
- Live result while typing, gentle errors ("Can't divide by 0", "Incomplete expression"), never crashes.
- Numbers use your locale's grouping and decimal separators; the `.` key shows your decimal separator.
- One smart `( )` key; backspace is the bottom-left key (long-press clears everything).
- **Caret editing:** tap anywhere in the expression to place a blinking caret, then type, paste or
  backspace there to fix a single digit. After `=`, tap the small calculation above the result to
  bring it back for editing.
- Tap the result to copy it, long-press the expression to copy the expression.

**Tabs**
- Up to 20 independent calculations ("Calc 1", "Calc 2", …), each with its own expression, result and
  history (last 100 entries).
- Tap a tab to switch, `+` to add, swipe the display left/right to change tabs.
- Long-press a tab: Rename, Duplicate, Close (with Undo).
- History (bottom sheet): tap an entry to reuse its result, long-press to copy, Clear per tab.
- Everything is saved and survives restarts.

**Floating calculator**
- A compact calculator panel (250×260 dp by default) above every app, with the same tabs, display
  and history, and a 5×4 keypad.
- Drag it by the header; minimize it to a bubble that snaps to the screen edge; drag the bubble onto
  the ✕ at the bottom to close it.
- Paste numbers from other apps (📋 in the header); tap the result to copy it back.
- Opens from the **Float** button in the app, the **FloatCalc** Quick Settings tile, the
  **Floating calculator** launcher shortcut (long-press the app icon), or the notification.
- Ongoing notification with **Hide/Show** and **Close**.
- Sizes S/M/L and opacity 50–100% in Settings. Position, size and state are remembered.

## Install on your phone

The APKs are in `dist/`:

| File | What |
|---|---|
| `dist/FloatCalc-1.0-release.apk` | Release build (R8-minified, signed with your local key). Use this one. |
| `dist/FloatCalc-1.0-debug.apk` | Debug build (`com.floatcalc.app.debug`, named "FloatCalc Debug"). |

**With a USB cable** (USB debugging on): `adb install -r dist/FloatCalc-1.0-release.apk`

**Without a computer:** copy the APK to the phone, open it with Files, and allow
"Install unknown apps" for that app when Android asks.

### Allow "Display over other apps"

The floating window needs this permission. Tap **Float** in the app and follow the dialog
(**Open settings** → turn on FloatCalc), or go to *Settings → Apps → FloatCalc → Display over other
apps*.

**If the switch is greyed out ("Restricted setting")**, which is common for apps installed from an APK
on Android 13–15 and later:
1. Open *Settings → Apps → FloatCalc* (the dialog has an **App info** button).
2. Tap **⋮** (top right) → **Allow restricted settings** and confirm.
3. Go back to *Display over other apps* and turn it on.

The app re-checks the permission when you return to it (some phones report the change a moment late),
then opens the floating window by itself.

Android 13+ also asks for notification permission. It's optional: without it the floating window
still works, you just don't get the Hide/Show and Close notification.

### Add the Quick Settings tile

- **Android 13+:** *Settings → Quick Settings tile → Add*, and confirm.
- **Older versions:** pull down Quick Settings, tap the ✎ (edit) button, and drag **FloatCalc** into
  your tiles.

Tapping the tile turns the floating calculator on and off. If the overlay permission is missing, it
opens the app's permission screen.

## Build from source

Requirements: JDK 17+, Android SDK with **Platform 37** (`platforms;android-37.0`) and
**Build-Tools 37.0.0**. `ANDROID_HOME` (or `local.properties` with `sdk.dir=…`) must point to the SDK.

```sh
./gradlew testDebugUnitTest lint   # unit tests and lint
./gradlew assembleDebug            # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease          # app/build/outputs/apk/release/app-release.apk (needs keystore.properties)
./gradlew distApks                 # copies both into dist/ as FloatCalc-1.0-{debug,release}.apk
```

Toolchain (all stable, resolved and built here): AGP 9.4.1 (built-in Kotlin), Kotlin 2.4.21,
Gradle 9.8.2, Compose BOM 2026.09.00 (Compose 1.12.1, Material 3 1.4.0), AndroidX Activity 1.13.0,
Lifecycle 2.11.0, DataStore 1.3.0, kotlinx.serialization 1.12.0, coroutines 1.11.0.

### Release signing — back up your keystore

Release builds are signed with `floatcalc-release.jks` in the project root. Its passwords are in
`keystore.properties`. Both files are gitignored:

```properties
storeFile=floatcalc-release.jks
storePassword=…
keyAlias=floatcalc
keyPassword=…
```

> **Back up `floatcalc-release.jks` and `keystore.properties` somewhere safe (e.g. a password
> manager).** Android only installs an update over an existing app if it is signed with the same key.
> If you lose them, you'll have to uninstall FloatCalc (losing its data) before installing a newer
> build.

Without `keystore.properties`, `assembleRelease` still builds, but the APK is unsigned.

## How it's built

```
app/src/main/java/com/floatcalc/app/
  engine/      Pure Kotlin, no Android imports: tokenizer, recursive-descent parser, BigDecimal
               evaluator, number formatting, keypad input rules, paste parsing
  data/        Tab model + pure tab operations, repositories (StateFlow + DataStore/JSON)
  ui/          Compose UI: calculator, tab strip, history, settings, permission dialog, theme
  overlay/     Floating window: foreground service, overlay windows, Compose owner, panel/bubble UI,
               trampoline and clipboard-reader activities, notification
  tile/        Quick Settings tile
```

- Single activity, Jetpack Compose with Material 3 (dynamic color on Android 12+).
- `AppContainer` (created in `FloatCalcApp`) holds the singletons. `TabsRepository` is the single
  source of truth for the app and the floating window, so both always show the same tabs.
- State is saved as JSON in DataStore (`tabs.json`, `settings.json`), 400 ms after the last change,
  and right away when the app or the floating window goes away.
- The floating window is a `specialUse` foreground service that adds `TYPE_APPLICATION_OVERLAY`
  windows with `FLAG_NOT_FOCUSABLE`, so the app underneath keeps its keyboard, back button and
  every touch outside the panel. Compose runs in those windows through `OverlayViewOwner`
  (LifecycleOwner + SavedStateRegistryOwner + ViewModelStoreOwner), RESUMED while shown and
  DESTROYED when removed.

## Decisions and deviations

Where the spec left room, or Android doesn't allow something exactly as written, this is what the app
does:

**Calculator behavior**
- Missing closing parentheses are closed automatically: `2×(3+4` previews and evaluates to 14.
- Implicit multiplication has the same precedence as `×` (left to right): `6÷2(1+2)` = 9.
- `a + (b%)` follows the same percent rule as `a + b%`; a percent that is part of a product
  (`200 + 10% × 2`) is just b/100.
- Results are shown rounded to 16 significant digits. Scientific notation (`1.23456789E19`) is used
  for 10¹⁶ and above and below 10⁻⁹; plain numbers are used otherwise.
- After `=`, the shown (rounded) result keeps its exact value behind the scenes, so chained
  calculations stay exact (`1 ÷ 3 = × 3 =` gives 1). Editing the result's digits drops that.
- After `=`: an operator or `%` continues from the result; a digit, `.` or `( )` starts fresh;
  backspace clears it (like the Pixel calculator's CLR).
- Keypads: main app `AC ( ) % ÷` / `7 8 9 ×` / `4 5 6 −` / `1 2 3 +` / `⌫ 0 . =`. There is no `±` key
  (at your request): type `−` before a number, or place the caret and add/remove it. The engine still
  supports sign toggling (tested) in case a key is wanted again.
- Caret: the input rules apply to the text before the caret as if you were typing at the end, and
  the text after it is kept (e.g. `12|34` + `.` → `12.34`; a second point in the same number is
  refused even if it is after the caret). A tap on the result after `=` still copies it, so the
  previous calculation (the small line above) is what you tap to edit. The caret is shown only when
  it is not at the end; `=` evaluates the whole expression and removes it.
- A digit or `.` typed right after `)` or `%` inserts `×` (`(2)×3`).
- The leading zero is replaced (`0` then `5` → `5`).
- Copied results are plain numbers: no grouping, your locale's decimal separator (what local apps
  expect), and an ASCII `-` for negatives. Copying an expression copies it as displayed.
- Pasting/reusing a value: it replaces a finished result or an empty display, follows an operator
  or `(` directly, and is multiplied in after a number (`5` + paste `12` → `5×12`). Pasted
  expressions and negative numbers are wrapped in parentheses.
- Paste accepts plain numbers (spaces, currency symbols and grouping separators removed; `1,234.56`,
  `1.234,56`, `1 234,56 €`, `1'234`) and simple expressions (`12*3+4`, `10/4`, `2x3`). An ambiguous
  single separator is resolved with your locale (`1,234` is 1234 in English and 1.234 in German).

**Tabs**
- New tabs get the lowest free number ("Calc 2" is reused after closing it). A duplicate gets a new
  default name and is placed right after its source.
- Undo puts a closed tab back where it was. If it was the last tab, the empty replacement tab is
  removed again (if you haven't used it).

**Floating window**
- Compact layout: a header (⠿ drag handle, + new tab, History/Keypad toggle, Paste, then Minimize,
  Open app, Close; the empty middle of the header also drags), the tab chips stacked vertically to the
  left of the display, and a 5×4 keypad `7 8 9 % ( )` / `4 5 6 − ÷` / `1 2 3 + ×` / `⌫ 0 . = AC` with
  backspace as a key (long-press clears). The live result is bold. Caret editing works here too.
- Long-pressing a tab in the floating window shows an inline Rename / Duplicate / Close row instead
  of a popup menu: popups and dialogs need input focus, which the non-focusable overlay must not
  take. Close shows an Undo message inside the panel.
- Renaming in the floating window temporarily makes the panel focusable (and moves it to the top
  if it is in the lower half, so the keyboard doesn't cover it). Back, ✓, ✕ or a touch outside the
  panel ends it and restores `FLAG_NOT_FOCUSABLE`.
- Paste: on Android 10+ only the focused app may read the clipboard, so the panel briefly clears
  `FLAG_NOT_FOCUSABLE`, waits for window focus, reads, and restores the flag. If the device doesn't
  grant focus within 0.7 s, a transparent activity reads the clipboard instead. Android 12+ shows its
  own "FloatCalc pasted from your clipboard" message; that can't be turned off.
- Size presets are S 220×230, M 250×260 (default), L 300×312 dp. If the screen is smaller (e.g.
  landscape), the panel shrinks to fit. To stay compact, keys are about 44×33 dp at M and the header
  buttons 30 dp (Compose still widens small touch targets); the main app keeps 48 dp touch targets.
- The floating window hides whenever the FloatCalc app itself is on screen (including split screen,
  where both would show the same tabs) and reappears 0.25 s after you leave it, so rotating the app
  doesn't make it flicker. The transparent helper activities don't count as "the app".
- The dismiss target is a separate pass-through window at 80% opacity: Android 12+ blocks touches
  through overlay windows that are more opaque than that. It exists only while you drag the bubble.
- "Open app" uses a PendingIntent with `MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE` on
  Android 16+ (the overlay is visible); older versions start the activity directly, which the
  overlay permission allows.
- If the overlay permission is revoked while running, the service notices (AppOps watcher and
  WindowManager errors), removes its windows and stops.
- Tapping Close in the panel, the notification's Close action, the tile, or dropping the bubble on
  the ✕ all stop the service and remove the notification.

**Other**
- The theme setting is also applied app-wide on Android 12+ (`UiModeManager.setApplicationNightMode`)
  so the window background before the first frame matches.
- Gradle's local build cache is turned off: a stale cache entry restored an outdated resource
  merge during development. Builds are a little slower but deterministic.
- The build prints a Gradle deprecation warning (`Configuration.setVisible`). It comes from the
  Android/Kotlin Gradle plugins, not from this project.

## Known limits

- **System screens:** Android hides third-party overlays on some system screens (permission dialogs,
  the overlay permission screen itself, parts of Settings, the lock screen).
- **Apps that block taps under overlays:** some apps, often banking and payment apps, ignore taps
  while another app's window is on screen. Minimize the calculator to the bubble, move it away, or
  hide it from the notification while you tap there.
- **Xiaomi/HyperOS, OPPO/ColorOS, realme, vivo and similar skins** may need extra permissions: enable
  "Display pop-up windows while running in the background" / "Floating windows", allow autostart, and
  set battery usage to "No restrictions" for FloatCalc, or the system may hide or kill the floating
  window.
- On Android 12+ the system shows its own toast whenever an app reads the clipboard.
- When renaming a tab in the floating window, the keyboard can still cover part of the panel on very
  small screens.

## Tests

`./gradlew testDebugUnitTest` runs 90 JVM unit tests:

- `CalculatorTest`: precedence, parentheses (incl. auto-close), implicit multiplication, unary minus,
  every percent rule, decimal precision, divide by zero, incomplete and malformed input, exponents,
  deep nesting.
- `FormattingTest`: trailing zeros, rounding, scientific notation, locale separators (incl. Indian
  grouping), expression formatting, caret position mapping, clipboard form.
- `CalcEditorTest`: input rules: live preview, `=` and gentle errors, behavior after `=`, exact
  chaining, operator replacement, one decimal point per number, leading zero, smart parentheses,
  `±`, `%`, backspace/clear, paste insertion, editing at the caret, editing the previous calculation.
- `PasteParserTest`: numbers, grouping/currency stripping, locale-ambiguous separators, simple
  expressions, rejecting unusable text.
- `TabOpsTest` / `TabsRepositoryTest`: add, close (incl. last tab), undo, rename, duplicate, 20-tab
  limit, history cap, sanitizing stored data, debounced persistence and flush.
