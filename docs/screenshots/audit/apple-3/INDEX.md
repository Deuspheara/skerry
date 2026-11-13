# Apple screen audit 3 (answering `docs/audit/apple-review-2.md`)

Every file here is a NEW capture from this round (nothing is carried over from apple-2). Harness: `apple/UITests/AuditCaptureUITests.swift` (iOS, scheme
`TwitchIOSPerf`), `apple/scripts/mac-run.sh -snapshot ... -snapshot-mode layer` (macOS), `apple/scripts/tv-run.sh -screenshot` (tvOS), `apple/scripts/drag-frames.sh`
(frames cut from a screen recording). iOS: iPhone 17 simulator `TwitchAuditPhone`, iOS 26.5, French, light + dark; `ax-*` = accessibility XXL, `rtl-*` = Arabic;
`light-real-*` = real Twitch data, signed out (a live channel picked at run time). Images are 640 px on the short side.

How I looked: I viewed contact sheets of all iOS captures (10 per sheet) and the macOS and tvOS ones individually or in sheets, not each frame at full size. Scores
are mine; the reviewer found my earlier ones about 0.5 to 1.0 high, so I took that off before writing them.

## What was fixed since review 2 (and what was not)

Fixed: drag mid-state rebuilt as one card (picture, title strip beside it, rounded body, status band gone after a third of the way, strip text fades in early) with frames from a recording
(`drag-frames-light-contact-sheet.png`); offline channel page with a message and an action instead of an empty composer; no play button or LIVE badge over a replay error; Search scope with no
categories shows an empty state; sign-in sheet has an opaque ground, a drag indicator and an opaque code box; Category and Following keep the row layout but Category has its header and no duplicated
title; Browse uses 2 columns of larger cards at accessibility sizes; fixed chrome capped at XL; macOS channel uses the honest twitch.tv link and chips, the mini player has a title, card type is larger;
tvOS settings headings and labelled rows, channel row label "En direct maintenant", sort chip readable, selected chip inverted; harness rotates landscape captures; Home/Browse lists leave room for the tab bar and strip.

Not done: native iOS 26 `TabView` / `tabViewBottomAccessory` / minimise on scroll (the labelled custom bar stays, tinted more opaque; no iOS 18 simulator is installed, so no fallback set); `.searchable`
with scopes on iOS; shared-Kotlin additions (channel about text, replay description, chapters); replay/clip pages gained chips, share and related but still no description or chapters; macOS sidebar selection and
Settings tabs are not visible in captures (the layer snapshot loses the sidebar and the Settings tab bar: a harness limit, not proof of the app); macOS search is still one result in a void; tvOS focus frames
(no tvOS UI-test target to press the remote; the simulator also has no light appearance, so tvOS has one set); Arabic chat direction per row; Arabic "clips/replays" localisation; the "2." step the review saw on
tvOS sign-in is not present in the current capture (`tvos/signin.png`).

Tried and reverted: a two-column tile grid on Following and Category. It made every UI-test swipe on those pages take 85 s (idle never reached); the rows are back. I did not find the cause, so the tile
layout is not shipped.

## Real data, signed out: the black picture (finding)

`light-real-player-portrait.png` and `light-real-strip-over-home.png` are a live channel (picked from Twitch a minute before). The picture stays black with a LIVE badge. The unified log of the app shows the
GQL calls and `usher.ttvnw.net` requests succeed (so signed-out token resolution works), an `AVPlayerItem` is created, and then the simulator's H.264 software decoder fails on every frame
(`err=-8969`, 838 times in 20 s). So: not a token problem, not ATS, not a stuck loading state; it is a decode failure of the live stream on the simulator, which the app does not surface (no error state while frames fail).
Whether a real device decodes the same stream is untested. An earlier capture of "Hors ligne" for the real channel was a channel that had gone offline in between.

## Scores (mine; review 2 in brackets)

| Screen | Score | Evidence | Still wrong |
|---|---|---|---|
| iOS Home | 7.8 (7.6) | `light-home`, `dark-home`, `-scrolled` | Tab bar covers content mid-scroll; no continue-watching; categories below the fold |
| iOS Following | 7.3 (7.2) | `*-following*` | Row layout; offline avatars are tint discs |
| iOS Browse | 7.6 (7.6) | `*-browse*` | No viewer counts; chips filter nothing visible in demo |
| iOS Category | 7.2 (6.8) | `*-category*` | Two rows and 70 % empty |
| iOS Search (all) | 7.5 (7.3) | `*-search-*` | Search is still a pushed page, not `.searchable` |
| iOS Channel live/offline | 7.3 (7.0) | `*-channel-*` | No about text; nav bar now empty so the header carries the name |
| iOS Settings | 8.0 (8.0) | `*-settings-*` | Cards not `Form`; sign-out neutral |
| iOS Sign-in sheet | 7.3 (6.8) | `*-signin-sheet` | No QR; no success frame |
| iOS Player live portrait | 7.8 (7.8) | `*-player-live-portrait` | Header still crowded |
| iOS Emote picker / pill / long-press | 7.8 (7.8) | `*-emote-picker`, `*-chat-pill`, `*-chat-row-longpress` | Three emotes per section in the demo; long-press frame not frozen, overlap not disproved |
| iOS Landscape | 7.5 (6.5) | `*-player-landscape-*` (now upright) | Overlay chat dense over bright video; no scrim under the title |
| iOS Replay / clip | 5.8 (4.5) | `*-player-replay-portrait`, `-clip-` | No description or chapters; empty lower half; "vod demo" placeholders |
| iOS Offline channel page | 7.0 (5.5) | `*-player-offline-channel` | Demo plays a test pattern for hibou in some runs |
| iOS Drag mid-state | 5.8 (3.5) | `*-player-drag-mid`, `drag-frames-light-contact-sheet` | Still a large grey slab under the picture; title clipped when the picture is wide; not smooth-proven (no frame metrics this round) |
| iOS Mini strip | 7.0 (7.0) | `*-strip-over-*` | Thumbnail corner not rounded; strip over the new Home |
| iOS Signed-out screens | 7.0 (7.0) | `*-signed-out`, `light-real-*` | No browsing without a session |
| iOS Dynamic Type XXL | 6.8 (6.8) | `*-ax-*` | Player chat uses the screen; settings rows truncate; hero title truncates |
| iOS Arabic RTL | 7.0 (7.0) | `*-rtl-*` | French chat lines read reversed; "replays/clips" not localised; first rail card clipped |
| iOS real-data set | 5.0 (3.5) | `light-real-*` | Browse/category/search show the sign-in card; picture black (see finding) |
| macOS Home/Following/Browse | 6.3 (6.0) | `macos/*-home`, `-following`, `-browse` | Sidebar missing in the layer snapshot; type still small |
| macOS Channel | 6.8 (6.0) | `macos/*-channel*` | Live card 650 px wide with a void to its right |
| macOS Search | 5.0 (5.0) | `macos/*-search` | One result in a void |
| macOS Player + chat | 6.0 (6.0) | `macos/*-player` | Black video (harness); small control strip |
| macOS Mini player | 5.5 (4.5) | `macos/*-mini` | Black picture in the harness, but now titled |
| macOS Settings | 4.5 (4.0) | `macos/*-settings` | Capture shows one tab, tab bar blacked out: cannot judge |
| tvOS Home/Following/Browse | 7.3 (7.2 / 6.5) | `tvos/home`, `following`, `browse` | No focus frame; titles truncate |
| tvOS Search | 6.5 (6.5) | `tvos/search` | Empty lower half |
| tvOS Channel | 6.3 (6.0) | `tvos/channel` | Tab still "Accueil" (system TabView behaviour) |
| tvOS Settings | 6.5 (4.5) | `tvos/settings` | Last row under the bottom edge; no focus frame |
| tvOS Sign-in | 7.0 (6.5) | `tvos/signin` | Annuler off-centre |
| tvOS Player chat compact / overlay | 7.8 / 6.0 (7.8 / 5.5) | `tvos/player-*` | The stills do not show the transport bar, so the collision fix is untested by image |

Calibrated mean of what I re-shot: iOS about 7.0, macOS about 5.7, tvOS about 6.9. Target 9.5 is not reached on any screen, and I do not claim 9 on any.
