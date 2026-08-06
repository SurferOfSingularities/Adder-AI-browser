# Requirements Document

## Introduction

The floating ad-blocking toggle in the Adder browser screen is currently a wide, fully-rounded pill centered at the bottom of the WebView area, showing a shield icon plus a "Blocking On"/"Blocking Off" text label with Material theme colors.

This feature restyles that control into a compact, slightly-rounded rectangle anchored to the bottom-right of the WebView area. The text label is dropped so the control is icon-only, and on/off state is communicated through fixed green (blocking on) and yellow (blocking off) background colors that stay identical in light and dark mode. Because the visible label is removed, the control must carry an accessible description for each state.

Behavior is unchanged: the control still reflects `BrowserViewModel.blockingEnabled`, still invokes `toggleBlocking()`, and is still non-interactive while a page is loading or the on-device model is busy.

Because the control loses its text label, an accepted tap also raises a short confirmation message reading "Blocking : On" or "Blocking : Off" for the state the tap produced. There is no platform `Toast` available in `commonMain`, so the message is presented as a Material 3 `Snackbar` driven from shared code, giving Android and iOS identical text, timing, and placement.

Finally, the architecture documentation in `.kiro/steering/architecture.md` and `docs/architecture.md` still describes the control as a bottom-center pill with no confirmation message. Both files are updated to match the new control and message behavior, with identical wording, per the documentation-mirroring convention.

## Glossary

- **Blocking_Toggle**: The floating Compose control in the browser screen (currently the private `AdBlockTogglePill` composable in `shared/src/commonMain/kotlin/com/adder/shared/ui/BrowserScreen.kt`) that shows and switches the ad-blocking state.
- **Browser_Screen**: The shared Compose Multiplatform screen (`BrowserScreen`) that hosts the platform WebView, the bottom `BrowserToolbar`, and the Blocking_Toggle.
- **Browser_ViewModel**: `BrowserViewModel` in `commonMain`, the single source of truth for `blockingEnabled`, `isLoading`, and `isModelBusy`, and the owner of `toggleBlocking()`.
- **Blocking_On_State**: The state where `Browser_ViewModel.blockingEnabled` is `true`.
- **Blocking_Off_State**: The state where `Browser_ViewModel.blockingEnabled` is `false`.
- **Interaction_Blocked_State**: The state where `Browser_ViewModel.isLoading` is `true` or `Browser_ViewModel.isModelBusy` is `true`.
- **WebView_Area**: The bounded Compose `Box` inside Browser_Screen that contains the platform WebView and sits above the bottom toolbar and clear of system bars.
- **Browser_Toolbar**: The `BrowserToolbar` composable rendered at the bottom of Browser_Screen, directly below the WebView_Area.
- **Toggle_Notice**: The transient confirmation message raised by an accepted Blocking_Toggle tap, stating the blocking state the tap produced.
- **Snackbar_Host**: The Material 3 `SnackbarHost` plus its `SnackbarHostState` in Browser_Screen (`commonMain`) that presents the Toggle_Notice on both platforms.
- **Preview_Set**: The `@Preview` composables for the Blocking_Toggle in the same file, annotated with `org.jetbrains.compose.ui.tooling.preview.Preview`.
- **Architecture_Docs**: The pair of files `.kiro/steering/architecture.md` and `docs/architecture.md`.
- **Toggle_Doc_Section**: The "Ad-Blocking Toggle (Virgin View)" section present in each of the Architecture_Docs.

## Requirements

### Requirement 1

**User Story:** As a browser user, I want the ad-blocking control to be a compact rectangle in the bottom-right corner, so that the control stays out of the way of page content I am reading.

#### Acceptance Criteria

1. THE Blocking_Toggle SHALL render as a rectangle with a corner radius of 8dp.
2. THE Blocking_Toggle SHALL be positioned within the WebView_Area using `Alignment.BottomEnd`.
3. THE Blocking_Toggle SHALL apply 24dp of padding on the bottom edge and 24dp of padding on the end edge, so the control remains clear of the bottom toolbar and the screen edge.
4. THE Blocking_Toggle SHALL render above the platform WebView in the WebView_Area so the control stays fixed while page content scrolls.
5. THE Blocking_Toggle SHALL apply 6dp shadow elevation so the control is visually separated from page content.

### Requirement 2

**User Story:** As a browser user, I want the control to show only a shield icon, so that the control takes up minimal screen space.

#### Acceptance Criteria

1. THE Blocking_Toggle SHALL display the `Icons.Filled.Shield` vector as the only visible content.
2. THE Blocking_Toggle SHALL omit the "Blocking On" and "Blocking Off" text labels.
3. THE Blocking_Toggle SHALL render the shield icon at a size of 24dp.
4. THE Blocking_Toggle SHALL apply equal horizontal and vertical inner padding of 12dp around the shield icon, producing a square-proportioned control.

### Requirement 3

**User Story:** As a browser user, I want the control's color to tell me whether ad blocking is on or off, so that I can read the state at a glance without a text label.

#### Acceptance Criteria

1. WHILE in Blocking_On_State, THE Blocking_Toggle SHALL render the container with the fixed green color `#2E7D32`.
2. WHILE in Blocking_Off_State, THE Blocking_Toggle SHALL render the container with the fixed yellow color `#F9C82E`.
3. THE Blocking_Toggle SHALL use the same container color values in light color scheme and dark color scheme.
4. WHILE in Blocking_On_State, THE Blocking_Toggle SHALL tint the shield icon with the fixed color `#FFFFFF`, giving a contrast ratio of at least 4.5:1 against the green container.
5. WHILE in Blocking_Off_State, THE Blocking_Toggle SHALL tint the shield icon with the fixed color `#1F1B00`, giving a contrast ratio of at least 4.5:1 against the yellow container.
6. THE Blocking_Toggle SHALL derive container and icon colors from `Browser_ViewModel.blockingEnabled` alone, independent of `MaterialTheme.colorScheme`.

### Requirement 4

**User Story:** As a user of a screen reader, I want the icon-only control to announce the current blocking state, so that I can operate the control without seeing the color.

#### Acceptance Criteria

1. WHILE in Blocking_On_State, THE Blocking_Toggle SHALL expose the content description "Ad blocking on. Tap to turn off." for the shield icon.
2. WHILE in Blocking_Off_State, THE Blocking_Toggle SHALL expose the content description "Ad blocking off. Tap to turn on." for the shield icon.
3. THE Blocking_Toggle SHALL present a touch target measuring at least 48dp by 48dp.

### Requirement 5

**User Story:** As a browser user, I want tapping the restyled control to switch blocking exactly as before, so that the visual change does not alter behavior.

#### Acceptance Criteria

1. WHEN the user taps the Blocking_Toggle AND the Blocking_Toggle is enabled, THE Blocking_Toggle SHALL invoke `Browser_ViewModel.toggleBlocking()` exactly once.
2. WHILE in Interaction_Blocked_State, THE Blocking_Toggle SHALL reject tap input and leave `Browser_ViewModel.blockingEnabled` unchanged.
3. WHEN `Browser_ViewModel.blockingEnabled` changes value, THE Blocking_Toggle SHALL recompose with the container color and content description of the new state.
4. THE Browser_Screen SHALL pass `blockingEnabled`, an enabled flag derived from `isLoading` and `isModelBusy`, and the `toggleBlocking` callback to the Blocking_Toggle using the existing parameter contract.

### Requirement 6

**User Story:** As a developer, I want previews for the restyled control, so that I can verify each visual state without running the app on a device.

#### Acceptance Criteria

1. THE Preview_Set SHALL include one preview rendering the Blocking_Toggle in Blocking_On_State with the control enabled.
2. THE Preview_Set SHALL include one preview rendering the Blocking_Toggle in Blocking_Off_State with the control enabled.
3. THE Preview_Set SHALL include one preview rendering the Blocking_Toggle in Interaction_Blocked_State.
4. THE Preview_Set SHALL declare each preview composable as `private` and place each preview in the same file as the Blocking_Toggle.
5. THE Preview_Set SHALL wrap each preview body in `MaterialTheme { }` and supply static values for all Blocking_Toggle parameters.

### Requirement 7

**User Story:** As a browser user, I want a short message after tapping the control, so that I can confirm which blocking state my tap produced now that the control has no text label.

#### Acceptance Criteria

1. WHEN `Browser_ViewModel.toggleBlocking()` runs, THE Browser_ViewModel SHALL publish one Toggle_Notice for that invocation.
2. THE Browser_ViewModel SHALL derive the Toggle_Notice message text from the value of `blockingEnabled` after the flip performed by `toggleBlocking()`.
3. WHERE the post-flip value of `blockingEnabled` is `true`, THE Browser_ViewModel SHALL set the Toggle_Notice message text to exactly `Blocking : On`.
4. WHERE the post-flip value of `blockingEnabled` is `false`, THE Browser_ViewModel SHALL set the Toggle_Notice message text to exactly `Blocking : Off`.
5. WHEN the user taps the Blocking_Toggle WHILE in Blocking_On_State AND the Blocking_Toggle is enabled, THE Browser_ViewModel SHALL publish a Toggle_Notice with the message text `Blocking : Off`.
6. WHEN the user taps the Blocking_Toggle WHILE in Blocking_Off_State AND the Blocking_Toggle is enabled, THE Browser_ViewModel SHALL publish a Toggle_Notice with the message text `Blocking : On`.
7. WHILE in Interaction_Blocked_State, THE Blocking_Toggle SHALL reject tap input and leave the Toggle_Notice unpublished, so a rejected tap produces no message.
8. THE Browser_ViewModel SHALL own the Toggle_Notice state, so the message text stays consistent with `blockingEnabled` across recomposition and across Android configuration changes.
9. THE Browser_ViewModel SHALL expose the Toggle_Notice as a single-consumption event that Browser_Screen marks as consumed after presenting the message, so one tap produces exactly one displayed message.
10. IF `toggleBlocking()` runs while a previously published Toggle_Notice is still unconsumed, THEN THE Browser_ViewModel SHALL replace the unconsumed Toggle_Notice with the newest message text, keeping at most one pending Toggle_Notice.

### Requirement 8

**User Story:** As a browser user, I want the confirmation message to be readable where it appears and to stay put while the page reloads, so that I can read the message without it being covered or cut short.

#### Acceptance Criteria

1. THE Browser_Screen SHALL present the Toggle_Notice as a Material 3 `Snackbar` through a single Snackbar_Host declared in `commonMain`, so Android and iOS show the same text, placement, and duration.
2. WHEN Browser_Screen consumes a Toggle_Notice, THE Browser_Screen SHALL display the Snackbar with `SnackbarDuration.Short`.
3. THE Snackbar SHALL display the Toggle_Notice message text as its only content, with no action label.
4. THE Snackbar_Host SHALL be positioned inside the WebView_Area at `Alignment.BottomCenter` with 88dp of bottom padding, placing the Snackbar above the 48dp-tall Blocking_Toggle and its 24dp bottom padding, and above the Browser_Toolbar.
5. THE Browser_Screen SHALL declare the Snackbar_Host after `PlatformWebView` and after the Blocking_Toggle within the WebView_Area, so the Snackbar draws above page content and above the Blocking_Toggle.
6. WHILE the page reload started by `toggleBlocking()` is in progress, THE Browser_Screen SHALL keep the Snackbar visible for the full `SnackbarDuration.Short` period, so `onPageStarted` and `onPageFinished` events leave the message intact.
7. WHEN Browser_Screen consumes a Toggle_Notice WHILE an earlier Snackbar is visible, THE Browser_Screen SHALL dismiss the visible Snackbar and display the new message text, keeping at most one Snackbar visible.
8. WHEN the Snackbar appears, THE Browser_Screen SHALL announce the Toggle_Notice message text to screen readers using the default Material 3 `Snackbar` semantics.

### Requirement 9

**User Story:** As a developer reading the project documentation, I want the architecture notes to describe the control as it now looks and behaves, so that the steering files and `docs/` stay a reliable source of truth.

#### Acceptance Criteria

1. THE Toggle_Doc_Section SHALL describe the Blocking_Toggle as a rectangular, icon-only button anchored to the bottom-right of the WebView_Area that stays fixed while page content scrolls.
2. THE Toggle_Doc_Section SHALL refer to the control as a button and SHALL use that term in place of the earlier term "pill".
3. THE Toggle_Doc_Section SHALL state that a green container indicates Blocking_On_State and a yellow container indicates Blocking_Off_State, with the same colors in light and dark mode.
4. THE Toggle_Doc_Section SHALL state that the Blocking_Toggle is non-interactive while a page is loading or the on-device model is busy, to avoid mid-pipeline reloads.
5. THE Toggle_Doc_Section SHALL state that each accepted tap shows a short Material 3 `Snackbar` reading `Blocking : On` or `Blocking : Off` for the state the tap produced, driven from shared code so both platforms behave identically.
6. THE Toggle_Doc_Section SHALL state that the Snackbar message survives the reload triggered by the toggle.
7. THE Architecture_Docs SHALL contain character-identical Toggle_Doc_Section content in `.kiro/steering/architecture.md` and `docs/architecture.md`.
8. WHEN the Toggle_Doc_Section changes in one of the Architecture_Docs, THE same change SHALL be applied to the other file within the same change set.
