# Implementation Plan: Model Name Popup

## Overview

Implement a transient `ModelNamePopup` composable in `BrowserScreen.kt` that displays the on-device LLM model name above the ad-block toggle button, and add dynamic model name resolution so the popup reflects the actual LLM availability on each platform. The popup fades in on composition, holds for ~2 seconds, then fades out. It re-triggers on tab switch via `key(currentTabId)`. Dynamic resolution uses a cached value updated by `LlmClassifier.isAvailable()` on each platform.

## Tasks

- [ ] 1. Implement dynamic model name resolution on Android
  - [ ] 1.1 Add companion object with cached model name to Android LlmClassifier
    - Add `companion object { var cachedModelName: String = "Gemini Nano"; private set }` to `LlmClassifier` in `LlmClassifier.android.kt`
    - Update `isAvailable()` to set `cachedModelName = "Gemini Nano"` when status is AVAILABLE or after successful download
    - Update `isAvailable()` to set `cachedModelName = "Heuristic Only"` in the else branch and in the catch block
    - File: `shared/src/androidMain/kotlin/com/adder/shared/detection/LlmClassifier.android.kt`
    - _Requirements: 5.1, 5.2, 5.4_

  - [ ] 1.2 Update ModelInfo.android.kt to return cached model name
    - Change `actual fun currentModelName(): String = "Gemini Nano"` to `actual fun currentModelName(): String = LlmClassifier.cachedModelName`
    - File: `shared/src/androidMain/kotlin/com/adder/shared/detection/ModelInfo.android.kt`
    - _Requirements: 5.1, 5.2_

- [ ] 2. Implement dynamic model name resolution on iOS
  - [ ] 2.1 Add companion object with cached model name to iOS LlmClassifier
    - Add `companion object { var cachedModelName: String = "Apple Intelligence"; private set }` to `LlmClassifier` in `LlmClassifier.ios.kt`
    - Update `isAvailable()` to set `cachedModelName = "Apple Intelligence"` when delegate returns true
    - Update `isAvailable()` to set `cachedModelName = "Heuristic Only"` when delegate returns false or is null
    - File: `shared/src/iosMain/kotlin/com/adder/shared/detection/LlmClassifier.ios.kt`
    - _Requirements: 5.1, 5.3, 5.4_

  - [ ] 2.2 Update ModelInfo.ios.kt to return cached model name
    - Change `actual fun currentModelName(): String = "Apple Foundation Models"` to `actual fun currentModelName(): String = LlmClassifier.cachedModelName`
    - File: `shared/src/iosMain/kotlin/com/adder/shared/detection/ModelInfo.ios.kt`
    - _Requirements: 5.1, 5.3_

- [ ] 3. Update BrowserViewModel to support reactive model name
  - [ ] 3.1 Change modelName from val to mutableStateOf and add refreshModelName()
    - Change `val modelName: String = currentModelName()` to `var modelName by mutableStateOf(currentModelName()); private set`
    - Add `fun refreshModelName() { modelName = currentModelName() }`
    - File: `shared/src/commonMain/kotlin/com/adder/shared/ui/BrowserViewModel.kt`
    - _Requirements: 5.5_

  - [ ] 3.2 Call refreshModelName() after first page load completes
    - In `onPageFinished()`, call `refreshModelName()` so that after the ad-block pipeline runs (which triggers `isAvailable()`), the model name updates reactively
    - This ensures subsequent popup appearances show the resolved name
    - File: `shared/src/commonMain/kotlin/com/adder/shared/ui/BrowserViewModel.kt`
    - _Requirements: 5.5_

- [ ] 4. Checkpoint - Verify dynamic model name resolution
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 5. Implement ModelNamePopup composable and integrate into BrowserScreen
  - [ ] 5.1 Create the private ModelNamePopup composable function
    - Add `ModelNamePopup(modelName: String, modifier: Modifier)` as a private composable in `BrowserScreen.kt`
    - Use `AnimatedVisibility` with `fadeIn()` / `fadeOut()` enter/exit transitions
    - Use a `remember { mutableStateOf(true) }` for `showPopup` state, flipped to `false` after a 2-second `LaunchedEffect` delay
    - Render a Material 3 `Surface` with `RoundedCornerShape(8.dp)`, `surfaceContainerHigh` background, `shadowElevation = 4.dp`
    - Display the model name using `labelMedium` typography with `12.dp` horizontal / `8.dp` vertical padding
    - Add required imports: `AnimatedVisibility`, `fadeIn`, `fadeOut`, `delay`
    - File: `shared/src/commonMain/kotlin/com/adder/shared/ui/BrowserScreen.kt`
    - _Requirements: 1.1, 1.2, 2.1, 2.2, 2.3, 4.1, 4.2, 4.3, 4.4_

  - [ ] 5.2 Wrap AdBlockToggleButton and ModelNamePopup in a Column layout
    - Replace the standalone `AdBlockToggleButton` in the WebView `Box` with a `Column(Modifier.align(Alignment.BottomEnd).padding(bottom = 24.dp, end = 24.dp), horizontalAlignment = Alignment.End)`
    - Inside the Column: `key(currentTabId) { ModelNamePopup(modelName = viewModel.modelName) }`, then `Spacer(Modifier.height(8.dp))`, then the existing `AdBlockToggleButton` (without its own alignment/padding modifier)
    - The `key(currentTabId)` ensures the popup recomposes and re-animates on tab switch
    - File: `shared/src/commonMain/kotlin/com/adder/shared/ui/BrowserScreen.kt`
    - _Requirements: 1.3, 3.1, 3.2, 3.3_

  - [ ]* 5.3 Write property test for model name text fidelity
    - **Property 1: Model name text fidelity**
    - **Validates: Requirement 1.2**
    - For any non-empty model name string provided to `ModelNamePopup`, the rendered text content shall equal the input string exactly

- [ ] 6. Add @Preview functions for ModelNamePopup
  - [ ] 6.1 Add preview composables for ModelNamePopup
    - Add `ModelNamePopupPreview()` with `modelName = "Gemini Nano"` wrapped in `MaterialTheme`
    - Add `ModelNamePopupLongNamePreview()` with `modelName = "Apple Foundation Models"` wrapped in `MaterialTheme`
    - Both previews are private, use `@Preview` from `org.jetbrains.compose.ui.tooling.preview.Preview`
    - File: `shared/src/commonMain/kotlin/com/adder/shared/ui/BrowserScreen.kt`
    - _Requirements: 4.1, 4.2, 4.3, 4.4_

- [ ] 7. Final checkpoint
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Tasks 1, 2, and 3 implement the dynamic model name resolution (Requirement 5) — these are prerequisites for the popup showing accurate information
- Tasks 5 and 6 implement the UI popup (Requirements 1–4)
- The `refreshModelName()` approach ensures the popup displays the correct name after the first `isAvailable()` check completes, even though `currentModelName()` remains synchronous
- On first launch, the popup shows the optimistic default; after the first page load triggers the pipeline, subsequent popups show the resolved name
- Property tests validate that the composable renders the exact model name string without modification

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "2.1", "3.1"] },
    { "id": 1, "tasks": ["1.2", "2.2", "3.2"] },
    { "id": 2, "tasks": ["5.1"] },
    { "id": 3, "tasks": ["5.2", "6.1"] },
    { "id": 4, "tasks": ["5.3"] }
  ]
}
```
