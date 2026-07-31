# Implementation Plan: Browsing History

## Overview

This plan builds the browsing-history feature incrementally in the shared KMP module, then wires it
into the platform WebViews and the browser UI. It follows the layering in the design: data model →
persistence abstraction → `HistoryStore` logic → WebView title accessor → `BrowserViewModel`
state/intents → History UI → integration. Property-based tests (Kotest Property, min 100 iterations)
and example-based unit tests are attached as sub-tasks close to the code they validate, so the 12
correctness properties are exercised as soon as their implementation lands.

Language: Kotlin (Kotlin Multiplatform). Tests live in `shared/src/commonTest`. Test tooling
(`io.kotest:kotest-property`, `io.kotest:kotest-assertions-core`, and the JVM `kotest-runner-junit5`
for the Android/JVM target) is added as part of task 1.

## Tasks

- [ ] 1. Add test tooling and the `HistoryEntry` data model
  - [ ] 1.1 Add Kotest Property test dependencies to the shared module
    - In `gradle/libs.versions.toml`, add versions/libraries for `io.kotest:kotest-property`,
      `io.kotest:kotest-assertions-core`, and `io.kotest:kotest-runner-junit5`
    - In `shared/build.gradle.kts`, create/extend the `commonTest` source set with
      `kotest-property` + `kotest-assertions-core`, and wire the JUnit5 runner + `useJUnitPlatform()`
      for the JVM/Android unit-test target so `commonTest` property tests run on the JVM
    - _Requirements: 6.1_

  - [ ] 1.2 Create the `HistoryEntry` @Serializable data model
    - Create `shared/src/commonMain/kotlin/com/adder/shared/model/HistoryEntry.kt` with a
      `@Serializable data class HistoryEntry(url: String, displayLabel: String, visitTimestamp: Long)`
    - Document that `displayLabel` is resolved at record time and `url` is the dedup identity
    - _Requirements: 1.1, 1.2, 1.3_

- [ ] 2. Create the `PersistentStore` expect/actual abstraction
  - [ ] 2.1 Define the `PersistentStore` expect class in commonMain
    - Create `shared/src/commonMain/kotlin/com/adder/shared/engine/PersistentStore.kt` with
      `expect class PersistentStore()` exposing `getString(key): String?` and `putString(key, value)`
    - Document that `getString` returns null on absence/read failure and `putString` silently no-ops
      on failure
    - _Requirements: 6.1, 6.6_

  - [ ] 2.2 Implement the Android `PersistentStore` actual backed by SharedPreferences
    - Create `shared/src/androidMain/.../engine/PersistentStore.android.kt` backed by
      `SharedPreferences("adder_prefs", MODE_PRIVATE)` obtained from an `AppContextHolder`
    - Create `shared/src/androidMain/.../AppContextHolder.kt` (object holding the application
      `Context`) and an `AdderContextInitializer : androidx.startup.Initializer<*>` that populates it
    - Register the initializer in `shared/src/androidMain/AndroidManifest.xml`; add the
      `androidx.startup:startup-runtime` dependency to the Android target
    - Wrap all prefs access in `runCatching`; treat a null Context/prefs as unavailable storage
      (reads return null, writes no-op)
    - _Requirements: 6.1, 6.6_

  - [ ] 2.3 Implement the iOS `PersistentStore` actual backed by NSUserDefaults
    - Create `shared/src/iosMain/.../engine/PersistentStore.ios.kt` backed by
      `NSUserDefaults.standardUserDefaults`, with `getString`/`putString` wrapped in `runCatching`
    - _Requirements: 6.1, 6.6_

- [ ] 3. Implement `HistoryStore` and its invariants
  - [ ] 3.1 Implement `HistoryStore` record/order/dedup/evict/delete/clear with eager load
    - Create `shared/src/commonMain/kotlin/com/adder/shared/engine/HistoryStore.kt` per the design:
      constructor `(store: PersistentStore = PersistentStore(), now: () -> Long = ...)`, companion
      `HISTORY_LIMIT = 200`, `MAX_TITLE_LENGTH = 512`, `STORAGE_KEY`
    - In-memory `MutableList<HistoryEntry>` in insertion order plus a monotonic `seq` per entry;
      `entries()` returns a copy sorted by `(visitTimestamp desc, seq desc)`
    - `record(url, title)`: early-return for blank/`about:blank`; exact-URL dedup updates timestamp +
      bumps `seq` (no duplicate); otherwise insert; resolve `displayLabel` = blank-title ? url :
      title.take(512); evict smallest `(visitTimestamp, seq)` while `size > HISTORY_LIMIT`; persist
    - `delete(url)`: remove matching entry (no-op if absent) then persist; `clear()`: empty + persist
    - `init`: `runCatching` the read + `Json.decodeFromString(ListSerializer(...))`, sort descending,
      truncate to 200, reassign `seq` by load position; start empty on any failure
    - Funnel every persistence write through `runCatching` so a failing store never throws
    - _Requirements: 1.1, 1.2, 1.3, 1.5, 1.6, 1.7, 2.1, 2.2, 2.3, 2.4, 2.5, 5.1, 5.2, 5.5, 6.1, 6.2, 6.3, 6.5, 6.6_

  - [ ]* 3.2 Add test doubles for HistoryStore tests
    - In `commonTest`, add `FakePersistentStore` (in-memory `MutableMap`) and
      `FailingPersistentStore` (throws on every `getString`/`putString`)
    - _Requirements: 6.1, 6.6_

  - [ ]* 3.3 Write property test for ordering
    - **Property 1: History is ordered by recency** (entries() non-increasing by timestamp, equal
      timestamps ordered by insertion newest-first, stable across repeated calls)
    - **Validates: Requirements 2.1, 2.2**
    - Kotest Property, `PropTestConfig(iterations = 100)`

  - [ ]* 3.4 Write property test for inserting a distinct valid URL
    - **Property 2: Recording a distinct valid URL inserts one entry** (count +1, entry url and
      visitTimestamp match the recorded values)
    - **Validates: Requirements 1.1, 2.4**
    - Kotest Property, min 100 iterations

  - [ ]* 3.5 Write property test for exact-URL dedup
    - **Property 3: Exact-URL dedup updates the timestamp without duplicating** (count unchanged,
      existing entry timestamp updated; covers reload-after-toggle case)
    - **Validates: Requirements 1.6, 2.3, 6.4**
    - Kotest Property, min 100 iterations

  - [ ]* 3.6 Write property test for display-label resolution
    - **Property 4: Display label is the truncated title or the URL** (label == title.take(512) when
      non-blank, else url)
    - **Validates: Requirements 1.2, 1.3, 3.3**
    - Kotest Property, min 100 iterations (include titles > 512 chars and blank/whitespace titles)

  - [ ]* 3.7 Write property test for the 200-entry cap
    - **Property 5: The store never exceeds the 200-entry limit** (in-memory and persisted counts
      <= 200; oldest by visitTimestamp evicted first)
    - **Validates: Requirements 1.7, 2.5, 3.5, 6.5**
    - Kotest Property, min 100 iterations

  - [ ]* 3.8 Write property test for excluded URLs
    - **Property 6: Excluded URLs are never recorded** (recording `about:blank`/blank leaves the
      entry set unchanged)
    - **Validates: Requirements 1.5**
    - Kotest Property, min 100 iterations

  - [ ]* 3.9 Write property test for single-entry deletion + persisted removal
    - **Property 8: Deleting an entry removes only that entry and persists the removal** (target
      removed, others retained in order, reloaded store lacks the deleted URL)
    - **Validates: Requirements 5.1, 5.3**
    - Kotest Property, min 100 iterations

  - [ ]* 3.10 Write property test for deleting an absent entry
    - **Property 9: Deleting an absent entry is a no-op** (in-memory set and persisted payload both
      unchanged)
    - **Validates: Requirements 5.5**
    - Kotest Property, min 100 iterations

  - [ ]* 3.11 Write property test for clear
    - **Property 10: Clearing removes all entries and persists the empty state** (empty in memory and
      after reload from the same backing store)
    - **Validates: Requirements 5.2**
    - Kotest Property, min 100 iterations

  - [ ]* 3.12 Write property test for persistence round-trip
    - **Property 11: Persistence round-trip preserves the ordered set** (new HistoryStore over the
      same PersistentStore yields entries() equal in urls/labels/timestamps and descending order)
    - **Validates: Requirements 6.1, 6.2**
    - Kotest Property, min 100 iterations (drive with sequences of record/delete/clear)

  - [ ]* 3.13 Write property test for graceful degradation
    - **Property 12: Storage failures degrade gracefully** (with `FailingPersistentStore`, record/
      delete/clear/entries never throw and ordering/dedup/200-cap invariants still hold)
    - **Validates: Requirements 6.6**
    - Kotest Property, min 100 iterations

  - [ ]* 3.14 Write example-based unit tests for HistoryStore
    - Fresh store over empty storage: `entries()` is empty (Req 3.4/5.4)
    - Multiple `record` calls on one instance accumulate up to the cap (Req 6.3)
    - _Requirements: 3.4, 6.3_

- [ ] 4. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 5. Add the WebView title accessor
  - [ ] 5.1 Add `currentTitle()` to `WebViewController`
    - In `shared/src/commonMain/.../ui/WebViewController.kt` add
      `internal var onCurrentTitle: (() -> String?)? = null` and `fun currentTitle(): String? =
      onCurrentTitle?.invoke()`
    - _Requirements: 1.2, 1.3_

  - [ ] 5.2 Register `onCurrentTitle` in the Android platform WebView
    - In `shared/src/androidMain/.../ui/PlatformWebView.android.kt`, set
      `controller.onCurrentTitle = { view.title }` when the WebView is created
    - _Requirements: 1.2_

  - [ ] 5.3 Register `onCurrentTitle` in the iOS platform WebView
    - In `shared/src/iosMain/.../ui/PlatformWebView.ios.kt`, set
      `controller.onCurrentTitle = { wkWebView.title }` when the WKWebView is created
    - _Requirements: 1.2_

- [ ] 6. Add history state, intents, and recording to `BrowserViewModel`
  - [ ] 6.1 Add history state and the `HistoryStore` instance with startup load
    - In `shared/src/commonMain/.../ui/BrowserViewModel.kt` add
      `historyVisible: Boolean` and `historyEntries: List<HistoryEntry>` (private-set
      `mutableStateOf`), a private `historyStore = HistoryStore()`, and an `init` that sets
      `historyEntries = historyStore.entries()`
    - _Requirements: 6.2, 6.3_

  - [ ] 6.2 Add history intents (open/close/revisit/delete/clear)
    - `openHistory()` (refresh entries + show), `closeHistory()` (hide, no reload),
      `onRevisit(entry)` (hide, set `url`/`inputText`, `webViewController.loadUrl(entry.url)`),
      `onDeleteHistory(entry)` and `onClearHistory()` (mutate store + refresh `historyEntries`)
    - _Requirements: 3.1, 3.6, 4.1, 4.2, 4.3, 5.1, 5.2, 5.3, 5.4, 5.5_

  - [ ] 6.3 Wire recording into `onPageFinished`
    - Add a `shouldRecord(url)` guard (excludes blank/`about:blank`); on a recordable load read the
      title via `webViewController.currentTitle().orEmpty()`, call `historyStore.record(url, title)`,
      then refresh `historyEntries`
    - _Requirements: 1.1, 1.4, 1.5, 6.4_

  - [ ]* 6.4 Add a fake `WebViewController` for ViewModel tests
    - In `commonTest`, provide a controller test double that records `loadUrl` calls and returns a
      configurable `currentTitle()` for the ViewModel history tests
    - _Requirements: 4.1_

  - [ ]* 6.5 Write property test for the revisit intent
    - **Property 7: Revisiting an entry loads it, closes history, and updates the address bar**
      (revisit issues `loadUrl(entry.url)`, sets `historyVisible=false`, sets `url`/`inputText`)
    - **Validates: Requirements 4.1, 4.2, 4.3**
    - Kotest Property, min 100 iterations
    - _Requirements: 4.1, 4.2, 4.3_

  - [ ]* 6.6 Write example-based unit tests for ViewModel history behavior
    - `openHistory()` then `closeHistory()` leaves `url` unchanged and issues no `loadUrl` (Req 3.6)
    - After `onRevisit(entry)` the store still contains `entry.url` (Req 4.4)
    - `onPageFinished` on a recordable URL records + refreshes `historyEntries`; an `about:blank`
      load records nothing (Req 1.4, 1.5, 5.3)
    - _Requirements: 3.6, 4.4, 1.4, 1.5, 5.3_

- [ ] 7. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 8. Build the History UI composables
  - [ ] 8.1 Implement `HistoryRow` and `HistoryEmptyState`
    - Create `shared/src/commonMain/.../ui/HistoryView.kt`; add `HistoryRow(entry, onClick, onDelete)`
      rendering `entry.displayLabel` (primary) + `entry.url` (secondary) with a trailing delete icon,
      and `HistoryEmptyState()` showing the no-history message
    - Add `@Preview` (multiplatform, `private`, `MaterialTheme`-wrapped, fake data): a title-label row,
      a URL-only long-URL row, and the empty state
    - _Requirements: 3.2, 3.3, 3.4, 5.4_

  - [ ] 8.2 Implement the `HistoryView` overlay composable
    - In the same file add `HistoryView(entries, onEntryClick, onEntryDelete, onClearAll, onClose,
      modifier)`: top bar (title, close, clear-all), then `HistoryEmptyState` when empty else a
      `LazyColumn` of `HistoryRow`s in the given (descending) order
    - Add `@Preview`s for populated and empty states (fake data)
    - _Requirements: 3.1, 3.2, 3.4, 3.5, 5.3, 5.4_

  - [ ] 8.3 Add the "History" menu item to `AppMenu`
    - In `shared/src/commonMain/.../ui/BrowserScreen.kt`, add an `onHistory` callback param to
      `AppMenu` and a "History" `DropdownMenuItem` next to "Settings" that dismisses the menu and
      invokes it; update the `AppMenu` preview call site
    - _Requirements: 3.1_

- [ ] 9. Wire the History UI into the browser screen
  - [ ] 9.1 Render `HistoryView` as an overlay driven by ViewModel state
    - In `BrowserScreen`, pass `onHistory = viewModel::openHistory` down to `AppMenu`, and when
      `viewModel.historyVisible` render `HistoryView` (full-screen overlay above the WebView area)
      wired to `historyEntries`, `onRevisit`, `onDeleteHistory`, `onClearHistory`, `closeHistory`
    - _Requirements: 3.1, 3.6, 4.1, 4.2, 4.3, 5.1, 5.2, 5.3, 5.4_

- [ ] 10. Final checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional (tests/test doubles) and can be skipped for a faster MVP.
- Each task references specific requirements for traceability; test tasks reference their design property.
- All 12 correctness properties from the design are covered by exactly one property-based test each
  (Properties 1-6, 8-12 under task 3; Property 7 under task 6), run with Kotest Property at a minimum
  of 100 iterations.
- Recording relies on the WebView invoking `onPageFinished` only for completed loads, so failed loads
  are excluded without extra handling (Req 1.4).
- Checkpoints ensure incremental validation as the store, ViewModel, and UI layers land.

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1"] },
    { "id": 1, "tasks": ["1.2", "5.1"] },
    { "id": 2, "tasks": ["2.1", "5.2", "5.3", "8.3"] },
    { "id": 3, "tasks": ["2.2", "2.3", "3.1", "8.1"] },
    { "id": 4, "tasks": ["3.2", "6.1", "8.2"] },
    { "id": 5, "tasks": ["3.3", "3.4", "3.5", "3.6", "3.7", "3.8", "3.9", "3.10", "3.11", "3.12", "3.13", "3.14", "6.2", "6.3", "6.4"] },
    { "id": 6, "tasks": ["6.5", "6.6", "9.1"] }
  ]
}
```
