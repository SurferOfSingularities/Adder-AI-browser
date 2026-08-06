# Implementation Tasks — Browser Tabs

## - [x] Task 1: Tab data model and TabManager core (create, close, activate)

**Objective:** Define the `Tab` state model and `TabManager` class with creation, closing, activation, and ordering logic — all pure shared-module code with no UI or persistence yet.

**Implementation guidance:**
- Create `shared/src/commonMain/kotlin/com/adder/shared/model/Tab.kt` with a `@Serializable` data class: `id: String`, `url: String`, `displayTitle: String`, `renderedBlockingMode: Boolean?`.
- Create `shared/src/commonMain/kotlin/com/adder/shared/engine/TabManager.kt`:
  - Holds `tabs: List<Tab>` (ordered), `activeTabId: String`, `recentlyActivated: List<String>` (MRU list for budgeting).
  - `createTab(url: String): Tab?` — returns null if at limit (20). Generates UUID-style ID, appends to end, activates it.
  - `closeTab(id: String)` — removes tab, picks next active per spec (higher position first, then lower), or creates fresh tab if last.
  - `activateTab(id: String)` — sets active, pushes to MRU front.
  - `TAB_LIMIT = 20`, `LIVE_WEBVIEW_BUDGET = 3`.
  - `liveTabIds(): Set<String>` — returns active + top 2 from MRU that differ from active.
  - All operations are synchronous and testable in isolation.
- Unit tests in `commonTest` verifying: creation increments count, closing active picks correct successor, limit enforcement, MRU ordering, close-last creates fresh tab.

**Demo:** Unit tests pass, confirming tab collection invariants hold for create/close/activate sequences.

---

## - [x] Task 2: Tab session persistence

**Objective:** Wire `TabManager` to `PersistentStore` so the tab collection survives app restarts.

**Implementation guidance:**
- Add a `TabSession` serializable wrapper: `tabs: List<Tab>`, `activeTabId: String`, `tabOrder: List<String>`.
- `TabManager` constructor takes a `PersistentStore` parameter (defaults to `platformPersistentStore()`).
- On init: attempt to restore from store key `"adder.tab_session.v1"`. Apply the fallback rules (absent/unreadable/empty → single initial tab; >20 → take first 20).
- After every mutation (create, close, activate, URL/title change): persist the session. Wrap in `runCatching` per the requirement.
- Unit tests with an in-memory `PersistentStore` fake: verify round-trip, verify graceful fallback on corrupt JSON.

**Demo:** Tests confirm that creating tabs, closing them, and then constructing a new `TabManager` from the same store recovers the exact tab list and active tab.

---

## - [x] Task 3: Integrate TabManager into BrowserViewModel

**Objective:** Refactor `BrowserViewModel` from single-page state to multi-tab state, delegating to `TabManager`. Each tab owns a `WebViewController`.

**Implementation guidance:**
- Add `TabManager` as a constructor dependency of `BrowserViewModel`.
- Replace the single `url`, `inputText`, `canGoBack`, `canGoForward`, `isLoading`, `isModelBusy` with per-tab state. Approach: `BrowserViewModel` keeps a `Map<String, TabUiState>` where `TabUiState` is a small class holding the mutable Compose state fields for a tab. The public properties (`url`, `inputText`, etc.) become delegates to `activeTabState`.
- Each tab gets its own `WebViewController` instance, stored in a map.
- `onUrlSubmit()`, `onBack()`, `onForward()`, `onRefresh()` all dispatch to the active tab's controller.
- Add intents: `createTab()`, `closeTab(id)`, `activateTab(id)`, `openTabSwitcher()`, `closeTabSwitcher()`.
- Add observable: `tabSwitcherVisible: Boolean`, `tabs: List<Tab>` (from TabManager), `tabCount: Int`.
- `onPageStarted` / `onPageFinished` / `onNavStateChanged` / `onModelBusyChanged` now take a `tabId` parameter to route events to the correct tab's state.
- History recording still happens on any tab's `onPageFinished`.

**Demo:** The app compiles. Single-tab behavior is preserved (the initial tab loads Google). The ViewModel exposes `tabCount = 1` and `tabs` with one entry.

---

## - [x] Task 4: Tab switcher UI (TabSwitcherView)

**Objective:** Build the tab switcher full-screen list overlay composable, following the `HistoryView` pattern.

**Implementation guidance:**
- Create `shared/src/commonMain/kotlin/com/adder/shared/ui/TabSwitcherView.kt`:
  - Top bar: Close button (left), "Tabs" title (center/weight), "+" new-tab button (right).
  - `LazyColumn` with one row per tab: `displayTitle` (bold, 1 line, ellipsis), `url` below (body small, 1 line, ellipsis), close "X" button on the right.
  - Active tab row has a left-edge accent color indicator (4dp vertical bar, primary color).
  - Tapping a row calls `onTabSelected(id)`. Tapping X calls `onTabClose(id)`. Tapping "+" calls `onNewTab()`.
  - Empty state is structurally impossible (always ≥1 tab) so not needed.
- Add `@Preview` functions: populated list with 3 tabs (one active), single tab.
- Wire into `BrowserScreen`: when `viewModel.tabSwitcherVisible`, show `TabSwitcherView` overlay (same `Box` + `windowInsetsPadding` pattern as history).

**Demo:** Opening the tab switcher shows the single default tab. Tapping "+" creates a second tab. Tapping a tab switches to it. Tapping X closes it.

---

## - [x] Task 5: Tab count button and toolbar refactor

**Objective:** Replace the hamburger menu with a 3-dot MoreVert icon and add a tab-count button to the right of the URL field.

**Implementation guidance:**
- In `BrowserToolbar`, replace `Icons.Filled.Menu` with `Icons.Filled.MoreVert`. Keep the same `AppMenu` dropdown contents (History, Settings, model name).
- Add a new `TabCountButton` composable after the `OutlinedTextField`:
  - Small Surface (rounded rect, `surfaceVariant` background) containing the tab count as `Text` (e.g. "1", "5", "20"). Use `typography.labelLarge`, monospace-friendly.
  - On click → `onTabSwitcher()`.
  - Accessibility: `contentDescription = "$count open tabs"`.
- Pass `tabCount: Int` and `onTabSwitcher: () -> Unit` into `BrowserToolbar` from `BrowserScreen`.
- Add `@Preview` for `TabCountButton` showing counts 1, 5, 20.

**Demo:** Toolbar shows the 3-dot menu on the left and the tab count badge on the right of the URL bar. Tapping the badge opens the tab switcher.

---

## - [x] Task 6: Multi-WebView management — per-tab PlatformWebView lifecycle

**Objective:** Support multiple live WebViews (one per live tab) managed by the budgeting logic, and handle hibernation/rehydration.

**Implementation guidance:**
- Refactor `BrowserScreen` to display the active tab's WebView keyed by `activeTabId`.
- Simplest correct approach for MVP: keep only the active tab's WebView in composition. Switching tabs re-composes with the new tab's controller and URL. Tabs within the live budget that get re-activated reload from their stored URL (they lose in-page back/forward history — acceptable for MVP, matches the hibernation behavior).
- Update `PlatformWebView` calls to pass the active tab's `WebViewController` and `url`.
- When activating a tab whose `renderedBlockingMode` differs from current `blockingEnabled`, the page load triggered by composition inherently applies the correct mode.
- `TabManager.liveTabIds()` is used to decide which tabs retain their URL (all do) — actual WebView lifetime is tied to composition.

**Demo:** Creating a second tab shows a new Google page. Switching back to the first tab reloads its URL. The tab's title and URL update correctly after navigation.

---

## - [x] Task 7: Ad-block pipeline scheduling (AdBlockScheduler)

**Objective:** Centralize pipeline execution with single-pass-at-a-time semantics and active-tab priority preemption.

**Implementation guidance:**
- Create `shared/src/commonMain/kotlin/com/adder/shared/engine/AdBlockScheduler.kt`:
  - Holds a `Mutex` or uses a single-threaded coroutine dispatcher to ensure one pass at a time.
  - `requestClassification(tabId: String, isActiveTab: Boolean, block: suspend () -> Unit)`:
    - If `isActiveTab` and a background pass is running → cancel background job, start active pass.
    - If not active and another pass is running → defer (queue).
    - On completion/cancellation of current pass → dequeue next (active-tab requests first).
  - Tracks `currentJob: Job?` and `currentTabId: String?`.
  - `cancelForTab(tabId: String)` — cancels if running or removes from queue.
- Refactor `PlatformWebView` (both platforms): instead of launching the pipeline directly in `onPageFinished`, call back to the ViewModel which routes through the scheduler.
- The ViewModel exposes `onPipelineRequested(tabId: String)` which invokes the scheduler.
- `isModelBusy` is only set true when the active tab's pass is running.

**Demo:** Opening two tabs rapidly results in the active tab's ads being removed first. The model-busy spinner only shows for the active tab's pipeline run.

---

## - [x] Task 8: Blocking mode across tabs

**Objective:** Ensure the global blocking toggle interacts correctly with per-tab `renderedBlockingMode`.

**Implementation guidance:**
- `Tab` already has `renderedBlockingMode: Boolean?`. On `onPageFinished` for a tab, set its `renderedBlockingMode` to the current `blockingEnabled` value.
- On `activateTab(id)`: if the activated tab's `renderedBlockingMode != blockingEnabled`, trigger a reload of that tab.
- `toggleBlocking()` in the ViewModel: flip the flag, reload active tab (existing behavior). Background tabs are NOT reloaded eagerly — they reload when activated (lazy reconciliation).
- The toggle button remains disabled while `isLoading || isModelBusy` on the active tab.

**Demo:** Turn off blocking, switch tabs, turn on blocking, switch back to the first tab — it reloads with blocking enabled. The toggle is disabled during page loads.

---

## - [x] Task 9: New-window requests open in a new tab

**Objective:** Handle `window.open()` / `target="_blank"` links by creating a new tab.

**Implementation guidance:**
- Android: Override `WebChromeClient.onCreateWindow()` to extract the target URL and call back to the ViewModel's `createTab(url)` instead of opening a system browser.
- iOS: Implement `WKUIDelegate`'s `webView(_:createWebViewWith:for:windowFeatures:)` — extract the URL from the navigation action and call `createTab(url)`.
- Both should return without creating a new native WebView (the tab creation handles that).
- If the URL is null/empty, fall back to creating a tab with the Initial URL.

**Demo:** Tapping a link that opens in a new window (e.g., `target="_blank"`) creates a new tab and navigates there.

---

## - [x] Task 10: Tab limit feedback and edge cases

**Objective:** Handle the 20-tab limit with user feedback, and ensure robustness for edge cases.

**Implementation guidance:**
- When `TabManager.createTab()` returns null (limit reached), the ViewModel publishes a Snackbar message: "Tab limit reached (20)".
- Use the same `SnackbarHostState` / single-slot pattern as the blocking toggle notice.
- Guard `closeTab` against double-close (requirement 3.7) — `TabManager` already no-ops, ViewModel should not crash.
- Rapid tab switching: since only the active tab is composed, Compose handles this naturally. Ensure the `key()` on `PlatformWebView` prevents stale callbacks from a disposed WebView reaching the wrong tab's state.

**Demo:** Create 20 tabs, attempt to create a 21st — a snackbar says the limit is reached. Close all tabs rapidly — always ends with one fresh tab.

---

## - [x] Task 11: Browsing history integration across tabs

**Objective:** Ensure the unified history continues to work correctly with multiple tabs.

**Implementation guidance:**
- `onPageFinished(tabId, url)` in the ViewModel still calls `historyStore.record(url, title)` regardless of which tab finished.
- The active tab's address bar and progress indicator remain unaffected by background tab page loads (already handled by routing events through `tabId`).
- History "revisit" loads the URL in the active tab (existing behavior in `onRevisit`).
- Closing a tab does not remove its history entries (HistoryStore is independent).

**Demo:** Navigate in tab 1, switch to tab 2 and navigate, open history — both pages appear in chronological order. Revisiting loads in the currently active tab.

---

## - [x] Task 12: Documentation update

**Objective:** Update `.kiro/steering/` and `docs/` to reflect the new tab architecture.

**Implementation guidance:**
- Update architecture docs with the TabManager, AdBlockScheduler, and per-tab WebViewController design.
- Document the toolbar change (3-dot menu, tab count button).
- Add TabSwitcherView to the UI component inventory.
- Mirror changes between `.kiro/steering/` and `docs/` per conventions.

**Demo:** Documentation accurately describes the multi-tab architecture and all new components.
