# Apple apps screen review 1 (iOS, macOS, tvOS)

Independent design review. Read-only: nothing was built or run. Every PNG listed in `docs/screenshots/audit/apple/INDEX.md`
(96 iOS captures: 48 dark, 48 light, plus the 16 `light-real-*` signed-out captures counted in those lists, and the two language-picker
captures) was opened, plus all 8 macOS captures in `docs/screenshots/macos/` and all 9 tvOS captures in `docs/screenshots/tvos/`.
The INDEX only covers iOS; macOS and tvOS are in their own folders. Design intent read from `docs/IOS_APP.md`, `docs/APPLE_PLATFORMS.md`.

Method notes. iOS files are 800 px wide for a 393 pt screen, so 1 pt is about 2 px. macOS files are 1280x800 renders of the app's own
window (no vibrancy, black selection bars, black video: known harness limits, not scored as defects unless stated). tvOS files are 1280x720
stills of a 1920x1080 UI. Contrast figures are estimates from rendered colours (no sampling tool), so confirm with a contrast tool.
Motion, focus and VoiceOver are invisible in stills; the tvOS set contains no focused-card frame at all, so focus is unscored by evidence.
All real-data captures are signed out; the demo captures use fictional art (not judged).

Scale: 9.5 = best-in-class shippable. Most shipping apps sit at 6 to 8.

## 1. Score tables

### iOS (iPhone 17, iOS 26.5, French)

| Screen (states grouped) | Score | Verdict |
|---|---|---|
| Home (light/dark-home, -scrolled) | 7.0 | Clean and consistent, but a flat one-line-title list with a tiny thumbnail; tab bar artefacts |
| Following (-following, -scrolled-offline, -bottom) | 6.5 | Near-copy of Home; offline section is thin |
| Browse (+ chips games/irl/music, scrolled) | 7.5 | Best page: clean 3-up poster grid, chips; no viewer counts, weak sort control |
| Category | 5.0 | Two rows and 70 % empty page, no identity (no box art, total viewers, tags) |
| Search (empty, typing, results, filters, no-results, recents) | 6.5 | Good recents and live-now block; blank Categories tab, bare no-results, no loading state |
| Channel live / offline | 6.0 | Small header, non-functional "Suivi", no banner/about, bare "Hors ligne" |
| Settings (top, 2, 3, bottom, language) | 7.5 | Well grouped, parity good; raw key `phone_about_version` shown; language leaves the app |
| Sign-in sheet | 6.0 | Works, clear 2 steps; low-contrast copy pill, cramped bottom, dark code box lost |
| Player live portrait | 7.5 | Strongest screen; chat dense and legible; header row crowded; first-impression controls fine |
| Player chrome, emote picker, chat long-press | 6.5 | Picker is one row of 8 on a big empty area; "new messages" pill collides with text |
| Player landscape (default, controls, modes 1-3) | 7.0 | Three modes work; overlay chat dense over bright video; side panel ignores trailing safe area |
| Player replay / clip portrait | 4.0 | Below the title row the page is empty; clip shows "clip demo" as streamer |
| Player drag mid-state | 4.5 | Title text over letterbox and video, mini-title spills; looks broken |
| Mini strip over Home/Browse/Following | 7.0 | Right pattern and position; flat, lens artefacts below it |
| Offline channel as player | 6.0 | Empty page plus composer for a channel that is not live |
| Signed-out Home / Browse (demo-signed-out) | 4.5 | Dead end: "Non connecté" plus one pill; Following tab hidden, Browse empty |
| Real data (all real-*) | 3.5 | Every screen says "Non connecté" or "Une erreur est survenue"; wrong copy on channel pages |
| Errors (replay unavailable, channel unknown) | 4.0 | Replay error overlaps the play button and carries a LIVE badge; unknown channel shows a sign-in wall |
| Light / dark parity | 7.0 | Dark is more finished; light has lens artefacts and grey-on-grey cards |
| **iOS overall** | **6.1** | |

### macOS (TwitchMac)

| Screen | Score | Verdict |
|---|---|---|
| Home / Following | 6.0 | Right idioms (sidebar, toolbar search, adaptive grid) but toolbar title "Sans titre", tiny type |
| Browse | 6.0 | Poster grid scales well; segmented filter floats off the content edge; labels 10-11 pt |
| Search | 5.0 | One result lost in 1000 px of void; segmented control offset; no empty or recents shown |
| Channel | 6.0 | Header good, hero card only 420 pt wide on a 1000 pt canvas; 2-column replays leave dead centre |
| Player + chat | 6.0 | Pane + chat column fits the Mac; black video unverifiable; control strip is tiny |
| Mini player | 4.5 | Black rectangle pinned bottom-right over a card, no chrome visible in the still |
| Settings window | 4.0 | 560x420 with three rows; no tabs visible although the doc promises General/Playback/Chat/Account/About |
| **macOS overall** | **5.4** | Largely unverified on a real display |

### tvOS (TwitchTV)

| Screen | Score | Verdict |
|---|---|---|
| Home / Following | 7.0 | System top tab bar, 4 cards per row, readable type; card titles truncate at 22 chars; no focus frame shown |
| Browse | 6.5 | Chip row plus 5-up posters; "Tous" selected chip is purple on purple (about 2:1) |
| Search | 6.5 | System keyboard row good; live-now cards fine; large empty lower half |
| Channel | 6.0 | Header good; tab bar still highlights "Accueil" while on a pushed channel; section label reads "Suivis" above a live card |
| Settings | 4.5 | Overlapping headings (a segmented picker sits between "Lecture" and "Chat"), unlabeled pickers, first row collides with tab bar |
| Sign-in | 6.5 | QR and code are right for 10 ft; the numbered steps start at "2.", "Annuler" crowds the footer |
| Player compact chat | 7.0 | Clean 27 % column, hierarchy good |
| Player overlay chat + transport | 5.0 | Transport menu glyphs and scrubber sit on top of chat lines; duplicated "lunaria" title; scrub bar on a live stream |
| **tvOS overall** | **6.2** | |

## 2. Per-screen review

### 2.1 iOS Home (`light-home.png`, `dark-home.png`, `*-home-scrolled.png`)

Works: stable row pattern, 16 pt gutters, LIVE badge (red 800 with white bold, about 5:1), avatars next to channel name, section title in
heavy weight, search pill and avatar reachable from every tab.

Defects.
- `light-home.png`, `light-following.png`, `light-strip-over-*.png`: the floating tab bar renders lens artefacts, a cyan block under Home and
  pink/red blobs under the heart/grid, sampled from the card behind it. It reads as a rendering bug, not as glass. In dark the same bar is
  fine, so the problem is light-specific (probably the glass tint plus thumbnail refraction).
- The tab bar has no labels. Three unlabeled glyphs (house, heart, grid) in a pill 210 pt wide. HIG tab bars carry a label for each item;
  icon-only is accepted only for universally understood symbols. Heart as "Following" is ambiguous with "Favourites".
- `light-home.png` bottom: the pill covers the last visible row while the list does not add bottom content margin (INDEX also notes it).
- Title lines clip to one line ("Run nocturne : boss final s..."), with 3 secondary lines under it (channel, category, LIVE + viewers). The
  thumbnail is 147 pt wide (about 37 % of the width). Twitch, YouTube and TikTok put a full-width 16:9 preview on phones; here the row layout
  shrinks the main content (the picture) to a thumbnail and spends space on four text lines. It is a TV-guide list, not a discovery feed.
- `dark-home-scrolled.png` is identical to `dark-home.png`: the capture did not scroll. `light-home-scrolled.png` shows a "Catégories"
  carousel that never appears in the dark capture, so there are no dark references for that block.
- Header: the purple play-in-TV glyph at far left is decorative, not a control. The avatar "C" is a lavender circle of about 37 pt, which is
  under the 44 pt minimum touch target (the hit area may be larger; the visible target is not).
- Light status area shows an iOS "Réglages" back chip (a test-harness leftover) in every light capture. Recapture.

Fixes to 9.5: use a native `TabView` with labelled tabs (Accueil, Suivis, Parcourir, plus a `Tab(role: .search)`), `tabBarMinimizeBehavior(.onScrollDown)`
on iOS 26 for the glass look, and bottom `contentMargins`; keep the custom bar only on iOS 18 to 25 and only if labelled. Offer a full-width
16:9 card layout (picture first, avatar + title + "channel, category, viewers" below, 2 lines for title) as the default, the row layout as a
density option. Allow titles two lines. Add a hero or "Continue watching" strip. Make the avatar button at least 44x44 pt. Give the logo glyph
a purpose (scroll to top) or drop it.

### 2.2 iOS Following (`*-following*.png`)

Works: live first, offline below, chevrons, "Vu en direct il y a 1 j" is useful.

Defects. Live list is the same card as Home (and in demo the same channels), so Home and Following are indistinguishable except for a
title; offline rows have flat tint avatars (`light-following-scrolled-offline.png`: pale blue disc with no initial, no image fallback) and
the section is introduced by a heading that is as heavy as "En direct". No empty state captured (zero followed channels). Following tab is
missing signed-out (see 2.14).

Fixes: make Home a discovery surface (recommended, categories, clips) and Following a roster (live strip of avatars on top, then
"En direct", then "Hors ligne"); offline rows with letter avatars, last-seen and a bell toggle; add an empty state with a call to action to
Browse; swipe actions to unfollow if the API allows.

### 2.3 iOS Browse (`*-browse*.png`)

Works: 3-column poster grid with 2:3 ratio, 12 pt gutters, chips as filters, selected chip filled purple (white on 7A3DE0 is about 6:1).

Defects. No viewer count on the posters, no tags, so sort by popularity is not legible. The "Tri : popularité" control is purple text at
about 3:1 on `FAFAFB` in light, not obviously a button; a Menu would show a chevron. `light-browse-chip-games-mid.png` is captured during a
re-layout (second row cards shifted 4 pt, ghost label "& DJ" behind "Échecs"); this is a visible jank when switching chips (cards animate
from different positions). `*-browse-chip-music.png` shows two cards and 75 % blank; no "more" content. Chips scroll off the right edge
without a fade cue (Créatif clipped at 775 px but fine). Search/tab artefacts as above.

Fixes: overlay "12K spectateurs" on the poster (bottom gradient), use `Menu` with an `arrow.up.arrow.down` label and "Popularité"/"Récents";
crossfade chips instead of re-layout; keep `matchedGeometry` off for the grid; sticky chip row under the header; section "Catégories
populaires" so a 2-card filter does not feel empty.

### 2.4 iOS Category (`*-category*.png`)

Defects: the screen is a title, a back circle and two rows. No box art, no total viewers, no follow-category control, no tags, no sort.
Category name is repeated as the secondary line of every card (redundant: "Valmoor" three times). 70 % empty. `*-category-scrolled.png` is
identical to `*-category.png` (nothing to scroll). Score 5.

Fixes: header with poster (96x128 pt), name, "17.9K spectateurs", tags as chips, a Follow button; drop the category line from the rows; use the
Browse grid of 16:9 live previews; "Tri" menu; empty state copy ("Personne n'est en direct ici") with a link to Browse.

### 2.5 iOS Search (`*-search*.png`)

Works: native-like pill with a back circle, keyboard raised at once, recents with clock glyph and per-item X, live-now block with
live-ring avatars, filter chips Tout/Chaînes/Catégories, system keyboard.

Defects.
- `*-search-filter-categories.png`: the Catégories tab is completely blank, no "no categories" message, no category results for "lun".
- `*-search-no-results.png`: a single centred 17 pt line "Aucun résultat". No hint (check spelling), no query echo, no action.
- `*-search-typing-loading.png` shows the same results as `*-results.png` and no loading indicator; loading state is not demonstrated.
- `light-search-recents.png` is covered by the iOS keyboard introduction card ("Speed up your typing...", in English) over the keyboard: a
  harness artefact, recapture; do not rely on it.
- Placeholder "Rechercher une chaîne" does not promise categories. The search is a pushed page with a custom field instead of
  `.searchable`; no scope bar, no suggestions while typing, no cancel semantics, keyboard has "search" key only.
- Recents section and "En direct maintenant" share the same heading weight; "Tout effacer" is purple at about 3:1 in dark (lavender `A970FF`
  on `0E0E10` is nearer 6:1, but in light `9B6CDB` on `FAFAFB` is about 3:1).
- Results show a ringed avatar and "LIVE 15K spectateurs" but not the stream title or category.

Fixes: use `.searchable` with `searchScopes`; results grouped with headers and "Voir tout"; categories as poster rows; no-result state with
`ContentUnavailableView.search(text:)` style (icon, title, hint, "Effacer la recherche"); skeleton rows for loading; show stream title and
category; placeholder "Chaînes, catégories".

### 2.6 iOS Channel (`*-channel-*.png`)

Works: avatar with a live ring, name, replay list with duration and views, schedule row.

Defects. Header is 140 pt tall with a 80 pt avatar, no banner, no about text, no follower count. "Suivi" is a filled-heart pill; the Twitch
viewer token cannot follow (INDEX/Phone notes), so a control that looks like state implies an action that does not exist. Nav title is the
lowercase login ("lunaria", "hibou") above the display name "Lunaria": duplicate and inconsistent casing. Offline state is a grey word
"Hors ligne" under the header, no last-seen or "next stream" emphasis. "Prochains lives" row shows a generated date "14 nov. 2026 à 15:56"
that equals the capture time (demo data, but the format is long; use "demain 15:56"). Replay rows have no progress, no date, no type
(archive/highlight) and no "see all". `light-channel-live.png` contains an iOS notification banner ("Prêt pour Apple Intelligence"): recapture.
Live card sits above "Prochains lives" with no "Watch live" label; fine but the card looks identical to a replay row apart from the LIVE chip.

Fixes: remove or reword the Follow pill ("Ouvrir sur twitch.tv" via `Link` with an `arrow.up.right` glyph); banner or blurred
avatar backdrop; about text 2 lines with "Plus"; segmented picker (Accueil / Vidéos / Clips / À propos) as on Twitch; title in the nav bar
only once the header scrolls away; replay rows with progress bars and relative dates.

### 2.7 iOS Settings (`*-settings-*.png`, `*-settings-language-picker.png`)

Works: inset grouped cards, tinted symbols, uppercase section captions, toggles at the right, explanatory footer under the playback
section, account card at top, consistent in both appearances. Switch colours (purple on) are readable.

Defects. `*-settings-2/3/bottom.png` show the raw string `phone_about_version 1.0 (1)`: a missing localisation key leaks to the user
(`phone_about_version` is the Android resource name that the sync script uses as key). Three near-identical captures (2, 3, bottom) are
the same scroll position: the capture did not scroll. Settings are custom cards rather than `Form`, so Dynamic Type, row heights and
accessibility rotor are not system-supplied. "Langue" row leaves the app (`*-language-picker.png` are blank screens with the "TwitchPlayer" back
chip): iOS 13+ per-app language is a legitimate system hand-off, but the row shows no hint ("Ouvre les Réglages") and the chevron implies
an in-app push. The footer text under "Aperçus Découvrir" says "Découvrir" although the app has no Discover feed (`APPLE_PLATFORMS.md`
parity table: Discover feed "no"): a setting for a feature the Apple apps do not have. "Se déconnecter" is a neutral pill; destructive
should be red text with confirmation.

Fixes: add the missing key (`about_version`) and a unit test that no value equals its key; hide the Discover setting on Apple; use `Form` +
`Section` (keeps the look with `.scrollContentBackground(.hidden)`); row hint for Langue; destructive role and confirmation for sign-out;
add Dynamic Type preview, a "Sur Twitch" about link and licences.

### 2.8 iOS Sign-in sheet (`*-signin-sheet.png`)

Works: two numbered steps, a primary purple CTA ("Ouvrir twitch.tv/activate", white on `A970FF` is about 3.4:1: below 4.5:1 for 17 pt
semibold), mono code with wide tracking, expiry countdown, Annuler top-left.

Defects. Sheet is about 55 % height with the dimmed page showing "Non connecté" and its own copy behind a blur; the page content bleeds
through at the top of the sheet in light (`light-signin-sheet.png`: blurred button ghost under the code). "Copier le code" pill is
lavender on lavender (about 2.4:1 in light). The code is not selectable (INDEX). In dark the code card background disappears (the light
version has a grey card), a parity gap. The bottom 80 pt holds nothing (the tab bar ghosts underneath). Title "Connexion" is centred while
Annuler is a button pill: mixed idioms. No QR path (phone is the same device) and no automatic return/polling indicator. Step 2
instruction "Saisissez ce code s'il n'est pas déjà rempli" is hedged copy.

Fixes: detents `.medium, .large`, drag indicator, `textSelection(.enabled)`, tap code to copy with haptic, CTA at 4.5:1 or 7:1 (darker
purple `772CE8` on white is about 6.3:1), a "En attente de la validation..." progress row, success confirmation, retry after expiry, remove
the double heading.

### 2.9 iOS Player live portrait (`*-player-live-portrait.png`, `*-player-back-portrait.png`)

Works: video 16:9 at top, one details row (avatar, title, channel and category, LIVE chip, viewers, uptime, chevron), dense legible chat
with badges, emotes, replies and sub notices, rounded composer. Dark chat colours are readable; nick colours corrected for light. Controls
overlay (`*-back-portrait.png`) is complete: collapse, title, chat toggle, more, play/pause, mute, fullscreen, LIVE + uptime.

Defects. Details row crowds the trailing side: LIVE chip, "15K" and "2:00:04" stack in a 100 pt column; "15K" has no icon or word so
it reads as a number; title is one line with ellipsis. The chevron at far right has no label (opens the channel). The safe area above the
video is a black 124 px band (status bar) which is correct, but the composer floats at the bottom with a 20 pt radius and no home-indicator
inset gap (about 40 px, acceptable). The pause/play control is a 67 pt translucent disc with the glass lens artefact (visible refraction
with rainbow demo colours): OK on real video. Top controls are 40 pt circles (under 44 pt). Emote button at left of the composer is
28 pt glyph with about 44 pt hit area (verify).

Fixes: viewers with an `eye` or `person.2` glyph and "15 k"; two-line title; hit areas to 44 pt; use `.glassEffect(.regular.interactive())`
groups with spacing 8; a "Partager" and "Clip" action in the more menu; a visible "back to live" pill when paused.

### 2.10 iOS Emote picker and chat row long-press (`*-player-emote-picker.png`, `*-player-chat-row-longpress.png`)

Works: search field, source tabs (Twitch, 7TV, BTTV, FFZ), keyboard toggle, "N nouveaux messages" pill, keyboard in landscape portrait fine.

Defects. The picker shows one row of eight emotes and then 350 px of blank panel (demo data, but there is no recents section, no sticky
sections or a "Récents" empty message). The composer shrinks the chat to 4 lines while the panel is open. The pill "2 nouveaux messages"
overlaps the message text "petitpois: On (...)" (clips the line) in `*-emote-picker.png`, and in `*-chat-row-longpress.png` it overlaps the
quoted reply line. Long-press does not show any menu in the still (reply highlight only appears as a keyboard raise with a reply bar
missing). The keyboard background shows the video colours through (blurred glass keyboard), unusual but system.

Fixes: pill sits above the composer with 8 pt gap and never inside the list bounds (inset the scroll content by the pill height); add
"Récents" and "Favoris" sections; use a context menu with Répondre / Copier / Signaler for long-press; show a reply banner above the
composer ("Réponse à X" with a close button).

### 2.11 iOS Player landscape (`*-player-landscape-*.png`)

Works: three chat modes (side panel, overlay, hidden), overlaid controls, collapse and fullscreen toggles, consistent dark/light (the panel
follows appearance).

Defects. The video is letterboxed inside the left 590 pt with a 56 px black band at the bottom where LIVE + uptime and the fullscreen
buttons live, but the title sits inside the picture over the stream colours (white text on yellow in the demo: about 1.3:1; use a scrim).
The side chat panel ends at the screen edge with no trailing safe-area inset and the composer is 15 pt from the edge. Overlay mode
(`*-chat-mode2.png`) has dark translucent bubbles on a bright rainbow; reading is possible but the 30 % wash makes mixed colours;
messages clip at the top and bottom edges without a fade. In `mode3` (hidden) controls remain but the right half has no hint to bring chat
back apart from a faint icon. Landscape captures are rotated after the fact so safe-area insets (Dynamic Island side) cannot be verified.

Fixes: top scrim gradient under title; inset by `safeAreaInsets` on both sides; fade the overlay at the top (mask) and clamp to 60 % of height;
double-tap on the video to toggle chat; persist the choice per orientation.

### 2.12 iOS Replay and clip (`*-player-replay-portrait.png`, `*-player-clip-portrait.png`)

Score 4. Under the video: avatar + "vod demo" / "clip demo" in the title slot (data placeholder) and then an empty page. No description,
chapters, date, views, related videos, no seek bar visible in the still, no chat replay, no share. "clip demo" is shown as the streamer.
A clip is one of the most shared Twitch objects, and the page offers nothing to do next.

Fixes: title (2 lines), channel row with Follow-out link, date and views, description, chapters for VODs, "Plus de ce streamer" rail, speed
chip, share sheet; for clips "Voir la rediffusion complète".

### 2.13 iOS Drag mid-state (`*-player-drag-mid.png`)

Score 4.5. While the sheet shrinks, the title "Run nocturne : boss fin..." and the channel "lunaria" overlap the black letterbox and the top
of the video at x 255 to 330; the pause and X glyphs are on a flat slab (`EDEDF2` / `202024`) taller than the video; the Home page peeks
above with the player at an odd 40 % offset and the tab bar is hidden. The dark capture adds a stray "Réglages" back chip. A mid-drag
frame must still look designed (it is on screen for 300 ms).

Fixes: cross-fade the expanded controls out before the strip's text fades in; clip the strip content to the strip frame; spring
interpolation of corner radius and shadow; rubber-band at limits; interactive dismiss with velocity.

### 2.14 iOS Mini strip (`*-strip-over-*.png`)

Works: docked above the tab bar, thumbnail, title, channel, play and close; same in all three tabs and both appearances.

Defects. The strip is 56 pt tall and 361 pt wide, flat `EDEDF2` (light) with a 1 px edge; no glass, no progress hairline, no live dot;
the thumbnail's left corner radius is clipped (INDEX). Strip glyphs (play, X) are 22 pt with about 44 pt hit areas (verify). The strip
and the tab bar overlap the last Home row, so two floating layers cover content. Title "Run nocturne : boss fin..." ellipsises at
17 chars though 40 pt are free.

Fixes: `tabViewBottomAccessory` on iOS 26 (system mini-player slot above the tab bar, collapses with the tab bar) and a custom fallback
below; show thumbnail with continuous corner radius inset 4 pt; LIVE dot; swipe down to dismiss, swipe up to expand.

### 2.15 Signed-out experience (`*-home-signed-out.png`, `*-browse-signed-out.png`, all `light-real-*.png`)

This is the app's biggest user-facing hole. A first-time user sees "Non connecté / Connectez-vous avec Twitch pour voir les chaînes que
vous suivez." and one pill, on Home and on Browse (the same copy on Browse, which has nothing to do with followed channels), on
Category, Channel, Search results (`light-real-search-typing.png` shows "Une erreur est survenue / Réessayer") and unknown channel
(`light-real-error-channel-unknown.png` shows the sign-in wall, not "channel not found"). The player (`light-real-player-portrait.png`)
shows a black picture, a pause glyph and a pill "Connectez-vous pour lire le chat et y écrire" that is not a button; the
`light-real-error-replay-unavailable.png` page overlaps its error title, subtitle and "Réessayer" with the play button and shows a LIVE
badge on a replay. The "C" avatar is replaced by a person glyph with no label. The tab bar loses the Following item (2 tabs: fine) but
becomes visually narrow and centred (257 pt) while the header stays full width.

Verdict: a dead end, and a trust problem: errors and sign-in walls are the same screen. Twitch's public catalogue is readable without a
user session through the same endpoints the website uses; if policy forbids that, then the app must say so up front and make signing in
the first-run experience, not an error.

Fixes (in order): (1) a signed-out Browse and Search that work (anonymous catalogue calls, or cached/popular snapshot); (2) an onboarding
screen on first launch: logo, three benefits, "Se connecter avec Twitch" (primary) and "Parcourir sans compte" (secondary); (3) copy per
screen ("Parcourir" must never talk about followed channels; "Introuvable" for an unknown channel; "Impossible de charger" with the real cause
for network errors); (4) make the chat prompt a button that opens the sign-in sheet; (5) a persistent, small "Connexion" action in the
header in place of the person glyph; (6) fix the replay error layout (no play button over an error, no LIVE badge).

### 2.16 Cross-state: errors (`light-real-error-*.png`)

Score 4. Generic copy, no icon, no cause. `error-replay-unavailable` collides three layers (title, subtitle, retry button, play disc).
Fix with a single reusable `ErrorState` (SF Symbol, title, one line, primary + secondary action) shared by iOS, macOS and tvOS.

### 2.17 macOS (`docs/screenshots/macos/*.png`)

Works: sidebar split view, toolbar search with Cmd+F, account menu, adaptive grid (3 columns at 1000 pt), stream cards with LIVE
and viewers overlaid in a pill, avatar + title + channel + category under the card, chat column with a 230 pt trailing width and a
composer, play pane with a header and an "Ouvrir la chaîne" button.

Defects.
- Window chrome: toolbar title reads "Sans titre" on Home (`home.png`) and "TwitchPlayer" elsewhere; it should be the section name or empty with
  a large title. A greyed "TwitchPlayer" in every toolbar is redundant with the sidebar.
- Sidebar: in `home.png` the first item is a black bar (selection, harness) and the rows are 13 pt with icons nearly invisible
  (dark grey on dark grey); there are no section headers ("Découvrir", "Bibliothèque") and no footer account/status. The sidebar width
  of 210 px (about 200 pt at 1x) is the correct default.
- Type is small: secondary text in cards is about 11 pt, caption 10 pt (`browse.png` labels). HIG minimum on macOS 10 pt; legibility on a Retina
  display is fine, but hierarchy flattens: title 12 bold, channel 11 regular grey, category 11 regular grey.
- `browse.png`, `search.png`: the segmented filter floats at x 274 to 654 with a 40 px left offset relative to the 234 px content edge;
  "Tri : popularité" is a purple text at the far right; neither is a toolbar control. Use a `Picker(.segmented)` in the toolbar or
  `.safeAreaBar`; sort as a toolbar `Menu`.
- `search.png`: a single result floats at top-left of a 1000 px canvas with a 52 pt avatar; no empty/recents/suggestions; search replaces the page
  instead of using the system search suggestions; X clear button OK.
- `channel.png`: hero card is 420 pt wide, left-aligned, leaving the right 60 % empty; replays in 2 columns with 350 px gaps. Use a
  hero row (live card left, channel info right) and a 3 to 4 column grid.
- `player-chat.png`: black video (harness). Control strip above the video is 20 pt high with a chevron, title, an options menu and X; no
  transport bar is visible (system `AVPlayerView` overlay, unverifiable). Bottom bar duplicates title and channel already in the header.
  Chat header "Chat" with no options; chat composer at the bottom is a 275x24 field (small).
- `mini.png`: a 160x190 black rounded rectangle at the bottom-right overlapping a card; no shadow, border or controls visible.
- `settings.png`: a 560x420 window with Thème, Animations, Langue only, and a white bar over the tab strip (harness). The documented
  tabs (Playback, Chat, Account, About) are absent from the only capture, so the Settings window cannot be judged. Language row
  button "Ouvrir les Réglages Système..." is the right macOS idiom.
- No window-level Liquid Glass is visible: the sidebar is solid `1B1B1D`; on macOS 26 the sidebar should float as glass with inset
  rounded selection.
- Dark only: no light-mode macOS capture exists.

Fixes: use `NavigationSplitView` with `.navigationTitle` per section, `.toolbar` items (sort menu, filter picker, account), the
system sidebar `List(selection:)` with `Label` icons and `.listStyle(.sidebar)`; bump card text to 13 / 12 pt; centre maximum content width at
1100 pt; implement hover states and `contextMenu` already described; add a `Settings` capture per tab; add a light capture and a minimum
window size (about 900x600).

### 2.18 tvOS (`docs/screenshots/tvos/*.png`)

Works: the system top tab bar; 80 pt-style left inset; 4 cards per row with LIVE and viewer count as a pill; focus-friendly card sizes
(about 267x150 px at 1280, which is 400 pt at 1920); a large-type search keyboard; live-now cards; compact chat column that leaves 2/3 for
the picture; sign-in with a QR code, a large mono code, address and expiry.

Defects.
- No focus state is shown in any capture. Tabs highlight (white pill, black text) but the first card has no visible lift, scale or
  glow, so focus legibility cannot be confirmed. Add a focused capture per screen.
- `home.png`/`following.png`: card text is white on `4A4A4C` (about 9:1) but the secondary channel/category grey `A0A0A5` on the same
  surface is about 3.6:1 at 22 pt equivalent; raise to `C7C7CC`. Titles clip at 20 chars ("Run nocturne : boss...") although the card is
  400 pt wide: allow 2 lines.
- `browse.png`: the selected "Tous" chip is purple text on a dark purple pill (about 2:1) and looks unselected next to the grey ones; a
  selected state on tvOS should invert (white on purple or black on white). "Tri : popularité" is a chip in a different style (purple on
  purple): the same low contrast.
- `search.png`: the bottom 35 % of the screen is empty; the "123" and "ESPACE" keys sit tiny (22 pt) to the left of a 4-pt different
  baseline; the live cards are 160 px wide (240 pt) rather than the 400 pt cards used elsewhere, an inconsistency.
- `channel.png`: the top tab bar still highlights "Accueil" while a pushed channel page shows; the section under the header is labelled "Suivis"
  over a single live card (wrong label: it is "En direct"); there is no Follow control (acceptable) and no "Retour" cue. The
  header avatar ring is 100 px with a heavy red ring (the same ring colour as the LIVE badge); it reads as an error state.
- `settings.png`: structural bug. The account row is at the top, "Lecture" heading follows, then two rows (Statistiques, raids), and then a segmented
  control "Chat masqué | Chat compact | Chat sur la vidéo" appears under "Lecture" and above its own heading "Chat"; a second picker "Petit | Moyen |
  Grand" sits directly under "Chat" with no label; the "Apparence" picker is cut by the bottom edge at y 676 of 720 (inside the 60 pt bottom
  safe area). Heading "Lecture", "Chat", "Apparence" are 28 pt grey at about 2.5:1 on the dark background. Pickers lack labels. Row
  "Se déconnecter" is red text at far right, which is right, but is the only red element.
- `signin.png`: title "Connexion" centred above a left-aligned two-column group; the instruction "2. Saisissez ce code..." exists but
  step 1 is replaced by "Scannez le code avec votre téléphone" so numbering starts at 2; "Annuler" pill (purple, 112 px wide) is the
  only control and sits 30 px under the expiry text with a stray three-dot glyph beneath it (a clipped second label). The
  code at 52 pt mono white is excellent. QR on white rounded square is correct.
- `player-chat-compact.png`: good; "Chat" header at 22 pt with an options glyph; line spacing and emote size fine.
- `player-chat-overlay.png`: the transport controls (chat, display, sliders, headphones, sleep, waveform glyphs) and the scrubber sit directly on top of
  the chat lines at 850 to 1230 px and the elapsed/remaining times "00:04 / -00:08" are shown for a live stream (a live stream should show
  "EN DIRECT" and no remaining time). The title is rendered twice ("lunaria" small above "lunaria" large). White chat text on the
  bright magenta/cyan test pattern is hard to read (demo video, but real video is also bright); the doc says 30 % wash, it is lighter in
  practice. The shadow does not lift the text off the blue band.

Fixes: a focus capture suite (focused card, focused chip, focused picker); selected-state styling for chips; settings rebuilt as a
`Form` or `List` with `Section` headers and `Picker` rows with labels (system menus on tvOS), and keep rows inside the 60 pt safe area;
`.badge`/focus-friendly card text at 26 pt with 2 title lines; use the nav bar item highlight consistent with the pushed section (or hide it);
replace the live scrubber with "EN DIRECT" and move transport menus above the chat or reduce chat opacity while the transport is visible.

### 2.19 Cross-cutting findings

1. Signed-out is a wall (see 2.15). Highest user impact on all platforms.
2. Missing/raw localisation key on iOS Settings (`phone_about_version`). Ship-blocker for polish.
3. Custom chrome instead of system components: iOS uses a custom header (back circle + pill), custom tab pill, custom settings cards, custom
   search page. HIG idioms (large titles, `.searchable`, `TabView`, `Form`, `ContentUnavailableView`, swipe-back, toolbar placement) bring
   Liquid Glass, Dynamic Type, VoiceOver, pointer and keyboard behaviours for free. Today the Liquid Glass use is cosmetic (tab pill and
   buttons) and shows artefacts.
4. Hierarchy: every screen uses the same heavy 28 pt section title and 17 pt rows; no secondary scale (no `.title2` / `.subheadline`
   contrasts), no accent beyond purple. Home, Following, Category and Search results use the same row, so wayfinding relies on one heading.
5. Dynamic Type and RTL: no capture at accessibility sizes or in Arabic exists; the row layouts use fixed thumbnail widths and one-line
   titles (clips already at default size), so AX1+ will truncate or overlap. `APPLE_PLATFORMS.md` says RTL screenshots are not verified.
6. Contrast: purple text on light (`Tri`, `Tout effacer`, `Copier le code`) about 2.4 to 3.4:1; tvOS selected chip about 2:1; tvOS section headings
   about 2.5:1; Apple minimum is 4.5:1 for text under 18 pt.
7. Empty/loading/error states are not designed: bare centred text on iOS Search, Category, Errors; no skeletons anywhere; no pull-to-refresh
   visible; stale banner exists in code but is not shown.
8. Consistency across platforms: iOS list rows vs Mac grid cards vs tvOS cards differ in information (Mac and tvOS put viewers on the
   thumbnail; iOS puts it in a text line). Pick one card model and reuse it.
9. Iconography: SF Symbols are used consistently (house, heart, square.grid.2x2) but outline/filled switching is done by tint pills; heart
   on Following and "Suivi" (filled heart) share the symbol with different meaning. The Twitch-style red ring around live avatars is
   heavy (3 pt) compared with the purple brand.
10. Copy: French is generally clean ("spectateurs", "Hors ligne"), but "Non connecté" is reused for every gated state; "Aperçus Découvrir"
    exists without a Discover feed; "Tri : popularité" in a chip; English leaks in system keyboard card only.
11. Light/dark parity: dark is more cohesive (glass edge, saturated purples). Light has lens artefacts, lavender-on-grey cards and
    a pale avatar; both appearances share one layout, which is good.
12. Capture quality: several captures do not show what their names say (see list in section 4), so regressions will slip through.

## 3. Capture defects to fix before the next review

- `dark-home-scrolled.png` equals `dark-home.png` (no scroll). `*-settings-2.png`, `-3.png`, `-bottom.png` are the same frame. `*-category-scrolled.png`
  equals `*-category.png`.
- `light-real-strip-over-home.png` is the player, not a strip; `light-real-browse-scrolled.png`, `-chip-games.png`, `light-real-category.png`,
  `light-real-channel-scrolled.png` all equal the sign-in wall.
- `light-search-recents.png` is covered by the keyboard intro card; `light-channel-live.png` has an Apple Intelligence notification banner.
- Every light capture shows a "Réglages" back chip in the status bar; clocks differ (15:58 vs 17:51) so states were taken in different runs.
- `*-settings-language-picker.png` are blank screens.
- macOS: sidebar selection black, mini player black, settings window truncated. tvOS: no focus frames.

## 4. Ranked executable fix list (highest impact first)

1. **Signed-out experience** (iOS Home, Browse, Category, Channel, Search, Player; macOS; tvOS). Make Browse, Category, Search and Channel work
   without a session (anonymous catalogue endpoints or cached snapshot), add a first-run welcome with "Se connecter" and "Parcourir sans compte",
   per-screen copy, and a chat prompt that is a button. Done when `light-real-*` shows content, not "Non connecté".
2. **Fix the leaked key `phone_about_version`** (iOS Settings). Add `about_version` to the Apple strings, add a test that fails when any
   rendered string equals its key, and hide the Discover setting on Apple.
3. **Replace the custom tab pill with a labelled system `TabView`** (iOS all tabs): labels, search tab role, `tabBarMinimizeBehavior`, bottom content margin,
   fallback pill with labels on iOS 18 to 25. Removes the light-mode lens artefacts.
4. **Redesign the replay and clip pages** (iOS player replay/clip, Mac, tvOS): title, channel row, date, views, description, chapters, related, share.
5. **Fix the drag mid-state and mini strip** (iOS `player-drag-mid`, `strip-over-*`): clip strip content, cross-fade, use `tabViewBottomAccessory` on iOS 26.
6. **Unified error and empty states** (all platforms): one `ErrorState`/`EmptyState` view with symbol, title, one line, actions; fix the
   replay error overlap and the LIVE badge; "Introuvable" for unknown channel; search no-results with hint and clear action; Categories scope blank state.
7. **Home as discovery, Following as roster** (iOS Home, Following): full-width 16:9 cards by default, 2-line titles, hero/continue-watching,
   live-avatar strip on Following, offline rows with letter avatars and last-seen.
8. **Settings rebuilt on `Form`/`List`** (iOS Settings, tvOS Settings): fixes tvOS overlapping headings and unlabeled pickers, gives Dynamic Type,
   destructive sign-out with confirmation, hint on the Language row.
9. **Contrast pass** (iOS `Tri`, `Tout effacer`, `Copier le code`, CTA on sign-in; tvOS chip selected state, section headings, secondary grey): 4.5:1 minimum;
   darker brand purple (`772CE8`) for text on light, `C7C7CC` for tvOS secondary.
10. **iOS navigation to system idioms** (all pushed iOS pages): native navigation bar with large titles, `.searchable` with scopes and suggestions,
    swipe-back, toolbar items; keep the zoom transition.
11. **Channel page** (iOS, Mac, tvOS): banner/backdrop, about, follower count, segmented sections, remove or reword the non-functional "Suivi"
    pill ("Ouvrir sur twitch.tv"), display name only in the title, "Hors ligne" with last stream.
12. **Category page** (iOS): poster header, total viewers, tags, grid of 16:9 live previews, sort menu, empty state.
13. **tvOS player overlay** (tvOS `player-chat-overlay`): "EN DIRECT" instead of elapsed/remaining, de-duplicate the title, keep transport menu
    clear of chat lines or dim chat while it shows, stronger shadow behind chat text.
14. **Sign-in sheet** (iOS, tvOS): detents with drag indicator, selectable and tap-to-copy code, waiting/confirmation states, tvOS numbering
    (1 then 2) and "Annuler" spacing, dark-mode code card parity.
15. **macOS toolbar and sidebar polish** (macOS all): section title or empty toolbar title, toolbar `Picker`/`Menu` for filter and sort, visible
    sidebar icons, content max width, larger card text (13/12 pt), channel hero layout, search results grid, a light capture.
16. **Chat details** (iOS player, emote picker, long-press): keep the "new messages" pill out of the text area, reply banner above the
    composer, Recents/Favourites sections in the picker, context menu on long-press.
17. **Landscape player** (iOS): scrim under title, trailing safe-area inset for the chat panel, fade masks in overlay mode, double-tap to toggle chat.
18. **Dynamic Type and RTL validation** (all platforms): capture AX3 and Arabic for Home, Browse, Search, Player; make rows wrap to two lines and
    let thumbnails scale with `@ScaledMetric`.
19. **Header and touch targets** (iOS): 44 pt minimum for avatar, player top-bar buttons and strip controls; give the logo glyph a purpose or remove it.
20. **tvOS focus and layout** (tvOS all): add focus captures, 2-line card titles, a consistent card size for Search live-now cards, highlight the
    right tab for pushed pages, keep every control inside the 60 pt vertical safe area.
21. **Browse polish** (iOS, tvOS): viewer counts on posters, sort as a `Menu`, crossfade between chip results, stable layout when switching chips.
22. **Liquid Glass audit** (iOS 26, macOS 26): float the Mac sidebar as glass, group iOS buttons in `GlassEffectContainer`, verify the material fallback
    with a fresh iOS 18 capture (none exists).
23. **Search results richness** (iOS, Mac, tvOS): stream title and category in the row, "Voir tout" per group, skeleton loading.
24. **Recapture the audit set** with the defects in section 3 corrected (scroll, banners, keyboard card, real signed-in data, macOS on a real display
    with vibrancy, tvOS focus), then re-score.
25. **Consistency model** (all): one stream card spec (viewers on thumbnail, avatar + title + channel + category) shared by iOS, macOS, tvOS.

## 5. Overall platform scores

| Platform | Score | Main drag |
|---|---|---|
| iOS | 6.1 | Signed-out wall, custom chrome, empty replay page, tab bar artefacts, leaked key |
| macOS | 5.4 | Thin Settings, small type, unverified vibrancy/hover, no light capture |
| tvOS | 6.2 | Broken Settings layout, no focus evidence, overlay chat collisions |

Target 9.5. Rough gap: iOS about 3.4 points (fixes 1 to 7 are about 2 points of it), macOS about 4, tvOS about 3.3.
