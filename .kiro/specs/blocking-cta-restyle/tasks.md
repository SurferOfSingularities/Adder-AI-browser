# Implementation Plan: Blocking CTA Restyle

## Overview

Three parts, in increasing order of blast radius:

1. **Restyle the floating control** (Req 1–6) — presentation-only change inside `shared/src/commonMain/kotlin/com/adder/shared/ui/BrowserScreen.kt`. The ad-blocking pill becomes a compact 48dp square button with an 8dp corner radius, anchored bottom-right, showing only a shield icon, coloured with fixed green/yellow literals that ignore the Material color scheme.
2. **Add the toggle confirmation message** (Req 7–8) — `BrowserViewModel.kt` gains a single-consumption `toggleNotice` slot, and `BrowserScreen.kt` presents it as a Material 3 `Snackbar` from `commonMain`.
3. **Sync the architecture docs** (Req 9) — the "Ad-Blocking Toggle (Virgin View)" section and the `BrowserViewModel` state bullet are replaced identically in `.kiro/steering/architecture.md` and `docs/architecture.md`.

Code edits land in exactly two files: `BrowserScreen.kt` and `BrowserViewModel.kt`. Everything touching `BrowserScreen.kt` is sequenced so no two tasks write it concurrently; the `BrowserViewModel.kt` work and the docs sync are independent and start in parallel. The composable's parameter contract (`blockingEnabled`, `enabled`, `onToggle`, `modifier`) is unchanged, so the compiler catches any call-site mismatch.

The project has no automated test suite (per project conventions). Verification is Compose preview rendering plus code inspection, so the correctness properties from the design are checked exhaustively over their finite input domains rather than by generated tests.

## Tasks

- [x] 1. Restyle the ad-blocking control
  - [x] 1.1 Add fixed color constants for both blocking states
    - In `BrowserScreen.kt`, add four private file-level `val`s next to the toggle composable: `BlockingOnContainer = Color(0xFF2E7D32)`, `BlockingOnIcon = Color(0xFFFFFFFF)`, `BlockingOffContainer = Color(0xFFF9C82E)`, `BlockingOffIcon = Color(0xFF1F1B00)`
    - Add the `androidx.compose.ui.graphics.Color` import; leave all existing imports (including `Row`/`Spacer`/`Text`, still used by `BrowserToolbar` and `AppMenu`) untouched
    - No reference to `MaterialTheme.colorScheme` in any of these values
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6_

  - [x] 1.2 Rewrite `AdBlockTogglePill` as `AdBlockToggleButton`
    - Rename the private composable, keeping the exact same signature: `blockingEnabled: Boolean, enabled: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier`
    - Replace the body with a clickable `Surface(onClick = onToggle, enabled = enabled)` using `shape = RoundedCornerShape(8.dp)`, `color = containerColor`, `contentColor = iconColor`, `tonalElevation = 0.dp`, `shadowElevation = 6.dp`, `modifier = modifier`
    - Derive `containerColor`, `iconColor`, and the content description from `blockingEnabled` only — never from `enabled` or the ambient theme
    - Content is a single `Icon(Icons.Filled.Shield)` with `Modifier.padding(12.dp).size(24.dp)` in that exact order (padding before size), yielding a 24dp glyph in a 48dp square touch target
    - Drop the `Row`, `Spacer`, and "Blocking On"/"Blocking Off" `Text`
    - Set `contentDescription` on the `Icon` to "Ad blocking on. Tap to turn off." / "Ad blocking off. Tap to turn on."
    - Add the code comments the design calls for: why `tonalElevation` is `0.dp`, and why modifier order matters
    - Rename the three existing previews in the same edit, since they are call sites of the composable being renamed and the file will not compile until they follow: `AdBlockToggleButtonBlockingOnPreview` (`blockingEnabled = true, enabled = true`), `AdBlockToggleButtonBlockingOffPreview` (`false, true`), `AdBlockToggleButtonDisabledPreview` (`true, false`) — each calling `AdBlockToggleButton(...)`
    - Keep each preview `private`, in the same file, annotated with `org.jetbrains.compose.ui.tooling.preview.Preview`, body wrapped in `MaterialTheme { }`, with static parameter values and a no-op `onToggle`
    - Leave the existing file comment documenting why `BrowserScreen`/`App`/`PlatformWebView` are unpreviewable
    - _Requirements: 1.1, 1.5, 2.1, 2.2, 2.3, 2.4, 3.1, 3.2, 3.4, 3.5, 3.6, 4.1, 4.2, 4.3, 5.1, 5.2, 5.3, 6.1, 6.2, 6.3, 6.4, 6.5, 7.7_

  - [x] 1.3 Update the `BrowserScreen` call site
    - Rename the call to `AdBlockToggleButton`, keeping the same four arguments (`viewModel.blockingEnabled`, `!viewModel.isLoading && !viewModel.isModelBusy`, `viewModel::toggleBlocking`, `modifier`)
    - Change the modifier from `.align(Alignment.BottomCenter).padding(bottom = 24.dp)` to `.align(Alignment.BottomEnd).padding(bottom = 24.dp, end = 24.dp)`
    - Keep the control as the second child of the weighted `Box` wrapping `PlatformWebView` so it keeps drawing above page content and stays fixed while the page scrolls
    - Replace the stale "Floating ad-blocking toggle pill" comment with wording that no longer describes a pill
    - _Requirements: 1.2, 1.3, 1.4, 5.4_

- [x] 2. Publish the toggle notice from the ViewModel
  - [x] 2.1 Add the single-consumption `toggleNotice` state and wire it into `toggleBlocking()`
    - In `shared/src/commonMain/kotlin/com/adder/shared/ui/BrowserViewModel.kt`, add `var toggleNotice by mutableStateOf<String?>(null)` with `private set`
    - Add `fun onToggleNoticeShown() { toggleNotice = null }` so the screen can mark the message as presented
    - Add `NOTICE_BLOCKING_ON = "Blocking : On"` and `NOTICE_BLOCKING_OFF = "Blocking : Off"` to the existing `private companion object` — exact strings, spaces around the colon included — so the literals appear once
    - Reorder `toggleBlocking()` to: flip `blockingEnabled`, then derive `toggleNotice` from the flipped value, then call `webViewController.reload()` last. Never read `blockingEnabled` for the message before the flip
    - Add the KDoc/comments the design calls for: the single nullable slot is what makes "at most one pending notice" structural, the derivation comment explaining why it sits after the flip, and the documented assumption that two consecutive notices must differ because the screen keys its effect on the notice value — with the `data class ToggleNotice(val message: String, val id: Long)` escape hatch named for whoever adds a second producer
    - _Requirements: 7.1, 7.2, 7.3, 7.4, 7.5, 7.6, 7.8, 7.9, 7.10_

  - [x] 2.2 Verify the notice always names the post-flip state
    - **Property 4: The notice message always names the post-flip blocking state**
    - Walk `toggleBlocking()` from both starting values of `blockingEnabled` and over repeated invocations; confirm the assignment reads the flipped value and that the two constants map to the required literals exactly
    - **Validates: Requirements 7.1, 7.2, 7.3, 7.4, 7.5, 7.6**

  - [x] 2.3 Verify at most one notice is ever pending
    - **Property 5: At most one notice is ever pending, and consumption clears it**
    - Enumerate the interleavings of `toggleBlocking()` and `onToggleNoticeShown()` (publish, publish-publish, publish-consume, consume with nothing pending); confirm the single nullable slot leaves no way to queue a second message, that `onToggleNoticeShown()` leaves it `null`, and that the slot's placement in the retained `ViewModel` keeps a pending notice alive across an Android configuration change
    - Confirm the property as worded in the design: no single publication is ever **presented to completion twice** — a publication interrupted before it finishes stays pending and is presented again, but once `onToggleNoticeShown()` has run it can never be presented again
    - **Validates: Requirements 7.8, 7.9, 7.10**

- [x] 3. Present the notice as a snackbar in `BrowserScreen`
  - [x] 3.1 Add the snackbar host state and the presenting effect
    - At the top of `BrowserScreen`, add `val snackbarHostState = remember { SnackbarHostState() }` and read `val notice = viewModel.toggleNotice`
    - Add an unconditional `LaunchedEffect(notice)` that, when `notice != null`, calls `snackbarHostState.showSnackbar(message = notice, duration = SnackbarDuration.Short)` and then `viewModel.onToggleNoticeShown()` — message and duration only, no `actionLabel`, no custom content slot
    - Key the effect on the notice value and **not** on `isLoading`, so the reload started by `toggleBlocking()` cannot restart or cut short the message; a newer notice does cancel it, which replaces the visible message instead of queueing behind it
    - Add the comment explaining the keying choice and naming both consequences, since re-keying on load state is the regression most likely to arrive with an unrelated fix
    - Keep the effect and the state declaration outside every `if` branch so neither leaves the composition when the progress indicator, history overlay, or busy scrim appear
    - `SnackbarHost`, `SnackbarHostState`, `SnackbarDuration`, `remember`, and `LaunchedEffect` all arrive through the existing wildcard imports — no import changes needed
    - _Requirements: 8.1, 8.2, 8.3, 8.6, 8.7, 8.8_

  - [x] 3.2 Declare the `SnackbarHost` inside the WebView area
    - Add `SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 88.dp))` as the **third and last** child of the weighted `Box`, after `PlatformWebView` and after `AdBlockToggleButton`, so it draws above page content and above the toggle
    - Use the default host content (no content lambda) so the message text is the snackbar's only content and Material 3's live-region semantics apply
    - Add the comment recording that the 88dp is derived from the toggle it must clear (24dp bottom padding + 48dp height = 72dp, leaving a **nominal** 16dp gap — Material 3's `Snackbar` adds its own 12dp of padding inside the host, so the visible gap is closer to 28dp) and that the two numbers must change together
    - _Requirements: 8.1, 8.3, 8.4, 8.5, 8.8_

  - [x] 3.3 Verify the presented snackbar survives the reload
    - **Property 6: The presented snackbar shows the newest notice verbatim, for its full duration**
    - Inspect the effect key and the declaration sites, then run the enumerable event sequences by hand (tap; tap then reload with `onPageStarted`/`onPageFinished`/`onModelBusyChanged` arriving mid-window; tap again mid-window): the message shows verbatim as the only content, stays up for the full `SnackbarDuration.Short`, is never doubled, and is ended early only by a newer notice
    - **Validates: Requirements 8.2, 8.3, 8.6, 8.7**

- [x] 4. Checkpoint - compile and review
  - Confirm both edited files have no unresolved references (old composable name, missing `Color` import, `toggleNotice` visibility) and that the toggle and snackbar wiring line up with the ViewModel API
  - This project has no automated test suite, so there is nothing to run here: verification is a clean compile plus preview rendering and the property inspections above
  - Ask the user before running any Gradle task (per project convention, Gradle permission is a per-session preference — re-ask rather than assuming an earlier answer holds)
  - Ask the user if questions arise.

- [x] 5. Preview coverage and toggle property verification
  - [x] 5.1 Add the two toggle-notice snackbar previews
    - Add `ToggleNoticeSnackbarOnPreview` and `ToggleNoticeSnackbarOffPreview` to `BrowserScreen.kt`, each `private`, annotated with the multiplatform `Preview`, body `MaterialTheme { Snackbar { Text("Blocking : On") } }` / `Snackbar { Text("Blocking : Off") }` with static text
    - Do not preview `SnackbarHost` itself — with no coroutine driving `SnackbarHostState` it renders empty and would assert nothing; previewing the `Snackbar` it delegates to gives the same pixels from static input
    - _Requirements: 8.3, 6.4, 6.5_

  - [x] 5.2 Verify colors are a function of blocking state alone
    - **Property 1: Container and icon colors are a function of the blocking state alone**
    - Render all three toggle previews under light and dark color schemes; confirm green `#2E7D32`/white on-state and yellow `#F9C82E`/`#1F1B00` off-state are identical across schemes, and that the disabled preview is visually indistinguishable from the blocking-on preview
    - Exhaustive over the four `blockingEnabled` × `enabled` cases plus two color schemes
    - **Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 5.3**

  - [x] 5.3 Verify the content description always names the blocking state
    - **Property 2: The content description always names the current blocking state**
    - Inspect both branches of the description expression; confirm neither is `null` and each names both the state and the resulting action
    - **Validates: Requirements 4.1, 4.2, 5.3**

  - [x] 5.4 Verify a tap's effects depend only on the enabled flag
    - **Property 3: A tap's effects are determined solely by the enabled flag**
    - Inspect that `onToggle` is passed only to `Surface(onClick = ...)` with `enabled = enabled`, is not wrapped in any additional `clickable`, and has no other call path — so a tap fires it exactly once when enabled and zero times when disabled
    - Confirm the disabled case leaves both `blockingEnabled` and `toggleNotice` untouched, since `toggleBlocking()` is the sole producer of the notice and is only reachable through that one click handler
    - **Validates: Requirements 5.1, 5.2, 7.7**

- [x] 6. Sync the architecture documentation
  - [x] 6.1 Replace the toggle section and ViewModel bullet in both architecture docs
    - Apply the same two edits to **both** `.kiro/steering/architecture.md` and `docs/architecture.md` in this single task, so the files cannot drift
    - Replace the whole "Ad-Blocking Toggle (Virgin View)" section with the replacement block from the design document, copied **character-identically** into each file
    - Update the "UI State Management" `BrowserViewModel` bullet in each file to add `toggleNotice` to the state list and `onToggleNoticeShown` to the intent list, using the exact bullet text from the design
    - Change nothing else in either file: leave the pre-existing `historyVisible`/`historyEntries` omission and the untagged-vs-`kotlin` code fence difference in `Platform LLM Integration` alone, both are out of scope
    - _Requirements: 9.1, 9.2, 9.3, 9.4, 9.5, 9.6, 9.7, 9.8_

  - [x] 6.2 Verify the two architecture docs are identical in the edited regions
    - **Property 7: The toggle documentation section is identical in both architecture docs**
    - Diff the "Ad-Blocking Toggle (Virgin View)" section and the edited `BrowserViewModel` bullet between the two files: no differences. Also read the replaced section against Req 9.1–9.6 and confirm the word "pill" does not appear in it
    - **Validates: Requirements 9.7, 9.8**

- [x] 7. Final checkpoint - review the full change set
  - Confirm the two code files compile clean, all five previews render, and the two docs diff empty in the edited regions
  - No automated test suite exists for this project, so this checkpoint is a review-and-inspection gate rather than a test run; the on-device manual verification list in the design is the user's to run
  - Ask the user before running any Gradle task, and ask if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- `BrowserScreen.kt` is written by 1.1 → 1.2 → 1.3 → 3.1 → 3.2 → 5.1 and those are intentionally serialized. `BrowserViewModel.kt` (2.1) and the docs (6.1) are separate files and start in parallel with the first wave; 3.1 consumes the ViewModel API added in 2.1, so 2.1 lands first
- The three toggle preview renames live in 1.2 rather than in a later task: renaming a private composable and its call sites is one atomic edit, and splitting it would leave the file uncompilable at the checkpoint in task 4
- The docs sync is deliberately one task covering both files — splitting it per file is the easiest way to let the two copies drift
- This codebase has no automated test suite; property tasks are exhaustive preview/inspection checks. Properties 1–4 have finite domains of two to eight cases, and Properties 5–7 quantify over event sequences and file content that are enumerable by hand
- Icon contrast ratios (≈5.0:1 on, ≈11.0:1 off) are static facts about the color constants, verified once in the design — recompute them if any literal changes
- The snackbar keeps default Material 3 colors and so does adapt to light/dark; the fixed-color rule applies to the toggle only
- The busy scrim tinting a visible snackbar is a known, accepted visual interaction recorded in the design, not a defect to fix here
- The ad-block engine, JS injection, and both `PlatformWebView` actuals are unchanged
- The dependency graph includes the two checkpoints (4, 7) as their own waves, since both are gates that must not overlap with the work they review: everything in task 5 runs after checkpoint 4, and checkpoint 7 runs last

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "2.1", "6.1"] },
    { "id": 1, "tasks": ["1.2", "2.2", "2.3", "6.2"] },
    { "id": 2, "tasks": ["1.3"] },
    { "id": 3, "tasks": ["3.1"] },
    { "id": 4, "tasks": ["3.2"] },
    { "id": 5, "tasks": ["4"] },
    { "id": 6, "tasks": ["5.1"] },
    { "id": 7, "tasks": ["3.3", "5.2", "5.3", "5.4"] },
    { "id": 8, "tasks": ["7"] }
  ]
}
```
