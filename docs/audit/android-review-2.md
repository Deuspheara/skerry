# Android phone screen review 2

Independent, read-only re-review of the Android phone app (`:phone`, Pixel 8 emulator, demo data) after the review-1 fixes. Inputs: `android-review-1.md`
(overall 5.9), `android-fix-plan.md` (the engineer's claims), every file in `docs/screenshots/audit/android-2/` (41 PNGs plus INDEX), and, for the states
android-2 does not cover, these round-1 frames from `docs/screenshots/audit/android/`: 12, 22, 26, 27, 28, 29, 38, 41, 42, 43, 72, 73, 74, 75b, 79, 83.
Nothing was built or run. Pixel sizes are from the 810 px wide files (1 dp is about 2 px). Contrast figures are estimates.

Same scale as round 1: 9.5 = best-in-class, shippable. Most shipping apps sit at 6 to 8.

## 1. Score table

"Evidence" says whether the score rests on a new capture or on a round-1 frame the engineer did not retake. A held score on an old frame is not a vote that
nothing changed; it is a refusal to credit a claim I cannot see.

| Screen | R1 | R2 | Delta | Evidence | Verdict |
|---|---|---|---|---|---|
| Home | 6.5 | 7.3 | +0.8 | new (01, 30) | Hero and two-line titles are real; still a single visual idea, nothing past the fold shown |
| Discover Live | 6.0 | 6.5 | +0.5 | new (03) | Card and labelled actions fix the rail; 45 % of the page is black void |
| Discover Clips | 3.5 | 6.5 | +3.0 | new (04) | Now a proper empty state; the weakest idea is a "Retry" on an empty list |
| Following | 6.0 | 7.5 | +1.5 | new (02, 18, 31) | Distinct from Home now; live-now ring row repeats the list under it |
| Browse | 7.0 | 7.5 | +0.5 | new (05, 32) | Unchanged strength; light card still built differently from dark |
| Category | 5.0 | 6.0 | +1.0 | new (06) | Header with art and viewers; no Follow button, 55 % empty, label reads as a chip |
| Search | 6.5 | 7.0 | +0.5 | new (07-09) | No-results state is good; result rows are still thin; ring and LIVE chip both red |
| Channel | 5.0 | 5.0 | 0.0 | old (41, 44, 83) | Claimed rebuilt, never recaptured; round-1 frames still show every round-1 defect |
| Player portrait live | 7.0 | 7.3 | +0.3 | new (10, 11, 33) | Title block added; hierarchy inverted; duplicated viewer count; light capture is mid-fade |
| Player replay | 5.5 | 5.5 | 0.0 | old (42, 43) | Claimed partly fixed; the frames still show 28 sp title, wrapped chips, 2 s seek bar |
| Chat input / emote picker / reply | 7.0 | 7.0 | 0.0 | old (22) | Not touched, correctly said so |
| Landscape (3 modes) | 6.0 | 6.0 | 0.0 | old (26-29) | Not recaptured; title still straddles the letterbox |
| Bottom sheets | 6.0 | 6.5 | +0.5 | new (12, 13) | One merged sheet; "value in use" claim only half true |
| Mini strip and drag | 6.0 | 6.3 | +0.3 | new strip (14, 34), old drag (38) | Hairline and spacing are there; still square, flush, no progress; drag untouched |
| Settings | 6.5 | 7.5 | +1.0 | new (15-17, 35) | Best-looking list in the app now; still 25 rows, no Chat sub-screen |
| Dialogs (sign-out, sign-in, theme, language) | 6.0 | 6.0 | 0.0 | old (75b, 79) | Claimed rebuilt; the only frames are round 1 and show the old state |
| Signed-out, error, loading | 3.5 | 7.5 | +4.0 | new (00, 40-45) | Biggest win; one component, fixed bar, per-tab copy, Retry |
| Wide layout | 4.0 | 4.5 | +0.5 | new Home (70), old rest (72, 73) | Home is decent; player and category unchanged and weak |
| Arabic / RTL | 7.5 | 7.0 | -0.5 | new (50-52) | Home mirrors well; 51 is a duplicate of 50 and 52 is a player; new bidi and glyph bugs |
| Large font | 6.0 | 7.0 | +1.0 | new (20-22) | 2-col Browse and a capped strip work; 1.5x Home is a skeleton, no player/settings/search |
| Light parity | 6.5 | 7.0 | +0.5 | new (30-35) | Mostly consistent; Browse card, ghosted player |
| PiP | n/r | n/r | n/a | old (74) | Still a launcher screenshot |

**Overall: 6.9 / 10 (+1.0).** The engineer's own self-score (Home 7.5, Following 7.5, Discover 7, Browse 8, Category 7.5, Search 7, Channel 6.5, Player 7.5,
Settings 7.5, error 8) averages about 7.4; I land 0.5 below it, and the gap is almost entirely over-claiming and unverified screens, listed in section 3.

The overall is a weighted mean (primary surfaces Home, Following, Discover, Browse, Player, Search, Settings count double). Strict 9.5 is not near: the app
now has a coherent system, but it is still demo art in grey cards, three large screens were never re-shot, and the first impression (Home, Discover) is
bigger than it is rich.

---

## 2. Screen by screen

### 2.1 Home (01, 30, 20-home-font20, 50, 70)  7.3 (+0.8)

Verified fixes:
- Hero stream card (01: 748 x 420 px, avatar, bold title, "Lunaria . Valmoor", viewer chip top-right) makes Home different from Following. Real.
- Titles now take two lines and the text block is centred on the thumbnail (01). The red LIVE rectangles are gone; only the dot + count chip stays. Real.
- Avatar and search icon scale correctly at 2.0x (20-home-font20); the clipped "C" is fixed.
- Light (30) keeps the same structure; the hero stays dark in light, which is the right call.
- Skeleton (00) matches the final row geometry (same thumbnail size and two bars), so the load-in will not jump.

Remaining and new defects:
- 01: the hero overlays the title directly on busy art. The gradient is only about 140 px tall, and the purple block under "Valmoor" runs through the text
  (01, 30). With real thumbnails this needs a deeper scrim (60 % black, 45 % of the card height).
- 01: the hero has a viewer chip but no LIVE state, category chip or "Watch" affordance; it reads as a banner, not a tappable stream. The whole card is the
  target, but nothing says so.
- 01: the hero's avatar (L) and the list's avatar (M) are the same size cue repeated; a hero should use the channel avatar at 40 dp and the row at 28 dp.
- 01: the fix plan promises "recommendations and categories" after the followed lives. No frame shows them; every Home frame ends at the strip or the bar.
  Unverified claim.
- 20-home-font15-dark.png is the loading skeleton, not Home at 1.5x. Home at 1.5x is therefore not evidenced.
- 20-home-font20: the two-line title is truncated with an ellipsis ("Ouverture sicilienne, on a...") while the channel name and category wrap onto two lines
  each; the row grows to 280 px and the thumbnail is top-aligned with 90 px dead space under it. At accessibility sizes, drop the category from the row and let
  the title have three lines.
- Section label "Live from your follows" is 20 sp regular (01); Browse and Following use a 28 sp bold page title. The plan says one header system; this is the
  one place it is not true.
- Contrast: the viewer count chip is white on near-black over art: fine. The category line (grey on near-black, about 4.6:1 in dark) is fine; in light the
  category grey on #FAFAFD is about 4.3:1, borderline for 14 sp.
- No long-press, no overflow on rows (unchanged from round 1).

Fixes to 9.5:
1. Hero: 16:9, deeper scrim, a LIVE dot plus category chip, a visible "Watch" label or play glyph, and a pager of 3 hero streams with an auto-advancing indicator.
2. Add a "Continue watching" row (replays) above the followed list, a "Recommended" row of 16:9 cards, and a categories chip row; capture them.
3. One 28 sp bold page title system or none on every tab; the Home section label needs the same treatment as Following's "Live now".
4. At font scale above 1.3, drop the category line and the avatar from the row; allow three title lines; align thumbnail and text top.
5. Retake 1.5x Home with data.

### 2.2 Discover Live (03) and Clips (04)  6.5 (+0.5) and 6.5 (+3.0)

Verified fixes:
- Labelled actions under the picture ("Sound off", "Chat", "Channel", "Watch") replace the unlabelled rail. Real and clear.
- Name, category and viewer count are now one row with the avatar; the viewer count is right-aligned. Real.
- "Swipe up for the next stream" pager hint at the bottom (03). Real.
- Clips (04): icon disc, title, one-line reason, Retry. The "dead black page" is gone.

Remaining and new defects:
- 03: the picture is 16:9 at the top and everything below y = 940 is black. That is 48 % of the screen empty, which is the reverse of the "TikTok" idea the
  tab exists for. Moving the card to the vertical middle or filling the page with the stream (cropped) is the single biggest Discover fix. The engineer
  labelled this "not a full-bleed crop"; it is a card in a void.
- 03: the picture has 12 dp side insets and 16 dp corner radius; the card above it ("Live / Clips" tabs, y = 155) sits 40 px above the picture with a faint
  hairline artefact (a dithered band at y = 180). Probably an encoder artefact of the 128 colour capture, but check it.
- 03: the title line ("Run nocturne : boss final sans degats") is the same weight as the category; a card needs a 20 sp title and 14 sp meta.
- 03: no Follow control, no share. "Channel" uses an info glyph (circle with an i), which reads as "information", not "open channel". The planned icon is a
  person or the avatar.
- 03: the "Sound off" circle shows a struck speaker as its default; it is a state in a label, which is clear now, but the button is the same weight as the
  other three, so muted-by-default feels like an error.
- 03: no chat preview at all; the round-1 chat peek was dropped with the full-bleed layout. The "Chat" action is the only trace.
- 04: "No clips to show right now" with a Retry button is the right form for an error, not an empty list. For an empty list the action is "Browse live
  streams" (go to the Live tab). The body says "Popular clips will appear here. Check back in a moment." which is not an explanation.
- 04: the content is centred slightly above the optical middle and the page has no card; fine.
- Live/Clips tabs: 14 sp, 2 px underline, positioned centred. They do not share a style with the bottom bar's pill indicator; fine, but their hit area is
  about 32 dp high.
- Signed-out Discover (41) keeps the Live/Clips tabs above a sign-in prompt that offers neither; hide them or disable them.

Fixes to 9.5: fill the page (stream cropped to 9:16 with a scrim, name and actions overlaid, a vertical pager with dots), keep the labelled actions on a
scrim, add Follow and a person icon for "Channel", chat peek in the lower third, clips as a vertical pager with the same card, an empty Clips state that
offers "Go to Live".

### 2.3 Following (02, 18, 31, 22-font15/20)  7.5 (+1.5)

Verified fixes:
- Page title "Following" at 28 sp bold, a "Live now" subheading, a row of ringed avatars, then live rows. This is the Instagram-stories pattern the review asked for.
- Offline section: dimmed avatars, one indent, no repeated "Offline" line (18). The section header is now a 20 sp title. Real.
- Light (31) is the same layout; rings and avatars hold contrast.

Remaining and new defects:
- 02, 31: the avatar row shows Lunaria, MaitreKoi, Cuisine d'Ines, DJ Oriel, Pixel M...; the live list directly underneath lists the same five channels in the
  same order. Two consecutive blocks carry the same information. A stories row earns its place only when the list below is different (for example, the list
  could be "Recommended from your follows" or the row could carry unwatched-clip counts).
- 02: rings are 3 px pure red (#FF0000) on #3A1A8A discs. Red appears here, in the viewer chips and in the Search ring: the plan says "red is for
  information", but the ring is decorative on a list where everything is live. A purple ring or a small red dot at the avatar's corner would calm it.
- 02: labels are 14 sp bold white; "Cuisine d'In..." truncates at 1.0x. 1.5x/2.0x show "Maitre...", "Cuisin...", "DJ O..." and "Luna..." (22). The avatars stay
  at 62 dp in all sizes, so at 2.0x the labels are more than the avatars.
- 18: the offline list has 126 px rows with only a name, no "last live 2 days ago", no bell, no last-category; 3 rows leave 90 px of dead area above the strip.
  The plan promised "last live when known"; the demo data may not have it, but then no frame proves the line exists.
- 18: the "Offline" header is 20 sp regular and the live rows' title weight is 500: the hierarchy is OK, but the header-to-row gap (50 px) is bigger than
  the live section's (35 px).
- No sort, no notification bell, no "see all" for the live row.

Fixes to 9.5: make the stories row a different list from the rows (or drop the rows' duplicates), purple ring plus a corner dot, truncation-proof labels
(two lines at accessibility sizes), "Last live 2 days ago" and a bell on offline rows with a 72 dp row, a sort menu, retake 18 with data that exercises
every line.

### 2.4 Browse (05, 21-font15/20, 32, 51)  7.5 (+0.5)

Verified fixes:
- 2.0x: two columns, name at 18 sp on the art (21-font20). Real, and the chip row stays scrollable.
- Chip component is shared with Search and Category (white fill for the selected state in dark, black fill in light). Real; 05 and 08 now match.
- Page title and chip row top spacing aligned with Following.

Remaining and new defects:
- 32 (light): each tile is art above a separate caption strip, the caption sits on a noisy stipple (dithering in the 128 colour file, but the strip is
  clearly a different surface from the art). 05 (dark) has the name overlaid on the art. The same screen has two constructions; the review-1 defect is
  unchanged and the fix plan does not claim it fixed.
- 05: still no viewer counts or tags under the name (a 3-column grid of 3:4 tiles with only a name is the thinnest card in the app).
- 05: the last chip ("Cr..." at 21-font15) is cut mid-word at the edge; "Sort" is unreachable without scroll and there is no trailing fade.
- 21-font15: the page is 2 columns already at 1.5x; at 1.0x it is 3. Two columns at 1.5x leaves 500 px tiles, which are too tall for the content.
- 51-browse-arabic.png is a byte-for-byte duplicate of 50-home-arabic.png (the capture tapped the wrong tab). Browse in Arabic is not evidenced.
- At 2.0x the first row is cut at the fold and the Kestrel Ops name is clipped by the bar (21-font20).

Fixes to 9.5: one tile construction in both themes (name over a bottom scrim), viewer count line ("14.8K watching"), trailing fade on the chip row, 2 columns
only above 1.3x with 4:5 tiles, retake the Arabic page with a real Browse tap.

### 2.5 Category (06, 52)  6.0 (+1.0)

Verified fixes:
- Header: a 72 dp art tile, 28 sp "Valmoor", "18.7K watching now" (06). Real, with the two rows adding up (14.8K + 3.9K).
- Rows are the Home row (no LIVE badge). Consistent.

Remaining and new defects:
- 06: no Follow button; the only follow control is a 24 dp heart at top-right with no label. Twitch and YouTube Gaming both put a labelled Follow in the header.
- 06: no tags, no follower total, no "Streams / Clips" segmentation. The header tile is the demo art square, 72 dp, with 16 dp radius; fine.
- 06: the "Language" label is 16 sp low-contrast grey beside two chips; it reads as a third chip (also called out in round 1, not fixed).
- 06: 55 % of the page is empty below two rows. No "more in this category" and no related categories.
- 06: the top row has the back arrow and heart floating at y = 142 with a 40 px dead gap before the header (y = 197); header and bar do not share a baseline.
- 52-category-arabic.png is not a category page; it is the player. Category RTL is not evidenced.
- 72 (wide category, round 1): still the round-1 title-only header; the new header was not captured on wide.

Fixes to 9.5: labelled Follow in the header, language chips without a pseudo-chip label (use a single "Language: English" dropdown chip), an empty-tail state
("Browse more categories" row), banner colour from the art, and a retake of Arabic and wide.

### 2.6 Search (07, 08, 09; 12 old)  7.0 (+0.5)

Verified fixes:
- No-results (09): icon disc, `No results for "zzqq"`, one line of help. The query is echoed. Real.
- Same MessageState shape as the rest of the app. The filter chips match Browse.

Remaining and new defects:
- 08: one result row occupies a 120 px band; 700 px of empty page follows. The row is avatar + name + a red LIVE chip + "14.8K viewers", and the avatar also has a
  red ring. Red ring and red chip say "live" twice (the plan admits this).
- 08: the result row gives no stream title or category, so the user cannot tell why Lunaria matched. Add the title and game.
- 08, 07: the empty/recents screen reads "Recent searches / Clear all / lun" then "Live now" with four big ringed avatars; the "Live now" block is the same
  ring pattern as Following, which is consistent, and the rows are 138 px tall (taller than Home rows).
- 07: the caret is a 3 px purple bar; fine. The placeholder is "Search channels" but the filter row offers Categories; the placeholder should be "Search".
- 09: nothing under the message (no suggestion, no "Search categories instead").
- 12 (round 1, search pill morph): the mid frame is the final empty-state with no pill; the morph is not evidenced in either round.
- Keyboard covers 40 % of 07-09 as expected. Not a defect.

Fixes to 9.5: one-ring-or-chip rule for live, the stream title and category on the row, a "See categories instead" action on no results, a neutral placeholder,
and a morph capture (3 frames).

### 2.7 Channel (round-1 41, 44, 83)  5.0 (0.0)

Held. The fix plan says: banner of theme colours, bigger avatar, status line, follow button and a separate "Open on Twitch" button, no duplicate Watch button.
None of this is in a frame: the only Channel captures are round-1 41 and 83, which still show a 96 dp avatar, "Following + open-in-new" fused into one pill, a
duplicate "Watch" button under the live card, and no banner. Either the code is unverified or the capture was skipped; the engineer's own INDEX says "the channel
page ... were not retaken". I cannot score a screen I cannot see, and the round-1 frames are what a reader of the folder gets.

If the claim is true, expected remaining issues from the round-1 frames: Replays list rows are 198 px with 2 lines of text and no description; the
offline header (83) leaves 45 % of the page for the list; "Offline" state has no "Last live" line. Re-shoot live, offline, and scrolled.

Fixes to 9.5: re-shoot, then banner art (from theme or box art), follower count (state honestly if the API has no number), Follow with a bell, tabs
(Home / Videos / About), one primary action ("Watch" on live, "Replays" on offline).

### 2.8 Player portrait live (10, 11, 33; round-1 21, 25, 40)  7.3 (+0.3)

Verified fixes:
- Title block under the picture (title, channel, viewers and category) is real (10).
- One gear replaces two buttons in the controls (11). Real.
- Chat remains the best surface in the app: coloured nicks, subscriber notices as cards, reply line, and the compact input.

Remaining and new defects:
- 10, 11: the stream title is 16 sp regular and smaller than the channel name (20 sp medium) below it; a stream title should be 18 to 20 sp medium, the channel
  line 14 sp. The hierarchy is inverted.
- 11: viewer count appears twice (the chip in the controls, "14.8K viewers" in the meta line); LIVE and the red dot chip appear on the controls only. The
  meta line also says "Valmoor . 14.8K viewers"; the redundancy costs width.
- 10: the open-in-new icon on the right of the channel row is unlabelled and the only action on the header; it opens the channel on twitch.tv? or in the app? The
  fix plan says it "stays". It needs a tooltip or a label ("Channel").
- 11: the top-left chevron, title, PiP, and chat toggle are fine, but the chat toggle (a speech bubble) is a state in a no-label control.
- 33 (light): the capture is mid-fade; the picture is fully covered by ghost controls ("LIVE", gear, title overlapped by the video). It proves nothing about
  the light controls. The chat area in light: nick colours (teal and orange) on #FAFAFD are about 3:1; the plan says WCAG pairs are unit-tested for the
  theme, but chat nick colours are user-defined and need an adjust-for-contrast pass.
- 10: the 16:9 picture touches the status bar area with a 95 px black bar above; fine. The divider between the info block and chat is a 1 px grey line; fine.
- 11: unreadable overlay: the subtitle in the controls (title, 14 sp white 70 %) sits on yellow video at about 2:1 and relies on the scrim.
- No "Follow", "share" or "clip" in the meta row; the row has one icon.
- 52 (Arabic player, light): no chat at all (white void below the header, 1 000 px), the video is black, and the pause icon has a stray comma glyph next to
  it. Either the capture caught a loading state or the RTL player has a bug; both need a retake. The meta line "Echecs . 6.3 [Arabic: thousand viewers]" is
  displayed as "Echecs . 6.3 [Arabic text]" in a jumbled order (bidi: the Latin category and number mix with Arabic words; wrap the whole meta in an
  RTL-isolated string).

Fixes to 9.5: title 18 sp medium at the top of the block, channel 14 sp below with an avatar and a labelled Follow, viewers and category as chips, drop the
duplicate count from the controls, label the external-link action, retake the light and Arabic frames, contrast-adjust chat nick colours.

### 2.9 Player replay (round-1 42, 43)  5.5 (0.0)

Held. The plan says: title scale, speeds in one scrolling row of pills. Round-1 42 and 43 still show a 28 sp "Session du mardi soir", a "0:12 . 3.6K views
. Yesterday" meta (and the duration duplicated: 0:12 is the position, not the length), an "Open channel" tonal pill, "Playback speed" label, and six speed
chips that wrap (2x on a second row). The seek bar is a segmented lilac pill with a visible handle and a thin separator; the controls overlay has a back
chevron and the title only (no PiP, no chat). Not recaptured, so the fix cannot be credited.

Fixes to 9.5: re-shoot; the title the same size as live (18 sp), one metadata line with the true length, a thin 4 dp seek bar with a 12 dp thumb and a
time label, a speed control that is one chip ("1x") opening a menu, and the "More from" rows reuse the Home row.

### 2.10 Chat input, emote picker, reply (round-1 22-24)  7.0 (0.0)

Not touched, so held. 22 shows the input at a comfortable size and a keyboard glyph on the right as the emote button, the chat area keeping 5 lines above the
keyboard. The plan claims the emote search is now an outlined rectangle; the frame with it (the picker itself, 22b) was not retaken. The reply bar (24) and
inserted emote (23) are unchanged. To 9.5: emote picker with tabs (recent / channel / global), a search field that is visibly different from the message input,
a clear send affordance when text is present.

### 2.11 Landscape (round-1 26-29)  6.0 (0.0)

Held; the plan says "landscape title inside the picture: not done" and none of 26-29 was retaken. 26 and 27 still show the title straddling the picture's top edge
(it starts at y = 16 on black and ends at y = 80 over the picture), the right panel with five chat lines of 22 sp, and a fixed black bar above and below the
video. 28 (overlay) has chat in a 580 px dark card over the video with a 28 sp line, and the controls row duplicates "LIVE / 14.8K". 29 (hidden) is
clean. To 9.5: title inside the scrim, one control row, an overlay width that follows the font scale, a drag handle on the side panel.

### 2.12 Bottom sheets (12, 13)  6.5 (+0.5)

Verified fixes:
- One "Playback settings" sheet with Quality and Sleep timer rows. Real, and "Sleep timer / Off" shows a value.
- The sleep sheet title "Stop playing after" with five full-height options, 56 dp rows. Opens fully.

Remaining and new defects:
- 12: "Quality" has no value on the right (no "Auto" or "1080p"), so "say the value in use" is true for Sleep only; the claim is over-stated.
- 13: the options run past the visible region ("1 hour 30 minutes" at the bottom and probably more below), no scroll hint and no selected state; the sheet is
  taller than half the screen with 4 invisible rows.
- 12, 13: both sheets are bare text; the Quality row's icon is a gear, which is the same glyph as the player's settings button that opened the sheet.
- The sheet scrim is 55 % black; chat behind is still legible (13), which is fine.
- Sheet corner radius 28 dp and a 32 x 4 drag handle: correct.

Fixes to 9.5: a value on every row (Quality: "Auto (1080p60)"), a check on the selected option, a "Cancel timer" row when one is on, and a quality sheet with the
bitrate in a second line.

### 2.13 Mini strip and drag (14, 34, 15-17 bottom, round-1 38, 39)  6.3 (+0.3)

Verified fixes:
- A hairline on top of the strip (14, 34): visible in dark; in light (34) the strip is #E9E6F0 on #FAFAFD, barely separated, but the hairline helps.
- 12 dp between pause and close (14: pause at x = 645, close at x = 763). Real, and the targets are far apart.
- The strip is capped at about 110 px (55 dp). Real.

Remaining and new defects:
- 14, 15: the thumbnail is square, flush with the left edge, no radius, no 8 dp inset; the title truncates ("boss final sans d..."). It still looks like a debug bar.
- No progress or LIVE line; no swipe-to-dismiss (unverifiable); no fade.
- 14: the strip covers the fourth row of the list with no extra bottom padding: the "1.8K" chip is cut in half at the strip's top edge. In Settings (15-17) the last
  row is cut at the strip too. Add the strip's height as list bottom padding while it is visible.
- Strip text is 16 sp white on #2A2832 with a 1 px divider; fine.
- 38 (round 1): untouched, per the plan ("Not touched"). The video is a 665 px rectangle pinned left with a black bar above it and the page showing at the right;
  the bottom bar is missing during the drag. If this is the real gesture, it is the most visible defect in the app. It needs a recording and a fix, not a
  dismissal.

Fixes to 9.5: 8 dp inset, 8 dp radius, a 2 dp progress/live line, bottom padding for lists, swipe-to-dismiss, a proportional drag transform that scales the video
and keeps the bar, a recording of the drag.

### 2.14 Settings (15, 16, 17, 35)  7.5 (+1.0)

Verified fixes:
- Rows have no icon gutters; each section has a small purple icon in its title (Playback, Chat, Appearance, Language, About). The lists look finished.
- Profile card with avatar, "Camille / Signed in", and an outlined "Sign out" button. Real.
- Switches and chevrons are aligned on a 56 to 64 dp rhythm; the Discover previews note is one paragraph (15).
- Light (35) has the same structure.

Remaining and new defects:
- 15: the section icon sits at x = 75 and the card's own text at x = 64; the label starts at x = 114 against the card at x = 32 (a 4 dp misalignment between
  the heading column and the rows).
- Still 25 rows in one scroll: Chat has nine settings (display, size, spacing, background, badges, time, animated emotes, animation limit) and no preview. The
  plan says "Chat sub-screen: not done".
- "Playback stats" and "Follow raids" have no explanations (15).
- 16: the "Animations" switch (Appearance) and "Animated emotes" (Chat) are two similarly named switches in different cards.
- 17: "TwitchPlayer / Version 1.0" and a disclaimer in a card of three rows; the disclaimer is body-sized and in the same card as a link; fine.
- Sign-out dialog (the claim: "a reason and a destructive confirm"): the only frame is round-1 75b, which shows "Sign out of Camille?" with no body and a
  lilac "Sign out"; the new dialog was not captured. Same for the Chat display, Theme and Language dialogs (46, 49, 50 are round 1). Dialog widths: "not
  unified" per the plan.
- 15-17 still carry a 120 px scrolled gap under the "Settings" bar (content scrolls under the bar with a hard cut at y = 190, no divider or elevation).

Fixes to 9.5: a Chat sub-screen with a live preview; the heading column aligned to the card text; one sentence of help on each terse row; a dialog set that shares
one width; a scroll divider under the top bar; retake all dialogs.

### 2.15 Signed-out, error and loading (00, 40-45)  7.5 (+4.0)

Verified fixes:
- One component: icon disc, 18 sp bold title, one grey line, a lilac pill. Home, Following, Browse and Discover each have their own copy ("Sign in to see
  what's live", "...see who you follow", "...browse Twitch", "...discover streams"). Real, and the wording matches the tab.
- The bar stays at four tabs and does not move (40-43). Real.
- Error (44, 45): "Couldn't load this page / Check your connection and try again / Retry" with an alert icon. Real, separate from signed-out.
- Skeleton (00).

Remaining and new defects:
- The plan admits "public content when signed out: not done". Browse (43) and Discover (41) are still gated: the copy says "Categories and live channels load
  with your Twitch account", which is a limit, not a feature. For 9.5 the screens need real content for guests.
- 40-43: the message block is centred at y = 880 on a 1 800 px page, i.e. 25 % lower than the optical centre; the avatar glyph in the disc is a generic person
  (the same as the header avatar), so the empty state does not say what the tab is.
- 41: Discover keeps the Live/Clips tabs over a sign-in prompt.
- 44, 45: the error is generic ("this page") with no cached content under it; 44 and 45 are two identical frames. A distinct offline state ("You're offline") with
  the cached last list would be the 9.5 behaviour.
- The sign-in dialog (round-1 79) shows a redacted device-code placeholder in a box with a copy icon; the box is an unstyled 360 x 180 px rectangle and the
  "Sign in" step text is long. The plan says the body string is restored; no new frame.
- No haptic or loading state on the Retry (unverifiable).

Fixes to 9.5: guest content for Browse and Discover, a tab-specific illustration, offline state distinct from error with cached content, hide Live/Clips when
signed out, retake the sign-in dialog with a styled code box.

### 2.16 Wide layout (70; round-1 71-73)  4.5 (+0.5)

Verified fixes:
- 70 (tablet Home, light): a 72 dp rail with icon-only items, a two-column grid, title and channel under the thumbnails, a hero card. Real, and it reads as a
  tablet layout.

Remaining and new defects:
- 70: the hero is 400 px tall (about 25 % of the page) and takes the first screen; the grid starts at y = 590. The hero's 16:9 should be capped (to about 40 % of the
  width on a tablet) or sit beside "Live from your follows".
- 70: captions are 12 sp, tiny for a tablet. The rail lacks labels and the search pill is 600 px wide.
- 73 (round 1): the player is stacked: the picture across the full width, then an unstyled chat of 12 sp text across 810 px with 1 100 px of white below, not side by
  side. The plan says "not done". This is the most obviously unfinished wide screen.
- 72 (round 1): the category page is the title-only header and a 2-column list; not updated.
- Only light was captured on tablet, and no landscape tablet.

Fixes to 9.5: a two-pane player (picture 62 %, chat 38 %), 14 sp captions, a capped hero, a category page with the new header on wide, and a dark and landscape
capture.

### 2.17 Arabic / RTL (50, 51, 52)  7.0 (-0.5)

Verified fixes:
- 50: the whole Home mirrors: the avatar goes left, the brand tile right, the bar order reverses, the hero's avatar moves to the right, the viewer chip to the left
  with the dot at its right edge. The Arabic count format "14.8 [thousand]" is correct. French titles no longer break (the plan's `TextDirection.Content` fix:
  "Any% en moins de 40 minutes ?" now shows the question mark at the end in 50).

Remaining and new defects:
- 51-browse-arabic.png is identical to 50 (a duplicate); 52-category-arabic.png is a player. Browse, Category, Settings and Following in RTL are not evidenced.
  The INDEX says so, which is honest, but it means two of the five screens review 1 cared about are untested.
- 52: the controls show a pause glyph with a trailing comma-like mark (a stray "," after the pause bars at x = 440, y = 330), and the chat area is a blank page.
- 52: the meta line "Echecs . 6.3 [thousand viewers]" displays out of order; the number and the category mix between directions.
- 50: the section label is set in a different font (a Naskh face, lighter weight) than the Latin 20 sp medium, so the heading looks thinner than its rows; the
  Arabic search placeholder is in a light grey that is about 3.4:1.
- 50: the "Run nocturne" hero title is left-aligned in an RTL card (the title text is French, content-aligned), while the Arabic subtitle should follow; fine.

Fixes to 9.5: retake Browse, Category, Following and Settings in Arabic with correct taps, fix the bidi isolation of the meta line, figure out the stray pause glyph,
match Arabic heading weights.

### 2.18 Large font (20, 21, 22)  7.0 (+1.0)

Verified fixes:
- 2.0x Browse: two columns, readable names (21-font20). Real.
- 2.0x Home and Following: no overlap; avatars and search icon scale or stay fixed in a controlled way; the strip is capped (round-1 69 not retaken).

Remaining and new defects:
- 20-home-font15-dark.png is a skeleton, not content.
- 22-font20: the "Live now" labels truncate to "Luna...", "Mait...", "Cuisi...", "DJ O..."; the avatars do not scale (62 dp). Rows have 3 lines of ellipsised title
  and the thumbnail is 185 px with 130 px empty beneath it.
- Settings, Player, Search, sheets, strip and Discover were not captured at large font. The most text-heavy screens (Settings, the chat) are the ones not shown.

Fixes to 9.5: capture the missing screens at 1.5x and 2.0x; labels on two lines under the avatars at large sizes; keep the row to thumbnail + title + channel.

### 2.19 Light and dark parity (30-35)  7.0 (+0.5)

Works: Home, Following, Settings and the strip share structure; the lilac accent is darker in light (#6A3FD8) and holds contrast; the hero stays dark in light.
Defects: Browse tile construction (32 vs 05); Player (33) is a mid-fade capture; the strip separator is weak in light (34); chat nick colours need contrast
adjustment on the light chat (33: teal and olive nicks about 3:1); the status bar icons in 33 are white on the black bar, correct.

### 2.20 PiP (round-1 74)  n/r

Still the launcher with a 105 x 55 px window overlapping the icons. A PiP review needs the window at the corner with its controls. Not assessable; the plan
does not mention it.

---

## 3. Verification of the engineer's claims (over-claiming flagged)

| Claim (fix plan) | Verdict from the pictures |
|---|---|
| Home / Following not duplicates | True (01, 02). But Following's live-now row duplicates the list below it |
| Signed-out / offline with Retry, fixed bar | True (40-45) |
| Discover Live as a card with labelled actions and pager hint | True (03); the card leaves 48 % of the page black, which the plan calls "not a full-bleed crop" and does not name as a defect |
| One empty/error component | True: icon disc, title, line, action on every state |
| Title block under the player | True (10); hierarchy inverted |
| Channel identity (banner, avatar, status line, separate "open on Twitch" button, no Watch) | Not verifiable: no new frame; round-1 41/83 contradict it. Over-claim of evidence |
| One chip / header / dialog | Chips and tab titles true; Home's section label is a third header style; dialogs not unified (admitted) |
| Drag transform | Not touched (admitted). Round-1 38 still looks broken |
| Large font: 2 columns, strip capped, initials fixed | Browse and avatar true; strip not retaken; Home 1.5x frame is a skeleton |
| Wide: Home rail and two columns | True (70); player and category unchanged |
| Category header (art, name, viewers) | True (06); no Follow button |
| Reduce red | Partly: badges gone, rings and Search chip remain (admitted) |
| Strip hairline, 12 dp gap, capped height | True (14, 34); still square and flush, covers the last row |
| Replay: title scale, speeds in a scrolling row | Not verifiable: round-1 42/43 show the old layout |
| Settings rows without gutters, section icons | True (15-17); heading column misaligned by 4 dp |
| Sign-out dialog with a reason and destructive confirm | Not verifiable: only round-1 75b exists and it has neither |
| Sheets open fully and say the value in use | Sleep only; Quality has no value; sleep options run past the fold |
| Emote search outlined, not a second pill | Not verifiable (no picker frame) |
| WCAG contrast of the theme pairs is a JVM test | Plausible; the chat nick colours are not theme pairs and fail in light (33) |
| bidi `TextDirection.Content` on titles | True for stream titles (50); meta lines still jumble (52) |
| Self-assessment 7.0 to 8.0 on every surface | 0.3 to 0.9 too high; Channel 6.5 against my 5.0 is the widest gap (no evidence) |

Capture-hygiene problems (these cost points because the folder is the deliverable): 51 duplicates 50; 52 is a player in a Category slot; 20-home-font15 is a
skeleton; 44 and 45 are identical; 33 is mid-fade; 74 is a launcher screenshot; no landscape, replay, clip player, channel, marquee, morph or drag frames.

## 4. Consistency, theme, type and RTL summary

- Consistency: a real system exists now (page titles, chips, MessageState, row card, ring avatars). Breaks: Home's 20 sp section label, Browse tile light/dark
  split, three different strip/sheet value conventions, two red-live treatments (ring plus chip) in Search.
- Light/dark: structure matches; accent, hero and strip hairline hold; Browse and chat nick contrast do not.
- Large font: no overlaps in the captured screens; no coverage of the dense ones.
- RTL: Home good; the rest unproven; bidi on metadata lines still wrong.

---

## 5. Overall and ranked fix list

**Overall 6.9 / 10** (round 1: 5.9).

Ranked by distance to 9.5 per effort:

1. Fill the Discover page: the picture, name and actions use 52 % of the screen and leave 48 % of black (03). Crop the stream to the page or centre the card.
2. Re-shoot every screen the plan claims but the folder does not show: Channel (live, offline, scrolled), replay and clip players, landscape (three modes),
   drag, PiP, dialogs (sign-out, theme, language, sign-in), search morph. At least 12 claims are unverifiable.
3. Fix the drag (38): a proportional shrink that keeps the bar, with a screen recording to prove it.
4. Home: a deeper hero scrim, a visible Watch or LIVE cue, and show (and capture) the recommendations and categories the plan describes below the follows.
5. Player block hierarchy: stream title 18 to 20 sp medium above a 14 sp channel line, remove the duplicate viewer count, label the external-link action, add a
   labelled Follow.
6. Browse: one tile construction in light and dark (name over a bottom scrim), viewer counts, trailing fade on the chips.
7. Following: make the live-now row and the list different content (or drop one); purple ring with a corner dot instead of a pure red ring; two-line labels at
   large sizes; "Last live" on offline rows.
8. Category: a labelled Follow button, a language dropdown instead of a label plus chips, an empty-tail state.
9. Guest content for Browse and Discover (the plan's top missing item) and a distinct offline state with cached content.
10. Settings: a Chat sub-screen with a live preview; align the heading column to the card text; explain "Playback stats" and "Follow raids".
11. Wide: a two-pane player and a capped hero on tablets; 14 sp captions; capture dark and landscape.
12. Strip: 8 dp inset, 8 dp radius, a 2 dp progress/live line, bottom list padding while it is shown, swipe to dismiss.
13. Sheets: a value on every row (Quality), a check on the selected item, a cancel-timer row, a visible scroll hint on the sleep sheet.
14. RTL: retake Browse, Category, Following, Settings and the player; isolate the bidi meta line; investigate the stray glyph next to the pause icon in 52.
15. Large font: capture and fix Settings, player, search, sheets and strip at 1.5x and 2.0x; replace the skeleton in 20-home-font15; label wrapping in the Live
    now row.

Also worth doing before a 9.5 claim: real box art and banners (the API gives none, so design for placeholder art that does not look like a test pattern), chat
nick contrast adjustment on light, and a search result row that carries the stream title and category.
