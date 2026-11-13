# iOS screen audit 2 (iPhone 17 simulator `TwitchAuditPhone`, iOS 26.5, French UI)

Re-capture after the fixes answering `docs/audit/apple-review-1.md`, in two rounds. Harness: `apple/UITests/AuditCaptureUITests.swift` (scheme
`TwitchIOSPerf`, by name). Round 1 captured every demo state in light and dark. Round 2 re-captured, in both appearances, only what it changed:
drag-mid, emote picker, chat pill and long-press, Home, Following, Category, Channel, and new Dynamic Type XXL (`ax-*`) and Arabic RTL (`rtl-*`) sets.
Screens round 2 did not touch (Search, Settings, sign-in, Browse, replay/clip, landscape) are round 1 captures, taken before the later
Dynamic Type caps, and were not re-viewed in round 2. **Not every dark capture of round 2 was viewed**: I viewed all light ones listed and the dark ones named below.
Real-data (`real-*`), macOS and tvOS captures were not redone.

Scores are mine and not inflated. Nothing here reaches 9: every screen has something visible left to criticise, listed below.

| Screen | Review 1 | Round 1 | Now | Viewed | What is still wrong |
|---|---|---|---|---|---|
| Drag mid-state | 4.5 | 5.0 | 6.5 | light + dark | The status-bar band is gone and the box wraps the picture, but the picture still sits left of the slab (it shrinks toward the strip's thumbnail slot) with empty slab to its right. A designed in-between would need the slab to shrink with it. |
| Emote picker | 6.5 | 6.5 | 8.0 | light + dark | Sections with headers (channel, global, locked) and an empty state. The demo has only three emotes per section, so "more rows" is untestable here. Recents tab only shows once used. |
| New-messages pill | 6.5 | 6.5 | 8.5 | light + dark | Sits under the last line, no overlap. Long-press menu capture is the dark "Répondre" one, whose chat rows overlap each other behind the menu (demo timing artefact, not confirmed as not a bug). |
| Home (signed in) | 7.0 | 7.5 | 8.0 | light + dark | Featured card, rail of followed channels, wide recommended cards. No "continue watching" on Home, categories strip below the fold, tab bar covers content mid-scroll by design. |
| Following | 6.5 | 7.0 | 7.5 | light + dark | Avatar rail with live rings, then rows, then offline with last seen. The rows are still the old thumbnail list and the offline avatars are tint discs without art. |
| Category | 5.0 | 5.0 | 7.5 | light + dark | Box art, audience, channel count, follow. No tags in demo data, no sort, rows are still the old row layout, the page is mostly empty with two streams. |
| Channel (live / offline) | 6.0 | 6.0 | 7.5 | light (live), dark (offline) | Backdrop, ring, live chip, honest "Ouvrir sur twitch.tv". No about text or banner (not in the shared model). Nav title is still the lowercase login (my edit to blank it is in the XXL capture only, the demo captures predate it). |
| Signed-out screens | 4.5 | 7.0 | 7.0 | light + dark (ax, rtl) | Same cards. Still no browsing without a session and no first-run welcome. |
| Dynamic Type XXL | not captured | not captured | 7.0 | light, 6 screens, after the fix | Chrome is capped at XL, content wraps; Home, Browse, Settings, Channel, Player and signed-out are readable with no overlap. Hero title still truncates at 1 line (ellipsis), the tab bar covers content, the player's chat uses most of the screen. Dark ax captures not viewed. |
| Arabic RTL | not captured | not captured | 7.5 | light, 6 screens | Layout, back chevron, tab order and rails mirror correctly. Chat lines in French inside an Arabic UI read word-reversed because the flow layout follows the UI direction; Arabic chat would read right. Not fixed. Dark rtl captures not viewed. |
| Other screens (Search, Settings, sign-in, Browse, replay/clip, landscape, strip) | see round 1 | see round 1 | unchanged since round 1 | round 1 only | See the earlier table. |

Averages are not meaningful when half the set was not re-viewed; the screens re-viewed this round moved from about 6 to about 7.6.

Why 9.5 is not reached, by screen: the remaining gaps are either shared-model limits (no channel about text, no category tags in demo data, no replay
description or chapters), a decision not taken (anonymous browsing), or work not done (system `TabView`, `.searchable`, a drag state with a shrinking slab, row
layouts replaced by cards on Following and Category, macOS and tvOS passes, real-data black picture investigation).

Real-data black picture (finding): not reproduced. The audit simulator had no signed-in session, so playback of a live channel by login was never attempted with a
token; the earlier black picture was a stuck loading state with the pause glyph. The app now shows a thumbnail while loading and a message with retry on failure
(see round 1), but whether signed-out playback by login resolves on real data remains untested.
