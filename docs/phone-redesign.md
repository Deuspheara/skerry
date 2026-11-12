# Phone redesign: research and plan

Scope: `:phone` only. The TV app and `shared/*` do not change except where stated. Written before any code, so the layout work
lands first and the optimisation work (profiles, benchmarks) is done once, after it.

User asks this plan covers: (1) drop the stock Material 3 bar look for a modern live-app layout, (2) tap a stream = player with
chat, the channel page is reached from the streamer row under the player, (3) YouTube-style drag to shrink the player into the app,
(4) chat input too big, (5) category cards grow when a name wraps to two lines, (6) fullscreen flicker on rotation.

## 1. Findings

Honest caveat: no official design specs for TikTok, Instagram, Kick or Twitch are published. Everything below comes from press and
help-centre pages, so the numbers in section 2 are **our decisions informed by these patterns**, not measured copies.

### Mini player (YouTube)

- Until Oct 2024 YouTube docked a strip **above the bottom navigation bar**: thumbnail, title, play/pause, close. Dragging the full
  player down collapsed to it, tapping expanded it again.
  [9to5Google 2018](https://9to5google.com/2018/05/24/youtube-android-miniplayer-ios-rolling-out/),
  [Droid Life](https://www.droid-life.com/2018/05/24/youtube-app-gets-a-better-collapsable-mini-player-when-browsing/)
- Oct 2024: a **floating, PiP-like rectangle** you can move and resize anywhere in the app, snapping to a position; close with the
  X or by dragging it to the bottom. 2025: the control bar was removed (only overlaid play/pause and close) and it can dock to the
  left or right edge. Users were split, some preferred the strip.
  [9to5Google 2024](https://9to5google.com/2024/07/24/youtube-miniplayer-picture-in-picture/),
  [Android Authority](https://www.androidauthority.com/youtube-floating-mini-player-design-tweak-3549849/)
- Decision: a **docked strip above the bottom bar** (the strip is predictable, keeps the bar reachable, needs no free-floating
  hit-testing, and is the pattern that shipped for six years). A floating variant is a later option, not part of this work.

### Live-app navigation and chat

- Twitch (July 2024 redesign): the **Home tab is a swipeable discovery feed**, tap the screen to join and chat; a follow drawer at
  the upper left lists who is live; the 2017 bar had three tabs. Reviewers note you must tap the avatar to reach the channel.
  [Twitch blog](https://blog.twitch.tv/en/2024/07/29/find-content-you-love-faster-with-the-new-twitch-mobile-app),
  [9to5Google](https://9to5google.com/2024/05/01/twitch-discovery-feed-app-redesign/),
  [PhoneArena](https://www.phonearena.com/news/twitch-mobile-app-redesigned_id160927)
- Kick: chat is opened from a chat icon **below the player**, a box at the bottom, then send; users complain when chat shrinks so the
  field is hidden by the keyboard. [Kick help](https://help.kick.com/en/articles/14994597-the-kick-mobile-app-a-viewer-s-guide)
- TikTok LIVE: comment box at the **bottom-left labelled "Add comment"**, messages in a translucent column over the video on the
  left/right; the box is absent when the host restricts chat. Sources disagree on hide-gestures, so we do not copy one.
  [Influenceflow](https://influenceflow.io/resources/how-to-read-chat-during-tiktok-live-the-complete-guide-2026/),
  [topqlearn](https://topqlearn.com/how-to-send-messages-in-tiktok-live-chat)
- YouTube Android tested a **floating comments panel in landscape**; livestream UX guidance: chat tray as default, overlay as an
  option, people switch to portrait to type. [9to5Google](https://9to5google.com/2021/06/07/youtube-on-android-tests-floating-comments-section-when-viewing-landscape-video/),
  [LiveLike](https://docs.livelike.com/docs/design-best-practices)
- Instagram: **icon-only bottom bar**, fixed positions (icon-only is only safe when positions never move); 2025 re-layout put
  Reels and DMs in the bar and the create button top-left.
  [Telerik](https://www.telerik.com/blogs/is-it-ever-ok-use-icons-without-labels-mobile-app-design),
  [Design Compass](https://designcompass.org/en/2025/10/01/instagram-testing-reels-focused-tab-layout/)
- Material 3 Expressive: the old navigation bar is deprecated for a **shorter "flexible" bar**; Google apps (Photos, Chat, Finance)
  moved to **floating pill bars**, which M3 does not offer as a nav component, so it is custom anyway.
  [9to5Google](https://9to5google.com/2025/05/14/material-3-expressive-navigation/),
  [Google Home bottom bar](https://9to5google.com/2025/11/19/google-home-bottom-bar)

### Compose mechanics

- `Modifier.anchoredDraggable` (foundation) replaces Material's deprecated `Swipeable`; an open-source mini/full player uses anchors
  by offset from the top and fades content by the expansion ratio.
  [Android docs](https://developer.android.com/jetpack/compose/touch-input/pointer-input/migrate-swipeable),
  [example](https://rahul-gill.github.io/blog/24-06-10-compose-music-player-layout)
- No source covers keeping one video surface alive through mini and full. The common advice (one player held outside the screen,
  surface drawn by whichever screen is active, `DisposableEffect` to detach) is unverified against current Media3, so our design
  avoids moving the view at all (section 3, "one surface").

### What the current code does (the reason a refactor is needed)

- `PhoneRoot.kt`: `NavigationSuiteScaffold` (stock M3 bar/rail, labels, 80 dp bar). `LiveKey`, `ReplayKey`, `ClipKey` are **pages on a
  tab back stack**: leaving the page destroys the player (`PlayerViewModel` is keyed `live-$login`, `rememberNativeVideoPlayer` is
  composable-scoped). So a mini player is impossible today without hoisting.
- `ChannelRoute` receives `onPlay = player::open`, and stream cards in Home, Category and Search currently open the **channel page**
  (`onChannel`). That is the "streamer page instead of player" behaviour.
- `PlayerScaffold.kt` owns orientation, immersive, PiP, controls and the stage; the stage is drawn in three different parents
  (PiP, landscape `Row`, portrait `Column`), so the `AndroidView` is recreated when the layout switches. That recreation (plus
  `requestedOrientation` flips) is the most likely fullscreen flicker: see section 3.6.
- `ChatInputRow` is an `OutlinedTextField` (56 dp min height, label padding, 48 dp send button) plus 4 dp vertical padding: roughly
  66 dp for one line. That is what looks heavy.
- `CategoryCard`: `maxLines = 2` without `minLines`, so a two-line name makes that card taller than its row neighbours
  (`StreamCard` already does `minLines = layout.minTitleLines`, categories do not).

## 2. Design proposal

Keep Material 3 as the **component and accessibility foundation** (ripple, semantics, text fields' IME handling, theming tokens) but
stop using its default *look* for chrome. Rule: custom chrome for the bottom bar, top bar, cards, mini player and chat input; M3 for
dialogs, sheets, snackbar, switches, text selection.

### 2.1 Tokens

New `PhoneChrome` object next to `PhoneTheme`, plain `@Immutable` data read from `CompositionLocal`; **no new colours in the base
scheme**, existing dark/light schemes stay (they carry the AA checks already documented).

| Token | Value |
|---|---|
| Bar height | 56 dp content + navigation-bar inset (icon-only). Was 80 dp + labels |
| Bar surface | `surface` at 94 % alpha dark / 96 % light, 0.5 dp top hairline `outlineVariant`; **no blur** (RenderEffect blur costs a layer and a full-screen read on mid-range phones; the translucency is only visual, content scrolls under it with a bottom `contentPadding`) |
| Active tab | filled icon 26 dp in `onSurface`, 3 dp primary dot under it; inactive: outlined icon 24 dp `onSurfaceVariant`; a 48 x 48 dp touch target each |
| Labels | none in the bar (talkback `contentDescription` + `Role.Tab` stay); tab order fixed (Instagram rule) |
| Screen gutter | 16 dp (unchanged) |
| Radii | thumbnails 10 dp, chips 999 dp, input 20 dp, mini player 0 (docked strip) |
| Type | existing M3 `Typography`, but titles `titleSmall` 14/20 semibold, meta `bodySmall` 12/16, viewer chip `labelSmall` 11/14 |
| Motion | existing `LocalReducedMotion` gates everything below |

Tabs stay the same four (Home, Browse, Search, Settings) so deep behaviour and tests stay valid. A rail is kept for width >= 600 dp
(same custom look, 72 dp wide, icons only).

### 2.2 Top bars

- **Home / Browse**: no `TopAppBar`. A 56 dp transparent row on the page surface: app mark or title left, **search pill** (36 dp high,
  `surfaceContainerHigh`, search icon + hint, opens the Search tab) filling the middle, **avatar (28 dp) or sign-in icon** right
  (opens Settings). It scrolls away with the list (collapsing = it is the first list item, no `nestedScroll` machinery) and comes back
  on a scroll up (`scrollBehavior`-free: a `derivedStateOf` on `firstVisibleItemIndex` toggles an `AnimatedVisibility`-free
  `graphicsLayer.translationY`; one State read).
- **Detail pages** (Category, Channel): a transparent 48 dp row with back arrow and title; the status bar area shows the page
  background (no tonal elevation tint on scroll).
- **Live badge**: the red LIVE chip is 18 dp high, 4 dp radius, `PhoneColors.Live`, white 10/12 bold text; viewer chip: scrim 60 %
  background, "12.4K" 11 sp, same height, bottom-left of the thumbnail.

### 2.3 Cards

- **Stream card (phone, 1 column)**: keep the **compact row** (it fits 6-7 streams per screen, a feature for a viewing app), but
  reserve the text block height: title `minLines = 2, maxLines = 2` is wrong in a row, so instead fix the row height to the thumbnail
  height (168 x 94.5 dp). The text column is `fillMaxHeight` with title at top (max 2 lines), channel and category pinned to the bottom.
  Every row is the same height whether the title is one or two lines.
- **Stream card (wide)**: unchanged geometry; keep `minLines = 2` for the title.
- **Category card**: 3:4 poster, name in a **fixed 2-line slot** (`minLines = 2, maxLines = 2`, 12 sp / 16 sp = 32 dp tall) with a
  viewer count line below at 11 sp; the card never changes height. Names are `TextOverflow.Ellipsis`. A unit test checks the slot
  height is a constant.
- **Home first item** (optional step, after the rest): the top live stream of the follows as a **full-bleed 16:9 hero** with chat
  teaser, tapping it opens the player. Not in the first cut.

### 2.4 Chat input (the Twitch/Kick/TikTok shared shape)

A **single pill**, not an outlined field:

- Container 40 dp high (one line; grows to 3 lines max, 96 dp, with the same 20 dp radius becoming 16 dp), `surfaceContainerHigh`,
  no outline, no floating label, 16 dp start padding.
- `BasicTextField` + hint text `bodyMedium` `onSurfaceVariant` ("Send a message"). Emote button (24 dp icon) is **not** in this cut (no emote
  picker exists in the app); the slot is left in the layout spec.
- Send: a 32 dp filled circle, `primary`, inside the pill's trailing edge, shown only when the text is not blank (the pill is
  otherwise free to use its full width). Touch target 48 dp through `minimumInteractiveComponentSize`.
- Outer padding 8 dp horizontal, 6 dp vertical, so the whole row is ~52 dp (was ~66 dp).
- IME: `imePadding()` on the row, `ImeAction.Send`, `singleLine = false` with `maxLines = 3`; Enter sends (`KeyboardActions`).
- Landscape side panel: same pill, over the chat panel, with `scrim` 55 % background instead of `surfaceContainerHigh`.
- Chat lines, badges and the jump-to-live pill stay (they were reviewed). The jump pill moves from an `ElevatedAssistChip` to a 32 dp
  pill in the same style as the viewer chip.

### 2.5 Player layout (portrait)

Expanded: 16:9 stage under the status bar, then a **streamer row** (avatar 36 dp, name `titleSmall`, category + live duration
`bodySmall`, Follow-on-Twitch icon, "Channel" chevron), then the chat. Tapping the row opens the channel page. Collapsed (mini):
56 dp strip above the bar: 100 x 56 dp video, title + channel (1 line each), play/pause, close.

## 3. Architecture

### 3.1 Principle: the player is not a page

Move the playing session out of navigation into one **root overlay** in `PhoneRoot`, above `NavDisplay` and the bottom bar:

```
PhoneRoot
 Box(fillMaxSize)
  Column: NavDisplay (tabs and detail pages)  +  BottomBar (hidden on detail pages as today)
  PlayerOverlay( session: PlayerSession )     // draws nothing when session == None
  SnackbarHost
```

`LiveKey`, `ReplayKey`, `ClipKey` are **removed from the back stack**. A tap calls `PlayerSession.open(PlaybackTarget)`.
`ChannelKey` stays a page.

### 3.2 State machine (pure Kotlin, JVM-testable)

```kotlin
sealed interface SessionState {
  data object Closed : SessionState
  data class Open(val target: PlaybackTarget, val sheet: SheetState, val fullscreen: Boolean) : SessionState
}
enum class SheetState { Expanded, Collapsed }          // draggable between the two
sealed interface SessionEvent {                         // inputs
  data class Open(val target: PlaybackTarget); data object Minimise; data object Expand; data object Close
  data object ToggleFullscreen; data class PipChanged(val inPip: Boolean); data object BackPressed
  data class Drag(val fraction: Float); data class DragEnd(val velocity: Float)
}
```

`PlayerSessionReducer.reduce(state, event): SessionState` plus `BackPolicy` (who consumes Back). No Compose, no Android: unit tests
cover every transition and these rules:

- `Open` on the same target while Collapsed = Expand; another target replaces the session (old one released).
- `BackPressed`: fullscreen -> exit fullscreen; Expanded -> Minimise (not Close); Collapsed -> not consumed (navigation handles it).
- `DragEnd`: fraction above 0.5 or velocity down above 600 dp/s -> Collapsed; below, or velocity up -> Expanded; in Collapsed, a
  drag **down** past the strip or a fling down -> Close.
- `PipChanged(true)` forces Expanded layout (the picture alone), `PipChanged(false)` returns to the previous sheet state.
- Fullscreen implies Expanded and is ignored for Replay/Clip when the aspect is portrait (none today).

`PlayerSession` (activity-scoped, holder `ViewModel` in `:platform-android` style, like `AuthViewModel`) owns: the reducer state
as `StateFlow<SessionState>`, **the `NativeVideoPlayer` / ExoPlayer for the current target**, and for a Live target the
`PlayerPresenter` (chat, quality, notices, sent results). The existing `PlayerViewModel` keyed by login is replaced by one the session
creates and drops when the target changes: presenter classes in `shared/presenters` are untouched.

### 3.3 One surface, never reparented

The 16:9 stage is **one composable instance** in the overlay and only its layout changes (`graphicsLayer` scale/translate plus
`Modifier.layout`): the `AndroidView` (`PlayerView`, SurfaceView) is created once per session and never leaves the tree, which also
avoids the black frame a SurfaceView shows when it is re-created. Tree stays constant in all of: expanded portrait, mini strip,
landscape fullscreen, PiP. Only wrapper `Modifier`s differ; a `movableContentOf` block holds the stage if the parent `Box` has to
switch between `Column` and `Row` for landscape (it keeps the `AndroidView` node alive across the parent change; verified in an emulator
test before relying on it, else fall back to a single `Box` with `Modifier.layout` positioning the stage and the side chat).

### 3.4 Drag gesture

`Modifier.anchoredDraggable` on the whole sheet (foundation, vertical), anchors `Expanded = 0`, `Collapsed = containerHeight -
barHeight - miniHeight`. Derived `progress` (0..1) drives, in one `graphicsLayer`/`layout` lambda (no recomposition per frame): stage
width 100 dp..full, stage height 56 dp..16:9, content below the stage alpha (1..0 over the first 60 %) and translation, strip content alpha (0..1
over the last 40 %), scrim behind. Chat list and streamer row sit under the stage in the sheet and are only composed while progress
< 1 (`if (progress < 1f)` read in the layout phase through a `derivedStateOf` boolean, not per frame). A drag that starts on the chat list
scrolls the list (nested scroll), so the sheet only drags from the stage and the streamer row; fling hand-off from the chat list is
not done (YouTube does not either). `reducedMotion`: same anchors, `snap` animation spec.

### 3.5 Navigation, insets, back

- Stream card tap: `onWatch(login)` -> `PlayerSession.open(Live(login))` (Home, Category, Search, Channel's live button). Channel
  cards and the search "channel" results open the channel page as today; **live** channel results play.
- Under the player the streamer row calls `onOpenChannel(login)`: the overlay collapses (`Minimise`) and navigation pushes `ChannelKey`,
  so the viewer lands on the page with the strip above the bar (YouTube does the same when you tap the channel).
- `PhoneNavigator.showsNavigation` keeps hiding the bar on detail pages, **but while a session is open the bar stays on tab roots**
  and the strip sits above it. On detail pages the strip sits above the gesture bar inset.
- Back order (`BackHandler` in the overlay, enabled only when the session is Open): fullscreen -> exit; Expanded -> Minimise;
  Collapsed -> falls through to `PhoneNavigator.back()`. Closing a session is the X, a drag down on the strip, or `Open` of another
  target; Back never closes it.
- Insets: expanded portrait uses `statusBarsPadding` on the stage as today; fullscreen uses `setImmersive` through `PlayerHost`
  (unchanged interface); the chat input uses `imePadding` + `navigationBarsPadding`; the strip adds the bar height as bottom padding
  by reading the same `WindowInsets.navigationBars` + `PhoneChrome.BarHeight` the bar uses (one function, `rememberBottomChromeHeight`).
  Page lists get that same value as `contentPadding` bottom so nothing hides under the strip.
- PiP: `PlayerHost.updatePictureInPicture` moves into `PlayerOverlay` (it needs only the session's player state and the stage
  bounds). Eligible means session Open **and Expanded and playing**; Collapsed never auto-enters PiP (the activity would otherwise
  shrink a 56 dp strip). `inPictureInPicture` forces the Expanded layout. The "any stop pauses, resume only if it was playing" policy
  documented for PiP stays in the session, not in a page.
- Background audio: `BackgroundPlayback.kt` (exists) keeps its policy but binds to the session instead of a page.
- Restoration: `PlayerRestoration` already saves chat mode and orientation lock by login. Add the `SessionState` (target,
  sheet) to `rememberSaveable` in `PhoneRoot` (serialisable `PlaybackTarget` already exists as `NavKey`s), so a process kill reopens the
  player Collapsed or Expanded as it was, but **never auto-plays a replay or clip** (current policy kept).

### 3.6 Fullscreen flicker (fix now in step 1, independent of the redesign)

Cause chain, from the code: the fullscreen button calls `FullscreenPolicy.toggle` -> `requestedOrientation = SENSOR_LANDSCAPE`. The
activity declares `configChanges="orientation|screenSize|..."` so it is not recreated, but `landscape` flips in Compose and the stage
moves between a `Column` (portrait) and a `Row` (landscape) parent, which **recreates the `AndroidView`** and re-toggles
`setImmersive` in a `DisposableEffect`; when the physical rotation then lands, the `OrientationEventListener` releases the lock
(`UNSPECIFIED`), the sensor reports the physical rotation and the system applies it a second time, producing a second configuration
change: portrait-ish for a frame -> landscape. Fixes, in this order of cost:

1. Keep one stage node across layouts (3.3, or `movableContentOf` as a first, small change).
2. Do not release the lock to `UNSPECIFIED` when the sensor agrees; switch to `SENSOR_LANDSCAPE` (the sensor decides between the two
   landscapes, but portrait is not allowed) and only go back to `UNSPECIFIED` when fullscreen is left. This removes the second flip.
3. Apply immersive in the same frame as the orientation lock, not in a later effect.
4. Verify on the real Samsung (SM-S721B) and the emulator by a frame capture: `adb shell screenrecord`, count frames of the
   portrait layout after the lock; acceptance = zero.

### 3.7 One-line rationale for custom chrome and perf

Custom bar/top bar/cards are plain `Box`/`Row`/`Icon` composables: **fewer** composables than `NavigationSuiteScaffold` (no
adaptive scaffold, no `NavigationBarItem` animations), no blur, no new libraries. R8 rules do not change.

### 3.8 Performance impact and what must be redone

- **Stability**: `PlaybackTarget`, `SessionState`, `SheetState`, `PhoneChrome` are annotated `@Immutable` or added to
  `config/compose/stability.conf` if the compiler reports them unstable; the Compose compiler report is diffed against
  `docs/perf-after-phase1.md` (same method: 5 unstable params, 130 skippable composables). Gate: no new unstable parameter on a
  composable that is on the drag path.
- **Recomposition**: drag progress is read only in `graphicsLayer`/`layout` lambdas and in `Modifier.drawBehind`; verified with the
  layout inspector recomposition counts during a drag (expected: 0 for the lists).
- **Baseline and startup profiles** for `:phone` (`phone/src/main/baselineProfiles/`) are **invalid for moved classes** (the
  player screens move to `PlayerOverlay`; `LivePlayerRoute` etc. disappear). They are regenerated in the last step with
  `benchmark-phone`, journeys updated: home -> tap stream (player with chat), minimise by drag, expand, fullscreen, close; the
  existing browse and search journeys stay. Packaging check (`check-profile-packaging.py`) must pass.
- **Size gate**: the phone APK was +3.9 % over the pre-split baseline (documented). Gate for this work: not larger than the current
  3,927,149 B by more than 1 %; new code is small, removed code (`NavigationSuiteScaffold` adaptive module, the old pages) should
  shrink it. If `material3-adaptive-navigation-suite` can be dropped entirely, that is a win (a rail needs only our own composable).
- **Frame timing**: new `benchmark-phone` journey `minimiseExpand` (frameDurationCpuMs p50/p90/p99 recorded in `docs/perf-phone.md`,
  emulator-only labelling as before).
- No change to R8, keep rules, `profileinstaller`, DEX layout.

### 3.9 Testing

- **JVM** (fast, `jvm()` target of `:phone` helpers or `:phone` unit tests): `PlayerSessionReducerTest` (every transition above,
  property-style: Back never returns Closed; open-same-target idempotent), `BackPolicyTest`, `DragSettleTest` (fraction/velocity
  table), `FullscreenPolicyTest` (updated for the sensor-landscape rule), `CategoryCardSlotTest` (constant height token), `ChatInputSpec`
  (limits: 500 chars, trim, blank not sent).
- **Compose UI tests (instrumented, emulator)**: tap stream opens player with chat (no channel page); streamer row opens the channel
  page and leaves the strip; drag stage down -> strip, tap strip -> expanded; close X; process-restoration test with `StateRestorationTester`;
  rotate while fullscreen keeps the same `PlayerView` instance (test tag + identity hash of the view before/after; the regression test for
  the flicker); the screen-reader tests that exist for the bar keep their `Role.Tab` semantics.
- **Screenshots** (repo rule): every UI step captures emulator screenshots, light and dark, portrait and landscape, font scale
  1.0 and 1.5 (names with two lines, "World of Warcraft", Arabic RTL for the bar and input). Stored in `docs/screenshots/phone/`.
- **Device**: the Samsung SM-S721B over wireless adb for gestures, fullscreen rotation and PiP; the emulator stays the source for
  numbers.
- `./gradlew check` gates every commit as before, joined by the gitleaks run.

## 4. Implementation plan (commit-sized, in order)

Each step ends with `./gradlew check`, emulator screenshots for UI changes and its own commit (Conventional Commits, no attribution).

| # | Step | Commit | Why this order |
|---|---|---|---|
| 1 | Fullscreen flicker: one stage node across layouts (`movableContentOf`), sensor-landscape lock, immersive in the same frame; UI test on view identity | `fix: stop the fullscreen rotation flicker` | independent bug, smallest change, proves the single-surface idea |
| 2 | Category card fixed 2-line name slot and stream row fixed height; constants + tests | `fix: keep category and stream cards the same height whatever the title length` | independent, visible, no architecture |
| 3 | Chat input pill (40 dp, send circle, 3-line growth) + landscape variant, tests, screenshots | `feat: compact pill chat input` | independent of navigation |
| 4 | `PhoneChrome` tokens and custom bottom bar and rail (icon-only, hairline, no `NavigationSuiteScaffold`), tests (`Role.Tab`, order), screenshots, drop the adaptive-navigation-suite dependency if unused | `feat: custom bottom bar and rail` | chrome before player so the strip knows the bar height |
| 5 | Top bars: Home/Browse search-pill row, detail-page row, live and viewer chips | `feat: modern top bars and live chips` | pure UI |
| 6 | `PlayerSessionReducer`, `BackPolicy`, drag settle logic with JVM tests (no UI) | `feat: player session state machine` | the part with real logic, tested alone |
| 7 | `PlayerSession` holder + `PlayerOverlay` with expanded state only (Live, then Replay, then Clip move off the back stack), PiP, background audio and restoration rebinding; tap on a stream plays | `refactor: host the player in a root overlay` | the risky change; behaviour first, no gesture yet |
| 8 | Streamer row opens the channel page; Channel keeps its page; back order | `feat: open the channel from the player` | needs 7 |
| 9 | Drag gesture, mini strip, close by drag/X, reduced-motion variant; UI tests | `feat: drag the player down into a mini player` | needs 7 and 4 |
| 10 | Remove dead code (`LivePlayerRoute`, replay/clip routes as pages), update `docs/ARCHITECTURE.md`, `docs/PRESENTERS.md` | `refactor: remove the page-based player routes` | cleanup |
| 11 | Stability report diff, regenerate Baseline and startup profiles with updated journeys, `minimiseExpand` benchmark, size and frame numbers in `docs/perf-phone.md` | `perf: regenerate phone profiles after the redesign` | last, so the profiles cover the final classes |
| 12 | Real-device pass on the Samsung (rotation, drag, PiP, chat input with keyboard), fixes, screenshots | `docs: phone redesign device notes` | |

Independent reviewer pass (fresh agent, score target 9) after steps 3, 5, 9 and 12.

### Risks to watch

- `movableContentOf` with an `AndroidView` has had issues across versions: step 1 includes the instrumented identity test; the fallback
  is single-`Box`-with-`layout`.
- A SurfaceView under a translucent parent shows through during the drag (z-order): the stage uses `setZOrderMediaOverlay` only if a
  black flash appears in testing; do not enable it speculatively.
- Wireless-adb Samsung validation is the first real-device run of the phone app; expect device-only issues (cutout, gesture
  navigation conflicts with the bottom drag, OEM PiP).
- Removing the page keys changes the saved state format: a one-time reset of saved back stacks is acceptable (not released).

## 5. As built (2026-11-12)

What shipped differs from sections 2 to 4 in these ways (the sections above stay as the plan and the research):

**Player sheet.** `PlayerSessionController` (state machine + saver, `feature/player/PlayerSession.kt`) is saved with the activity; `PlayerOverlay` hosts the page above the tab
`Row` with an `AnchoredDraggableState` (Expanded 0, Collapsed above the bar, Dismissed below the screen). Dismissed is an anchor only while the sheet rests as the strip, and a slow
downward flick of the strip counts as no fling (`DismissPolicy`: a drag past 65 % of the way off the screen, or a flick over 2,000 dp/s, closes it; X always does).
`PlayerSheetLayout` places the one picture child in every mode with relative placement, so right-to-left mirrors it (picture at the right edge, strip text to its left, rail on the right).
The strip grows with the font (56 dp up to 1.6x that) so a two-line title and subtitle are never cut.
Each target owns a `ViewModelStore` (cleared when another target replaces it or the session closes); the doc's "`PlayerSession` owning the player" became "the page stays composed".
**Back** never closes the player: fullscreen -> upright (the page's handler, or the sheet's own lock to portrait before a replay or clip has loaded), expanded -> minimise, strip -> the navigation.
**Restoration**: the saved session (target + where the sheet rests) comes back with `autoplay = false`, so a replay or clip opens paused; a back stack that cannot be read resets the tab to
its root (`NavKeyCodec`, `rememberGuardedBackStack`) instead of crashing. **Accessibility**: the page behind the expanded sheet is removed from the semantics tree, the chat box has a label, the
bar's animation honours reduced motion.

**Bar and header (round 2).** The bar shows Home, Following (only for a signed-in viewer: it needs one) and Browse, icon-only; two tabs when signed out, never Search or Settings.
Search is a page opened from the header's pill (the pill is the field, grown) and Settings a page opened from the account button; both hide the bar like any detail page and Back returns to where
they were opened. Home lost its "For you / Following" tab row (Following moved to the bar). The rail takes the same tab list.
**Search** (`feature/search`): custom pill field with the back arrow at its start, recent searches (persisted, removable one by one or all, `SearchEvent.RemoveRecent` / `ClearRecents`), the
channels live now, an All / Channels / Categories filter row (`FilterPill`), result rows with avatar, LIVE mark and viewers, loading / no result / failed (with Retry) states
(`SearchUiState.searching` / `failed`). **Settings**: a profile card (initial, name, sign in / out pill), then rounded sections whose rows lead with an icon tile, show their value and end
with a chevron that mirrors. **Category**: one-line title that scrolls slowly when it does not fit (ellipsis with reduced motion) and no gap above the language filter.

**Not done from the plan:** the home "hero" stream, a floating mini player, blurred bar translucency, and a hint of the Kick / TikTok layouts beyond what the press describes.
