# Caching, startup and refresh (phone and TV)

One policy in the shared modules, used by both apps.

## Startup: stored copy first, network second
- **Screens:** Home, Following, Browse and each category (one copy per category and language filter) are kept as JSON snapshots per
  account (`SnapshotCache`, platform cache area, 24 h). A screen shows its stored copy as soon as the account is known, then the
  network copy replaces it (stale-while-revalidate, `Loader`); a failed refresh keeps what is shown.
- **Session:** `TwitchSessionRepository.start()` reads the account (plain preferences) *before* decrypting the tokens with the Keystore, so
  screens read their snapshot while the Keystore works. Every call to Twitch waits for `TokenSource.awaitReady()` (tokens loaded): it is
  done in `TwitchApi` itself (every Helix request, so search, follows, chat sends, the player's channel loads and the live watcher are covered),
  in the chat connection (`EventSubChatSource`) and in `Loader`. The wait is bounded by 10 s (`READY_TIMEOUT_MS`): a token store that throws counts
  as nothing stored (signed out, requests released) and one that never answers delays a request by the timeout at most, it never hangs it.
  No network request is made before the first content.
- **Not measured:** no benchmark was run for this change (the host was saturated: load average 16 to 33, a debug cold start took 12 s and
  hit an ANR that has nothing to do with the code path). The expectation (content from disk right after the account is read) comes from
  the code path, not from numbers; re-measure with the benchmark module on a quiet host.

## Foreground refresh
- `ForegroundSignal` (shared core) is fired by the activity's `onStart` (phone and TV `MainActivity`). Every `Loader` that was given the
  signal refreshes when its data is at least 60 s old (or was never loaded), skips the refresh when a load is already running, keeps the
  shown data until the new copy lands (no scroll reset) and swaps lists through keyed `animateItem` (phone `itemMotion`, TV `itemMotion`).
- Tested with a fake clock in `ForegroundRefreshTest`.
- `follows()` is shared: a forced reload within 3 s of the previous load reuses it, so Home and Following refreshing together make one request.

## Profile pictures
- `AvatarStore`: channel id to picture URL, persisted (`avatars` snapshot) with a 7-day TTL per entry. Lookups are batched by 100 ids
  (Helix limit), only for ids not already known; a cold start needs no request for pictures it has (tested). The disk copy is written
  **off the request path**: `put` updates memory and signals one writer, which waits 500 ms (a burst of batches is one write) and writes the
  newest map without its expired entries; one writer means writes cannot land out of order (tested).
- Streams carry their `avatarUrl`; the phone shows it next to the streamer's name on every stream row (a circle with the initial while
  there is no picture). Coil keeps the image on disk (64 MB) and in memory (20 %) by URL, so a picture is downloaded once.

## Phone motion and lists
- One-line titles with an ellipsis on category cards and stream rows (no reserved second line).
- `Modifier.itemMotion(scope)`: fade in 240 ms, move 280 ms, fade out 140 ms, none with reduced motion. Used on Home, Following, Category,
  Browse (filter chips) and Search lists; items have stable keys.
- Search keeps the previous answer on screen while the next one loads (dimmed, with a 2 dp progress line after 150 ms); the skeleton is only
  for a first load with nothing to show.

## When the session ends (sign-out, or Twitch refusing the stored token)
`forgetAccountData` clears: the screen snapshots on disk (which hold the profile pictures and the last-seen times), the recent searches and
emotes, **the replay positions behind "continue watching"** (`VodProgressStore.clearAll`), and, in memory, the avatar map, the last-seen map and
the cached follows and clips lists (`AccountScoped.forgetAccount`). Tested in `SessionEndClearsAccountDataTest` for both ways the session can end, and
that a session that keeps working keeps all of it.

## Freshness and failed refreshes
- `Load.Ready` carries `loadedAtMs` (when the data was fetched; unknown for a copy read from disk) and `refreshFailed`. A failed refresh keeps
  the data on screen and sets the flag; a good one clears it.
- Phone: `StaleMarker` above Home, Following, Browse and Category data says "Couldn't refresh" (with "updated N min ago" once the data is three
  minutes old or more) and offers Retry; it is a live region, so a pull to refresh that failed is announced. Discover says it another way.
- TTLs (`TtlCache`) are measured on the **wall clock** (`WallClockTimeSource`), which keeps running in deep sleep; a clock set backwards makes
  a cached value stale, never fresh. `feedClips()` is cached for 10 minutes.

## TV: a refresh does not move the focus ring
`LoadGate` (TV) applies a refreshed list through `RefreshHold`: while focus is inside the content and the viewer pressed a key less than 4 s
ago, the new list waits; it is applied when they stop (or focus leaves). Loading, errors and the first data are shown at once, and a screen
nobody touches never waits. Keyed lists keep focus on an item that is still in the list. Policy tested in `RefreshHoldTest`; not exercised on a
real TV.
