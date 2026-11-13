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
