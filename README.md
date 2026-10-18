# Touch Cursor — ТЕХНИЧЕСКОЕ ЗАДАНИЕ / IMPLEMENTATION SPEC

> The MVP is implemented. The original technical specification below remains authoritative; the implementation and testing notes describe the delivered behavior.

## Implemented MVP: setup and behavior

Build with JDK 17 or newer and Android SDK 36:

```powershell
.\gradlew.bat assembleDebug testDebugUnitTest lint
```

The standard Gradle wrapper is included. Android Studio's bundled JDK 21 was used for validation. Set the Android SDK path in your untracked `local.properties` (see `local.properties.example`). The debug APK is `app/build/outputs/apk/debug/app-debug.apk` and supports Android 7.0 / API 24 and newer.

1. Install the APK, open Touch Cursor, and select **Open Accessibility settings**.
2. Manually enable **Touch Cursor Service**. On Android versions that restrict accessibility for sideloaded apps, Android may require **Allow restricted settings** in the app's system information screen first; availability depends on the installer/device.
3. Tap the floating **arrow icon / OFF** button to enter cursor mode. Swipe to move the arrow relatively. Double-tap the touchpad area to send one touch at the arrow tip.
4. Hold still for the system long-press timeout, then release. The service then sends a long touch at the cursor position. Moving beyond Android's touch slop cancels tap/hold recognition. Multi-touch cancels the gesture.
5. Tap the **arrow icon / ON** button to return to ordinary touch. System status/navigation bars stay outside the capture area.
6. In the app, configure sensitivity and each button's visibility, horizontal/vertical position, width, height, and opacity. Each floating control has a native vector icon and a short label. **Button opacity** is independent for each control: 100% is solid and 30% is faint; it affects the icon, label and background together. **Save** applies button changes immediately. COPY, PASTE and KEYS are optional and disabled initially. Position uses normalized travel coordinates; controls do not move by dragging during ordinary use. Reset restores all defaults.

### Edge swipe and excluded apps (0.2.0)

- Open **Configure edge swipe**, enable **Reveal buttons with an edge swipe**, and choose Left, Right, Top or Bottom. Set **Trigger position** (0–100% along that edge), **Trigger length** (48–240 dp) and **Trigger thickness** (8–32 dp), then Save. The existing always-visible controls remain the default until this option is enabled.
- When enabled, controls initially collapse to a small chevron handle. Swipe inward from the handle to show the enabled buttons at their saved positions. Tap the handle or make a short outward swipe to hide them. Hiding also exits cursor mode. Tapping a collapsed handle does not reveal it accidentally; accessibility activation can still open it.
- Position is normalized to the available travel area and adapts to rotation. The handle returns collapsed after service restart, screen lock, or leaving an excluded app. Disabling edge swipe restores the always-visible controls.
- Open **Choose excluded apps**, search by app name or package, select games/other apps, and Save. While a selected app is foreground, all Touch Cursor windows disappear, including the cursor, capture surface and edge handle. On leaving it, controls return in normal touch mode. The service remains enabled so it can detect app changes; the Touch Cursor settings app is always available for recovery.
- The picker lists launchable apps in the current profile and preserves previously selected package IDs if an app is unavailable. It uses a launcher-intent visibility declaration, not QUERY_ALL_PACKAGES or Usage Access. Exclusions and edge configuration are stored locally alongside existing button settings.

The narrow edge handle consumes touches starting inside its configured strip; gestures outside that strip pass through when the panel is collapsed. System status/navigation bars remain accessible, so top/bottom triggers follow the usable content edge. On Android 10+ the handle requests gesture exclusion only for its own bounds, subject to Android's system-navigation limits and OEM behavior. App exclusions follow accessibility application-window focus; overlays and IME windows do not count as switching apps.

### Architecture and privacy

- `CursorAccessibilityService` owns the overlay lifecycle, screen-lock receiver and action controllers. `OverlayManager` owns every `TYPE_ACCESSIBILITY_OVERLAY` window. Windows and pending callbacks are cleaned up when the service disconnects or is destroyed.
- The transparent input surface is non-touchable in normal mode. Mode changes recreate its input channel to avoid stale touchability flags. The separate arrow is always non-touchable/non-focusable. Window bounds supply usable screen coordinates; button positions scale with the usable area and cursor coordinates clamp after configuration changes.
- `TouchpadGestureDetector` and `CursorController` are pure Kotlin. Gesture recognition never injects until physical pointer-up. Movement and recognition use Android's configured touch/double-tap slop and timing thresholds.
- Before injection, all touchable overlay windows are removed synchronously. A frame callback dispatches after removal; `GestureResultCallback` restores the currently requested state on success or cancellation. A generation token rejects stale callbacks, and only one injection can run. A watchdog is only a recovery path for a missing OEM/service callback, not the normal timing mechanism. No sleeping is used to coordinate injection.
- Local `SharedPreferences` store configuration only. No third-party runtime dependencies, Internet/overlay permissions, text logs, analytics, root, hidden APIs or runtime shell commands are used. JUnit and AndroidX Test dependencies are confined to tests; the integration fixture activity is packaged only in the separate test APK.
- Accessibility content access is required for focused-node COPY/PASTE. Interactive-window access detects IME visibility and the foreground application's package for exclusions. Events are limited to window state/set changes, view focus and selection; cursor-window geometry changes are filtered/coalesced when checking the foreground app.

### Public API limitations

- Injected input is Android touch input, not a hardware mouse. Drag/drop, scrolling, hover and multi-display are outside this MVP.
- A long press is emitted after release, rather than while the physical finger remains held, to avoid forwarding the physical hold into the target app.
- COPY/PASTE use the focused node's supported accessibility actions. Unsupported selections, custom rendering, missing editable focus and unavailable clipboard content can cause an action to fail; the app displays feedback without changing another field or reading/logging clipboard contents.
- KEYS uses `SoftKeyboardController` to hide/allow the IME. Showing is best effort: the focused editable node is asked to focus/click, and the owning app decides whether to open its keyboard. It never sends global BACK. The service restores automatic keyboard policy on teardown if it hid the keyboard. See the [official SoftKeyboardController reference](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService.SoftKeyboardController).
- Android gesture dispatch cancels competing touch streams. Avoid placing another finger down during the brief injected touch. Touchable controls are temporarily absent during injection so even a cursor positioned over a control can reach the underlying app. A new physical touch during that interval may reach the underlying app or cancel injection; no public API provides a simultaneous physical-input shield that also passes injected touch through.
- Other accessibility services, OEM gesture handling, system UI, cutouts and app-specific accessibility/IME behavior can affect results. Screen-off exits cursor mode and hides overlays until unlock.

### Validation

Fourteen local JUnit tests cover cursor bounds/sensitivity, touchpad gestures, edge-swipe directions, cancellation, multi-touch rejection and reset behavior. Six integration tests pass on an Android 7.0 / API 24 emulator, including received touch coordinates, copy/paste, IME hiding, layout persistence/rotation, disconnect during injection, all four edge triggers and app exclusions. Build, lint and runtime results are recorded in [VALIDATION.md](VALIDATION.md).

## 1. Цель

Сделать небольшое Android-приложение без root, которое после ручного включения Accessibility Service может работать поверх других приложений как виртуальный touchpad/cursor mode, похожий по ощущениям на режим курсора в Microsoft Windows App / RDP-клиенте.

Приложение должно быть локальным. Сеть не нужна. Не добавлять `INTERNET`, аналитику, телеметрию или отправку accessibility/clipboard данных.

## 2. Базовые технические ограничения

- Kotlin + обычный Android SDK / Views.
- `minSdk = 24`.
- Не использовать root.
- Не использовать Shizuku.
- Не требовать ADB после установки.
- Не использовать hidden/private Android API.
- Основной механизм: `AccessibilityService`.
- Для окон поверх других приложений использовать `WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY`.
- По возможности **не запрашивать `SYSTEM_ALERT_WINDOW`** и не использовать обычный `TYPE_APPLICATION_OVERLAY`.
- Для эмуляции прикосновения использовать `AccessibilityService.dispatchGesture()`.
- В accessibility config обязательно оставить `canPerformGestures=true`.
- Для COPY/PASTE нужен доступ к accessibility tree, поэтому оставить `canRetrieveWindowContent=true`.
- Accessibility service должен быть `android:exported="true"`, но защищён `android.permission.BIND_ACCESSIBILITY_SERVICE`, чтобы его мог bind-ить Android system.
- Не подписываться на `typeAllMask` без необходимости: skeleton уже ограничен событиями focus/window/selection.

Пользователь вручную включает сервис в системных настройках Accessibility. Приложение не должно пытаться обходить этот шаг.

## 3. Состояния приложения

Нужно минимум два состояния:

### NORMAL

Android работает как обычно. Касания пользователя напрямую получает приложение под пальцем.

Поверх системы остаются только явно включённые управляющие overlay-кнопки, прежде всего кнопка переключения режима курсора.

### CURSOR

На экране появляется виртуальный курсор.

Основная область экрана превращается в touchpad: касание/свайп пользователя управляет курсором относительно текущей позиции, а не нажимает напрямую на элемент под пальцем.

Повторное нажатие кнопки режима возвращает `NORMAL`.

## 4. Курсор

Курсор должен быть отдельным overlay View.

Требования:

- отображается поверх других приложений только в режиме `CURSOR`;
- позиция хранится в экранных координатах;
- стартовая позиция при первом включении — центр экрана;
- движение относительное: `cursor += fingerDelta * sensitivity`;
- координаты всегда clamp в границы доступной области экрана;
- курсор сам не должен перехватывать touch;
- для окна курсора использовать `FLAG_NOT_TOUCHABLE` и `FLAG_NOT_FOCUSABLE`;
- предусмотреть настройку чувствительности движения;
- не добавлять инерцию в MVP.

Учитывать orientation change, display size, status/navigation bars и cutout/insets. Позицию после изменения размеров экрана нужно корректно ограничить новой рабочей областью.

## 5. Touchpad / fullscreen capture layer

В режиме `CURSOR` создать прозрачный полноэкранный `TYPE_ACCESSIBILITY_OVERLAY`, который получает реальные touch events пользователя.

Он нужен только для распознавания жестов touchpad и не должен рисовать непрозрачный фон.

### Swipe одним пальцем

Любое движение пальца:

1. вычислить `dx/dy` между MotionEvent;
2. умножить на sensitivity;
3. передвинуть виртуальный курсор;
4. не передавать этот свайп приложению под overlay.

### Double tap

Два быстрых тапа по touchpad-области должны выполнить **один обычный tap в текущей позиции виртуального курсора**.

Координата физического double tap значения не имеет — кликаем там, где стоит cursor.

### Hold / long press

Удержание пальца на touchpad должно выполнить long press в текущей позиции курсора.

Главная цель MVP — открывать context menu и выполнять действия, которые обычно вызываются долгим нажатием Android.

Порог long press должен быть вынесен в константу/настройку, начальное значение около системного long-press timeout.

## 6. Критический момент: injection и touchable overlay

Fullscreen capture overlay находится поверх целевого приложения. Нельзя допустить, чтобы `dispatchGesture()` попадал обратно в собственный capture overlay.

Нужно реализовать безопасный механизм injection:

1. распознать tap/long press;
2. временно сделать capture layer нетачабельным или временно убрать его из WindowManager;
3. вызвать `dispatchGesture()` в координате курсора;
4. дождаться `GestureResultCallback.onCompleted()` или `onCancelled()`;
5. вернуть capture layer в touchable state, если режим `CURSOR` всё ещё активен.

Нужно защищаться от race conditions и повторных injections. В момент injection должен существовать только один активный injected gesture.

Отдельный cursor overlay при этом может оставаться видимым, так как он всегда `NOT_TOUCHABLE`.

### Long press и физический палец

Особенно аккуратно обработать ситуацию, когда пользователь всё ещё держит палец на capture overlay. Нельзя допустить утечки реального физического касания в нижнее приложение после временного выключения capture window.

Для первого стабильного варианта допустима такая семантика: распознать hold, дождаться безопасного состояния pointer/up и затем сгенерировать long press в позиции курсора. Надёжность важнее миллисекундной идентичности реальному удержанию.

## 7. Gesture injection

Создать отдельный `GestureInjector`.

MVP методы концептуально:

- `tap(x, y)`;
- `longPress(x, y, duration)`.

Использовать только `AccessibilityService.dispatchGesture()` + `GestureDescription`.

Не пытаться создавать настоящий `SOURCE_MOUSE`. Целевое приложение должно видеть синтетическое touch-событие, а не аппаратный mouse click.

Следовательно, для MVP не нужны:

- настоящий LMB/RMB;
- hover;
- pointer capture;
- root input injection.

### Future / optional

Архитектура не должна мешать позднее добавить:

- drag & drop;
- scroll;
- two-finger gestures;
- продолженные `StrokeDescription` gestures.

Но это **не входит в текущий обязательный MVP**.

## 8. Основная кнопка переключения режима

Должна существовать небольшая overlay-кнопка `Cursor Mode`.

Поведение:

- NORMAL -> CURSOR;
- CURSOR -> NORMAL.

Кнопка остаётся доступной поверх других приложений.

### Настройка кнопки

Пользователь должен иметь возможность настраивать:

- положение X/Y на экране;
- размер / width / height;
- желательно opacity;
- сторону/место по умолчанию;
- возможность перетащить кнопку в режиме настройки.

Настройки должны сохраняться локально.

Для координат желательно хранить не только абсолютные px, а нормализованную позицию или другой формат, который адекватно переживает смену разрешения/orientation.

## 9. Дополнительные overlay-кнопки

Нужно предусмотреть универсальную систему дополнительных кнопок поверх экрана.

Каждая кнопка должна иметь минимум:

- `id`;
- `action`;
- `enabled/visible`;
- позицию X/Y;
- width;
- height;
- желательно opacity.

Пользователь должен иметь возможность изменять **позицию и размер каждой кнопки независимо**.

Для текущего ТЗ нужны следующие действия:

1. COPY
2. PASTE
3. KEYBOARD TOGGLE

Эти кнопки желательно сделать опциональными: пользователь может скрыть ненужные.

Кнопки могут быть доступны и в NORMAL, и в CURSOR — это можно сделать настройкой. По умолчанию допустимо показывать их постоянно вместе с основной кнопкой.

## 10. COPY

Кнопка COPY должна попытаться скопировать текущую выделенную строку/текст в системный clipboard.

Предпочтительный путь:

1. найти текущий input/accessibility-focused `AccessibilityNodeInfo`;
2. проверить, поддерживает ли node действие COPY;
3. выполнить `AccessibilityNodeInfo.ACTION_COPY` / соответствующий `AccessibilityAction.ACTION_COPY`.

Это **best effort**, потому что стороннее приложение может:

- не предоставлять selection через accessibility;
- не поддерживать COPY action;
- использовать собственный canvas/web/rendering.

В случае невозможности копирования приложение не должно падать.

Не логировать и не сохранять содержимое скопированного текста.

## 11. PASTE

Кнопка PASTE должна попытаться вставить содержимое системного clipboard в текущее редактируемое поле.

Предпочтительный путь:

1. определить input-focused editable accessibility node;
2. проверить поддержку PASTE;
3. выполнить `AccessibilityNodeInfo.ACTION_PASTE` / `AccessibilityAction.ACTION_PASTE`.

Это также best effort и не гарантируется для произвольного стороннего UI.

Если подходящего focused editable node нет — ничего разрушительного не делать.

## 12. Показать / скрыть клавиатуру

Кнопка `KEYBOARD TOGGLE` должна максимально надёжно управлять soft keyboard.

Нужно учитывать, что Android не даёт универсального публичного API, который из произвольного overlay гарантированно принудительно откроет IME в любом стороннем приложении.

Поэтому реализация должна быть best effort.

### Hide

Accessibility service может использовать `SoftKeyboardController` и `SHOW_MODE_HIDDEN`, если это подходит по поведению. Альтернативные безопасные публичные механизмы допустимы.

### Show

Для показа:

- вернуть режим keyboard controller в `SHOW_MODE_AUTO` / подходящий режим;
- найти текущее editable/input-focused поле;
- при необходимости восстановить input focus или выполнить безопасное действие, которое заставит текущее приложение запросить IME.

Не использовать хаки/hidden APIs.

Если стороннее приложение не позволяет программно показать клавиатуру, кнопка должна корректно закончить действие без crash.

Важно: скрытие через глобальный BACK использовать осторожно — если клавиатура уже скрыта, это может закрыть экран приложения. Не делать такой fallback без проверки состояния.

## 13. Экран настроек

`MainActivity` должна в итоге стать простым экраном конфигурации и onboarding, но не содержать основную runtime-логику.

Минимально нужно:

- показать, включён Accessibility Service или нет;
- кнопка/инструкция открыть системные Accessibility settings;
- sensitivity cursor;
- настройка основной overlay-кнопки;
- список дополнительных кнопок COPY / PASTE / KEYBOARD;
- для каждой кнопки: visible, position, width, height;
- reset layout to defaults.

Можно сделать простой Android Views UI. Красивый дизайн вторичен по сравнению со стабильностью overlay.

## 14. Хранение настроек

Настройки полностью локальные.

Подойдёт:

- SharedPreferences для самого простого MVP;
- DataStore, если реализация остаётся небольшой.

Не нужна БД.

Желательно концептуально иметь модель:

```text
OverlayButtonConfig
- id
- action
- enabled
- xNormalized
- yNormalized
- widthDp
- heightDp
- opacity
```

Это описание модели, а не требование буквально использовать именно эти имена полей.

## 15. WindowManager структура

Предпочтительная архитектура:

```text
AccessibilityService
    |
    +-- OverlayManager
          |
          +-- mode toggle window
          +-- optional action buttons
          +-- cursor visual window
          +-- touch capture window
```

Не создавать десятки несвязанных WindowManager owner'ов. Жизненный цикл overlay должен контролироваться одним компонентом.

При выключении Accessibility Service или уничтожении service нужно гарантированно удалить все добавленные windows.

## 16. Предлагаемая структура Kotlin

```text
com.example.touchcursor/
├── MainActivity.kt
├── service/
│   └── CursorAccessibilityService.kt
├── overlay/
│   ├── OverlayManager.kt
│   ├── CursorOverlayView.kt
│   └── TouchCaptureOverlayView.kt
├── input/
│   ├── CursorController.kt
│   ├── GestureInjector.kt
│   └── TouchpadGestureDetector.kt
├── actions/
│   ├── TextActionController.kt
│   └── KeyboardController.kt
├── settings/
│   └── SettingsRepository.kt
└── model/
    └── OverlayButtonConfig.kt
```

Эта структура уже создана в skeleton. Её можно менять только если новая структура объективно проще.

## 17. Privacy / security

Accessibility — очень мощное разрешение, поэтому приложение должно быть максимально прозрачным:

- не иметь сетевой функциональности;
- не сохранять содержимое экранов;
- не собирать вводимый текст;
- не логировать clipboard;
- не логировать accessibility tree в production;
- не делать действий без явной команды пользователя;
- не использовать accessibility для автоматизации чего-либо кроме функций данного курсора и кнопок.

## 18. UX и обратная связь

Желательно добавить короткий haptic feedback:

- вход/выход из Cursor Mode;
- успешный virtual click;
- long press;
- нажатие action button.

Но haptic не должен быть обязательным для первой сборки.

Курсор должен быть хорошо виден на светлом и тёмном фоне. Позже можно добавить настройку cursor size/appearance, но это не обязательный MVP.

## 19. Приоритет реализации для Codex / Sol

Делать строго итерационно:

### Phase 1 — service + overlay

- проект собирается;
- Accessibility Service включается;
- поверх приложений появляется toggle button;
- `TYPE_ACCESSIBILITY_OVERLAY` работает без `SYSTEM_ALERT_WINDOW`.

### Phase 2 — cursor mode

- toggle NORMAL/CURSOR;
- cursor visual;
- fullscreen capture layer;
- relative swipe movement;
- sensitivity + boundary clamp.

### Phase 3 — click

- double tap recognition;
- безопасный temporary pass-through capture overlay;
- `dispatchGesture()` tap в cursor position;
- корректное восстановление capture layer.

### Phase 4 — long press

- hold recognition;
- long press injection;
- защита от leakage физического pointer.

### Phase 5 — configurable controls

- изменение позиции/размера основной кнопки;
- универсальный `OverlayButtonConfig`;
- COPY / PASTE / KEYBOARD buttons;
- сохранение настроек.

### Phase 6 — settings/onboarding

- service status;
- переход в Accessibility settings;
- настройка sensitivity/button layout;
- reset defaults.

После каждой фазы приложение должно собираться и базовый предыдущий функционал не должен ломаться.

## 20. Acceptance criteria MVP

MVP считается выполненным, когда на обычном Android без root:

1. пользователь включает Accessibility Service вручную;
2. появляется боковая overlay-кнопка;
3. положение и размер основной кнопки можно настроить;
4. нажатие включает Cursor Mode;
5. появляется cursor;
6. свайпы двигают cursor относительно пальца;
7. приложение под пальцем при движении cursor не получает эти касания;
8. double tap выполняет обычный Android tap в текущей позиции cursor;
9. hold выполняет long press в позиции cursor;
10. повторное нажатие toggle возвращает обычное управление Android;
11. доступны опциональные overlay-кнопки COPY, PASTE, KEYBOARD;
12. для каждой дополнительной кнопки можно изменить размер и положение;
13. настройки переживают перезапуск приложения/service;
14. приложение не требует root, Shizuku, ADB runtime или `SYSTEM_ALERT_WINDOW`;
15. приложение не использует сеть.

## 21. Не входит в MVP

Не тратить время первой версии на:

- настоящий mouse device / SOURCE_MOUSE;
- right click;
- hover;
- аппаратный pointer capture;
- multi-display;
- drag & drop;
- two-finger scroll;
- gestures/macros;
- cloud sync;
- analytics;
- Play Store polish.

После стабильного MVP можно отдельно добавить drag/scroll и кастомизацию внешнего вида.

## 22. Build baseline skeleton

Шаблон настроен на:

- Android Gradle Plugin 9.4.0;
- Gradle 9.6.0;
- Java/JDK 17;
- compileSdk 36;
- targetSdk 36;
- minSdk 24.

AGP 9.x использует built-in Kotlin, поэтому отдельный `org.jetbrains.kotlin.android` plugin здесь намеренно не добавлен.

The standard `gradlew`, `gradlew.bat` and `gradle-wrapper.jar` have been generated using local Gradle and are included. They use the specified Gradle 9.6.0 distribution.

## 23. Финальная инструкция агенту

Перед написанием кода прочитать этот файл полностью.

Сначала добиться минимально работающего end-to-end пути:

`enable service -> overlay toggle -> cursor -> move -> double tap injection -> exit cursor mode`.

Только после этого делать COPY/PASTE/keyboard/settings.

Если конкретный Android API ведёт себя по-разному на OEM прошивках, выбирать наиболее простой публичный SDK вариант и документировать fallback. Не усложнять проект заранее.

## 24. Официальные Android references для агента

При спорном поведении API проверять в первую очередь официальную документацию:

- Accessibility service guide: https://developer.android.com/guide/topics/ui/accessibility/service
- AccessibilityService / dispatchGesture: https://developer.android.com/reference/android/accessibilityservice/AccessibilityService
- TYPE_ACCESSIBILITY_OVERLAY: https://developer.android.com/reference/android/view/WindowManager.LayoutParams#TYPE_ACCESSIBILITY_OVERLAY
- AccessibilityNodeInfo COPY/PASTE: https://developer.android.com/reference/android/view/accessibility/AccessibilityNodeInfo
- SoftKeyboardController: https://developer.android.com/reference/android/accessibilityservice/AccessibilityService.SoftKeyboardController

