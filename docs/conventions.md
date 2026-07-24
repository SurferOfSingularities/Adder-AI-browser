# Coding Conventions

## Language & Style

- Follow official Kotlin coding conventions: https://kotlinlang.org/docs/coding-conventions.html
- iOS platform code is written in Swift, following Swift API Design Guidelines.
- Keep code concise and readable. Prefer clarity over cleverness.

## Package Structure

- `com.adder.shared` — Shared KMP module (commonMain)
- `com.adder.shared.model` — Data models (DomElement, classification results)
- `com.adder.shared.detection` — Ad detection logic (heuristics, prompt building)
- `com.adder.shared.engine` — Pipeline orchestration
- `com.adder.shared.ui` — Compose Multiplatform UI (browser screen, composables)
- `com.adder.android` — Android app entry point (single Activity)
- iOS code uses Swift module naming conventions (minimal — just entry point + LLM bridge)

## Libraries & Patterns

- **UI:** Compose Multiplatform for all UI. The browser screen is a shared composable in commonMain.
- **WebView in Compose:** Use `AndroidView` (Android) and `UIKitView` (iOS) to embed platform-native WebViews inside Compose.
- **JSON:** Use kotlinx.serialization. Do not use Gson or Moshi.
- **Async:** Use Kotlin coroutines. Do not use RxJava.
- **Platform abstractions:** Use `expect`/`actual` declarations for platform-specific implementations (e.g., LLM classifiers, WebView bridges).
- **WebView:** Platform-native only (Android WebView, iOS WKWebView), embedded in Compose via interop.
- **JavaScript injection code:** Stored as string constants in the shared module for reuse across platforms.
- **Navigation:** Not needed for MVP (single screen app). If needed later, use Compose Navigation.

## Compose Previews

- **Every previewable composable must have at least one `@Preview` function.** Use the multiplatform annotation `org.jetbrains.compose.ui.tooling.preview.Preview` (from `compose.components.uiToolingPreview`), not the Android-only one.
- Preview functions are `private`, live in the same file as the composable, and wrap the content in `MaterialTheme { }` so theme colors resolve.
- Cover meaningful states. For stateful/toggleable UI, add a preview per key state (e.g. enabled/disabled, on/off).
- Pass static/fake data to preview functions — no live data, network, or platform calls.
- **Exception:** composables that embed the platform WebView (`PlatformWebView`, and screens that host it like `BrowserScreen`/`App`) cannot be previewed because the `expect`/`actual` WebView needs a live native view. Keep UI logic in small, previewable composables so most of the UI stays covered.

## iOS Interop

- iOS app uses `ComposeUIViewController` from Compose Multiplatform to render the shared Compose UI.
- Swift code is minimal — just the app entry point and LLM bridge.
- Platform-specific `actual` implementations in `iosMain` delegate to Swift where needed (especially for Apple Foundation Models which require Swift).
- WKWebView is embedded in Compose via `UIKitView` interop.

## Naming

- `expect`/`actual` classes use the same name across source sets.
- Use descriptive names: `AdDetector`, `LlmClassifier`, `DomElement`, `AdBlockEngine`.
- Enum values use UPPER_SNAKE_CASE: `DEFINITE_AD`, `LIKELY_AD`, `AMBIGUOUS`, `NOT_AD`.

## Testing

- No automated tests for now. Focus on working, demoable code first.
- Manual verification on real devices is the primary validation method during MVP.

## Documentation Maintenance

- **Source of Truth:** The `.kiro/steering/` and `docs/` directories must always be kept in sync.
- **Mirroring:** Any update to a file in `.kiro/steering/` must be immediately mirrored to the corresponding file in `docs/` (and vice versa).
- **AI Instructions:** When an AI agent modifies architecture or conventions, it should be instructed to update both locations.

## General

- Keep the shared module as the source of truth for business logic.
- Platform modules should be thin: UI + platform API bridges only.
- Prefer simple, direct implementations over over-engineered abstractions.
