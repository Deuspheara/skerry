# Android phone: fix plan and status after review 1

Written after the work (the plan was decided at the start but only put on paper at the end). Review 1 (`android-review-1.md`) scored the phone app
5.9/10 overall. This lists what was decided, what was done (commits `73ee691`, `fc1e910`, `8f20fae`, `aa6f22f`, `14aba01`) and what was not.

## Decisions
- **Home / Following / Discover are three different things.** Home = one hero stream, then the followed lives, then recommendations and
  categories. Following = a page title, a "Live now" row of avatars with a live ring, the live rows, continue watching, then offline channels
  (dimmed, with "last live" when known). Discover = one stream per page as a card with its facts and labelled actions.
- **One empty/error component** (`MessageState`: icon disc, title, one line, action) used for signed out, error, empty, no results.
- **A bar that never moves:** four tabs always (Home, Discover, Following, Browse). A signed-out Following shows its own sign-in message.
- **Red is for information:** no LIVE badge in lists where everything is live (Home, Following, Category); the viewer count with its dot stays.
- **One shared component per job:** `FilterPill` (all chips), `PageTitle` (Browse, Following), `MessageState`, `HeroStreamCard`, `LiveNowRow`.

## Review's top 15: status
| # | Fix | Status |
|---|---|---|
| 1 | Home and Following not duplicates | Done (hero on Home; live-now row and page title on Following) |
| 2 | Signed-out / offline states | Done: per-tab copy, error with Retry, fixed tabs. Public content when signed out: not done (the presenters answer `SignedOut`) |
| 3 | Discover Live layout | Done as a card (picture, facts, labelled actions, pager hint). Not a full-bleed crop |
| 4 | One empty/error component | Done (clips, no results with the query echoed, offline, signed out) |
| 5 | Title block under the player | Done (stream title, viewers, category). The open-in-new icon stays on the header |
| 6 | Channel identity | Partly: banner of theme colours, bigger avatar, status line, follow button and a separate "open on Twitch" button, no duplicate Watch button. No tabs, no follower count (not in the data) |
| 7 | One chip / header / dialog | Chips and tab titles: done. Dialog widths: not unified |
| 8 | Drag transform | Not touched (the capture 38 was not re-examined) |
| 9 | Large font | Done: posters go to 2 columns, the strip stops at about 64 dp and drops its subtitle, initials stop scaling, the search icon scales |
| 10 | Wide layout | Not done for the player (side by side). Home on a tablet shows a rail and two columns |
| 11 | Category header | Done (art, name, viewers now) |
| 12 | Reduce red | Partly (badges); the Search ring and its LIVE chip are both still there |
| 13 | Chat polish | Not changed (the top fade and reply line already existed); nick contrast is now tested |
| 14 | Mini strip | Done: hairline on top, 12 dp between pause and close, capped height. No progress line |
| 15 | Replay player | Partly: title scale, speeds in one scrolling row of pills. Seek bar not touched |
Also done: bidi (`TextDirection.Content`) on stream and replay titles; Settings rows without icon gutters (section icons instead), a sign-out
dialog with a reason and a destructive confirm; one playback settings sheet (quality, sleep timer) replacing two buttons; sheets open fully
and say the value in use; the emote search is an outlined rectangle (not a second pill); WCAG contrast of the theme's pairs is a JVM test.

## Not done
Settings "Chat" sub-screen with a live preview; tablet side-by-side player; landscape title inside the picture; real box art and banners
(the API gives none); a progress line on the strip; public content when signed out.

## Honest self-assessment (not a review)
Home 7.5, Following 7.5, Discover 7, Browse 8, Category 7.5, Search 7, Channel 6.5, Player 7.5, Settings 7.5, signed-out/error 8. These are my
numbers from looking at the captures in `docs/screenshots/audit/android-2/`; an independent review has not been run on this round. 9.5 was not reached.


---

# Round 2 (after `android-review-2.md`, 6.9 / 10)

Frames: `docs/screenshots/audit/android-3/` (94, every one looked at; index and gaps in its `INDEX.md`). Code: `6cf5153` plus the working-tree
changes that other sessions' commits swept in (the phone files of this round are in HEAD; `./gradlew check` passes on it).
Decisions from the coordinator: no unofficial GQL guest browsing; designed guest and offline states instead.

## Ranked list of review 2: status
| # | Item | Status (with the frame that shows it) |
|---|---|---|
| 1 | Fill the Discover page | Done as far as a card goes: backdrop of the stream's picture, the card + facts + actions centred, chat fills the rest, no black half (12, 14). Not a full-bleed crop |
| 2 | Re-shoot the unverifiable claims | Done: channel live / offline / scrolled / clips (40, 41, 60-63), replay (90-92), clip (95, 96), landscape in three modes (70, 72-74), drag (80), PiP (98), dialogs (101, 103-105), search morph (20-1..4) |
| 3 | Fix the drag | Found and fixed from the frames: a black band above the shrinking picture was carried down with it (now closes in the first 10 % of the drag); the bar is revealed proportionally under the sheet by design. Frames 80-1..4 show it |
| 4 | Home | Deeper scrim, category chip, play mark (dropped at large text), 40 dp avatar; recommendations and categories now captured (02, 03); one section-header style |
| 5 | Player hierarchy | Title 18 sp medium above, channel line smaller, duplicate viewer count removed from the controls, the link is a labelled "Twitch" button (50) |
| 6 | Browse | One tile construction in both themes (name over a bottom scrim, 15, 133); no viewer counts (the data has none); no trailing fade |
| 7 | Following | Purple ring + red corner dot, names never broken across two lines (the column grows with the text), "Last seen live" in the demo data (11). The live-now row and the list still show the same channels |
| 8 | Category | Labelled "Add to favourites" button replaces the heart, language chips without the pseudo-chip label (16). No related-categories tail |
| 9 | Guest / offline | Distinct "You're offline" (airplane mode, 155, 156) and "Couldn't load this page" (154); signed-out Discover hides Live/Clips (151). No guest content (decision). Cached content while offline: not shown in any frame (see INDEX) |
| 10 | Settings | Help lines on Playback stats and Follow raids (100). No Chat sub-screen with a preview |
| 11 | Wide | Two-pane player: picture 62 % centred on black, page beside it (141, 145; landscape 143). Hero not capped, captions not enlarged |
| 12 | Strip | Not changed this round (no inset or radius, no progress line); captured at 1.5x, 2x, light, dark (126, 138, 162) |
| 13 | Sheets | Quality row shows its value ("Auto"); the sleep sheet opens fully (51, 53); no check mark on a chosen timer; the quality sheet itself could not be captured (demo has no renditions) |
| 14 | RTL | Browse, Category, Following, Settings, player retaken in Arabic with the right taps (111-115). A French notice inside an Arabic chat now keeps its "!" at the end (direction from its own text) |
| 15 | Large font | Settings, player, search, sheet-free strip, Home and Following at 1.5x and 2x (120-126). Home rows drop the streamer picture above 1.3x |

Other fixes made while looking at the frames: the status bar icons of the (always black) Discover page are light in the light theme; a clip opened
while another player sat in the strip kept the strip's picture size (a new stream now opens at once, not by growing out of the strip); the
replay's own length is used in its meta line (the demo clip is 12 s long, the replay 3 h).

## Not done
Settings Chat sub-screen with a live preview; strip inset/radius/progress; the "stories row repeats the list" duplication; capped hero and larger
captions on tablets; quality sheet frame; sign-in code dialog frame; cached lists shown offline (frame); viewer counts on category tiles (no data).

## Calibrated self-scores (reviewer 2 was about 0.5 below my numbers; these are lowered by that)
Home 7.5, Following 7.3, Discover 7.0, Browse 7.7, Category 7.0, Search 7.0, Channel 6.5, Player live 7.4, Replay 7.0, Settings 7.5, Sheets 6.8,
Strip + drag 6.8, Landscape 6.5, Wide 7.0, RTL 7.5, Large font 7.3, Light parity 7.3, Dialogs 6.8, Signed-out / error / offline 8.0, PiP 7.0.
Overall about 7.2. 9.5 is not reached anywhere; the demo art (grey cards, a test pattern as video) caps how good any frame can look.
