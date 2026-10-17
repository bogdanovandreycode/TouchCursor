# Codex / Sol implementation note

The authoritative specification is `README.md`.

Implement the project incrementally and keep it buildable after each step.
Do not add root, Shizuku, ADB runtime dependencies, hidden APIs, or network functionality.
Prefer public Android SDK APIs and a small pure-Kotlin/View-based implementation.
Do not request `SYSTEM_ALERT_WINDOW` unless a verified technical blocker makes it unavoidable; the intended design uses `TYPE_ACCESSIBILITY_OVERLAY` from the enabled `AccessibilityService`.
