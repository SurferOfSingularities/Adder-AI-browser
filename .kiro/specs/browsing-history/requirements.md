# Requirements Document

## Introduction

This feature adds a browsing history capability to the Adder browser. As the user navigates
to web pages in the built-in WebView, each successfully loaded page is recorded as a history
entry. The user can open a dedicated history view to see recently visited sites in reverse
chronological order and tap an entry to revisit that page. The user can also remove individual
entries or clear the entire history.

The feature lives in the shared Compose Multiplatform module and follows the existing MVVM
unidirectional data flow: history state and intents are owned by the ViewModel layer, and the
history data itself is managed by a dedicated component in the shared module (consistent with
how `SiteWhitelist` is structured today). Browsing history persists across sessions: entries
are written to durable device storage through a platform storage abstraction (an `expect`/`actual`
Persistent_Store), so the recorded history survives app restarts rather than being scoped to a
single in-memory session.

## Glossary

- **Browser**: The Adder application's built-in WebView-based browser that loads and displays web pages.
- **History_Store**: The shared-module component that records, orders, retrieves, and removes browsing history entries, persisting those entries durably to the Persistent_Store so they survive app restarts.
- **Persistent_Store**: The platform-provided durable storage mechanism (e.g. Android SharedPreferences / DataStore, iOS UserDefaults) accessed from the shared module via an `expect`/`actual` abstraction, used to persist History_Entry items across sessions.
- **History_Entry**: A single recorded visit, consisting of the page URL, the page title (when available), and the visit timestamp.
- **History_View**: The Compose Multiplatform UI surface that displays the list of History_Entry items to the user.
- **Browser_ViewModel**: The existing `BrowserViewModel` that owns observable UI state and exposes navigation intents.
- **Visit_Timestamp**: The moment, in epoch milliseconds, at which a page finished loading and was recorded.
- **History_Limit**: The maximum number of History_Entry items retained by the History_Store, set to 200.
- **Page_Title**: The document title reported by the loaded page.

## Requirements

### Requirement 1: Record visited pages

**User Story:** As a user, I want each page I visit to be recorded, so that I can find sites I have been to before.

#### Acceptance Criteria

1. WHEN a page finishes loading successfully in the Browser, THE History_Store SHALL create a History_Entry containing the page URL and the Visit_Timestamp expressed as epoch milliseconds.
2. WHERE a Page_Title is reported for a loaded page, THE History_Store SHALL store the Page_Title, truncated to a maximum of 512 characters, as the display label in the corresponding History_Entry.
3. WHERE no Page_Title is reported for a loaded page, THE History_Store SHALL store the page URL as the display label of the corresponding History_Entry.
4. IF a page load fails before completion, THEN THE History_Store SHALL exclude that page from the recorded History_Entry items and SHALL preserve all previously recorded History_Entry items.
5. WHERE the loaded URL uses the "about:blank" scheme, THE History_Store SHALL exclude that URL from the recorded History_Entry items.
6. WHEN a page finishes loading with a URL that matches the URL of an existing History_Entry, THE History_Store SHALL update that History_Entry's Visit_Timestamp to the new Visit_Timestamp instead of creating a duplicate History_Entry.
7. WHEN creating a History_Entry would cause the total number of stored History_Entry items to exceed the History_Limit of 200, THE History_Store SHALL remove the History_Entry with the oldest Visit_Timestamp before storing the new History_Entry.

### Requirement 2: Order and deduplicate history

**User Story:** As a user, I want my history ordered by most recent and free of consecutive duplicates, so that the list is easy to scan.

#### Acceptance Criteria

1. THE History_Store SHALL order History_Entry items by Visit_Timestamp (epoch milliseconds) in descending order, with the History_Entry having the largest Visit_Timestamp appearing first.
2. IF two or more History_Entry items have equal Visit_Timestamp values, THEN THE History_Store SHALL order those items relative to one another by their insertion order, with the most recently inserted item first, so that the resulting order is deterministic.
3. WHEN a page finishes loading with a URL string exactly equal to the URL of the current most-recent History_Entry (the entry with the largest Visit_Timestamp), THE History_Store SHALL set the Visit_Timestamp of that existing History_Entry to the epoch-millisecond time of the page-load completion and SHALL NOT create a new History_Entry.
4. WHEN a page finishes loading with a URL string not equal to the URL of the current most-recent History_Entry, THE History_Store SHALL create a new History_Entry whose Visit_Timestamp equals the epoch-millisecond time of the page-load completion.
5. WHEN the count of History_Entry items exceeds the History_Limit of 200, THE History_Store SHALL remove History_Entry items in ascending Visit_Timestamp order (oldest first) until the count of History_Entry items equals 200.

### Requirement 3: View browsing history

**User Story:** As a user, I want to open a history view, so that I can see the sites I have recently visited.

#### Acceptance Criteria

1. WHEN the user selects the history action from the Browser menu, THE History_View SHALL display the History_Entry items ordered by Visit_Timestamp in descending order, with the most recently visited entry first.
2. THE History_View SHALL display, for each History_Entry, the Page_Title as the display label and the complete page URL.
3. IF a History_Entry has no Page_Title or an empty Page_Title, THEN THE History_View SHALL display the page URL as the display label for that entry.
4. WHILE the History_Store contains zero History_Entry items, THE History_View SHALL display an empty-state message indicating that no history is recorded.
5. THE History_View SHALL display at most History_Limit (200) History_Entry items, retaining the 200 most recent entries by Visit_Timestamp and omitting older entries.
6. WHEN the user dismisses the History_View, THE Browser SHALL return to the currently loaded page without reloading or changing it.

### Requirement 4: Revisit a page from history

**User Story:** As a user, I want to tap a history entry, so that I can return to that page.

#### Acceptance Criteria

1. WHEN the user selects a History_Entry from the History_View, THE Browser SHALL load the URL stored in the selected History_Entry.
2. WHEN the user selects a History_Entry from the History_View, THE History_View SHALL close and display the Browser page view.
3. WHEN the user selects a History_Entry from the History_View, THE Browser SHALL update the address bar to display the URL of the selected History_Entry.
4. IF the URL stored in the selected History_Entry fails to load, THEN THE Browser SHALL display an error indication identifying the failed URL and SHALL retain the selected History_Entry in the History_Store.

### Requirement 5: Remove history entries

**User Story:** As a user, I want to delete history entries, so that I can manage what is recorded.

#### Acceptance Criteria

1. WHEN the user selects the delete action for a single History_Entry, THE History_Store SHALL remove that History_Entry from the recorded items and SHALL persist the removal to the Persistent_Store so that the removed History_Entry remains absent after the application is relaunched.
2. WHEN the user selects the clear-all action, THE History_Store SHALL remove all History_Entry items and SHALL persist the cleared state to the Persistent_Store so that no History_Entry items are present after the application is relaunched.
3. WHEN a History_Entry is removed, THE History_View SHALL update the displayed list to exclude the removed History_Entry while retaining all non-removed entries.
4. WHEN the count of remaining History_Entry items reaches zero, THE History_View SHALL hide the entry list and display the empty-state message.
5. IF the user selects the delete action for a History_Entry that is no longer present in the History_Store, THEN THE History_Store SHALL make no change to the recorded items and SHALL make no change to the Persistent_Store.

### Requirement 6: History persistence

**User Story:** As a user, I want my history to be saved durably, so that I can revisit sites I visited in earlier sessions even after restarting the app.

#### Acceptance Criteria

1. WHEN a History_Entry is created, updated, or removed, THE History_Store SHALL write the resulting set of History_Entry items to the Persistent_Store.
2. WHEN the application starts, THE History_Store SHALL load the previously persisted History_Entry items from the Persistent_Store and SHALL make them available to the History_View in descending Visit_Timestamp order.
3. WHILE the Browser_ViewModel instance is active, THE History_Store SHALL retain all recorded History_Entry items, up to the History_Limit of 200 entries, across page navigations.
4. WHEN ad blocking is toggled and the current page reloads, THE History_Store SHALL retain the existing History_Entry items unchanged except for the most-recent-entry Visit_Timestamp update defined in Requirement 2.
5. IF recording a new History_Entry would cause the History_Store to exceed the History_Limit of 200 entries, THEN THE History_Store SHALL remove the History_Entry with the lowest Visit_Timestamp before adding the new entry, keeping both the in-memory count and the persisted set at or below 200 entries.
6. IF the Persistent_Store is unavailable or a read or write operation fails, THEN THE History_Store SHALL continue operating with its in-memory History_Entry items for the duration of the session without crashing the Browser.
