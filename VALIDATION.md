# Touch Cursor 0.3.2 validation

## Independent app/system controls — 2026-10-19

The disabled app mode button now stays hidden in both modes, including periodic overlay recovery. Two-finger exit and the native shortcut remain supported. On Android 11+, the service no longer requests `FLAG_REQUEST_ACCESSIBILITY_BUTTON`: AOSP's [shortcut-managed service settings](https://android.googlesource.com/platform/packages/apps/Settings/+/4230ad4b07fd71060e25df0f7cde141cb1c4c899/src/com/android/settings/accessibility/InvisibleToggleAccessibilityServicePreferenceFragment.java) tie service access to shortcut assignment when that flag is set. The optional **Touch Cursor mode** activity uses the public accessibility shortcut target declaration and finishes without displaying a window. It toggles the connected service with the same screen/exclusion checks as before; it never enables service access. Android 8–10 retains the original service-button metadata/callback. The new activity is disabled before API 30.

| Check | Result |
| --- | --- |
| `jacocoDebugUnitTestCoverageVerification lint assembleDebug assembleDebugAndroidTest` | Passed; **155 unit tests**, 0 failures/errors/skips, 0 lint errors and the same 3 existing version-update warnings |
| Unit coverage | **1148/1248 lines = 92.0%**, **536/745 branches = 71.9%**; the 90%/70% gate passes |
| Focused native shortcut tests on Android 13/API 33 | **2 passed**; enter/exit with app button hidden, received tap/hold, and removal of the system shortcut while service/app controls remain usable |
| Full instrumentation on Android 13/API 33 x86_64 | **13 passed**, including actual System UI shortcut input and two-finger exit |
| Full compatible instrumentation on Android 7/API 24 x86 | **11 passed**; the two API 31+ shortcut tests are filtered out |
| APK signature/metadata | Verified v2 debug signature; version 0.3.2 (5), minSdk 24, targetSdk 36, no requested permissions |

Reports: `app/build/reports/032-build-validation.log`, `app/build/reports/api33-shortcut-032.txt`, `app/build/reports/api33-instrumentation-032.txt`, `app/build/reports/api24-instrumentation-032.txt`, and the standard unit/coverage/lint reports.

Deliverable: `app/build/outputs/apk/debug/TouchCursor-0.3.2-debug.apk` (972472 bytes). SHA-256: `4413C127A33A166E451E9CB5B12A446F5205E4E61BB4805B3DD8F3EE81A4ED48`. Install over the previous debug build to preserve settings. On Android 11+, keep **Touch Cursor Service** enabled and replace any old service shortcut with **Touch Cursor mode** if a native mode-toggle icon is wanted. Leave the system shortcut unassigned for app controls only. OEM Settings layouts remain device dependent; the user's OnePlus/Android 14 was not modified or physically tested.

---

## 0.3.1 historical validation

## Unit regression suite — 2026-10-18

Test-only changes expand the original 14 unit tests to **148 tests in 16 test classes**, with **0 failures/errors/skips**. Production code is unchanged by this update. JUnit 4.13.2, Robolectric 4.17 and JaCoCo 0.8.14 are test/build dependencies only. Robolectric uses API 34 by default; service lifecycle tests also run on API 24 (17 total lifecycle invocations).

| Check | Result |
| --- | --- |
| `testDebugUnitTest jacocoDebugUnitTestReport lint assembleDebug` | Passed; 148 tests, 0 lint errors, the same 3 existing version-update warnings |
| `jacocoDebugUnitTestCoverageVerification` | Passed; enforces 90% line / 70% branch minimums and is included in `check` |
| Workflow syntax (`actionlint` 1.7.12) | Passed; `check --dry-run` also confirms coverage verification is part of the standard task graph |
| JaCoCo application line coverage | **1139 / 1238 = 92.0%** |
| JaCoCo application branch coverage | **536 / 743 = 72.1%** |

Coverage includes every application class; only generated R/BuildConfig classes are excluded. Reports: `app/build/reports/tests/testDebugUnitTest/index.html` and `app/build/reports/jacoco/jacocoDebugUnitTestReport/html/index.html`; XML is alongside HTML in the coverage report directory. Execution log: `app/build/reports/final-unit-validation.log`; gate log: `app/build/reports/coverage-gate.log`.

Covered regressions include double-tap/hold/movement boundaries, cancellation, multi-touch exit on the final pointer release, native shortcut hit testing, settings limits/persistence/isolation, save/cancel/invalid-color dialogs, filtered exclusion selection, COPY/PASTE eligibility, keyboard policy restoration without BACK, foreground/window identity, bounded System UI traversal, injection rejection/cancellation/timeouts/stale callbacks, input-window removal before dispatch, missing window-snapshot recovery, hidden activation-button exit, exclusions, display changes, sleep/wake, service reconnect/unbind and preference/callback cleanup.

The new `.github/workflows/android.yml` configures tests, coverage verification, lint and debug builds for pushes/PRs, with downloadable reports. Action versions are pinned to verified commits; workflow syntax passes actionlint, but it has not been executed on GitHub in this workspace. The rebuilt APK has the same SHA-256 as the previously validated 0.3.1 APK. Unit checks do not replace actual input routing/OEM validation; no emulator or phone tests were rerun in this test-only update. Earlier device validation is preserved below.

## Runtime/input regression validation — before the unit-suite expansion

Validated on 2026-10-18 with JDK 21, AGP 9.4.0, Gradle 9.6.0 and SDK 36.

| Check | Result |
| --- | --- |
| `assembleDebug assembleDebugAndroidTest testDebugUnitTest lint` | Passed; 14 unit tests, 0 lint errors, the same 3 existing version-update warnings |
| Instrumentation on Android 13 / API 33 x86_64 | **12 passed**, including the actual System UI floating accessibility shortcut |
| Instrumentation on Android 7 / API 24 x86 | **11 passed**; the floating-menu test requires API 31+ and is filtered out |
| APK signature/metadata | Verified v2 debug signature; version 0.3.1 (4), minSdk 24, targetSdk 36, no requested permissions |

The input regression was reproduced on API 33: gesture recognition requested a tap and Android reported successful dispatch, but the target app did not receive it. Dispatch from the first frame callback could precede completion of overlay-removal input transactions. Waiting across frame boundaries and checking that removed overlay IDs disappear from the accessibility window snapshot restored received taps and long presses. A bounded preparation watchdog and the existing gesture watchdog return to normal touch on failure. Geometry is read from the attached input surface rather than a cached readiness flag.

The newer emulator also exposed an input surface covering the entire display despite automatic inset fitting. Explicit metrics/insets now keep it outside status/navigation bars and cutouts, and configuration changes recompute those dimensions. Rotation and repeated screen-off/unlock tests pass.

The floating-shortcut test uses physical touchscreen events at the actual System UI target: enter with the activation button hidden and the edge panel collapsed, inject a click and long press, exit with the same native icon, re-enter and exit with the temporary EXIT control, then verify that an excluded app cannot enter cursor mode. System UI target bounds are read through public accessibility APIs. A separate two-finger test confirms exit only after both fingers are lifted, with no injected click and normal direct touch restored.

The layout regression changes font scale during an injected long press, then verifies another click after reattachment at unchanged bounds. All three cursor shapes still pass coordinate, size, opacity and persistence checks. The test harness waits for the exact input channel on BLAST devices, not just a registered/drawn window. COPY/PASTE is validated against a known clipboard sentinel instead of depending on toast accessibility events that newer Android versions omit.

Runtime tests were launched with `adb -s <emulator> shell am instrument -w -r com.example.touchcursor.test/androidx.test.runner.AndroidJUnitRunner`, targeting only the emulators. Reports: `app/build/reports/api33-instrumentation.txt` (`OK (12 tests)`) and `app/build/reports/api24-instrumentation-031.txt` (`OK (11 tests)`). Unit/lint reports retain their standard locations. No runtime shell calls or test fixtures are packaged in the delivered APK.

The API 33 emulator used the official AOSP image `android/x86_64-33_r02.zip`, verified against its published SHA-1 before extraction. Its `Pixel_33` launch overrides the broken Google APIs image with `-sysdir %LOCALAPPDATA%\Android\Sdk\system-images\android-33\default\x86_64`.

Deliverable: `app/build/outputs/apk/debug/TouchCursor-0.3.1-debug.apk` (1010455 bytes). SHA-256: `B862B8829084A5B7B725B039A432297FA33C19D10FD4D841C51EF190E0AB74B0`. Install over the previous debug build to retain settings. The standard `app-debug.apk` is identical.

Remaining device boundary: the user's OnePlus / Android 14 has not been physically validated. If its System UI does not expose a labeled shortcut target, the temporary EXIT control and two-finger exit remain available. No app can guarantee survival after force-stop, disabled accessibility access, or OEM service termination.

---

# Touch Cursor 0.3.0 validation (historical)

Validated on 2026-10-18 with Android Studio JDK 21, AGP 9.4.0, Gradle 9.6.0 and Android SDK 36.

| Check | Result |
| --- | --- |
| `assembleDebug assembleDebugAndroidTest` | Passed |
| `testDebugUnitTest` | 14 passed, 0 failures |
| `lint` | 0 errors; the same 3 existing SDK/Gradle version warnings |
| `connectedDebugAndroidTest` on API 24 | 9 passed, 0 failures, confirmed in the final XML/HTML reports |
| Additional wake regression | Passed: click injection still works after unlock |
| `apksigner verify --verbose` | Passed, APK Signature Scheme v2 |
| APK metadata | Version 0.3.0 (3), minSdk 24, targetSdk 36; no requested permissions |

The integration suite verifies all three shapes at 64 dp and 40% opacity, preference persistence across service restarts, actual window size/opacity, and click coordinates within 2 px of the target. It also verifies three consecutive sleep/wake/unlock cycles without re-enabling the service, a successful click after unlock, collapsed-edge restoration, hiding/restoring the mode button, and the existing movement/hold, copy/paste, rotation, injection teardown and app-exclusion behavior.

The new system accessibility-button test is filtered out on API 24 using `SdkSuppress(minSdkVersion = 30)`, because its test driver uses Android's global accessibility-button action. The application callback itself supports API 26+. **That callback has not been runtime-tested on a newer emulator or physical phone:** the existing API 33 AVD lacks its kernel/system image, and attempts to download a replacement failed. Its metadata and public SDK API usage compile and pass lint. Device/OEM validation of the reported wake problem is still needed; an app cannot guarantee survival after force-stop or OEM termination of its accessibility service.

An actual settings-dialog UI check selected Crosshair, changed size to 89 dp and opacity to 50%, saved, and confirmed the persisted preferences. The dialog preview was visually inspected; evidence: `app/build/reports/runtime/cursor-appearance-api24.png`.

Final API 24 reports: `app/build/reports/validation-api24/final-suite/index.html` and `app/build/reports/validation-api24/final-results.xml`. Unit and lint reports retain their standard paths under `app/build/reports`.

Deliverable: `app/build/outputs/apk/debug/TouchCursor-0.3.0-debug.apk` (984424 bytes). SHA-256: `F76B9AA99BF342639880831F0A202D28367A935302A566307FBE50A665BE5CFE`. The standard `app-debug.apk` has identical contents. Install over the previous debug build to retain settings.

---

# Touch Cursor 0.2.0 validation (historical)

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
