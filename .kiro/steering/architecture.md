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
