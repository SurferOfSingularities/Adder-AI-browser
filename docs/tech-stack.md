# Tech Stack

## Build System

- **Kotlin Multiplatform** plugin (latest stable)
- **Gradle** with Kotlin DSL (build.gradle.kts)
- **Android Gradle Plugin** (latest stable compatible with KMP)
- **Xcode** for iOS build and framework linking

## Shared Module Dependencies

- `org.jetbrains.kotlinx:kotlinx-serialization-json` — JSON serialization for DOM element models
- `org.jetbrains.kotlinx:kotlinx-coroutines-core` — Coroutines for async pipeline
- **Compose Multiplatform** (via `org.jetbrains.compose` plugin) — Shared UI framework
- `org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose` — ViewModel for Compose
- `org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose` — Lifecycle-aware Compose

## Android Dependencies

- `com.google.android.gms:play-services-mlkit-genai-prompt` — Gemini Nano on-device LLM via ML Kit
- AndroidX core libraries
- Platform WebView (no additional dependencies)
- minSdk: 34
- targetSdk: latest stable

## iOS Dependencies

- Apple `FoundationModels` framework (iOS 26+) — On-device LLM
- Apple `WebKit` framework — WKWebView
- No third-party pod/SPM dependencies for MVP

## Key Technical Choices

| Decision | Choice | Rationale |
|----------|--------|-----------|
| UI framework | Compose Multiplatform | Shared UI code across Android and iOS from commonMain |
| JSON library | kotlinx.serialization | KMP-native, no platform-specific setup |
| Async | Kotlin coroutines | KMP-native, structured concurrency |
| WebView | Platform-native via Compose interop | AndroidView / UIKitView to embed WebView in Compose |
| LLM (Android) | Gemini Nano / ML Kit | Google's official on-device LLM, managed by AICore |
| LLM (iOS) | Apple Foundation Models | Apple's official on-device LLM, native Swift API |
| UI (Android) | Compose (via shared module) | Single Activity hosting ComposeView |
| UI (iOS) | Compose (via ComposeUIViewController) | Swift entry point renders shared Compose UI |

## Version Constraints

- Android: Gemini Nano requires Android 14+ on supported hardware (Pixel 8+, Samsung S24+)
- iOS: Foundation Models requires iOS 26+ on Apple Silicon devices (iPhone 15 Pro+, M-series)
- Both LLM integrations must gracefully degrade on unsupported devices (heuristic-only mode)
