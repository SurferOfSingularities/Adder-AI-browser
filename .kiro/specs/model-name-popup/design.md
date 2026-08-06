# Design Document: Model Name Popup

## Overview

A transient popup composable that displays the on-device LLM model name in the bottom-right corner of the WebView area, directly above the existing `AdBlockToggleButton`. It fades in on screen composition, remains visible for ~2 seconds, then fades out and is removed from the composition tree. The implementation is entirely within `BrowserScreen.kt` in the shared `commonMain` module — no ViewModel changes are required.

## Architecture

### Component Diagram

```
BrowserScreen
└─ Box (WebView area)
   ├─ PlatformWebView
   ├─ Column (Alignment.BottomEnd, padding end=24.dp, bottom=24.dp)
   │   ├─ ModelNamePopup (AnimatedVisibility)
   │   ├─ Spacer (vertical gap)
   │   └─ AdBlockToggleButton
   └─ SnackbarHost
```

The existing `AdBlockToggleButton` is moved into a `Column` with `Alignment.BottomEnd` positioning. The `ModelNamePopup` is placed above it in the same column, separated by a small `Spacer`.

### Data Flow

```
BrowserViewModel.modelName (String, read-only)
        │
        ▼
BrowserScreen (reads modelName)
        │
        ▼
ModelNamePopup(modelName = viewModel.modelName)
        │
        ▼
AnimatedVisibility(visible = showPopup)
        │
        ▼
Surface > Text(modelName)
```

No new state flows are introduced. The existing `BrowserViewModel.modelName` property (backed by `currentModelName()`) provides the string.

## Components

### ModelNamePopup

A `private @Composable` function in `BrowserScreen.kt`.

```kotlin
@Composable
private fun ModelNamePopup(
    modelName: String,
    modifier: Modifier = Modifier
) {
    var showPopup by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        delay(2000L)
        showPopup = false
    }

    AnimatedVisibility(
        visible = showPopup,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shadowElevation = 4.dp
        ) {
            Text(
                text = modelName,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
    }
}
```

### Integration in BrowserScreen

The `AdBlockToggleButton` currently sits alone with `Modifier.align(Alignment.BottomEnd).padding(...)`. The change wraps it and the popup in a `Column`:

```kotlin
// Inside the WebView area Box
Column(
    modifier = Modifier
        .align(Alignment.BottomEnd)
        .padding(bottom = 24.dp, end = 24.dp),
    horizontalAlignment = Alignment.End
) {
    key(currentTabId) {
        ModelNamePopup(modelName = viewModel.modelName)
    }
    Spacer(modifier = Modifier.height(8.dp))
    AdBlockToggleButton(
        blockingEnabled = viewModel.blockingEnabled,
        enabled = !viewModel.isLoading && !viewModel.isModelBusy,
        onToggle = viewModel::toggleBlocking
    )
}
```

The `key(currentTabId)` wrapper ensures the popup recomposes (and re-animates) when the active tab changes, satisfying the "fresh fade-in on tab switch" requirement.

## Interfaces

No new public interfaces are introduced. The `ModelNamePopup` composable is `private` to `BrowserScreen.kt`.

**Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `modelName` | `String` | The model name to display, sourced from `BrowserViewModel.modelName` |
| `modifier` | `Modifier` | Standard Compose modifier for external layout adjustments |

## Data Models

No new data models are required. The feature uses the existing `BrowserViewModel.modelName: String` property which delegates to `currentModelName()`.

## Animation Behavior

| Phase | Mechanism | Duration |
|-------|-----------|----------|
| Fade-in | `AnimatedVisibility` with `fadeIn()` (default 200ms) | ~200ms |
| Visible hold | `LaunchedEffect` + `delay(2000L)` | 2000ms |
| Fade-out | `AnimatedVisibility` with `fadeOut()` (default 200ms) | ~200ms |

The `showPopup` state starts as `true`, causing immediate fade-in. After the 2-second delay, it flips to `false`, triggering the fade-out exit animation. Once the exit animation completes, `AnimatedVisibility` removes the content from the composition tree.

## Visual Style

| Property | Value | Rationale |
|----------|-------|-----------|
| Shape | `RoundedCornerShape(8.dp)` | Matches `AdBlockToggleButton` shape |
| Background | `MaterialTheme.colorScheme.surfaceContainerHigh` | Elevated surface per M3 spec |
| Text color | `MaterialTheme.colorScheme.onSurface` | Standard on-surface text |
| Shadow elevation | `4.dp` | Distinguishes from underlying content |
| Typography | `labelMedium` | Compact, readable at small size |
| Horizontal padding | `12.dp` | Comfortable text inset |
| Vertical padding | `8.dp` | Balanced vertical breathing room |
| Gap to toggle button | `8.dp` (Spacer) | Small visual separation |

## Error Handling

| Scenario | Behavior |
|----------|----------|
| Empty model name string | Popup still renders with empty text (visually thin pill). No crash. |
| Very long model name | Text renders single-line, may extend beyond expected width. Acceptable for MVP since known model names are short ("Gemini Nano", "Apple Foundation Models"). |
| Rapid tab switching | Each `key(currentTabId)` change disposes the old `LaunchedEffect` and creates a fresh one. No stale timers or memory leaks. |

## Preview Functions

Two preview functions will be added:

```kotlin
@Preview
@Composable
private fun ModelNamePopupPreview() {
    MaterialTheme {
        ModelNamePopup(modelName = "Gemini Nano")
    }
}

@Preview
@Composable
private fun ModelNamePopupLongNamePreview() {
    MaterialTheme {
        ModelNamePopup(modelName = "Apple Foundation Models")
    }
}
```

## Dynamic Model Name Resolution

### Overview

The current `currentModelName()` implementations return hardcoded strings. This section describes how each platform dynamically resolves the model name based on actual LLM availability, keeping the function synchronous via a cached value that is updated when `LlmClassifier.isAvailable()` is first called.

### Architecture

```
┌──────────────────────────────────────────────────────┐
│ commonMain                                           │
│                                                      │
│  expect fun currentModelName(): String               │
│                                                      │
│  BrowserViewModel                                    │
│    var modelName by mutableStateOf(currentModelName())│
│    fun refreshModelName() { modelName = currentModelName() } │
└──────────────────────────────────────────────────────┘
        │                               │
        ▼                               ▼
┌─────────────────────┐    ┌─────────────────────────┐
│ androidMain         │    │ iosMain                  │
│                     │    │                          │
│ LlmClassifier      │    │ LlmClassifier            │
│   companion object  │    │   companion object       │
│     cachedModelName │    │     cachedModelName      │
│                     │    │                          │
│ isAvailable() →     │    │ isAvailable() →          │
│   updates cache     │    │   updates cache          │
│                     │    │                          │
│ ModelInfo.android.kt│    │ ModelInfo.ios.kt         │
│   actual fun =      │    │   actual fun =           │
│   cachedModelName   │    │   cachedModelName        │
└─────────────────────┘    └─────────────────────────┘
```

### Data Flow

```
App Launch
    │
    ▼
BrowserViewModel.init()
    │  modelName = currentModelName()  → returns optimistic default
    │
    ▼
ModelNamePopup shows optimistic default ("Gemini Nano" / "Apple Intelligence")
    │
    ▼
First page load triggers AdBlockEngine pipeline
    │
    ▼
LlmClassifier.isAvailable() called
    │  ├─ Available: cachedModelName stays as platform LLM name
    │  └─ Unavailable: cachedModelName = "Heuristic Only"
    │
    ▼
BrowserViewModel.refreshModelName()
    │  modelName = currentModelName()  → returns resolved name
    │
    ▼
Subsequent popups show the resolved model name
```

### Android Implementation

#### LlmClassifier.android.kt

Add a `companion object` with a mutable cached name. The `isAvailable()` method already performs the availability check — it simply updates the cache as a side effect:

```kotlin
actual class LlmClassifier actual constructor() {

    companion object {
        /** Optimistic default — most target devices support Gemini Nano. */
        var cachedModelName: String = "Gemini Nano"
            private set
    }

    private var generativeModel: GenerativeModel? = null
    private var available: Boolean? = null

    actual suspend fun isAvailable(): Boolean {
        if (available != null) return available!!

        return withContext(Dispatchers.Main) {
            try {
                val model = Generation.getClient()
                val status = model.checkStatus()

                when (status) {
                    FeatureStatus.AVAILABLE -> {
                        generativeModel = model
                        available = true
                        cachedModelName = "Gemini Nano"
                        true
                    }
                    FeatureStatus.DOWNLOADABLE -> {
                        model.download().collect { downloadStatus ->
                            Log.d(TAG, "Download status: $downloadStatus")
                        }
                        generativeModel = model
                        available = true
                        cachedModelName = "Gemini Nano"
                        true
                    }
                    else -> {
                        available = false
                        cachedModelName = "Heuristic Only"
                        false
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking LLM availability", e)
                available = false
                cachedModelName = "Heuristic Only"
                false
            }
        }
    }

    // classifyElements unchanged...
}
```

#### ModelInfo.android.kt

```kotlin
actual fun currentModelName(): String = LlmClassifier.cachedModelName
```

### iOS Implementation

#### LlmClassifier.ios.kt

Add a `companion object` with the same caching pattern:

```kotlin
actual class LlmClassifier actual constructor() {

    companion object {
        /** Optimistic default — assumes Apple Intelligence is available on target devices. */
        var cachedModelName: String = "Apple Intelligence"
            private set
    }

    var swiftClassifier: IosLlmClassifierDelegate? = null

    actual suspend fun isAvailable(): Boolean {
        val available = swiftClassifier?.isAvailable() ?: false
        cachedModelName = if (available) "Apple Intelligence" else "Heuristic Only"
        return available
    }

    actual suspend fun classifyElements(elements: List<DomElement>): List<Boolean> {
        val classifier = swiftClassifier ?: return List(elements.size) { false }
        return classifier.classifyElements(elements)
    }
}
```

#### ModelInfo.ios.kt

```kotlin
actual fun currentModelName(): String = LlmClassifier.cachedModelName
```

### BrowserViewModel Changes

The `modelName` property changes from a `val` to a `var` backed by `mutableStateOf`, with a refresh method:

```kotlin
// Before:
val modelName: String = currentModelName()

// After:
var modelName by mutableStateOf(currentModelName())
    private set

fun refreshModelName() {
    modelName = currentModelName()
}
```

`refreshModelName()` is called by the ad-block pipeline after the first `isAvailable()` call completes. This ensures the popup displays the correct name from the second appearance onward.

### Timing Considerations

| Scenario | Popup displays |
|----------|---------------|
| First launch, LLM available | Optimistic default (correct) |
| First launch, LLM unavailable | Optimistic default initially → "Heuristic Only" after first page load |
| Subsequent launches (cache already set) | Correct resolved name immediately |
| Tab switch after resolution | Correct resolved name |

The optimistic default ensures the first popup appearance is meaningful even before the async availability check completes. On devices where the LLM is unavailable, the name self-corrects after the first page load triggers the pipeline.

### Error Handling

| Scenario | Behavior |
|----------|----------|
| ML Kit throws during availability check | `cachedModelName` set to "Heuristic Only" |
| iOS delegate is null (not injected) | `isAvailable()` returns false → "Heuristic Only" |
| Network error during Gemini Nano download | Exception caught → "Heuristic Only" |
| Multiple concurrent `isAvailable()` calls | The `available` guard in Android prevents re-execution; iOS delegate handles idempotently |

## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system — essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

### Property 1: Model name text fidelity

*For any* non-empty model name string provided to the `ModelNamePopup` composable, the rendered text content SHALL be exactly equal to the input string with no truncation, transformation, or modification.

**Validates: Requirements 1.2**
