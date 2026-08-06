# Requirements Document

## Introduction

This feature adds multi-tab browsing to the Adder browser. Today the browser is a single-screen,
single-WebView experience: `BrowserViewModel` owns one set of page state (`url`, `inputText`,
`canGoBack`, `canGoForward`, `isLoading`, `isModelBusy`) and `WebViewController` bridges to exactly
one live platform WebView. This feature introduces a collection of independent Tabs, each with its
own page, title, navigation history, and loading state, plus the UI surfaces to create, switch,
and close Tabs.

Three constraints shape the requirements:

1. **Live WebViews are expensive.** Android WebView and WKWebView each hold significant memory, and
   the on-device LLM (Gemini Nano / Apple Foundation Models) competes for the same budget. The
   number of simultaneously live WebViews is therefore capped, and Tabs beyond that cap are
   hibernated down to their URL and title.
2. **The on-device LLM is a single scarce resource.** The ad-block pipeline must not run
   concurrently for several Tabs, and the foreground Tab must take priority over background Tabs.
3. **Ad removal is destructive and reload-based.** The existing blocking toggle works by reloading
   the page. With multiple Tabs, each Tab records which blocking mode its currently rendered content
   was produced under, so a Tab rendered under a stale mode is brought into line when it is opened.

The feature lives in the shared Compose Multiplatform module and follows the existing MVVM
unidirectional data flow. Tab state and intents are owned by the ViewModel layer, the Tab collection
and its invariants are owned by a dedicated shared-module component, and cross-session persistence
reuses the `expect`/`actual` Persistent_Store introduced by the browsing-history feature. Tab UI is
presented as an in-app overlay, consistent with History_View and with the project convention of not
introducing Compose Navigation.

## Glossary

- **Browser**: The Adder application's built-in WebView-based browser that loads and displays web pages.
- **Tab**: An independent browsing context consisting of a current URL, a display title, a page load state, back/forward availability, and a Rendered_Blocking_Mode.
- **Tab_Manager**: The shared-module component that owns the Tab collection and its invariants: creation, closing, ordering, activation, Live_Web_View budgeting, and persistence to the Persistent_Store.
- **Tab_Order**: The left-to-right sequence in which Tabs are presented to the user; position 1 is the first Tab.
- **Active_Tab**: The single Tab whose page is displayed in the Browser page area and whose state drives the Address_Bar, the progress indicator, and the navigation controls.
- **Background_Tab**: Any Tab that is not the Active_Tab.
- **Live_Web_View**: A platform WebView instance (Android `WebView` or iOS `WKWebView`) attached to a Tab and holding that Tab's rendered page and in-WebView back/forward list.
- **Hibernated_Tab**: A Tab that holds no Live_Web_View and is represented only by its retained current URL and display title.
- **Live_WebView_Budget**: The maximum number of Tabs that hold a Live_Web_View at one time, set to 3.
- **Tab_Limit**: The maximum number of Tabs that may exist at one time, set to 20.
- **Tab_Switcher**: The Compose Multiplatform UI surface that lists every Tab and offers the create, activate, and close actions.
- **Tab_Count_Button**: The control in the Browser toolbar that displays the current Tab count and opens the Tab_Switcher.
- **Address_Bar**: The existing URL text field in the Browser toolbar.
- **Blocking_Mode**: The Browser-wide ad-blocking setting, either enabled or disabled, currently held as `BrowserViewModel.blockingEnabled`.
- **Rendered_Blocking_Mode**: The Blocking_Mode value that was in effect when a Tab's currently rendered page finished loading.
- **Ad_Block_Pipeline**: The existing DOM extraction, heuristic classification, LLM classification, and DOM removal sequence orchestrated by `AdBlockEngine`.
- **Classification_Pass**: One end-to-end execution of the Ad_Block_Pipeline for one page load in one Tab.
- **Browser_ViewModel**: The existing `BrowserViewModel` that owns observable UI state and exposes browser intents.
- **Persistent_Store**: The platform-provided durable key/value storage accessed from the shared module through the existing `expect`/`actual` abstraction (Android `SharedPreferences`, iOS `NSUserDefaults`).
- **Tab_Session**: The persisted representation of the Tab collection: each Tab's current URL and display title, the Tab_Order, and which Tab is the Active_Tab.
- **History_Store**: The existing shared-module component that records and orders browsing history entries.
- **Initial_URL**: The URL a newly created Tab loads, `https://www.google.com`, matching the existing `BrowserViewModel.INITIAL_URL`.
- **Display_Title**: The label shown for a Tab: the page title reported by the loaded page, truncated to 512 characters, or the Tab's current URL when no page title is reported.

## Requirements

### Requirement 1: Create tabs

**User Story:** As a user, I want to open additional tabs, so that I can keep several pages open at the same time.

#### Acceptance Criteria

1. WHEN the Browser starts with no persisted Tab_Session, THE Tab_Manager SHALL create exactly one Tab loading the Initial_URL and SHALL set that Tab as the Active_Tab.
2. WHEN the user selects the new-tab action, THE Tab_Manager SHALL create a Tab loading the Initial_URL, SHALL place that Tab at the last position in the Tab_Order, and SHALL set that Tab as the Active_Tab.
3. WHEN a Tab is created, THE Address_Bar SHALL display the URL that the created Tab loads.
4. WHEN a loaded page requests a new window for a target URL, THE Tab_Manager SHALL create a Tab loading that target URL, SHALL place that Tab at the last position in the Tab_Order, and SHALL set that Tab as the Active_Tab.
5. IF creating a Tab would raise the Tab count above the Tab_Limit of 20, THEN THE Tab_Manager SHALL leave the existing Tab collection and the Active_Tab unchanged and THE Browser SHALL display a message stating that the limit of 20 tabs is reached.
6. WHEN a Tab is created, THE Tab_Manager SHALL assign that Tab an identifier that differs from the identifier of every other existing Tab.

### Requirement 2: Switch between tabs

**User Story:** As a user, I want to switch to another open tab, so that I can move between pages without losing my place.

#### Acceptance Criteria

1. WHEN the user selects a Tab from the Tab_Switcher, THE Tab_Manager SHALL set that Tab as the Active_Tab and SHALL retain every other Tab in the Tab collection.
2. WHEN the Active_Tab changes, THE Browser SHALL display the newly Active_Tab's page in the page area and SHALL display the newly Active_Tab's current URL in the Address_Bar.
3. WHEN the Active_Tab changes, THE Browser SHALL display the newly Active_Tab's back availability, forward availability, and page load state in the navigation controls and the progress indicator.
4. WHERE the newly Active_Tab holds a Live_Web_View whose Rendered_Blocking_Mode equals the current Blocking_Mode, WHEN that Tab becomes the Active_Tab, THE Browser SHALL display the page already rendered in that Live_Web_View without issuing a page load.
5. WHEN a Tab becomes the Active_Tab, THE Tab_Manager SHALL record that Tab as the most recently activated Tab.
6. WHEN the user dismisses the Tab_Switcher without selecting a Tab, THE Tab_Manager SHALL leave the Active_Tab unchanged and THE Browser SHALL display the Active_Tab's page without issuing a page load.

### Requirement 3: Close tabs

**User Story:** As a user, I want to close tabs I no longer need, so that I can keep my open tabs manageable and free device resources.

#### Acceptance Criteria

1. WHEN the user selects the close action for a Tab, THE Tab_Manager SHALL remove that Tab from the Tab collection and SHALL retain the remaining Tabs in their existing relative Tab_Order.
2. WHEN a Tab is closed, THE Tab_Manager SHALL stop that Tab's page load and SHALL release that Tab's Live_Web_View.
3. WHEN the Active_Tab is closed and at least one Tab exists at a higher position in the Tab_Order, THE Tab_Manager SHALL set the Tab at the nearest higher position as the Active_Tab.
4. WHEN the Active_Tab is closed and every remaining Tab is at a lower position in the Tab_Order, THE Tab_Manager SHALL set the Tab at the nearest lower position as the Active_Tab.
5. WHEN a Background_Tab is closed, THE Tab_Manager SHALL leave the Active_Tab unchanged.
6. WHEN the last remaining Tab is closed, THE Tab_Manager SHALL create one Tab loading the Initial_URL and SHALL set that Tab as the Active_Tab, so that the Tab count is 1.
7. IF the user selects the close action for a Tab that is no longer present in the Tab collection, THEN THE Tab_Manager SHALL leave the Tab collection, the Tab_Order, and the Active_Tab unchanged.

### Requirement 4: Per-tab browsing state

**User Story:** As a user, I want each tab to keep its own page, title, and navigation history, so that tabs stay independent of one another.

#### Acceptance Criteria

1. THE Tab_Manager SHALL hold, for each Tab, a current URL, a Display_Title, a page load state, a back availability value, a forward availability value, and a Rendered_Blocking_Mode.
2. WHEN a page finishes loading in a Tab, THE Tab_Manager SHALL set that Tab's current URL to the loaded URL and SHALL set that Tab's Display_Title to the reported page title truncated to 512 characters.
3. WHERE a loaded page reports no page title, THE Tab_Manager SHALL set the Tab's Display_Title to that Tab's current URL.
4. WHEN the user submits a URL in the Address_Bar, THE Browser SHALL load that URL in the Active_Tab and SHALL leave every Background_Tab's current URL unchanged.
5. WHEN the user selects the back action, the forward action, or the refresh action, THE Browser SHALL apply that action to the Active_Tab's Live_Web_View and SHALL leave every Background_Tab's rendered page unchanged.
6. WHEN a page starts loading in a Background_Tab, THE Browser SHALL leave the progress indicator, the Address_Bar, and the navigation controls showing the Active_Tab's state.
7. WHEN the user selects a browsing history entry from the History_View, THE Browser SHALL load that entry's URL in the Active_Tab.
8. WHEN a page starts loading in a Tab, THE Tab_Manager SHALL set that Tab's page load state to loading and SHALL set that Tab's current URL to the URL being loaded.

### Requirement 5: Tab switcher and tab count surfaces

**User Story:** As a user, I want a place to see and manage all my open tabs, so that I can find and reach the page I want.

#### Acceptance Criteria

1. THE Tab_Count_Button SHALL display the current number of Tabs in the Tab collection.
2. WHEN the user selects the Tab_Count_Button, THE Tab_Switcher SHALL display every Tab in the Tab collection in Tab_Order.
3. THE Tab_Switcher SHALL display, for each listed Tab, that Tab's Display_Title and that Tab's current URL.
4. THE Tab_Switcher SHALL display a visual indicator identifying which listed Tab is the Active_Tab.
5. THE Tab_Switcher SHALL display a close action for each listed Tab and a new-tab action.
6. WHEN the Tab collection changes, THE Tab_Switcher SHALL display the resulting Tab collection and THE Tab_Count_Button SHALL display the resulting Tab count.
7. WHILE the Tab_Switcher is displayed, THE Browser SHALL retain the Active_Tab's rendered page so that dismissing the Tab_Switcher displays that page without a page load.

### Requirement 6: Ad blocking across tabs

**User Story:** As a user, I want the ad-blocking setting to behave predictably in every tab, so that what I see always matches what the toggle says.

#### Acceptance Criteria

1. THE Blocking_Mode SHALL apply to every Tab in the Tab collection.
2. WHEN the user toggles the Blocking_Mode, THE Browser SHALL reload the Active_Tab under the new Blocking_Mode.
3. WHEN a page finishes loading in a Tab, THE Tab_Manager SHALL set that Tab's Rendered_Blocking_Mode to the Blocking_Mode value that was in effect for that page load.
4. WHEN a Tab whose Rendered_Blocking_Mode differs from the current Blocking_Mode becomes the Active_Tab, THE Browser SHALL reload that Tab under the current Blocking_Mode.
5. WHILE the Blocking_Mode is disabled, THE Browser SHALL load pages in every Tab without injecting the early-hide CSS and without running the Ad_Block_Pipeline.
6. WHILE the Active_Tab's page load is in progress or a Classification_Pass for the Active_Tab is running, THE Browser SHALL present the Blocking_Mode toggle control in a disabled state.
7. THE Ad_Block_Pipeline SHALL apply the same site whitelist and the same detection cache to page loads in every Tab.

### Requirement 7: Ad-block pipeline scheduling across tabs

**User Story:** As a user, I want ad detection to stay responsive when several tabs load at once, so that the tab I am looking at is cleaned first.

#### Acceptance Criteria

1. WHILE the Blocking_Mode is enabled, WHEN a page finishes loading in a Tab, THE Ad_Block_Pipeline SHALL request a Classification_Pass for that Tab.
2. THE Ad_Block_Pipeline SHALL run at most one Classification_Pass at a time across the whole Tab collection.
3. WHEN a Classification_Pass is requested for the Active_Tab while a Classification_Pass for a Background_Tab is running, THE Ad_Block_Pipeline SHALL cancel the running Background_Tab pass and SHALL start the Active_Tab pass.
4. WHEN a Classification_Pass is requested for a Background_Tab while another Classification_Pass is running, THE Ad_Block_Pipeline SHALL defer the requested pass until the running pass completes or is cancelled.
5. WHEN a Tab whose Classification_Pass was cancelled becomes the Active_Tab, THE Ad_Block_Pipeline SHALL request a Classification_Pass for that Tab.
6. WHEN a Tab is closed while a Classification_Pass for that Tab is running or deferred, THE Ad_Block_Pipeline SHALL cancel that pass and SHALL leave passes for the remaining Tabs unchanged.
7. WHILE a Classification_Pass for the Active_Tab is running, THE Browser SHALL display the model-activity indicator.
8. WHILE the only running Classification_Pass belongs to a Background_Tab, THE Browser SHALL leave the model-activity indicator hidden and SHALL leave the Active_Tab's page area free of the model-activity overlay.
9. WHEN a Classification_Pass completes for a Tab, THE Ad_Block_Pipeline SHALL apply the resulting removals to that Tab's Live_Web_View and SHALL leave every other Tab's rendered page unchanged.

### Requirement 8: Live WebView budget and tab hibernation

**User Story:** As a user, I want many open tabs without the browser running out of memory, so that the app stays stable on my device.

#### Acceptance Criteria

1. THE Tab_Manager SHALL hold a Live_Web_View for the Active_Tab.
2. WHILE the Tab count exceeds the Live_WebView_Budget of 3, THE Tab_Manager SHALL hold a Live_Web_View for the Active_Tab and for the 2 most recently activated other Tabs, and SHALL release the Live_Web_View of every remaining Tab.
3. WHEN a Tab's Live_Web_View is released, THE Tab_Manager SHALL stop that Tab's page load, SHALL destroy the platform WebView instance, and SHALL retain that Tab's current URL and Display_Title.
4. WHEN a Hibernated_Tab becomes the Active_Tab, THE Browser SHALL create a Live_Web_View for that Tab and SHALL load that Tab's retained current URL.
5. WHEN a Live_Web_View is created for a Hibernated_Tab, THE Tab_Manager SHALL set that Tab's back availability and forward availability to the values reported by the newly created Live_Web_View.
6. WHILE a Tab is a Hibernated_Tab, THE Tab_Switcher SHALL display that Tab's retained Display_Title and retained current URL.
7. THE Tab_Manager SHALL retain the current URL and Display_Title of every Tab in the Tab collection, up to the Tab_Limit of 20 Tabs, independently of how many Live_Web_View instances exist.

### Requirement 9: Tab session persistence

**User Story:** As a user, I want my open tabs to still be there after I restart the app, so that I do not lose the pages I was reading.

#### Acceptance Criteria

1. WHEN a Tab is created, closed, or activated, or when a Tab's current URL or Display_Title changes, THE Tab_Manager SHALL write the resulting Tab_Session to the Persistent_Store.
2. WHEN the Browser starts and a Tab_Session is present in the Persistent_Store, THE Tab_Manager SHALL restore the persisted Tabs with their persisted current URLs, Display_Titles, and Tab_Order, and SHALL set the persisted Active_Tab as the Active_Tab.
3. WHEN a Tab_Session is restored, THE Browser SHALL create a Live_Web_View for the Active_Tab only and SHALL treat every restored Background_Tab as a Hibernated_Tab.
4. WHEN a Tab_Session is restored, THE Browser SHALL load the restored Active_Tab's persisted current URL under the current Blocking_Mode.
5. IF the persisted Tab_Session is absent, unreadable, or contains zero Tabs, THEN THE Tab_Manager SHALL create one Tab loading the Initial_URL and SHALL set that Tab as the Active_Tab.
6. IF the persisted Tab_Session contains more Tabs than the Tab_Limit of 20, THEN THE Tab_Manager SHALL restore the first 20 Tabs in the persisted Tab_Order and SHALL omit the remaining persisted Tabs.
7. IF a read from or a write to the Persistent_Store fails, THEN THE Tab_Manager SHALL continue operating with the in-memory Tab collection for the remainder of the session without propagating an exception to the Browser.

### Requirement 10: Browsing history across tabs

**User Story:** As a user, I want one browsing history covering every tab, so that I can find a page again regardless of which tab I opened it in.

#### Acceptance Criteria

1. WHEN a page finishes loading in any Tab, THE History_Store SHALL record a history entry for that page under the existing recording, ordering, deduplication, and eviction rules.
2. THE History_Store SHALL present a single set of history entries covering page loads from every Tab.
3. WHEN a page finishes loading in a Background_Tab, THE Browser SHALL leave the Active_Tab's displayed page and Address_Bar unchanged while THE History_Store records the history entry.
4. WHEN a Tab is closed, THE History_Store SHALL retain every history entry recorded from that Tab.

### Requirement 11: Cross-platform tab behavior

**User Story:** As a user on either Android or iOS, I want tabs to work the same way, so that the browser behaves consistently across my devices.

#### Acceptance Criteria

1. THE Tab_Manager SHALL produce the same Tab collection, Tab_Order, and Active_Tab outcomes for a given sequence of create, close, and activate actions on Android and on iOS.
2. WHERE the platform is Android, THE Browser SHALL back each Live_Web_View with an `android.webkit.WebView` instance embedded in Compose through `AndroidView`.
3. WHERE the platform is iOS, THE Browser SHALL back each Live_Web_View with a `WKWebView` instance embedded in Compose through `UIKitView`.
4. THE Tab_Switcher, the Tab_Count_Button, and the Tab state model SHALL be defined in the shared module's `commonMain` source set and SHALL be used by both the Android application and the iOS application.
5. WHEN a Tab's Live_Web_View is released on either platform, THE Browser SHALL free that platform WebView instance so that the number of live platform WebView instances is at most the Live_WebView_Budget of 3.
