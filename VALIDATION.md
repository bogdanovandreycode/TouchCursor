# Touch Cursor 0.2.0 validation

Validated on 2026-10-17 using Windows, Android Studio's JDK 21.0.8, AGP 9.4.0, Gradle 9.6.0 and Android SDK 36.

## Final results

| Command/check | Result |
| --- | --- |
| `.\gradlew.bat assembleDebug` | Passed; signed debug APK generated |
| `.\gradlew.bat assembleDebugAndroidTest` | Passed; separate test APK generated |
| `.\gradlew.bat testDebugUnitTest` | Passed: 14 tests, 0 failures |
| `.\gradlew.bat lint` | Passed: 0 errors, 3 version-update warnings |
| `.\gradlew.bat connectedDebugAndroidTest` | Passed: 6 tests, 0 failures on Android 7.0 / API 24 x86 emulator |
| Final `.\gradlew.bat connectedDebugAndroidTest assembleDebug testDebugUnitTest lint` | Passed after the edge-swipe/exclusion lifecycle review |
| `apksigner verify --verbose` | Passed; APK Signature Scheme v2 |
| APK metadata/permission inspection | `com.example.touchcursor`, version 0.2.0, minSdk 24, targetSdk 36; no requested permissions |

The three lint warnings suggest newer Gradle/compileSdk/targetSdk versions. The specification's Gradle 9.6.0 and SDK 36 versions were deliberately retained. No lint baseline or broad suppression was introduced.

## Runtime evidence

The integration fixture is a separate app in the test APK. Its touch counters and received screen coordinates verify that injected gestures reach an underlying application, not Touch Cursor's input surface.

1. Normal touch increments the underlying button's counter. Entering cursor mode and moving a finger moves the arrow relatively without incrementing the counter. Double-tap generates one click at the intended cursor coordinate, checked within 2 px; finger coordinates are different. A stationary hold generates a long press only after release. Exiting cursor mode restores ordinary touch.
2. Independently configured button sizes/positions survive disabling and re-enabling the service. A saved 55% opacity is verified on the actual WindowManager window after restart. All enabled controls remain within usable display bounds after rotation.
3. Unsupported copy fails safely. Selecting `alpha` in the fixture, copying, clearing its focused editable field, and pasting restores `alpha`. KEYS hides a visible IME and can return its policy to automatic without terminating the service.
4. Disabling the service during an injected long press removes every overlay. A later gesture callback does not recreate windows.
5. Inward swipes on all four edges reveal controls. A tap on a collapsed handle does not reveal them. Tapping the expanded handle exits active cursor mode and hides the controls; touch elsewhere reaches the fixture normally. Edge side, normalized position and trigger dimensions persist through service restart and stay valid after rotation.
6. Excluding the foreground fixture removes all seven possible overlay windows, including the non-touchable input probe and edge handle. The fixture receives direct touches. Opening Touch Cursor settings restores normal-mode controls even if its package was supplied programmatically in the blacklist. Returning to the excluded fixture hides them again. Restarting the service inside that app does not show overlays; changing the exclusion list restores the collapsed handle without a service restart.

The tests exposed a mode-transition race involving a reused input channel. Mode changes now remove and recreate that channel instead of only updating its touchability flags. The complete integration suite passed after this change.

Additional UI checks on the emulator verified:

- MainActivity launches and displays service status/instructions/settings.
- The Copy configuration dialog exposes visibility, position, independent width/height and opacity.
- Saving through the UI persists visibility and a changed width in SharedPreferences.
- The Accessibility settings button opens Android's Accessibility screen.
- Touch Cursor Service is listed and can be enabled through Android's actual switch/confirmation dialog.
- The floating controls and visible cursor appear over the Android launcher.
- Each floating control and its configuration entry has a native vector icon. Opacity affects the complete floating window, including its icon, label and background; the setting is independent for each button.
- The Edge swipe dialog saves its enabled state, selected screen side and trigger position/dimensions.
- The searchable app picker lists installed launcher apps, filters by name/package, and saves checked package IDs. Settings was selected through the actual UI to verify picker persistence.

Reports: `app/build/reports/tests/testDebugUnitTest/index.html`, `app/build/reports/lint-results-debug.html`, `app/build/reports/androidTests/connected/debug/index.html`. Visual evidence under `app/build/reports/runtime`: `cursor-mode-api24.png`, `edge-settings-api24.png`, `edge-collapsed-api24.png`, `app-exclusions-api24.png`.

## Delivery and remaining limits

Install `app/build/outputs/apk/debug/app-debug.apk`, then manually enable Touch Cursor Service in Accessibility settings. The APK is debug-signed and requires Android 7.0 or newer.

Validation used an API 24 emulator, not a physical phone. Newer Android versions, OEM-specific accessibility/IME behavior, cutout devices and other accessibility services still need device testing. Showing an IME remains app-controlled/best effort; injected input is touch rather than hardware mouse input. See README for the precise long-press and temporary pass-through semantics.

ADB/shell access is used only by development tooling and the instrumentation harness. The delivered app contains no runtime shell execution, root, Shizuku, hidden APIs, Internet functionality or SYSTEM_ALERT_WINDOW request.

## Changed files

- Implemented all existing Kotlin skeletons: `MainActivity.kt`; `service/CursorAccessibilityService.kt`; `overlay/OverlayManager.kt`, `CursorOverlayView.kt`, `TouchCaptureOverlayView.kt`; `input/CursorController.kt`, `GestureInjector.kt`, `TouchpadGestureDetector.kt`; `actions/TextActionController.kt`, `KeyboardController.kt`; `settings/SettingsRepository.kt`; `model/OverlayButtonConfig.kt`.
- Updated `app/src/main/AndroidManifest.xml`, `res/xml/accessibility_service_config.xml`, `res/values/strings.xml`; added the launcher/cursor, mode, copy, paste and keyboard vector icons under `res/drawable` and local-only backup rules in `res/xml/data_extraction_rules.xml`.
- Added standard `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`; updated `app/build.gradle.kts` for test dependencies/runner. Created untracked, machine-specific `local.properties`.
- Added two JUnit test files under `app/src/test`, integration tests and a standalone Java fixture under `app/src/androidTest`, plus the test manifest.
- Updated `README.md` and added this validation record. The original specification and AGENTS.md were preserved.
- Version 0.2.0 adds `model/EdgeRevealConfig.kt`, `input/EdgeSwipeDetector.kt`, `overlay/EdgeHandleView.kt`, `service/ForegroundAppTracker.kt`, `settings/AppExclusionsDialog.kt` and `input/EdgeSwipeDetectorTest.kt`. It updates settings persistence, the overlay/service lifecycle, MainActivity, accessibility event configuration, launcher package visibility and integration tests. COPY/PASTE refresh their focused nodes before inspecting supported actions.

This directory has no Git repository. The source changes were reviewed against a copy of the original skeleton using `git diff --no-index`; no repository was initialized and no commit was created.
