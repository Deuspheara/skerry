# Caching, startup and refresh (phone and TV)

One policy in the shared modules, used by both apps.

## Startup: stored copy first, network second
- **Screens:** Home, Following, Browse and each category (one copy per category and language filter) are kept as JSON snapshots per
  account (`SnapshotCache`, platform cache area, 24 h). A screen shows its stored copy as soon as the account is known, then the
  network copy replaces it (stale-while-revalidate, `Loader`); a failed refresh keeps what is shown.
- **Session:** `TwitchSessionRepository.start()` reads the account (plain preferences) *before* decrypting the tokens with the Keystore, so
  screens read their snapshot while the Keystore works. Requests wait for `SessionRepository.awaitReady()` (tokens loaded), so none is sent
  unsigned. No network request is made before the first content.
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
  (Helix limit), only for ids not already known; a cold start needs no request for pictures it has (tested).
- Streams carry their `avatarUrl`; the phone shows it next to the streamer's name on every stream row (a circle with the initial while
  there is no picture). Coil keeps the image on disk (64 MB) and in memory (20 %) by URL, so a picture is downloaded once.

## Phone motion and lists
- One-line titles with an ellipsis on category cards and stream rows (no reserved second line).
- `Modifier.itemMotion(scope)`: fade in 240 ms, move 280 ms, fade out 140 ms, none with reduced motion. Used on Home, Following, Category,
  Browse (filter chips) and Search lists; items have stable keys.
- Search keeps the previous answer on screen while the next one loads (dimmed, with a 2 dp progress line after 150 ms); the skeleton is only
  for a first load with nothing to show.
