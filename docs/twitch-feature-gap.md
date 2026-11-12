# Twitch feature gap analysis (phone app)

Research snapshot: 2026-11-12. Built from web searches (standard mode) plus what the repository already established
(`docs/TWITCH_INTEGRATION.md`, `docs/phone-redesign.md`). Twitch changes its APIs without much notice: recheck every
"possible" row against the live reference (https://dev.twitch.tv/docs/api/reference/) before building.

**Confidence tags.** `[S]` supported by a source I read in this pass; `[R]` already verified in this repository
(live calls or earlier doc work); `[U]` unconfirmed: reasoning or recollection, no source found. Several pages that matter
(support.twitch.tv, the Helix reference, the Developer Services Agreement text) were not returned by search, so no
claim below about their exact wording is made.

Token facts used throughout: the phone app signs in through the OAuth device-code flow with a **public client**, so it
holds a **user access token** with the scopes `user:read:follows`, `user:read:chat`, `user:write:chat` `[R]`. It has no
client secret and therefore no app access token. Chat is read over EventSub WebSocket, not IRC `[R]`. Playback uses the
native player on Twitch's HLS, with the unofficial GraphQL access-token query `[R]` (docs/TWITCH_INTEGRATION.md,
"Playback statistics and ad marker").

---

## 1. What the official mobile app offers

Sources: Twitch blog, 29 Jul 2024 ([new mobile app](https://blog.twitch.tv/en/2024/07/29/find-content-you-love-faster-with-the-new-twitch-mobile-app)),
TwitchCon San Diego recap ([Sep 2024](https://blog.twitch.tv/en/2024/09/20/everything-we-announced-at-twitchcon-san-diego)),
secondary coverage ([9to5Google](https://9to5google.com/2024/05/01/twitch-discovery-feed-app-redesign/),
[PhoneArena](https://www.phonearena.com/news/twitch-mobile-app-redesigned_id160927),
[Engadget](https://engadget.com/twitch-is-giving-all-users-access-to-its-discovery-feed-later-this-month-041009070.html),
[Dexerto](https://jamtest.dexerto.com/twitch/twitch-reveals-major-change-to-make-streams-a-lot-more-like-tiktok-3149774),
[Tubefilter](https://newsletter.tubefilter.com/p/twitch-goes-vertical)). Support-centre pages were not returned by search,
so the feature list below comes from press and the blog, not from Twitch's help articles.

| Official feature | What it is | Our phone app |
|---|---|---|
| **Discovery Feed** `[S]` | A vertically scrolling feed of live streams and clips; swipe to the next item, tap to join the stream's chat. Tested Aug 2023, rolled out to all mobile users in 2024 (dates differ between sources). Twitch's help text reportedly says previews let viewers sample streams without a pre-roll ad `[S: secondary source]`. | Missing. |
| **Clips feed** `[S]` | Separate vertical, portrait feed of clips, reached by swiping from the main feed. | We list a channel's top clips and play one clip at a time. No feed. |
| **Stories / offline streamer updates** `[S: secondary]` | Stories from followed streamers who are offline. | Missing. |
| **Following drawer** `[S]` | Live followed channels, with sorting and titles (Sept 2024); thumbnails, quick access to followed categories and resuming recent VODs were announced. | Following tab: live followed channels only. No offline list, no followed categories, no "continue watching". |
| **Vertical streams** `[S: secondary]` | Creators can broadcast vertical video, shown full-screen on mobile (Feb 2025 announcement, tests with a few broadcasters). | Our player letterboxes everything as 16:9 in portrait; a vertical stream would need a layout rule. |
| **Chat** `[U for details]` | Emotes (including the viewer's subscriber emotes), badges, replies, mentions, mod actions for moderators, channel-point and hype-train UI, predictions and polls panels, gift-sub/raid notices. | Chat shows messages, badges, Twitch/7TV/BTTV/FFZ emotes `[R]`, send, notices, raid banner `[R]`. No emote picker, no replies, no mentions highlight, no predictions/polls/points UI. |
| **Channel points** `[U]` | Balance, claim bonus chest, redeem rewards. | Missing. |
| **Drops** `[S: dev guide]` | Campaign progress and claiming in the app. | Missing. |
| **Schedule** `[R]` | Channel schedule. Search found nothing about a mobile schedule screen. | Shown on the channel page `[R]`. |
| **Notifications** `[S: secondary]` | A Notifications tab (live alerts, etc.) exists in the redesign. | Only an in-app "X is live" check while the app is in front `[R]`. No push. |
| **VOD** `[U]` | Chapters, markers, resume position. | Replay with seek and resume `[R]`; no chapters/markers. |
| **Playback options** `[U]` | Quality, audio-only, sleep timer. | Quality, background audio and PiP `[R]`; no audio-only toggle, no sleep timer. |

Not confirmed: whether the current app still ships Stories, the exact Following drawer layout today, any 2025-2026 changes.
I found nothing newer than early 2025 in the results.

---

## 2. What a third-party app can support

"Helix" = documented HTTP API; "EventSub" = documented push; "GQL" = Twitch's undocumented GraphQL used by the
website/official apps (not covered by the developer agreement, can break or be blocked at any time). The repository rule
(docs/TWITCH_INTEGRATION.md) is to avoid undocumented interfaces except where already accepted: the live and clip
resolvers are the two exceptions, with a fallback message.

| Feature | Documented route for a viewer token? | Verdict |
|---|---|---|
| Discovery feed content (streams) | `GET /streams` and `GET /streams/followed` (Helix `[R]`). Twitch's own recommendation ranking is **not** public. | Feasible with our own ranking (viewers, language, followed categories). |
| Clips feed | `GET /clips` lists a channel's or game's clips; playable URL only through GQL `[R]`. | Feasible with the same GQL risk we already carry. |
| Follow / unfollow | The Create/Delete follow endpoints were shut down on 27 Jul 2021 and Helix offers no replacement for a user token `[S]` ([announcement thread](https://discuss.dev.twitch.com/t/deprecation-of-create-and-delete-follows-api-endpoints/32351)). `[R]` in our earlier work. | **Not possible.** Keep "open twitch.tv". |
| Followed channels / live followed | `GET /channels/followed` and `GET /streams/followed`, scope `user:read:follows` `[R]`. | Done. Offline followed list is feasible (same call). |
| Followed categories | No Helix endpoint I could confirm `[U]`. | Probably only GQL. Do not plan on it. |
| Stories / offline updates | None documented `[U]`. | Not feasible. |
| Emotes (global, channel) | Helix "Get Global/Channel Chat Emotes" exist; EventSub fragments carry emote ids `[R]`. The repo no longer calls the Helix ones, it reads ids from messages `[R]`. | Picker for channel and global Twitch emotes is feasible. |
| Viewer's own emotes (sub emotes in other channels) | The old Kraken "user emotes" call did not move to Helix, per forum threads `[S: community]` ([thread](https://discuss.dev.twitch.tv/t/retrieve-the-emotes-usable-by-a-user/20165), [uservoice](https://twitch.uservoice.com/forums/310213-developers/suggestions/43599900)). The repo notes the doc lists `user:read:emotes` for Get User Emotes `[R: TWITCH_INTEGRATION.md scope table]`, but I could not confirm the endpoint's current state in this pass. | **Unconfirmed.** Check the live reference for "Get User Emotes" before promising a "my emotes" tab. Without it, a picker shows channel, global and 7TV/BTTV/FFZ only. |
| 7TV / BTTV / FFZ emotes | Public provider APIs, no auth `[R]` (table in TWITCH_INTEGRATION.md). Chatterino documents the same services ([wiki](https://wiki.chatterino.com/Third_party_services/)); 7TV has a live Event API `[S]`. | Already rendered in chat `[R]`. A picker over them is feasible; they can disappear without notice. |
| Replies, mentions | `user:write:chat` message send accepts a reply parent id in the Helix docs `[U: not re-checked]`; the incoming event carries `reply` data `[U]`. Mentions are local string matching. | Likely feasible; verify the field names. |
| Channel points: balance, claim, redeem | Documented API is the **broadcaster** side: `channel:manage:redemptions` / `channel:read:redemptions` ([scopes](https://dev.twitch.tv/docs/authentication/scopes/)). A viewer's balance/claim/redeem call is not documented `[S: scopes page lists only broadcaster scopes]`. | **Not possible without GQL.** |
| Predictions, polls | Helix and EventSub need `channel:read:polls` / `channel:read:predictions` of the **broadcaster** ([polls](https://dev.twitch.tv/docs/api/polls/), [predictions](https://dev.twitch.tv/docs/api/predictions)); `[R]` our earlier finding. Voting by a viewer is not in Helix. | **Not possible** (display or vote). |
| Hype train | EventSub `channel.hype_train.*` needs the broadcaster's `channel:read:hype_train` ([scopes](https://dev.twitch.tv/docs/authentication/scopes/), [forum](https://discuss.dev.twitch.com/t/eventsub-hype-train-begin-subscription/31157)). A secondary source says Get Hype Train Events is scheduled for removal on 15 Jan 2026 `[U]`. | **Not possible** for other channels. The chat notice for a hype-train message is not a documented event. |
| Gift subs, raids, sub notices | `channel.chat.notification` with `user:read:chat` `[R]`; `channel.raid` needs no scope `[R]`. | Done (notices, raid banner). Richer cards are UI work only. |
| Moderation in chat | Moderator endpoints need `moderator:manage:*` scopes and the user being a mod `[U]`. | Possible but only for mods; low audience. Defer. |
| Drops | Documented API is for the **game/publisher** (EventSub + entitlements) ([Drops guide](https://dev.twitch.tv/docs/drops/technical-guide/)); a viewer inventory is not public `[S: forum](https://discuss.dev.twitch.com/t/get-list-of-drops-entitlements-for-a-game/45170)`. | **Not possible.** |
| Go-live push notifications | `stream.online` via EventSub needs a transport: WebSocket (only while the app runs; 10-point budget, see our EventSub table `[R]`) or webhook/conduit (needs a server and an app token). | In-app only without a backend. A backend would need an app token, i.e. a server holding a client secret (we have none). **Not possible as true push** without hosting. |
| Whispers | `POST /whispers` with `user:manage:whispers`; developers report silent drops and daily recipient limits ([thread](https://discuss.dev.twitch.tv/t/whispers-silently-dropped/43881)). | Possible, unreliable, low value. |
| Schedule | `GET /schedule` `[R]`. | Done. |
| VOD chapters / markers | `GET /videos` carries no chapters; markers need the broadcaster's `user:read:broadcast` `[U]`. | Probably GQL only. Skip. |
| Audio-only, sleep timer, quality | Local features; audio-only maps to the audio rendition in the HLS master `[R: we already parse variants]`. | Feasible, no Twitch API needed. |

---

## 3. The mobile "split feed" (vertical discovery feed)

### What it is

A full-screen, one-stream-per-screen vertical pager: the stream plays muted or unmuted behind a translucent overlay (title,
streamer, viewers, follow), chat sits as an overlay or one swipe away, swipe up/down moves to the next/previous stream,
tap enters the full player and chat `[S]` (Twitch blog and coverage above).

### Rules to respect before building

* **Terms.** The Developer Services Agreement text could not be retrieved in this pass; search surfaced only generic
  clauses (licence terminates on non-compliance, Twitch keeps ad rights) from
  [legal.twitch.com](https://legal.twitch.com/legal/developer-agreement). The repo's own position stays: no ad
  removal, no skipping, ads stay in the stream `[R]`. A feed that **autoplays several streams** must not hide or
  shorten pre-roll ads: the official app avoids pre-roll in previews by design `[S: secondary]`, while our native
  player plays whatever HLS Twitch serves, which may include a pre-roll for each stream opened. **Open question for
  Twitch developer support**, same status as the WebView question already in TWITCH_INTEGRATION.md.
* **Playback source.** The native player already uses the GQL access token `[R]`. A feed multiplies those calls
  (one per previewed stream), raising the chance of rate limits or blocks. Throttle and cache, and keep the fallback
  "unavailable" path.
* **Data and battery.** Each live preview is an HLS session. Plan on a hard cap of **one playing and at most one
  preloaded neighbour**; previews on cellular must start at the lowest rendition (use `DefaultBandwidthMeter` /
  max video size on the preview track selector) and stop after a few seconds idle. Nothing found in search quantifies
  Twitch HLS preview cost `[U]`; measure on the Samsung with `dumpsys batterystats` and `NetworkStatsManager`
  before enabling by default.

### Media3 building blocks (sourced)

* `DefaultPreloadManager` ranks items by distance from the one on screen and assigns per-item preload status; it must
  **share the bandwidth meter, load control and track selector** with the player, otherwise the preloaded rendition
  is discarded on start, and preload can steal bandwidth from the playing item ([Android
  Developers](https://developer.android.com/blog/posts/elevating-media-playback-a-deep-dive-into-media3-s-preload-manager-part-2),
  [announcement](https://android-developers.googleblog.com/2025/09/introducing-preloading-with-media3.html)).
* A warm pool of players saves creation cost but not data ([ProAndroidDev](https://proandroiddev.com/preloading-media-a-future-forward-approach-with-exoplayer-877ca6b0873d)).
  For live HLS the useful part is preloading the **manifest and the first segments**, not a long buffer: there is no
  "download the whole video" equivalent.
* Not sourced `[U]`: how `DefaultPreloadManager` behaves on live HLS and on Twitch's low-latency playlists
  (live-edge handling, `currentLiveOffset` after a preload). Prototype it before designing around it.

### Proposed architecture on what exists

Existing pieces `[R]`: `PlayerSessionController` (state machine), `PlayerOverlay` with the `AnchoredDraggableState`
sheet, one stage view never re-parented (`PlayerSheetLayout`), per-target `ViewModelStore`, shared presenters in
`shared/presenters`, the Media3 player behind `VideoPlayer` in `:platform-android`.

1. **Shared:** `FeedPresenter` (`StateFlow<FeedUiState>`, events `Swipe(next|prev)`, `Open(login)`, `Refresh`) in
   `shared/presenters`; its ranking is a pure function over `GET /streams` (+ `/streams/followed` first), unit-tested
   (language filter, dedupe, mature-flag, followed-first). The page window (current ± 1) is state, so Apple/desktop UIs
   can reuse it.
2. **Platform interface:** `FeedPlayerPool` in `:platform-android`: at most 2 `ExoPlayer` instances (current + one
   neighbour), one shared `DefaultBandwidthMeter`, `DefaultLoadControl` tuned small, and a `DefaultPreloadManager`
   built on the same track selector. Previews start muted on the lowest rendition above 360p, switch to full ABR when
   the viewer stays (e.g. 2 s) or taps.
3. **UI:** `FeedScreen` as a new root destination (a bar tab or the Home top) using a `VerticalPager` with
   `beyondViewportPageCount = 0`; the active page owns the single `PlayerView` surface (moved, never duplicated, same
   rule as the sheet); overlay UI reads page offset **only in layout/graphics lambdas** (rule from the redesign).
   Tap hands the **same player instance** to `PlayerSessionController` (`open(login)`) so the sheet opens without a
   second load.
4. **Chat in the feed:** do not connect chat for every page. Connect the EventSub chat only for the page that has been
   settled for ~2 s, with a small bounded overlay (last 3-4 lines), disconnect on leaving. This matters because the
   WebSocket budget is 10 points `[R]`.
5. **Gestures:** vertical swipe = next/previous (pager), long-press = pause, double tap = nothing (avoid conflict
   with the sheet drag), swipe left reveals the Clips feed later (same pager pattern). Respect Samsung gesture
   navigation edges and reduced motion; keep the sheet's drag-to-minimise only on the stage drag handle (the feed uses
   the whole screen).
6. **Lifecycle:** stop and release both players on `ON_STOP`, never preload on metered networks unless the viewer
   opts in, and release on low-memory callbacks.

### Risks

| Risk | Impact | Mitigation |
|---|---|---|
| Pre-roll ads on every previewed stream; terms unclear | Policy / rejection | Ask Twitch support first; one stream at a time; no ad manipulation. |
| GQL token calls multiply, rate-limited or blocked | Feed breaks | Cache tokens briefly, throttle, graceful "unavailable", feature flag. |
| Data and battery on cellular | Bad reviews, real cost | Lowest rendition, WiFi-only default, measure on device. |
| Preload on live HLS behaves differently from VOD `[U]` | Wasted effort | Prototype with 3 real channels before UI work. |
| Two players plus the sheet's player | Memory, surface z-order | One player owner; feed hands its player to the sheet. |
| Ranking quality (no access to Twitch's recommendations) | Feed feels random | Followed first, language-aware, category affinity from watch history on device. |
| Ad/ToS drift on "autoplay" | Takedown | Keep a settings switch to disable previews. |

---

## 4. Prioritised backlog

Scale: impact (user value), effort (S ≤ 2 days, M ≤ 1 week, L > 1 week), feasibility (A = documented API, G = relies on undocumented GQL, N = not possible).

| # | Item | Impact | Effort | Feas. | Notes |
|---|---|---|---|---|---|
| 1 | **Emote picker in chat** (Twitch channel + global, 7TV/BTTV/FFZ, recents, search; "my sub emotes" only if Get User Emotes is confirmed) | High | M | A | Already requested; data sources exist `[R]`. Verify `user:read:emotes` status first. |
| 2 | **Chat replies and mentions** (reply parent, highlight when mentioned, tap-to-reply) | High | S-M | A (verify field) | Pure chat UI plus one request field. |
| 3 | **Following: offline list, "continue watching" VODs, followed categories** (categories only if sourceable) | Med-High | M | A / G | Offline channels from `GET /channels/followed`; categories likely GQL, skip if so. |
| 4 | **Vertical Discovery feed (live)** | High | L | A + G | Section 3. Needs the Twitch-support answer on previews and a prototype. |
| 5 | **Clips feed** (vertical pager of clips, same pager as #4) | Med-High | M | A + G | Reuses clip resolver `[R]`. Do after #4's pager. |
| 6 | **Audio-only mode and sleep timer** | Med | S | A (local) | Nice for background listening; no API risk. |
| 7 | **Vertical-stream aware layout** (fill the stage when the stream is portrait) | Med | S | A (local) | Needs the video size in `PlayerSnapshot` `[R: width/height exist]`. |
| 8 | **In-app live alerts** (already polling) → richer: per-channel toggles, a notification list | Med | M | A | True push needs a backend: not possible without one. |
| 9 | **Rich chat notices** (gift subs, resubs, raids as cards) | Med | S | A | `channel.chat.notification` fragments `[R]`. |
| 10 | **Moderator tools in chat** (delete, timeout) for viewers who are mods | Low-Med | M | A (mods only) | Small audience; defer. |
| 11 | **Offline Stories** | Low | L | N | No documented source. |
| 12 | **Channel points (claim/redeem), predictions, polls, hype train, drops UI** | High (viewer) | - | N / G | Documented APIs belong to broadcasters or publishers; only GQL could work. Recommend **not** building (ToS and break risk). Say so in the UI: "open on twitch.tv". |
| 13 | **Follow / unfollow** | High | - | N | Removed in 2021 `[S]`. Keep the twitch.tv link. |
| 14 | **VOD chapters/markers** | Low | M | G | Skip. |
| 15 | **Whispers** | Low | S | A (flaky) | Skip. |

### Recommended next three

1. **Emote picker (#1)** with **replies/mentions (#2)** as one chat-input release: the user asked for it, the data sources
   are already integrated, and it needs no new risky dependency.
2. **Following upgrades (#3, offline list + continue watching)**: cheap, documented, visibly closes a gap with the official app.
3. **Feed prototype spike (#4, time-boxed to 2-3 days)**: one pager, one preloaded neighbour, measure data/battery on the Samsung,
   and in parallel send the ads/autoplay question to Twitch developer support. Decide the full build from those numbers.

## Could not confirm

* Twitch's Developer Services Agreement text on third-party playback/ads, and Twitch support-centre feature pages.
* Whether Helix "Get User Emotes" and `user:read:emotes` currently work for a viewer token.
* Reply-parent and mention fields on `POST /chat/messages` and on the EventSub message payload (field names unchecked).
* Any 2025-2026 changes to the official app's feed, Stories, or Following drawer.
* The Helix hype-train deprecation date (single secondary source).
* Media3 preload behaviour on live and low-latency HLS; data and battery cost of live previews.

---

## 5. Status after the implementation pass (2026-11-12)

| # | Item | Status |
|---|---|---|
| 1 | Emote picker | Done (docs/EMOTES.md). |
| 2 | Replies and mentions | Done: reply context line, swipe / long-press / TalkBack action to reply, reply bar, `reply_parent_message_id` on send, answer-to-me counts as a mention, `@name` suggestions from who wrote in the chat. The `reply` field of the EventSub message and the send parameter are as documented; **not run against a live chat**. |
| 3 | Following: offline list, continue watching, last seen | Done: offline channels carry "last seen live" (recorded by this app each time the follows are read, because Twitch has no per-channel last-stream time for a whole list), a "Continue watching" row from the saved replay positions (one batched `GET /videos?id=`). Followed categories: not done (GQL only). |
| 4 | Vertical Discover feed | Done as the "warm neighbour" version, see docs/discover-feed.md. The ads / terms question is still open. |
| 5 | Clips feed | Done as the Clips half of Discover (week's most viewed clips of the four busiest categories). |
| 6 | Audio-only and sleep timer | Sleep timer done (live and replays, 15 to 90 minutes). Audio-only was already a quality in the list; the stage now says "Audio only" with the streamer's picture instead of staying black. **No background (screen-off) listening**: the app pauses on stop and has no media service. |
| 7 | Vertical-stream-aware layout | Done: the expanded portrait picture takes the stream's own shape (up to 42 % of the sheet), 16:9 otherwise. |
| 8 | In-app live alerts, per-channel toggles | Not done. True push is not possible without a backend. |
| 9 | Rich chat notices | Done: subscription, resubscription, gifts, raid, announcement (with its colour) and bits badge notices are cards with an icon and an accent. |
| 10 | Moderator tools | Not done (small audience). |
| 11 to 15 | Stories, channel points, predictions, polls, hype train, drops, follow, VOD chapters, whispers | Not done, not feasible or not worth the risk, for the reasons in section 2. |

Also added: the stream's language and tags as pills under the title in the live player.
