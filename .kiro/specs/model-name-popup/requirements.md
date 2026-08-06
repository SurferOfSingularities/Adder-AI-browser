# Requirements Document

## Introduction

A transient popup that displays the on-device LLM model name each time the browser screen is composed. The popup appears directly above the floating ad-block toggle (shield) button in the bottom-right corner, fades in on appear, remains visible for approximately 2 seconds, then fades out and disappears completely. It uses a Material 3 Surface with elevation for a solid, branded look consistent with the app's color scheme.

## Glossary

- **ModelNamePopup**: A composable UI element that displays the current on-device LLM model name as a transient overlay within the WebView area of BrowserScreen.
- **AdBlockToggle**: The existing floating shield button positioned at Alignment.BottomEnd in the WebView area, used to toggle ad blocking on and off.
- **BrowserScreen**: The main shared Compose Multiplatform screen hosting the WebView, toolbar, and overlays.
- **BrowserViewModel**: The ViewModel that owns all observable UI state for BrowserScreen, including the `modelName` property.
- **currentModelName()**: An expect/actual function declared in commonMain that returns a human-readable string identifying the on-device LLM available on the current platform.
- **LlmClassifier**: The expect/actual class responsible for on-device LLM classification. On Android it wraps Gemini Nano via ML Kit; on iOS it delegates to Apple Foundation Models via Swift interop.
- **Heuristic Only**: The fallback mode string displayed when no on-device LLM is available, indicating the app uses pattern-matching heuristics exclusively for ad detection.

## Requirements

### Requirement 1: Popup Display on Screen Composition

**User Story:** As a user, I want to see which AI model is powering the ad blocker each time I open the browser, so that I have confidence the on-device AI is active.

#### Acceptance Criteria

1. WHEN BrowserScreen is composed, THE ModelNamePopup SHALL become visible by fading in from fully transparent to fully opaque.
2. THE ModelNamePopup SHALL display the model name string provided by BrowserViewModel.modelName.
3. WHEN BrowserScreen is recomposed due to tab switching or navigation, THE ModelNamePopup SHALL appear again with a fresh fade-in animation.

### Requirement 2: Popup Timing and Fade-Out

**User Story:** As a user, I want the model name popup to disappear automatically after a brief display, so that it does not obstruct my browsing.

#### Acceptance Criteria

1. WHEN the ModelNamePopup has been visible for approximately 2 seconds, THE ModelNamePopup SHALL begin fading out from fully opaque to fully transparent.
2. WHEN the fade-out animation completes, THE ModelNamePopup SHALL be removed from the visible composition entirely.
3. THE ModelNamePopup SHALL use Compose animation APIs (animateFloatAsState or AnimatedVisibility) for both fade-in and fade-out transitions.

### Requirement 3: Popup Positioning

**User Story:** As a user, I want the popup to appear near the blocking button so the model information is visually associated with the ad-blocking feature.

#### Acceptance Criteria

1. THE ModelNamePopup SHALL be positioned directly above the AdBlockToggle button, forming a vertical stack in the bottom-right corner of the WebView area.
2. THE ModelNamePopup SHALL use end-aligned horizontal positioning matching the AdBlockToggle button's end padding of 24.dp.
3. THE ModelNamePopup SHALL maintain a small vertical gap between itself and the top edge of the AdBlockToggle button.

### Requirement 4: Popup Visual Style

**User Story:** As a user, I want the popup to look consistent with the app's design language so it feels like a native part of the interface.

#### Acceptance Criteria

1. THE ModelNamePopup SHALL render as a Material 3 Surface with elevation (shadow) to distinguish it from the underlying content.
2. THE ModelNamePopup SHALL use the app's MaterialTheme color scheme for background and text colors.
3. THE ModelNamePopup SHALL display the model name text using a compact typography style (labelMedium or bodySmall) with appropriate horizontal and vertical padding.
4. THE ModelNamePopup SHALL use a rounded corner shape consistent with the AdBlockToggle button's RoundedCornerShape.

### Requirement 5: Dynamic Model Name Resolution

**User Story:** As a user, I want the popup to display the actual model available on my device rather than a hardcoded name, so that I get accurate information about what's powering ad detection.

#### Acceptance Criteria

1. THE `currentModelName()` expect/actual function SHALL determine the model name dynamically based on actual device capabilities and model availability rather than returning a hardcoded string.
2. WHILE the Android platform implementation is active, WHEN the Gemini Nano model is available (FeatureStatus.AVAILABLE or after successful download), THE `currentModelName()` function SHALL return "Gemini Nano". WHEN the model is unavailable, THE `currentModelName()` function SHALL return "Heuristic Only".
3. WHILE the iOS platform implementation is active, WHEN Apple Foundation Models are available (SystemLanguageModel.default.availability is .available), THE `currentModelName()` function SHALL return "Apple Intelligence". WHEN Apple Foundation Models are unavailable, THE `currentModelName()` function SHALL return "Heuristic Only".
4. THE model name resolution SHALL remain synchronous to avoid complexity in the UI layer. Platform implementations MAY cache the result of an initial availability check performed at startup or first LLM use.
5. THE BrowserViewModel.modelName property SHALL continue to use `currentModelName()` and reflect the dynamically resolved name without requiring additional ViewModel changes.
