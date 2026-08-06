# Design Document

## Overview

This feature has three parts, in increasing order of blast radius:

1. **Restyle the floating ad-blocking control** (Req 1–6) — a presentation-only change to a single private composable in the shared Compose Multiplatform module. The control changes from a wide, fully-rounded, labelled pill centered at the bottom of the WebView area into a compact 48dp square with an 8dp corner radius, anchored bottom-right, showing only a shield icon, coloured with fixed green/yellow values that ignore the Material color scheme.
2. **Add a toggle confirmation message** (Req 7–8) — because the control loses its text label, each accepted tap now raises a single-consumption notice on `BrowserViewModel`, which `BrowserScreen` presents as a Material 3 `Snackbar` from `commonMain`. This is the only part of the feature that touches state management.
3. **Sync the architecture docs** (Req 9) — replace the "Ad-Blocking Toggle (Virgin View)" section in both `.kiro/steering/architecture.md` and `docs/architecture.md` with identical updated content, and extend the `BrowserViewModel` state list in their "UI State Management" sections.

The control keeps its existing parameter contract (`blockingEnabled`, `enabled`, `onToggle`, `modifier`), so `BrowserScreen` only changes the alignment/padding modifier it passes in and the composable name it calls.

The composable is renamed from `AdBlockTogglePill` to `AdBlockToggleButton` because "pill" describes the shape being removed. Renaming a private composable is contained to one file.

### Scope

| Area | Change |
|---|---|
| `AdBlockTogglePill` → `AdBlockToggleButton` | Rewritten: shape, colors, content, sizing, semantics |
| `BrowserScreen` call site | Call site renamed; `Alignment.BottomCenter` + `padding(bottom = 24.dp)` → `Alignment.BottomEnd` + `padding(bottom = 24.dp, end = 24.dp)` |
| Three toggle previews | Renamed to match; parameters unchanged |
| `BrowserScreen` | Gains a remembered `SnackbarHostState`, one `LaunchedEffect`, and one `SnackbarHost` inside the WebView area |
| `BrowserViewModel` | Gains `toggleNotice` state, `onToggleNoticeShown()`, and two message constants; `toggleBlocking()` publishes the notice |
| Two snackbar previews | New |
| `.kiro/steering/architecture.md`, `docs/architecture.md` | Toggle section replaced; ViewModel state list extended. Identical edits in both. |
| `PlatformWebView` (android/ios `actual`) | Unchanged |
| Everything else | Unchanged |

Code edits land in two files, both in `shared/src/commonMain/kotlin/com/adder/shared/ui/`: `BrowserScreen.kt` and `BrowserViewModel.kt`.

## Architecture

The control's place in the existing unidirectional data flow is unchanged; the notice adds one downward channel and one upward acknowledgement:

```
BrowserViewModel                    BrowserScreen                 AdBlockToggleButton
  blockingEnabled: Boolean  ──────▶ reads state, derives  ──────▶ blockingEnabled
  isLoading: Boolean                enabled flag, passes          enabled
  isModelBusy: Boolean              modifier                      onToggle
  toggleBlocking()          ◀────────────────────────────────────  tap
  toggleNotice: String?     ──────▶ LaunchedEffect →
                                    SnackbarHost
  onToggleNoticeShown()     ◀────── after the snackbar finishes
```

`AdBlockToggleButton` stays a stateless, side-effect-free composable: every visual output is a pure function of its two Boolean parameters. It holds no `remember`, reads no ambient theme value for color, and performs no platform calls — which is what makes the three previews meaningful and what makes the light/dark invariance structural rather than incidental. It knows nothing about the notice; publishing is entirely the ViewModel's job, triggered by the same `onToggle` callback that already existed.

### Layout placement

The control remains the second child of the `Box` that wraps `PlatformWebView` inside the insets-padded `Column`. Declaration order gives it a higher z-index than the WebView, so it draws over page content, and because it is a sibling of the WebView rather than a child of the web document, it does not move when the page scrolls (Req 1.4). The snackbar host is declared third, so it draws above both (Req 8.5).

```
Column (windowInsetsPadding(safeDrawing))
├── Box (weight = 1f)                  ← WebView_Area
│   ├── PlatformWebView(fillMaxSize)
│   ├── AdBlockToggleButton            ← align(BottomEnd), padding(bottom = 24, end = 24)
│   └── SnackbarHost                   ← align(BottomCenter), padding(bottom = 88)
├── LinearProgressIndicator (when isLoading)
└── BrowserToolbar
```

Because the parent `Column` already applies `WindowInsets.safeDrawing` padding and the toggle lives inside the weighted `Box` (which ends where the toolbar begins), the 24dp bottom padding measures from the top of the toolbar, not from the screen edge. The 24dp end padding measures from the safe-area right edge. Together these satisfy Req 1.3's intent of clearing both the toolbar and the screen edge.

The snackbar's 88dp bottom padding is derived from the control it must clear: 24dp of toggle bottom padding + 48dp of toggle height = 72dp, so the toggle's top edge sits 72dp above the bottom of the WebView area. 88dp leaves a nominal 16dp gap above it (Req 8.4). The nominal figure understates the visible gap: Material 3's `Snackbar` carries its own 12dp of padding inside the host, so the actual space between the toggle's top edge and the snackbar container is closer to 28dp. Req 8.4 only asks that the message clear the control, which holds either way. Both paddings are measured from the same origin, so the two numbers stay consistent as long as they are changed together.

## Components and Interfaces

### `AdBlockToggleButton`

Public signature is unchanged from `AdBlockTogglePill` apart from the name, so Req 5.4's "existing parameter contract" holds by construction and the compiler catches any call-site mismatch.

```kotlin
@Composable
private fun AdBlockToggleButton(
    blockingEnabled: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
)
```

| Parameter | Meaning |
|---|---|
| `blockingEnabled` | Drives container color, icon tint, and content description. Nothing else. |
| `enabled` | Drives clickability only. Deliberately does **not** affect color (Req 3.6). |
| `onToggle` | Invoked once per accepted tap. |
| `modifier` | Alignment and outer padding, supplied by `BrowserScreen`. |

### Implementation

```kotlin
@Composable
private fun AdBlockToggleButton(
    blockingEnabled: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor = if (blockingEnabled) BlockingOnContainer else BlockingOffContainer
    val iconColor = if (blockingEnabled) BlockingOnIcon else BlockingOffIcon
    val description = if (blockingEnabled) {
        "Ad blocking on. Tap to turn off."
    } else {
        "Ad blocking off. Tap to turn on."
    }

    Surface(
        onClick = onToggle,
        enabled = enabled,
        shape = RoundedCornerShape(8.dp),
        color = containerColor,
        contentColor = iconColor,
        // Tonal elevation is intentionally 0: M3 tonal tinting would only apply to
        // theme surface colors anyway, and leaving it off keeps the container color
        // exactly the literal above under every color scheme.
        tonalElevation = 0.dp,
        shadowElevation = 6.dp,
        modifier = modifier
    ) {
        Icon(
            imageVector = Icons.Filled.Shield,
            contentDescription = description,
            modifier = Modifier
                .padding(12.dp)
                .size(24.dp)
        )
    }
}
```

Design notes on this shape:

- **Modifier order matters.** `.padding(12.dp)` before `.size(24.dp)` gives a 24dp icon with 12dp on all four sides, so the `Surface` measures 48dp × 48dp — satisfying the square proportion (Req 2.4) and the 48dp touch target (Req 4.3) from the same two numbers rather than from a separate `defaultMinSize`. Reversing the order would size the padded box to 24dp and shrink the glyph.
- **`Icon` inherits its tint from `LocalContentColor`,** which `Surface(contentColor = iconColor)` provides. This keeps one source for the content color and means any future child of the container picks it up automatically.
- **`contentDescription` lives on the `Icon`,** matching Req 4.1/4.2's wording. Since `Surface`'s clickable overload contributes the button role and click action, the icon's description becomes the accessible name announced for the control.
- **Material3's clickable `Surface` does not substitute disabled colors.** Passing `enabled = false` only removes the click handling and ripple; the `color`/`contentColor` arguments are honoured verbatim. That is what lets Req 3.6 hold in `Interaction_Blocked_State` without special-casing.

### Color constants

Declared as private file-level `val`s next to the composable, using hardcoded `Color` literals. There is no reference to `MaterialTheme.colorScheme` anywhere in the color logic, which is how light/dark invariance (Req 3.3) is enforced structurally instead of by convention.

```kotlin
private val BlockingOnContainer = Color(0xFF2E7D32)  // green
private val BlockingOnIcon = Color(0xFFFFFFFF)
private val BlockingOffContainer = Color(0xFFF9C82E) // yellow
private val BlockingOffIcon = Color(0xFF1F1B00)
```

The fixed-color rule applies to the toggle only. The snackbar keeps the default Material 3 colors (`inverseSurface` / `inverseOnSurface`), so it adapts to light and dark like the rest of the chrome — nothing in Req 8 asks for fixed values there, and overriding them would mean re-deriving contrast pairs for no benefit.

### `BrowserViewModel` — toggle notice state

The notice is modelled as a **single nullable slot of Compose state plus a consume function**:

```kotlin
/**
 * Pending toggle confirmation message, or `null` when there is nothing to
 * show. Single-consumption: the screen calls [onToggleNoticeShown] once it
 * has presented the message. A single nullable slot is what makes "at most
 * one pending notice" structural rather than enforced.
 */
var toggleNotice by mutableStateOf<String?>(null)
    private set

/**
 * Flips ad blocking on/off, publishes the confirmation message for the new
 * state, and reloads the current page so the new mode takes effect.
 */
fun toggleBlocking() {
    blockingEnabled = !blockingEnabled
    // Derived *after* the flip, so the message names the state this tap
    // produced rather than the one it replaced.
    toggleNotice = if (blockingEnabled) NOTICE_BLOCKING_ON else NOTICE_BLOCKING_OFF
    webViewController.reload()
}

/** Marks the pending [toggleNotice] as presented, so it is shown only once. */
fun onToggleNoticeShown() {
    toggleNotice = null
}

private companion object {
    const val INITIAL_URL = "https://www.google.com"
    const val NOTICE_BLOCKING_ON = "Blocking : On"
    const val NOTICE_BLOCKING_OFF = "Blocking : Off"
}
```

The exact strings, including the spaces around the colon, live in named constants so the Req 7.3/7.4 literals appear once and previews can reference the same wording without duplicating it by hand.

Ordering inside `toggleBlocking()` matters and is the one thing a future edit can quietly break: the message must be derived from the flipped value. Assigning `toggleNotice` on the line after the flip, and never reading `blockingEnabled` before it, is what makes Req 7.2 hold. The `reload()` call goes last; it is an imperative side effect on the live WebView and does not read either piece of state.

#### Why a nullable state slot rather than `Channel` or `SharedFlow`

| Option | Fit |
|---|---|
| `mutableStateOf<String?>` + consume function | **Chosen.** Every other piece of ViewModel state here is `mutableStateOf`, so the screen reads the notice the same way it reads `isLoading` — no new dependency, no collector to wire, no `viewModelScope`. The single slot gives Req 7.10 ("at most one pending") for free: a second publication overwrites the first, because there is nowhere for the first to queue. |
| `Channel(CONFLATED)` + `receiveAsFlow` | Rejected. Would give the same conflation, but introduces the only flow collection in the module and requires the screen to collect in a scope, for a payload that is one nullable string. It also makes the pending value unreadable for debugging, since receiving removes it. |
| `MutableSharedFlow(replay = 0)` | Rejected, and subtly wrong for this case: with no replay, a notice emitted while the screen is not collecting is lost, which conflicts with Req 7.8's requirement that the message survive an Android configuration change. Adding replay to fix that reintroduces the replay-on-recreate double-show problem the single-consumption design exists to avoid. |

The nullable slot also satisfies Req 7.8 by placement alone: it lives in the `androidx.lifecycle.ViewModel`, which is retained across Android activity recreation, so a notice published just before a rotation is still pending afterwards and gets shown once.

One assumption is worth writing down: the screen keys its effect on the notice *value*, so two consecutive notices must differ. That holds because the only producer is a strict flip, which always alternates `Blocking : Off` / `Blocking : On`. If a second producer is ever added (for example a whitelist rule forcing blocking off), two identical consecutive messages become possible and the key must become an identity-bearing wrapper, e.g. `data class ToggleNotice(val message: String, val id: Long)`. A comment on `toggleNotice` records this so the constraint is visible at the point where it would be broken.

### `BrowserScreen` — call site and snackbar wiring

```kotlin
@Composable
fun BrowserScreen(
    viewModel: BrowserViewModel = viewModel { BrowserViewModel() }
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val notice = viewModel.toggleNotice

    // Keyed on the notice, deliberately not on isLoading: the reload started by
    // toggleBlocking() must not restart or cancel the message (Req 8.6). A newer
    // notice does cancel it, which dismisses the visible snackbar and shows the
    // new text instead of queueing behind it (Req 8.7).
    LaunchedEffect(notice) {
        if (notice != null) {
            snackbarHostState.showSnackbar(
                message = notice,
                duration = SnackbarDuration.Short
            )
            viewModel.onToggleNoticeShown()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // ... unchanged Column / WebView_Area ...
    }
}
```

Inside the WebView area, after `PlatformWebView` and after the toggle:

```kotlin
// Floating ad-blocking toggle button — floats above the toolbar
AdBlockToggleButton(
    blockingEnabled = viewModel.blockingEnabled,
    enabled = !viewModel.isLoading && !viewModel.isModelBusy,
    onToggle = viewModel::toggleBlocking,
    modifier = Modifier
        .align(Alignment.BottomEnd)
        .padding(bottom = 24.dp, end = 24.dp)
)

// Toggle confirmation message — declared last in this Box so it draws above
// both the page content and the toggle button.
SnackbarHost(
    hostState = snackbarHostState,
    modifier = Modifier
        .align(Alignment.BottomCenter)
        .padding(bottom = 88.dp)
)
```

The surrounding comment ("Floating ad-blocking toggle pill") is updated so it stops describing a pill.

The default `SnackbarHost` content renders `Snackbar(snackbarData)`, which shows the message text alone when no action label was supplied, and carries Material 3's built-in live-region semantics. Passing only `message` and `duration` to `showSnackbar` is therefore what satisfies Req 8.3 and Req 8.8 — no custom content slot, no `actionLabel`.

#### Why the message survives the reload

Three separate mechanisms have to hold, and each is a consequence of where things are declared rather than of extra defensive code:

- **The effect is keyed on `notice`, not on load state.** `onPageStarted`/`onPageFinished` write `isLoading`, `isModelBusy`, and `inputText`. Those recompose `BrowserScreen`, but `LaunchedEffect` restarts only when its key changes, so the in-flight `showSnackbar` call is untouched (Req 8.6).
- **Nothing removes the effect or the host from the composition.** Both are declared unconditionally — the effect at the top of `BrowserScreen`, the host inside the always-present weighted `Box`. The conditional siblings (progress indicator, history overlay, busy scrim) appear and disappear around them without leaving the composition. A `LaunchedEffect` cancels when it leaves the composition, so keeping it out of every `if` is what makes the survival claim structural.
- **The reload does not recompose the WebView subtree.** `toggleBlocking()` reaches the live WebView through `webViewController.reload()`, an imperative call, so no parameter of `PlatformWebView` changes and no view is recreated.

One visible side effect of the reload remains: when `isLoading` flips true, the 3dp `LinearProgressIndicator` is inserted between the WebView area and the toolbar, which shrinks the weighted `Box` by 3dp and nudges the snackbar and toggle up by the same amount. It is a 3dp shift on an already-animating surface; not worth restructuring the layout to avoid.

#### Interaction with the model-busy scrim

The busy scrim is drawn in the root `Box` **after** the `Column`, filling the screen at 30% `scrim` alpha. It therefore paints over the snackbar as well as over the page, so a snackbar still visible when the ad-block pipeline starts inference will be tinted.

This can happen: the toggle is disabled while `isModelBusy` is true, so a tap can never *start* during inference, but the sequence tap → reload → page finished → pipeline runs can bring the scrim up inside the snackbar's ~4s window.

**Position: accept the tinting.** Three reasons.

- The requirements pin the host inside the WebView area, declared after the toggle (Req 8.4, 8.5). Drawing the snackbar above the scrim would mean hoisting the host to the root `Box` after the scrim, which contradicts both.
- The scrim is a deliberate "the whole app is busy" signal. A control or message that stayed at full brightness through it would read as still-interactive.
- Legibility survives it. The scrim darkens the snackbar container and its text together, and the default `inverseSurface`/`inverseOnSurface` pair starts far above the 4.5:1 minimum, so the tinted result stays comfortably readable.

This is recorded as a known, accepted visual interaction and is on the manual verification list. If a device check shows it is worse than expected, the cheapest fix that keeps the required placement is lowering the scrim alpha, not moving the host.

#### Interaction with the history overlay

Same class of finding, stronger effect: the history overlay is also drawn in the root `Box` after the `Column` and fills the screen opaquely, so it does not tint a visible snackbar — it covers it completely.

**Position: accept this too.** Reaching it requires opening the history overlay inside the message's ~4s display window, immediately after a toggle tap. In that sequence the user has already moved on to a different task, and the message they just triggered has served its purpose. Avoiding it would mean the same hoist-the-host-above-the-overlay restructuring that Req 8.4/8.5 rule out, for a narrower case than the scrim. The notice is not consumed while it is obscured — `showSnackbar` keeps running underneath — so no state is lost; only the last part of the display window is hidden.

### Imports

`androidx.compose.ui.graphics.Color` is added. `Icons.Filled.Shield`, `RoundedCornerShape`, and `dp` are already imported. `SnackbarHost`, `SnackbarHostState`, `SnackbarDuration`, and `Snackbar` arrive through the existing `androidx.compose.material3.*` wildcard, and `remember`/`LaunchedEffect` through `androidx.compose.runtime.*`. `Spacer`/`Row`/`Text` imports stay because `BrowserToolbar` and `AppMenu` in the same file still use them — no import cleanup is needed or wanted.

## Data Models

No serialized or persisted model. The feature adds exactly one piece of in-memory state:

| State | Type | Owner | Lifetime |
|---|---|---|---|
| `toggleNotice` | `String?` | `BrowserViewModel` | Set by `toggleBlocking()`, cleared by `onToggleNoticeShown()`. Retained across Android configuration changes with the rest of the ViewModel. |

Everything else read by this feature is the existing `Boolean` triple on `BrowserViewModel` (`blockingEnabled`, `isLoading`, `isModelBusy`), and the only other state written is via the existing `toggleBlocking()` intent. Nothing is added to the detection pipeline, the JS bridges, or any persistent store.

## Documentation Changes (Req 9)

Both `.kiro/steering/architecture.md` and `docs/architecture.md` are edited. The two files currently hold identical text in the two affected sections, and both edits below must be applied **character-identically** to each file in the same change set (Req 9.7, 9.8).

### Replacement for the "Ad-Blocking Toggle (Virgin View)" section

The existing section is replaced in full by:

```markdown
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
```

Checked against Req 9.1–9.6: bottom-right rectangular icon-only button fixed while scrolling (9.1); the word "pill" is gone and "button" is used throughout (9.2); green/yellow meaning with light/dark parity (9.3); non-interactive while loading or the model is busy (9.4); the exact `Blocking : On` / `Blocking : Off` snackbar text and its shared-code driver (9.5); message survives the reload (9.6).

### Update to the "UI State Management" section

That section enumerates the ViewModel's state fields and intents, so it goes stale with this change. One bullet is edited in both files — `toggleNotice` added to the state list and `onToggleNoticeShown` to the intent list:

```markdown
- **`BrowserViewModel`** (`androidx.lifecycle.ViewModel`, in `commonMain`) owns all observable UI state (`url`, `inputText`, `canGoBack`, `canGoForward`, `isLoading`, `isModelBusy`, `blockingEnabled`, `toggleNotice`, `modelName`) as Compose `mutableStateOf` and exposes intents (`onUrlSubmit`, `onBack`, `toggleBlocking`, `onToggleNoticeShown`, etc.). It is obtained in the composable via the multiplatform `viewModel { }` factory.
```

The rest of that section is untouched.

Two notes on the surrounding files:

- The pre-existing history state (`historyVisible`, `historyEntries`) is also absent from that list. Adding it is out of scope here and is left alone rather than folded into this change set.
- The two files already differ outside the edited sections: the `Platform LLM Integration` fence is untagged in the steering copy and tagged `kotlin` in the `docs/` copy. Req 9.7 only constrains the toggle section, so this is noted rather than fixed — worth a separate cleanup.

## Accessibility

| Concern | Resolution |
|---|---|
| Icon-only control has no visible label | `contentDescription` names both the current state and the action ("Ad blocking on. Tap to turn off."), so screen-reader users get more than the old visual label conveyed. |
| State communicated by color alone | The content description carries the state textually, and the post-tap snackbar states the resulting mode in words, so the color is a redundant cue rather than the sole one. |
| Touch target | 24dp icon + 12dp padding per side = 48dp × 48dp, meeting the minimum without extra modifiers. |
| Icon contrast | Computed once against the fixed container colors (below); both pass WCAG AA for non-text/graphical content and for text at 4.5:1. |
| Confirmation is announced, not just drawn | The default Material 3 `Snackbar` used by `SnackbarHost` carries live-region semantics, so the message text is announced when it appears without extra semantics code (Req 8.8). |
| Message readable long enough | `SnackbarDuration.Short` is the Material default timing and is not shortened by the reload, so the announcement is not cut off mid-utterance. |

Contrast ratios for the fixed pairs, computed from WCAG relative luminance:

| State | Icon | Container | Ratio | AA 4.5:1 |
|---|---|---|---|---|
| Blocking on | `#FFFFFF` | `#2E7D32` | ≈ 5.0:1 | pass |
| Blocking off | `#1F1B00` | `#F9C82E` | ≈ 11.0:1 | pass |

These are static facts about constants, so they are verified by this one-time calculation rather than by a repeated test. If either color literal is ever changed, the ratio must be recomputed.

Full WCAG conformance is not established by these checks alone — it needs manual testing with assistive technologies and expert review. The TalkBack/VoiceOver pass in the verification list covers the parts this feature can be responsible for.

## Error Handling

The composables perform no I/O, no parsing, and take no nullable or out-of-range inputs beyond `toggleNotice`, whose `null` case is the explicit "nothing to show" state. The one piece of async work — `showSnackbar` — is a suspending call inside a `LaunchedEffect`, so its only non-happy path is cancellation, and cancellation is load-bearing here rather than exceptional:

| Situation | Behavior |
|---|---|
| A newer notice arrives while a snackbar is visible | The effect restarts, cancelling `showSnackbar`. The host clears the visible snackbar and shows the new text. The superseded notice is never marked consumed, which is correct — it was replaced, not shown to completion (Req 8.7). |
| The screen leaves the composition while a snackbar is visible | The effect is cancelled with the composition; `toggleNotice` stays pending in the retained ViewModel and is shown once when the screen returns. |
| `toggleNotice` is `null` | The effect body no-ops. No snackbar, no consume call. |

The remaining failure modes are development-time and are handled by prevention rather than recovery:

| Risk | Mitigation |
|---|---|
| A future edit reintroduces theme-derived colors, breaking light/dark invariance | Colors are file-level constants with no `MaterialTheme` reference; the invariance is stated as Property 1 below. |
| Modifier order flipped, shrinking the glyph or the touch target | Ordering is called out in the implementation comment; the enabled previews make the regression visible immediately. |
| Content description dropped back to `null` when someone copies the old icon-only pattern | Stated as Property 2; the description is a required positional concern in the single `Icon` call. |
| `tonalElevation` reintroduced, tinting the container | Explicitly set to `0.dp` with a comment explaining why. |
| Control drifts under the bottom toolbar or system navigation bar | Placement inside the weighted `Box` under the existing `safeDrawing` padding is unchanged from the working pill; only alignment and padding values differ. |
| The notice message is derived before the flip, so it names the previous state | The derivation sits on the line after the flip with a comment saying why; Property 4 states the rule. |
| The effect is re-keyed on `isLoading` (or the host moved into a conditional branch), cutting the message short on reload | Both are called out in code comments and stated as Property 6. This is the regression most likely to be introduced by an unrelated "fix". |
| A second notice producer is added, making two identical consecutive messages possible and defeating the value key | Documented on `toggleNotice` with the identity-wrapper escape hatch spelled out. |
| The two architecture docs drift | Property 7 states the section must be byte-identical; the change set edits both files together. |

Behavior when the on-device LLM is unavailable is untouched — `isModelBusy` still gates interaction the same way, so the heuristic-only fallback path is unaffected.

## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system — essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

Per project conventions this codebase has no automated test suite; manual device verification and Compose previews are the validation method. The properties below are therefore specified as correctness statements to be checked exhaustively by inspection, preview rendering, and scripted manual sequences. Properties 1–4 have small finite input domains (two to eight cases), so exhaustive checking is complete and randomized generation would add no coverage. Properties 5 and 6 quantify over sequences of events, where the interesting cases are enumerable by hand (single tap, double tap mid-message, tap then reload, tap then rotate).

### Property 1: Container and icon colors are a function of the blocking state alone

For any combination of `blockingEnabled`, `enabled`, and ambient `MaterialTheme` color scheme (light or dark), the rendered container color and icon tint are exactly `#2E7D32`/`#FFFFFF` when `blockingEnabled` is true and exactly `#F9C82E`/`#1F1B00` when `blockingEnabled` is false — never varying with `enabled` or with the color scheme.

**Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 5.3**

### Property 2: The content description always names the current blocking state

For any value of `blockingEnabled`, the shield icon exposes a non-null content description equal to "Ad blocking on. Tap to turn off." when blocking is on and "Ad blocking off. Tap to turn on." when blocking is off, so the state is never conveyed by color alone.

**Validates: Requirements 4.1, 4.2, 5.3**

### Property 3: A tap's effects are determined solely by the enabled flag

For any combination of `blockingEnabled` and `enabled`, a single tap on the control invokes `onToggle` exactly once when `enabled` is true and exactly zero times when `enabled` is false; in the disabled case both `BrowserViewModel.blockingEnabled` and `BrowserViewModel.toggleNotice` are left unchanged, so a rejected tap produces neither a mode change nor a message.

**Validates: Requirements 5.1, 5.2, 7.7**

### Property 4: The notice message always names the post-flip blocking state

For any starting value of `blockingEnabled` and any number of consecutive `toggleBlocking()` invocations, after each invocation `toggleNotice` is non-null and equals exactly `Blocking : On` when `blockingEnabled` is then true and exactly `Blocking : Off` when it is then false — never the message for the state the tap replaced.

**Validates: Requirements 7.1, 7.2, 7.3, 7.4, 7.5, 7.6**

### Property 5: At most one notice is ever pending, and consumption clears it

For any interleaving of `toggleBlocking()` and `onToggleNoticeShown()` calls, `toggleNotice` holds at most one message at any instant: `onToggleNoticeShown()` leaves it `null`, and a `toggleBlocking()` call while a message is still pending replaces that message with the newest one rather than queueing behind it. Consequently no single publication is ever presented to completion twice — a publication whose display is interrupted before it finishes (by the screen leaving the composition) stays pending and is presented again, but once `onToggleNoticeShown()` has run it can never be presented again — and the pending message — including across an Android configuration change — is always the one derived from the current `blockingEnabled` value.

**Validates: Requirements 7.8, 7.9, 7.10**

### Property 6: The presented snackbar shows the newest notice verbatim, for its full duration

For any consumed notice and any interleaving of `onPageStarted`, `onPageFinished`, and `onModelBusyChanged` events arriving during its display window, the visible snackbar shows that notice's message text verbatim as its only content, stays visible for the full `SnackbarDuration.Short` period, and is never accompanied by a second snackbar; the only thing that ends it early is a newer notice, which replaces the visible message rather than queueing after it.

**Validates: Requirements 8.2, 8.3, 8.6, 8.7**

### Property 7: The toggle documentation section is identical in both architecture docs

For any state of the repository after this change set, the "Ad-Blocking Toggle (Virgin View)" section in `.kiro/steering/architecture.md` and the same section in `docs/architecture.md` are character-identical, as is the edited `BrowserViewModel` bullet in their "UI State Management" sections.

**Validates: Requirements 9.7, 9.8**

### Non-property invariants

These hold in every state but have no varying input, so they are recorded as static assertions rather than properties: the 8dp corner radius (Req 1.1), `Alignment.BottomEnd` placement with 24dp bottom/end padding (Req 1.2, 1.3), 6dp shadow elevation (Req 1.5), shield-only content with no text (Req 2.1, 2.2), 24dp icon with 12dp padding yielding a 48dp square touch target (Req 2.3, 2.4, 4.3), z-order above the WebView (Req 1.4), the unchanged parameter contract at the call site (Req 5.4), the preview set's shape and privacy (Req 6.1–6.5), a single `SnackbarHost` declared in `commonMain` (Req 8.1), its `BottomCenter` placement with 88dp bottom padding declared after the WebView and the toggle (Req 8.4, 8.5), the default Material 3 announcement semantics (Req 8.8), and the six content claims the replacement doc section must make (Req 9.1–9.6).

## Testing Strategy

No automated tests, per the project's testing convention. Verification is preview-based plus manual on-device checks.

### Preview coverage

Five previews, all `private`, co-located in `BrowserScreen.kt`, each wrapping its body in `MaterialTheme { }` and passing static values (Req 6.1–6.5). Names track the rename:

| Preview | Input | Verifies |
|---|---|---|
| `AdBlockToggleButtonBlockingOnPreview` | `blockingEnabled = true`, `enabled = true` | Green container, white shield, 8dp radius, square shape |
| `AdBlockToggleButtonBlockingOffPreview` | `blockingEnabled = false`, `enabled = true` | Yellow container, dark shield |
| `AdBlockToggleButtonDisabledPreview` | `blockingEnabled = true`, `enabled = false` | Colors identical to the on-state preview despite being disabled |
| `ToggleNoticeSnackbarOnPreview` | static text `Blocking : On` | Snackbar renders the message as its only content, no action affordance |
| `ToggleNoticeSnackbarOffPreview` | static text `Blocking : Off` | Same, for the off-state wording |

The disabled preview is the direct visual check for Property 1's independence from `enabled`: it must be indistinguishable from the blocking-on preview.

The two snackbar previews render the Material 3 `Snackbar` directly with static text, which is the same composable the default `SnackbarHost` content produces:

```kotlin
@Preview
@Composable
private fun ToggleNoticeSnackbarOnPreview() {
    MaterialTheme {
        Snackbar { Text("Blocking : On") }
    }
}
```

`SnackbarHost` itself is **not** previewed. Its content is driven entirely by `SnackbarHostState`, which only has a visible snackbar while a suspending `showSnackbar` call is in flight; a preview has no coroutine driving it, so the host renders empty and the preview would assert nothing. Previewing the `Snackbar` it delegates to gives the same pixels with static input. For the same reason there is no preview of the notice wiring — `LaunchedEffect` plus ViewModel state is behavior, not layout, and belongs in the manual checks below.

`BrowserScreen`, `App`, and `PlatformWebView` remain unpreviewable because they embed the `expect`/`actual` native WebView — the existing file comment documenting this stays.

### Manual device verification

Run on both Android and iOS.

Restyled control:

1. Control renders bottom-right of the WebView area, clear of the toolbar and the screen edge.
2. Scroll a long page: the control stays fixed and draws above page content.
3. Tap: color flips green ↔ yellow and the page reloads with the new blocking mode.
4. During page load and during model inference: taps are ignored, the color does not change, **and no message appears** (Property 3).
5. Render previews (or run the app) under light and dark system themes: container and icon colors are identical.
6. Screen reader (TalkBack / VoiceOver): the control announces the state-specific description and reads as a button.
7. Layout inspector: the control measures 48dp × 48dp.

Toggle notice:

8. Tap while blocking is on: the message reads exactly `Blocking : Off`, spaces around the colon included. Tap again: exactly `Blocking : On` (Property 4).
9. The message appears above the toggle button and above the toolbar, and does not cover either.
10. Watch a full display window through the reload it triggers: the message stays up for the normal short duration and is not cut short when the progress indicator appears or the page finishes (Property 6). Expect a 3dp upward nudge as the progress indicator appears.
11. Tap again as soon as the control re-enables, while the first message is still visible: the first message is replaced by the second, and no second message queues up behind it (Property 6, Property 5).
12. Let a message run to completion, then wait: no message reappears, and no message shows twice (Property 5).
13. Android only — rotate the device while a message is visible: the message is shown once for the rotated layout and the blocking state is unchanged (Req 7.8).
14. Trigger inference on an ad-heavy page immediately after a toggle so the busy scrim comes up over a visible message: confirm the tinted message is still readable. This is the accepted interaction recorded above, not a defect.
15. Screen reader: the message text is announced when it appears (Req 8.8).

Documentation:

16. Diff the "Ad-Blocking Toggle (Virgin View)" section and the edited `BrowserViewModel` bullet between `.kiro/steering/architecture.md` and `docs/architecture.md`: no differences (Property 7).
17. Read the replaced section against Req 9.1–9.6, and confirm the word "pill" does not appear in it.
