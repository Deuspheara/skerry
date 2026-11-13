# Android phone screen review 1

Independent design review of the 86 emulator captures in `docs/screenshots/audit/android/` (Pixel 8, API 36, `:phone`, demo data),
read against the intent in `docs/phone-redesign.md`. Every PNG listed in `INDEX.md` was opened. Read-only review: nothing was built
or run.

Method notes. Pixel sizes are from the 810 px wide downscaled files, so 1 dp is about 2 px. Contrast ratios are estimates from the
rendered colours (no sampling tool was available), so treat them as "check this with a contrast tool", not as measured values. Motion
is not visible in stills; only frames that show a broken in-between state are flagged. The demo art is abstract placeholder art, so
nothing below judges real thumbnails or box art.

## 1. Score table

Scale: 9.5 = shippable, best in class. Most shipping apps sit at 6 to 8.

| Screen (states grouped) | Score | One-line verdict |
|---|---|---|
| Home (01, 34, 57, 64, 67, 52, 75) | 6.5 | Clean and consistent but a plain list, and identical to Following |
| Discover Live (02, 04, 35, 59) | 6.0 | Right idea, weakest layout: letterboxed picture, floating chrome, ambiguous icons |
| Discover Clips (03) | 3.5 | A single line of text on a black page |
| Following (05, 06, 36, 60) | 6.0 | Duplicates Home; offline section is thin and misaligned |
| Browse (07-10, 37, 53, 58, 65, 66, 71) | 7.0 | Strongest screen; 3-column grid breaks at large font; light/dark cards differ |
| Category (11, 72) | 5.0 | A list with a filter; no category identity, 60 % empty |
| Search (13-19, 30, 63, 67-search) | 6.5 | Functional and tidy; weak result rows, flat empty/no-result states |
| Channel (41, 44, 83) | 5.0 | Oversized placeholder, redundant Watch, ambiguous Following pill, no identity |
| Player portrait live (20, 21, 25, 40, 54, 55, 61, 68) | 7.0 | Best screen overall; header and chat hierarchy need work |
| Player replay (42, 43) | 5.5 | Type scale breaks from live; speed chips wrap; odd seek bar |
| Chat: input, emote picker, reply (22-24) | 7.0 | Compact input delivered; picker is confusing and plain |
| Landscape (26-29) | 6.0 | Good three modes; title straddles the letterbox; overlay clips lines |
| Bottom sheets (31, 32) | 6.0 | Correct, but bare |
| Mini player strip + drag (30, 34-37, 39, 62, 69, 75, 38) | 6.0 | Good pattern; strip is plain; the drag still (38) looks broken |
| Settings and dialogs (45-50, 56, 75b) | 6.5 | Tidy grouped cards; too long; mixed icon gutters; thin dialogs |
| Sign-in, signed-out, offline (78-82, 79) | 3.5 | Wrong copy, Browse and Discover gated behind sign-in, no real offline state |
| Wide layout (70-73) | 4.0 | One giant card per screen; player is not side by side |
| Arabic / RTL (51-55) | 7.5 | Mirroring is right; bidi punctuation bugs |
| Large font 1.5x/2.0x (64-69) | 6.0 | No overlap, but data loss in Browse and a clipped avatar |
| Light theme parity (56-63, 75) | 6.5 | Works; some structural drift from dark |
| PiP (74) | n/r | Capture is the launcher with a small window; cannot assess |

**Overall: 5.9 / 10.** The foundations are sound (custom icon-only bar, search pill header, compact chat input, consistent row card,
RTL mirroring). It does not yet look like TikTok Live, YouTube or Twitch mobile because of five things: there is no imagery or
identity beyond placeholder tiles, Home and Following are the same list, the Discover screen is the least polished surface despite
being the most "TikTok" idea, the flows around sign-in and offline are wrong, and large-screen and large-font behaviour is untested
by design.

---

## 2. Screen by screen

### 2.1 Home (01, 34, 57, 64, 67, 75, 52, 77)

Score 6.5.

Works:
- Header is calm: brand tile, 36 dp search pill, avatar. Gutters are a consistent 16 dp. The pill is a good pattern.
- The row card (168 x 94 dp thumbnail, title, channel, category) is aligned and the text block is vertically centred. Seven rows
  are visible on a Pixel 8, which suits a viewing app.
- Dark and light are both clean; the light variant (57) keeps the same structure.

Defects:
- Home (01) and Following (05) show the same seven live rows in the same order. Two of four tabs carry the same content, so the
  tab bar spends 25 % of its width on redundancy. Home has nothing a viewer expects from the first tab (recommended, a "continue
  watching" or "recently watched" row, a top featured stream).
- Every card carries a red LIVE badge and a red-dot viewer chip. On a list titled "Live from your follows" the badge carries zero
  information, and seven saturated red rectangles at 10 sp shout louder than the title. White 10 sp bold on this red is roughly
  4.6:1, which is borderline for small text. (01)
- Titles are cut to one line with an ellipsis ("Run nocturne : boss final sans...", "Gratin dauphinois, la version d...") while
  the row has room for two lines; the text block floats with blank space above and below. (01, 57)
- No category chip, language or stream age on the card; the category is plain grey text under the channel, so scanning by game is
  slow.
- Section title "Live from your follows" (about 20 sp medium) is the only heading style on the page and differs from "Browse"
  (about 28 sp bold, 07) and from Following, which has no heading at all (05). Three tabs, three header treatments.
- Rows have no overflow or long-press affordance (no "not interested", no share).

Large font and RTL:
- 1.5x and 2.0x (64, 67): no overlap. The search-pill magnifier does not grow with the text and looks small; the avatar "C" is
  clipped at the bottom at 2.0x (67, 66). Titles still truncate to one line at 1.5x even though there is room.
- Arabic (52): correct mirroring of the whole row, bar order, pill and avatar. Bug: the French title "? Any% en moins de 40 minutes"
  shows its question mark at the start, a bidi isolation problem (user-generated text must be wrapped in FSI/PDI or given
  `TextDirection.Content`).
- Signed-out Home (78): see section 2.15.

To reach 9.5:
1. Make Home a real discovery surface: one large 16:9 hero card for the top stream (full width, title below, avatar at left,
   viewer count top-right), then rows. Or make Home the Discover feed and fold Discover into it. Either way remove the duplicate.
2. Drop the LIVE badge where the list is all live; show the red dot plus viewer count only (YouTube and Twitch both do this).
   Keep the badge for mixed lists (Search, Channel).
3. Allow two lines for titles (`maxLines = 2`, row height fixed to thumbnail height) and add a category chip or colour-keyed
   category line.
4. One header system: 28 sp bold large title per tab, or none, but the same everywhere.
5. Isolate bidi text; stop the magnifier from shrinking; give the avatar a fixed circle with text centred and unscaled.

### 2.2 Discover Live (02, 04, 35, 59) and Clips (03)

Scores 6.0 (Live) and 3.5 (Clips).

Works:
- Full-bleed vertical pager idea is the right "TikTok Live" gesture and the chat peek over the video is on-brand.
- Right-hand action rail (mute, chat, open, play) is large and reachable.

Defects (Live):
- A 16:9 stream is centred in a 9:20 page: about 590 px of black above (the LIVE badge and the Live/Clips tabs float inside it)
  and 190 px of black below. The page reads as a mostly empty black screen with a rainbow strip in the middle, not an immersive
  card. (02, 04)
- The LIVE badge (y = 145) and the Live/Clips tabs (y = 155) are not on one baseline. The tabs are off-centre against the
  badge-plus-tabs group. The Live/Clips tabs also sit on the status bar area with no scrim.
- Rail icons carry no labels and are ambiguous: a struck-through speaker (muted state, or a mute button?), a speech bubble (open
  chat? toggle?), an open-in-new arrow, and a play-in-circle that looks identical to the Discover tab icon. They have no scrim, so
  they dissolve on bright video. The mute button shows the muted glyph by default, so it is unclear whether it is a state or an
  action. (02)
- The bottom info block is cramped: avatar, name, category, viewer count floating in the middle of the row at x = 540 to 620
  (not right-aligned, not attached to the name), title squeezed below. There is no Follow button, no heart or share. (02, 04)
- Chat lines at the bottom-left run into the info block with no gap, and the first line is half faded while the message list
  has no background, so contrast depends on the video; on a bright frame the pastel nick colours (for example Gaspard teal at
  roughly 3:1) will fail. (02)
- Pager indicators are absent: nothing says there are more streams to swipe to. (02, 04)
- 35 (strip over Discover): the page swaps to a tinted placeholder and the type colours look inverted (yellow text, pink LIVE).
  This is likely a palette artefact of the capture, but check it: if real, it is a bug.

Defects (Clips, 03):
- Only the line "No clips to show right now." on black, no icon, no reason, no action, no retry, and the Live/Clips tabs are the
  only chrome. This is the single weakest screen in the set.

To reach 9.5: crop the video to fill (`ContentScale.Crop` for a preview page, with a blurred or darker fill only where the
aspect really differs) or place it in a 16:9 card at top and use the rest for chat and info; add a bottom scrim; one 56 dp rail
with a 40 dp circle behind each icon and a text label under it; right-align the viewer count; add Follow, share, and a pager
hint; give Clips a proper empty state (icon, "Clips will appear here", Retry).

### 2.3 Following (05, 06, 36, 60)

Score 6.0.

Works: same card system as Home, a clear Offline section, large touch rows.

Defects:
- Duplicate of Home (see 2.1).
- No page heading (05), so the first row sits at y = 229 directly under the search bar; the page feels unfinished next to the
  large "Browse" title.
- The Offline section (06) is indented by an extra 16 dp (avatars at x = 63 vs 32 for live rows), has 84 dp rows with a 52 dp
  avatar, a hairline divider that starts at the text, and the secondary line "Offline" repeated on every row. That repeats a
  fact the section header already states; "Last live 2 days ago" would carry information.
- Offline avatars are not dimmed; the section header "Offline" is smaller than "Live from your follows", so hierarchy is
  inverted.
- No "continue watching", no notification bell, no sort.

To reach 9.5: a "Live now" horizontal row of large avatars with red rings on top (Instagram stories pattern, the redesign's
"follow drawer"), then Live rows, then an Offline list with "last live" data and dimmed avatars, one indent, one divider style.

### 2.4 Browse (07 to 10, 37, 53, 58, 65, 66, 71, 82)

Score 7.0.

Works: large title, filter chips, a tidy 3-column grid of 3:4 tiles with the name overlaid in dark (full-bleed art, bottom
gradient); no jumping row heights (the redesign goal for two-line names is met); chips scroll horizontally.

Defects:
- Three chip styles exist in the app: Browse chips are outlined with a tonal selected state (07, about 32 dp high), Search chips
  are filled pills with an inverted selected state (white on dark, 14), Category language chips are filled with an inverted
  selected state and a plain "Language" label that reads as a chip (11). One component should serve all three.
- Chips are about 32 dp high. The visual size is fine; verify the hit area is padded to 48 dp.
- The last chip is cut mid-word at the edge (07), which signals scroll, but there is no trailing fade and "Sort" is hidden.
- Filtered states (08, 09, 10) leave 40 to 70 % of the page empty with no footer or "more" hint; with only two Music tiles
  (10) the grid looks abandoned.
- Light (58) changes the card construction: the name moves out of the art into a separate light caption strip below a shorter
  tile; dark overlays the name on the art. The same screen thus has two compositions. Pick one.
- 1.5x (65): names truncate ("Pixel Ra...", "Kestrel ..."); 2.0x (66): "Valmo...", "Pixel ...", "Discu...", "Musiq...". The INDEX flags this correctly; at 2.0x the grid should drop to 2 columns, and a two-line name should be allowed.
- Wide (71): a 4-column grid with a 100 dp left rail and good use of width, but the captions are tiny and the chip row has
  an unlabeled "Sort: popularity" chip that appears only on wide.
- Arabic (53): mirrored correctly; first chip row is clipped at the left edge by design.

To reach 9.5: use real box art at 3:4 with a 1 line title and the viewer count beneath ("14.8K watching"), one chip component, a
sticky chip row, 2 columns at font scale above 1.3, no empty tails (show "All categories" as the default footer).

### 2.5 Category (11, 72)

Score 5.0.

Works: back, title, follow heart; list rows reuse the stream card.

Defects:
- No category identity: no box art, viewer total, tags or follower count, just a title next to a back arrow. A category page on
  Twitch or YouTube Gaming leads with art.
- The page title starts at x = 111 while content starts at x = 32, so the title is neither aligned to the gutter nor to the back
  arrow; the heart sits at x = 755 and is about 24 dp with no label.
- "Language" is a text label with two chips; the label (low-contrast grey) reads as a third chip.
- Two items fill 20 % of the screen; the rest is empty (11) with no hint, no related categories.
- The bottom bar is absent here but present on Home; reasonable for a pushed page, but then the strip floats at the bottom with
  nothing under it (see 41).
- Wide (72): content hugs the screen edge at 9 dp; the gutters differ from every other wide page (about 50 dp); no rail.

To reach 9.5: header with art, name, viewer count, Follow button; chips row beneath; consistent gutters; empty-tail state.

### 2.6 Search (13 to 19, 30, 63, 67-search)

Score 6.5.

Works: pill morphs to a full-width field with back arrow and mic; keyboard action is a magnifier; "Live now" suggestions on
empty; recents with Clear all and per-item X; filter chips All/Channels/Categories.

Defects:
- Result rows (14) have only avatar, name, a LIVE badge and "22.9K viewers". There is no stream title, no category, no
  Following state. The avatar has a thick red ring (about 3 dp) and a red LIVE chip beside it: the same signal twice, and the
  ring on dark purple has weak contrast with the fill.
- The red ring is much heavier than the Instagram/Twitch live ring (about 2 dp gradient). 
- "No results" (19) is one line of text, no echo of the query, no suggestion, no filter chips (they vanish in 19 but remain in
  the Categories filter, 15), and it sits at the vertical centre of a screen with the keyboard up, so it ends up high on the
  page.
- The Categories filter (15) shows "No results" for "lun", which is correct, but the empty state is identical to the
  all-states case.
- 17b is captioned "search after keyboard hidden" but the capture is the Home screen; either the keyboard dismiss/Back leaves
  Search (a flow bug) or the capture is mislabeled. Check the Back behaviour: first Back should only hide the keyboard.
- 12 (the pill morph) was not captured in motion.
- 30 (strip over Search): the strip is shown without a bottom bar (the Search page hides the bar). The strip floats directly
  above the gesture pill; fine, but it should have a bottom inset to match the bar-visible case.

Large font (67-search): readable, no overlap; magnifier and mic icons stay small relative to the text.

To reach 9.5: richer rows (avatar with a 2 dp ring, name, stream title, category), a real empty state with illustration and the
query echoed ("No results for 'zzqq'"), Retry-style suggestions, and consistent chips.

### 2.7 Channel (41, 44, 83)

Score 5.0.

Works: avatar, name, Following state, replays list with duration chips; "Similar" row.

Defects:
- The top 56 dp is a lone back arrow on empty background; the name and avatar start 36 dp lower. No banner, no gradient, no
  identity.
- The name is about 32 sp regular weight: too light and too large relative to the rest of the app.
- "Following" is a pill with a trailing open-in-new glyph (41, 83). It is not clear whether the pill unfollows, opens Twitch, or
  manages notifications; icon inside a state label confuses.
- The live area is a full-width 16:9 placeholder card (about 140 dp high) and, below it, a full-width lavender "Watch" button.
  Two controls do the same thing; the card takes a third of the screen without showing the stream.
- Missing: follower count, bio, tabs (Videos, Clips, About), schedule, notification bell, share.
- Replays: the metadata line mixes relative ("Yesterday") and absolute ("Nov 4, 2026") dates (44). Thumbnails have no watched
  progress bar. Item titles are one line only.
- Similar row (44): names truncated ("Cuisine d'...") and centre-aligned; no visible scroll affordance.
- The strip overlaps the replays with a fixed bottom padding (41); the last row clips underneath.

To reach 9.5: collapsing app bar with banner (or blurred avatar), 24 sp semibold name, verified badge, follower count, one primary
action (Follow) and a bell; the live card is the Watch action (drop the button) or the live thumbnail plays a muted preview;
tabs for Videos, Clips, About; progress bars on replays.

### 2.8 Player portrait: live (20, 21, 25, 40, 54, 55, 61, 68)

Score 7.0.

Works:
- Video at the top, flush to the status bar, with a clear black inset; chat below; compact 52 dp input pill delivering the
  "chat input too big" request; the streamer row is compact; controls overlay is tidy.
- Chat is dense but legible; mentions are highlighted with a tint plus left rule; subscription and gift events are cards; reply
  context is shown above the message.

Defects:
- The header row (avatar, name, category, open-in-new icon, 20) has no title, no viewer count and no Follow. The stream title
  and viewer count appear only when the controls are visible (21). A viewer who never touches the screen cannot see what they
  are watching.
- The open-in-new glyph at the right of the header is ambiguous (open the channel? open in the browser?).
- The first chat line is cut mid-glyph under the header hairline (20, 24, 40), and the older lines fade to low alpha
  (Gaspard at about 3:1). A top gradient mask would look intentional; a mid-glyph clip does not.
- The reply preview ("In reply to ClaraPlays: Quelqu'un sait quelle musique est e...") is bold, grey and about the same size as
  the message; it competes with the message. It should be 12 sp regular at 70 % alpha.
- Row rhythm is uneven: the vertical gap between rows changes with emote height (about 8 px to 24 px), so the list looks
  jittery (20, 40).
- Controls (21): chevron-down (minimise) top-left, PiP, and a chat bubble top-right (what does it do when chat is already shown?),
  centre pause, a bottom row of LIVE, viewers, volume, quality gear, sleep timer stopwatch, fullscreen. The sleep timer is
  given the same weight as volume. Title over video relies on a weak scrim (white 14 sp over colourful frames is below 3:1 in
  the mid band).
- The 64 dp centre button on a dark circle is fine, but the live view has no "back to live" control (not applicable for live).
- 25 and 40 show the re-expanded state; layout is stable. 
- Light (61): status bar area is black while the page is light; acceptable for video, but the transition from a black bar to a
  light header is hard. Chat nick colours are darkened for light, but several still look under 4.5:1 (ClaraPlays pink, petitpois
  teal at roughly 3 to 3.5:1). Placeholder "Send a message" on the light pill is about 4:1.
- Large font (68): the video-to-chat ratio collapses to about six lines of chat, header and input pill grow to about 56 dp each;
  title in controls truncates; the LIVE and viewer chips become 56 dp.
- Arabic (54, 55): chat and controls mirror correctly. The "Marlou ... pour 6 mois !" notice puts the "!" at the start
  (bidi isolation issue, as in 2.1). The mini controls mirror, but the chevron-down moves to the right, which is correct.
- Wide (73): same portrait layout stretched to 810 dp, video not enlarged, a large dead area under the chat. A tablet needs
  a side-by-side layout (video left, chat right, as the landscape mode already shows).

To reach 9.5: title row (title 16 sp semibold, 2 lines, then channel and viewer count) under the video, Follow button in the
header, share and clip actions; consistent 6 dp row gap; reply line 12 sp; a top fade mask; single "chat settings" icon;
side-by-side at 600 dp.

### 2.9 Player replay (42, 43)

Score 5.5.

Defects:
- Title "Session du mardi soir" is about 28 sp regular, much larger and lighter than the live player's 20 sp; typographic
  jump (42).
- Metadata "0:12 . 3.6K views . Yesterday": "0:12" is the clip length from the demo data (the list says 3:12:40); in real data
  the duration format must match.
- Playback speed: a label plus six chips; "2x" wraps alone onto a second row (42, 43). Speed is also reachable from the gear
  (probably) so this duplicates. At 32 dp the chips are small targets.
- The seek bar (43) shows two thumbs (a vertical pill at 0:05 and a dot at the end); the buffered region draws as a second
  bar. A single thumb and a lighter buffer line are needed. The bar spans only 40 % of the width because time labels, timer,
  gear and fullscreen share its row.
- -10 and +10 icons are fine; the pause button matches the live one.
- The page has no chat replay and no description; "Open channel" tonal button is the only action.

To reach 9.5: 18 sp semibold title, single-line metadata, speed in a sheet from the gear only, a full-width seek bar above the
buttons row with 1 thumb, "Up next" list with progress marks.

### 2.10 Chat: input, emote picker, reply (22, 22b, 23, 24)

Score 7.0.

Works: the compact input; emoji button; send appears only with text; reply bar with purple label, quoted text and close; the
@name prefill; the picker has search and provider tabs.

Defects:
- The picker (22b, 23) shows two stacked pill fields (message input and "Search emotes") with the same shape and fill; it is
  easy to type in the wrong one.
- Provider tabs (Twitch, 7TV, BTTV, FFZ) are text only, with the selected one in purple and no underline or pill, so the
  selected state is weak (about 3:1 purple on near black) and the touch targets are about 24 dp wide.
- A "Recent" tab appears after the first insert and pushes the others right (22b vs 23): a layout shift.
- Section headings ("Channel", "Subscribe to use these") are 13 sp grey semibold; the locked emotes show a lock badge and are dimmed (good) but the emote grid has no consistent cell size.
- The inserted emote appears as the raw token `demoHeart` in the input (23); fine for Twitch, but a visual chip would be
  friendlier.
- The reply bar's text is two lines at 12 and 14 sp; strong, but it extends to the screen edges while the input keeps a 16 dp
  inset (24).
- The emoji button turns into a keyboard glyph while the picker is open (22): good.

To reach 9.5: one input; picker as a bottom panel with a drag handle, search icon inside the tab row, pill tabs, sticky
section headers, no layout shift.

### 2.11 Landscape (26 to 29)

Score 6.0.

Works: three clear modes (side panel, overlay, hidden); controls follow cutout insets; the side panel has a message input and the
overlay has a rounded scrim.

Defects:
- 26: the chat text is large relative to portrait (about 18 sp) and event cards wrap to 3 lines. The input pill hugs the right
  edge without an inset.
- 27: the two-line title is placed at y = 30 to 80, straddling the video top edge and the black letterbox; half of the text is
  on the picture and half on black. Pin it inside the picture or lay it fully in the bar.
- 28, 28b: the overlay card clips its first line mid-glyph ("quelle musique est en fond ?", "ici, c'est super bien fait"); no
  fade at the top; no input is available in this mode. The card colour takes on a green/red cast from the video; the nick
  colours on saturated video drop below 3:1 (Gaspard green over green).
- 29: with chat hidden, nothing hints how to bring it back and the picture fills the screen with 180 px side bars.

To reach 9.5: font scale for the side panel, top fade on the overlay, an overlay input via tap, controls title inside the
picture, a chat hint toast.

### 2.12 Bottom sheets (31, 32)

Score 6.0.

Works: M3 modal sheet with a handle, selection check, left-aligned 16 sp options, 47 dp rows.

Defects:
- 31: behind the sheet the portrait chat area reads "Chat hidden" in large type, partly clipped (y = 1165), so a hidden-chat
  state exists in portrait but has no good empty design.
- The quality sheet has no current-value hint ("Auto (720p60)"), no bitrate hints.
- 32: the list ("15 minutes ... 1 hour 30 minutes") is cut off at the bottom; no "Off"/"End of stream" option is visible and
  there is no scroll cue. A countdown when active is not shown.
- Both sheets use the stock M3 look (flat list, no icon), unlike the custom chrome elsewhere.

To reach 9.5: header with icon and subtitle, segmented "Auto / 720p / 480p / Audio", a visible Off state, 56 dp rows.

### 2.13 Mini player strip and drag (30, 34 to 37, 38, 39, 62, 69, 75)

Score 6.0.

Works: the docked strip above the bar is predictable; it shows live video as a thumbnail, title and subtitle, a pause/play and a
close button; the same strip is shown on every tab (34 to 37, 30); light and dark both work.

Defects:
- 38 (mid-drag): the video is a 665 x 445 px rectangle pinned to the left with a black bar above (72 px) and a 145 px gap to the
  right where the page shows through; the bottom bar is missing and the info/chat below keeps its full width. If this is what a
  user sees during the gesture, it looks broken and not like YouTube's proportional shrink. Verify with a screen recording.
- The strip is 56 dp with a flat surface, a square thumbnail flush with the left edge, no corner radius, no progress or LIVE
  indicator, and a two-line text that truncates ("dég..."). It has no top hairline, so on light (62, 75) it blends with the bar
  (strip surface about #E7E5EE vs bar #FAFAFD, under 1.2:1).
- The first row under the strip is cut with no extra bottom padding (34, 36, 41).
- Close is next to pause with about 8 dp between targets: a mis-tap closes the stream.
- 2.0x (69): the strip grows to about 88 dp and hides one more row; the subtitle is dropped. Cap the strip at 64 dp and let the title use 1 line.
- 39: after the drag the strip is paused (play glyph); fine, but the state should read as "paused" (dimmed).

To reach 9.5: 8 dp corner radius and inset 8 dp from the edges (floating look) or a full-width strip with a 2 dp progress/live line;
a fade between thumbnail and text; separate pause and close by at least 16 dp; swipe-to-dismiss; proportional transform in
the drag.

### 2.14 Settings and dialogs (45 to 51, 56, 75b)

Score 6.5.

Works: grouped rounded cards with section labels, profile card with Sign out, M3 switches, sensible copy in the Discover
previews note, dialog options with radio buttons, native language names.

Defects:
- Only the first row of each card has a leading icon tile (45, 47): the rows below have a blank 56 dp gutter, so the lists look
  unfinished and the rows are visually indented for no reason. Either give each row an icon or none.
- The icon tiles use a dark purple fill with a white glyph; the tile is about 1.4:1 against the card in dark, so they read as
  blobs.
- 25+ rows in one scroll; the seven chat settings (Chat display, Text size, Spacing, Background, Badges, Time, Animated emotes,
  Animation limit) belong in a Chat screen with a live preview. "Follow raids" and "Playback stats" are unexplained.
- Typography: body text in Settings and dialogs appears tracked out (M3 default 0.5 sp), while Home uses tight tracking; the two
  sections feel like two apps.
- Dialogs: "Chat display" (46) and "Theme" (49) have about 90 px of dead space under the options and a lone Cancel; the
  options say "Compact chat / Chat over video", which differs from landscape's "side panel / overlay / hidden" vocabulary.
  "Sign out of Camille?" (75b) is narrower (550 px vs 630 px) than the other dialogs and has no body text or destructive
  colour.
- Language dialog (50) is a single tall scroll with no search and no scrollbar.
- Light (56): works; card/background separation is about 1.1:1 so cards rely on shape; Off switch outline is fine.
- Arabic (51): fully mirrored, chevrons point the correct way; the "% 80" order is right for Arabic.
- The signed-in header card shows "Camille / Signed in" with a Sign out outlined button on the same row; reasonable.

To reach 9.5: Chat sub-screen with preview, consistent icon gutters, one dialog width, a destructive-styled sign-out confirmation
with a one-line body, language search.

### 2.15 Sign-in, signed-out, offline (78 to 82, 79, 83)

Score 3.5.

Defects:
- "Not connected / Sign in with Twitch to see the channels you follow." is used for Home (78), Browse (80), Discover (81) and
  Browse offline (82). "Not connected" means a network error to most users, but the cause is a signed-out state; and "channels
  you follow" is wrong on Browse and Discover.
- 81 and 82 were captured with the signal icon crossed out, but show the same signed-out screen, so there is no real offline
  or error state, no Retry, and no cached content.
- If Browse and Discover genuinely require a viewer token, say so ("Sign in to browse live channels"); if not, show public
  content. Locking public browse behind sign-in is not the TikTok/YouTube experience.
- Tab positions move when signed out (3 tabs, 78 vs 4 tabs, 01): Discover shifts from x = 304 to 405 and Browse from x = 709 to
  625, breaking the "icon-only bars need fixed positions" rule the redesign cites from Instagram. Keep the Following tab and show
  its own signed-out CTA.
- Layout: title at about 24 sp regular, body at 16 sp, a lavender pill CTA with dark text (good contrast); nothing else. No
  illustration, no secondary "continue as guest".
- Sign-in dialog (79): step copy is clear, a full-width CTA and a copy button are good, a countdown bar is a nice touch; the
  code field is unreadable in the capture (redacted) so its size cannot be judged; the code should be 32 sp mono with letter
  spacing. Dead space (about 80 px) between expiry and Cancel.
- Offline channel (83): "Offline" under the name, Following pill, replays; fine. No "notify me when live".

To reach 9.5: one distinct signed-out state per tab, a real offline state ("You are offline" with Retry and cached rows), fixed tab
positions.

### 2.16 Wide layout (70 to 73)

Score 4.0.

Defects: Home (70) is a single column of full-width 16:9 hero cards (about 190 dp tall each), one stream per screen with a tiny
caption; a 100 dp rail is correct but isolated. A 2 or 3 column grid is needed. Category (72) loses the rail and has 9 dp edge
gutters. The player (73) is the phone layout stretched, with large dead space under the chat. Rail items are top-aligned and
unlabelled with a thin 3 dp dot under the pill.

### 2.17 PiP (74)

Not rated. The capture shows the launcher with a small picture; the window has no visible controls, but that cannot be seen
here. PiP should expose play/pause and mute remote actions.

---

## 3. Cross-cutting findings

**Design system.**
- Three chip styles (Browse outlined/tonal, Search filled/inverted, Category filled/inverted, 07, 14, 11), three header systems
  (Home section title, Following none, Browse large title), two dialog widths, two card compositions between dark and light
  (07 vs 58), two seek bars. The tokens table in `phone-redesign.md` is a good start; enforce it with shared components
  (`PhoneChip`, `PhoneHeader`, `PhoneDialog`).
- Colour: a purple primary (lavender in dark), a Twitch-red LIVE, and a dark purple tile used for icons and avatars. The red
  appears 7 to 14 times per screen; it is the loudest element everywhere. Reduce it.
- Type: one family (Roboto), medium contrast. Titles are 14 to 28 sp across screens with no clear ladder (Channel name 32,
  replay title 28, Browse 28, Home section 20, player title 20 and 16). Define a five-step scale and apply it.
- Elevation: cards are about 1.1:1 against the page, so grouping depends on rounded shape only. Fine in dark; thin in light.

**Bottom bar.** Icon-only with a pill and a dot under the active icon (two indicators for one state). Fixed order when signed in;
changes when signed out. The Discover icon (play in a circle) is repeated by the Discover rail's "play" action and the strip's
play button. The bar hides on pushed pages (Category, Channel, Search, Player). Add a badge dot for live follows on the
Following tab.

**Headers.** The search pill header is on Home, Following and Browse (good), but the title treatment differs. Pushed pages
(Category, Channel) have a bare back arrow at 56 dp with the content starting 36 dp below. Titles in pushed headers are
off-gutter (11).

**Cards.** One row card for streams (good), a tile card for categories, a hero card on wide. The LIVE badge plus the viewer chip
is redundant. Thumbnails 10 dp radius (matches the plan). No watched progress, no category chips.

**Chips.** See above; also not 48 dp targets as drawn.

**Chat.** The richest part of the app: badges, highlights, events, replies, emotes. Needs one rhythm (6 dp), calmer reply
context, a top fade, a minimum contrast for nick colours on the chosen background (compute and clamp to 4.5:1 per theme and
per overlay scrim), and a size scale per mode (landscape is too large).

**Player chrome.** Reasonable, but too many equal-weight icons (PiP, chat, volume, quality, sleep timer, fullscreen). Move
volume and sleep timer into the quality/settings sheet; keep PiP, fullscreen, and a single settings gear.

**Empty, error and loading states.** Clips (03), Search no-results (19), signed-out (78 to 82) and the hidden-chat state (31)
are each plain text. No skeleton or error-with-retry was captured. Build one `EmptyState` component (icon, title, one line,
optional action).

**Large font.** 1.5x is acceptable; 2.0x loses meaning in Browse and cuts titles. The unscaled icons next to scaled text look
off (search pill). Cap the strip height, allow two lines, drop to two columns.

**RTL.** Mirroring is thorough (bar, rows, chat, controls, chevrons). Only bidi punctuation on user text needs fixing.

**Light/dark parity.** Works, but the Discover page is black in both themes and the status bar is black over the player in
light; card composition and strip contrast differ.

**Touch targets.** Header avatar (about 28 dp visible), chips (about 32 dp), emote tabs (about 24 dp wide) and the
strip's pause/close pair are the items to verify at 48 dp.

**Iconography.** Mix of filled and outlined Material icons; open-in-new used for four different meanings (player header,
Discover rail, Following pill, sign-in button). Pick distinct icons.

**Copy.** "Not connected" (misleading), "Follow raids" (unexplained), "Compact chat/Chat over video" vs "side panel/overlay",
"Offline" repeated 3 times in a list, "No clips to show right now." Tighten and unify.

---

## 4. Ranked fix list

Highest impact first. Each item lists the affected screens.

1. **Remove the Home/Following duplicate.** Give Home its own content (hero stream, recommended, continue watching) or merge it
   into Discover; make Following distinct (Live-now avatar row plus offline with "last live"). Home 01, Following 05/06, tab bar.
2. **Fix signed-out and offline.** Per-tab signed-out copy ("Sign in to see who you follow", "Sign in to browse"), a real offline
   state with Retry, keep four tab positions, make public content available if the API allows it. 78, 80, 81, 82.
3. **Rebuild Discover Live layout.** Full-bleed crop or card layout, bottom scrim, labelled 40 dp rail, aligned viewer count,
   Follow and share, pager hint; fix the badge/tab baseline. 02, 04, 35, 59.
4. **Give every empty/error state a component.** Icon, title, one line, optional action. Clips 03, no results 19, hidden chat
   31, wide 72, offline 81/82.
5. **Add a title block under the player.** Title, channel, viewer count, Follow; remove the dependence on the controls overlay
   for essential info; replace the ambiguous open-in-new icon. 20, 21, 25, 40, 61.
6. **Channel page identity.** Collapsing banner, 24 sp semibold name, follower count, one Follow action plus bell, remove the
   duplicate Watch, tabs for Videos/Clips/About, progress on replays, fix the Following pill icon. 41, 44, 83.
7. **Unify chips, headers, dialogs and card composition** into shared components; one header system and one dialog width; same
   category card structure in light and dark. 07, 11, 14, 45, 49, 58, 75b.
8. **Fix the drag transform.** Video, info and chat must scale and translate together; keep the bar visible; verify with a
   recording. 38.
9. **Large-font behaviour.** Browse to 2 columns above 1.3x scale, allow two-line titles, cap the strip at 64 dp, make header
   icons scale with text, fix the clipped avatar letter. 64, 65, 66, 67, 68, 69.
10. **Wide layout.** 2 to 3 column Home grid; side-by-side player (video left, chat right) at 600 dp; consistent gutters and a
    rail on pushed pages. 70, 72, 73.
11. **Category page header** with art, viewer total and Follow; consistent gutter for the title. 11, 72.
12. **Reduce red.** Drop LIVE badges on all-live lists; thin the Search ring to 2 dp; soften red dots. 01, 05, 14, 41.
13. **Chat polish.** Uniform 6 dp rhythm, calmer reply line (12 sp regular), top fade masks (also in the landscape overlay),
    nick-colour contrast clamp at 4.5:1 per theme and per scrim, smaller landscape type. 20, 28, 28b, 61.
14. **Mini strip refinement.** Corner radius or progress line, 16 dp between pause and close, light-theme separation, bottom
    padding under lists. 34, 36, 41, 62, 75.
15. **Replay player.** Match live type scale, speed chips into the gear sheet, single-thumb seek bar. 42, 43.
16. **Bidi isolation** for user text (titles, system notices) in RTL. 52, 54, 55.
17. **Settings restructure.** Chat sub-screen with live preview, consistent icon gutters, unify tracking, dialog padding and
    vocabulary (side panel/overlay/hidden everywhere). 45, 46, 47, 48.
18. **Emote picker.** One input only, pill tabs, no layout shift, sticky headers. 22, 22b, 23.
19. **Landscape.** Title inside the picture, chat font scale, overlay top fade and tap-to-reply, hint when chat is hidden. 26,
    27, 28, 29.
20. **Controls diet.** Move volume and sleep timer into the settings sheet; one gear. 21, 31, 32.
21. **Search results.** Add stream title and category, unify the live ring, query echo in no-results. 14, 16, 19.
22. **Bottom bar.** Pick one active indicator (pill or dot), add a live badge on Following, keep the order fixed. All.
23. **Sheets.** Current-value subtitle, visible Off and countdown for the sleep timer, scroll cue. 31, 32.
24. **Touch targets.** Verify and pad chips, emote tabs, header avatar. 07, 11, 22b.
25. **Capture/test gaps.** Recapture 17b (flow bug or mislabel), 35 (colour inversion), 12, 38 (motion) and add clip player,
    loading skeleton, error with Retry, real thumbnails and box art. Contrast numbers above should be re-measured with a tool.
