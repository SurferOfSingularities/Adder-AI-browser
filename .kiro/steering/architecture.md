# Architecture Decisions

## Ad Detection Pipeline

The system uses a **hybrid detection approach**:

1. **Heuristic filter (fast, shared code):** Pattern matching on class names, IDs, known ad domains, iframe patterns, and element dimensions. Classifies elements as DEFINITE_AD, LIKELY_AD, AMBIGUOUS, or NOT_AD.
2. **LLM classification (platform-specific, for ambiguous elements only):** On-device LLM analyzes element context to make intelligent ad/not-ad decisions. Only invoked for elements the heuristic filter cannot confidently classify.

This hybrid approach minimizes LLM calls (expensive, slower) while still catching contextual/native ads that heuristics miss.

## Post-Render Cleanup

Pages load and render normally first. Ad removal happens after page load completes. This approach:
- Avoids complex request interception or MITM proxying
- Works reliably with platform WebViews
- Trades a brief "flash of ad content" for implementation simplicity (optimized later with early CSS injection)

## Ad-Blocking Toggle (Virgin View)

Users can switch between the ad-blocked view and the original ("virgin") view of a page via a floating button anchored to the bottom-right of the WebView area, fixed while the page scrolls. The button is a compact, slightly-rounded rectangle whose only content is a shield icon: a green container means blocking is on, a yellow container means blocking is off, and both colors are the same in light and dark mode.

Because ad removal is **destructive** (ad nodes are removed from the DOM, and a MutationObserver keeps removing dynamically-added ones), the toggle cannot simply reveal already-removed elements. Instead it uses a **reload-based bypass**:

- `BrowserViewModel.blockingEnabled` (default `true`) is the single source of truth. It lives in the ViewModel, so the choice **persists across navigation for the session**.
- `BrowserViewModel.toggleBlocking()` flips the flag and reloads the current page.
- Both platform WebViews receive `blockingEnabled` and read the latest value (via `rememberUpdatedState`) on each page load:
  - **Blocking on:** inject early-hide CSS and run the ad-block pipeline (existing behavior).
  - **Blocking off (virgin):** skip both the early CSS injection and the pipeline, so the page loads completely untouched.
- The button is non-interactive while the page is loading or the model is busy, to avoid mid-pipeline reloads.

Because the button carries no text label, every accepted tap also shows a short confirmation message:

- `toggleBlocking()` publishes a single-consumption `BrowserViewModel.toggleNotice` reading `Blocking : On` or `Blocking : Off` for the state the tap produced, and `BrowserScreen` clears it with `onToggleNoticeShown()` once it has been shown.
- `BrowserScreen` presents it as a Material 3 `Snackbar` for `SnackbarDuration.Short`, driven from shared `commonMain` code so Android and iOS get identical text, placement, and duration. The host sits at the bottom-center of the WebView area, above the button.
- The message survives the reload the toggle triggers, because the effect that shows it is keyed on the notice rather than on load state.
- A tap while an earlier message is still visible replaces it rather than queueing a second one, so at most one message is on screen.

This keeps the destructive removal strategy intact while giving a clean on/off switch at the cost of a reload per toggle.

## UI State Management

The browser screen follows an MVVM-style unidirectional data flow:

- **`BrowserViewModel`** (`androidx.lifecycle.ViewModel`, in `commonMain`) owns all observable UI state (`url`, `inputText`, `canGoBack`, `canGoForward`, `isLoading`, `isModelBusy`, `blockingEnabled`, `toggleNotice`, `modelName`) as Compose `mutableStateOf` and exposes intents (`onUrlSubmit`, `onBack`, `toggleBlocking`, `onToggleNoticeShown`, etc.). It is obtained in the composable via the multiplatform `viewModel { }` factory.
- **`WebViewController`** is a thin, state-free imperative bridge to the live platform WebView. The platform WebView registers its command handlers (`loadUrl`/`goBack`/`goForward`/`reload`) on creation; the ViewModel invokes them. This separation exists because those commands are tied to the live WebView instance and its composition lifecycle, so they should not live in a lifecycle-scoped ViewModel.
- **`PlatformWebView`** takes the controller plus `blockingEnabled` and reports events back to the ViewModel through callbacks (`onPageStarted`, `onPageFinished`, `onNavStateChanged`, `onModelBusyChanged`) rather than mutating shared state directly.

This gives a single source of truth for state, keeps navigation/intent logic out of composables, and survives Android configuration changes.

## Shared Module Ownership

The shared KMP module owns ALL business logic AND the UI layer via Compose Multiplatform:
- Heuristic rules and pattern matching
- LLM prompt construction
- DOM element data models and serialization
- Pipeline orchestration logic
- URL normalization
- **Browser UI (Compose Multiplatform)** — shared composable screens for both platforms

Platform modules are thin wrappers that provide:
- WebView bridge (platform-specific WebView embedded in Compose via AndroidView/UIKitView)
- JavaScript injection/callback bridges
- LLM API calls (via `expect`/`actual`)
- Application entry point (Activity / ComposeUIViewController)

## Platform LLM Integration

```
// In commonMain
expect class LlmClassifier {
    suspend fun classify(elements: List<DomElement>): List<ClassificationResult>
    suspend fun isAvailable(): Boolean
}

// In androidMain — uses Gemini Nano via ML Kit GenAI Prompt API
// In iosMain — uses Apple Foundation Models via Swift interop
```

Both platforms use the same prompts (built by shared `PromptBuilder`) for consistent classification behavior.

## DOM Communication

JavaScript ↔ Kotlin/Swift communication:
- **Extraction:** JS collects DOM metadata → serializes to JSON → passed to Kotlin/Swift via evaluateJavascript callback
- **Removal:** Kotlin/Swift determines elements to remove → passes selectors/IDs via JS injection → JS hides/removes elements

Data model: `DomElement` (kotlinx.serialization) with fields for tag, id, classes, src, text snippet, dimensions, parent context.

## Fallback Behavior

If the on-device LLM is unavailable (unsupported device, model not downloaded):
- The app still works using heuristic-only mode
- Ambiguous elements are left in place (conservative approach — don't remove things we're unsure about)
- User is informed that AI-enhanced blocking is unavailable
