# Apple apps screen review 2 (iOS, macOS, tvOS)

Independent, read-only review (nothing built or run). Round 2 of `docs/audit/apple-review-1.md`. Viewed: about 70 of the 122 PNGs in
`docs/screenshots/audit/apple-2/` (every screen family in light and dark: home, following, browse and chips, category, channel live and
offline and scrolled, search x7, settings, sign-in, player portrait, drag, emote picker, pill, long-press, replay, clip, offline, landscape,
strip, signed-out, all six `ax-*` and `rtl-*` families), all 8 macOS and all 9 tvOS captures, and the real-data and error captures of
`docs/screenshots/audit/apple/` that apple-2 lacks. Near-duplicates (the remaining `-scrolled`, `-bottom`, `-chip-irl`, and the second
appearance of identical layouts) were spot-checked, not opened one by one. Scale: 9.5 = best-in-class shippable; most shipping apps sit 6 to 8.

Method limits. The two landscape families are portrait screenshots rotated 90 degrees by the harness (see `light-player-landscape-controls.png`:
text runs sideways), so landscape is not scored from pixels. Demo art is fictional and not judged. Contrast numbers are estimates.
Focus, motion, VoiceOver are invisible in stills.

## 1. Scores

### iOS (iPhone 17, iOS 26.5, French)

| Screen | R1 | Engineer R2 | Mine R2 | Delta vs R1 | Note |
|---|---|---|---|---|---|
| Home | 7.0 | 8.0 | 7.6 | +0.6 | Featured card, followed rail, wide cards are real gains; accent drift and tab bar tint left |
| Following | 6.5 | 7.5 | 7.2 | +0.7 | Avatar rail is good; rows below are still the old thumbnail list |
| Browse (+chips) | 7.5 | not touched | 7.6 | +0.1 | Unchanged; still no counts, 75 % blank on a 2-card chip |
| Category | 5.0 | 7.5 | 6.8 | +1.8 | Header is right; page still 70 % empty, title duplicated |
| Search (all states) | 6.5 | not touched | 7.3 | +0.8 | No-results state fixed (undeclared by the INDEX); Categories tab still blank |
| Channel live / offline | 6.0 | 7.5 | 7.0 | +1.0 | Honest link, ring, chips; nav title, stale date, 3-row chips |
| Settings (+language) | 7.5 | not touched | 8.0 | +0.5 | Version key fixed, licences row added (undeclared) |
| Sign-in sheet | 6.0 | not touched | 6.8 | +0.8 | Waiting line and countdown added; bleed, pill contrast, dark box remain |
| Player live portrait | 7.5 | not touched | 7.8 | +0.3 | Best screen; header still crowded, title truncates |
| Emote picker / pill / long-press | 6.5 | 8.0 / 8.5 | 7.8 | +1.3 | Picker and pill good; long-press capture still shows overlapping rows |
| Player landscape | 7.0 | not touched | n/s | n/a | Harness rotated; carry 6.5 |
| Replay / clip | 4.0 | not touched | 4.5 | +0.5 | Unchanged in substance |
| Player drag mid-state | 4.5 | 6.5 | 3.5 | -1.0 | Over-claimed, see 2.9; worse than R1 |
| Offline channel as player | 6.0 | not touched | 5.5 | -0.5 | Empty page plus composer |
| Mini strip | 7.0 | not touched | 7.0 | 0 | Over a stale home layout (old rows) |
| Signed-out Home / Browse | 4.5 | 7.0 | 7.0 | +2.5 | Welcome card with CTA is a real fix |
| Real data / errors | 3.5 / 4.0 | not redone | 3.5 / 4.0 | 0 | Old captures still say "Non connecté"; replay error still overlaps the play button |
| Dynamic Type XXL | n/c | 7.0 | 6.8 | new | See 2.12 |
| Arabic RTL | n/c | 7.5 | 7.0 | new | See 2.13 |
| Light / dark parity | 7.0 | n/a | 7.5 | +0.5 | Better; light tab bar is the outlier |
| **iOS overall** | **6.1** | n/a | **7.0** | **+0.9** | |

### macOS

No capture changed since round 1 (`git log` on `docs/screenshots/macos`: last touched in the compress commit, same pixels). Nothing was fixed or shown
fixed, so scores are carried.

| Screen | R1 | Mine R2 | Delta |
|---|---|---|---|
| Home / Following | 6.0 | 6.0 | 0 |
| Browse | 6.0 | 6.0 | 0 |
| Search | 5.0 | 5.0 | 0 |
| Channel | 6.0 | 6.0 | 0 |
| Player + chat | 6.0 | 6.0 | 0 |
| Mini player | 4.5 | 4.5 | 0 |
| Settings window | 4.0 | 4.0 | 0 |
| **macOS overall** | **5.4** | **5.4** | **0** |

### tvOS

| Screen | R1 | Mine R2 | Delta | Note |
|---|---|---|---|---|
| Home / Following | 7.0 | 7.2 | +0.2 | Top bar now a floating glass pill |
| Browse | 6.5 | 6.5 | 0 | "Tous" selected chip still purple on purple |
| Search | 6.5 | 6.5 | 0 | |
| Channel | 6.0 | 6.0 | 0 | Tab still "Accueil"; "Suivis" above a live card |
| Settings | 4.5 | 4.5 | 0 | Pickers still wedged between headings |
| Sign-in | 6.5 | 6.5 | 0 | Step "2." still shown without a "1." |
| Player compact chat | 7.0 | 7.8 | +0.8 | Header, "..." menu, readable rows |
| Player overlay chat + transport | 5.0 | 5.5 | +0.5 | Transport icons still sit on chat lines |
| **tvOS overall** | **6.2** | **6.4** | **+0.2** | |

## 2. Per screen

### 2.1 iOS Home (`light-home.png`, `dark-home.png`, `*-home-scrolled.png`)
Verified fixes: tab bar now labelled (Accueil, Suivis, Parcourir); full-width featured card with LIVE and viewers; "En direct parmi vos suivis" rail with
two-line titles; "Pour vous" 16:9 cards with avatar rows; bottom bar no longer has the cyan/red lens artefact (it is tinted but coherent).
Defects.
- `light-home-scrolled.png`, `dark-home-scrolled.png`: the active accent flips from purple to royal blue (logo glyph, "Accueil" icon). Brand colour is
  not stable across scroll states; looks like a tint token overridden by a pressed or scroll state. Blocks any 9.
- `light-home.png`, `light-home-scrolled.png`, `light-browse.png`: the bar picks up the colour of the card under it (pink, olive green, lilac) and in
  `light-browse.png` shows vertical seams at the card gutters. That is what real glass does, but the label contrast then depends on content
  (grey "Suivis" on pink is near 3:1). Dark is fine.
- The bar is a custom pill, not `TabView`; the engineer lists this as not done. It also is not collapsible on scroll and does not add bottom insets
  (scrolled shots show the last card under the bar by design).
- Featured card has no "Continue watching" or categories strip above the fold; the first pixel under the nav is a hero but nothing says "featured".
- Header: 37 pt avatar disc, play-glyph logo still decorative.
Fixes to 9.5: native `TabView` (iOS 26 gets Liquid Glass, `tabBarMinimizeBehavior(.onScrollDown)`, search `Tab(role: .search)`), custom bar only for
iOS 18 to 25 with an opaque `.regularMaterial` fallback; fix the accent token (one `tint`); ensure label contrast with a thin material behind labels;
44 pt avatar target; Continue-watching rail; make logo scroll-to-top.

### 2.2 iOS Following (`*-following*.png`)
Good: avatar rail with live rings, labelled "Suivis", offline with "Vu en direct il y a 1 j". Defects: rows are the R1 thumbnail list (147 pt picture, 4
text lines, titles clipped to one line "Run nocturne : boss final s..."), so Following is a denser clone of Home; `*-following-scrolled-offline.png` offline
avatars are flat tint discs, no initial; the rail uses a red ring for every item including channels the row says are not all equal; no empty state
captured. Fix: use the Home card layout for live items (2-line titles), letter avatars offline with a dimmed (not red) ring, a bell/notify affordance,
an empty state with a link to Browse.

### 2.3 iOS Browse (`*-browse*.png`)
Unchanged from R1 and still the cleanest page: 3-up posters, filled chips. Defects: no viewer count on posters; "Tri : popularité" is text with a sort glyph,
not a `Menu` with a chevron (purple on `FAFAFB` about 4:1 in light); `light-browse-chip-games-mid.png` is a clean state now (R1 ghost label is gone), but
`dark-browse-chip-music.png` is 2 cards and 75 % black with no "more" or related content; chip row is clipped at the right edge without a fade cue.
Fix: gradient overlay "12K spectateurs" on each poster, `Menu` sort, fade mask on the chips, a "Catégories populaires" section under sparse filters.

### 2.4 iOS Category (`*-category.png`)
Verified: poster, name, "19K spectateurs · 2 chaînes", "Suivre la catégorie" pill. Defects: nav bar title "Valmoor" and the header title "Valmoor" side by side
(duplicate; hide the nav title until scroll); the two rows are still the R1 list and repeat the category as the subtitle ("Valmoor" twice more); the page
is 70 % empty (`light-category.png`: nothing below y=1000 of 2000); `*-category-scrolled.png` equals the base; the follow pill has no pressed or followed
state shown and, per the Twitch limits, may not be functional (the channel page was made honest, the category one was not). Fix: show category as a
header only once, 16:9 live cards in a 2-column grid, drop the category line, "Personne n'est en direct ici" empty state, hide or make real the Follow pill.

### 2.5 iOS Search (`*-search-*.png`)
Verified (not declared in the INDEX): `dark-search-no-results.png` now has icon, title, hint "Vérifiez l'orthographe ou essayez un autre nom." and an
"Effacer la recherche" button; results show a ringed avatar with LIVE and viewers; recents and "En direct maintenant" are sound.
Defects: `light-search-filter-categories.png` is still a blank page under the selected chip (no empty state, no result); `dark-search-typing-loading.png`
shows a dimmed "Chaînes" header (loading grey) but no spinner or skeleton, so loading is still not legible; the search is a custom pushed field, not
`.searchable`; no scope bar; placeholder "Rechercher une chaîne" does not promise categories; results omit stream title and category. Fix: a
categories result list (poster rows), an empty state for the Categories scope, `ProgressView` or skeleton rows, `.searchable` with `searchScopes`.

### 2.6 iOS Channel (`*-channel-*.png`)
Verified: banner, ringed avatar, display name plus "@lunaria", red "En direct · 15K spectateurs", "Vous suivez cette chaîne", counts, and an honest
"Ouvrir sur twitch.tv". Defects:
- Nav title is the lowercase login ("lunaria", "hibou") above the display name; the INDEX admits it. Hide it until the header scrolls off.
- `light-channel-live.png`: banner ends in a hard horizontal edge at y=530 and the 217 pt avatar straddles it with its ring clipped at the left page margin;
  the avatar is oversized next to a 17 pt name. The chips wrap onto three rows (live, followed + replays, clips) which pushes content down 280 pt.
- "Prochains lives" row shows "14 nov. 2026 à 19:13", equal to the capture time (demo data, but the format is not relative; use "Demain 19:13").
- Replays carry no progress, date or type; no tabs (Accueil, Vidéos, Clips, À propos); offline state is only a chip (`dark-channel-offline.png`).
- The "Vous suivez cette chaîne" chip looks like state but, per the Twitch viewer-token limits, cannot be toggled: acceptable as a read-only status only if
  it has no button affordance (it has none), good.
Fix: segmented picker, rounded banner with gradient to the background, 72 pt avatar, merge counts into one line, relative dates, progress bars.

### 2.7 iOS Settings (`*-settings-*.png`)
Verified: raw key gone ("Version 1.0 (1)"), disclaimer text and "Licences open source" row added, Langue row shows the current language. Defects: still custom
cards instead of `Form`; "Se déconnecter" is a neutral white pill (`light-settings-top.png`), not destructive, and there is no confirmation (tvOS uses red text);
Langue still leaves the app with no hint; the "Chat compact / Moyen / Normal" rows look like values but are pickers (no chevron, `light-settings-top.png`).
`*-settings-2/3/bottom` still the same scroll in light. Fix: `Form` + `.scrollContentBackground(.hidden)`, destructive role with `confirmationDialog`,
chevrons or `Picker(.menu)` indicators, a "Réglages système" hint.

### 2.8 iOS Sign-in sheet (`*-signin-sheet.png`)
Verified: Annuler plus title, step list, CTA, tracked code, "Copier le code", expiry, and a spinner line "En attente de votre validation sur twitch.tv..." (new).
Defects: `light-signin-sheet.png` still shows the page's text ("Voyez qui est en direct maintenant") bleeding through the top of the sheet and
a blurred ghost under the spinner line; "Copier le code" is lavender on lavender (about 2.4:1 light); `dark-signin-sheet.png` lost the code card (light has a grey
card), a parity gap; the bottom 150 pt is empty; the primary CTA white on `772CE8` is about 6:1 (fine); Annuler is a pill while the title is centred (mixed
idioms). Fix: opaque `.presentationBackground`, a code card in both themes, darker copy label, `ShareLink`/`UIPasteboard` haptic confirmation.

### 2.9 iOS Player
Live portrait (`*-player-live-portrait.png`): still strongest; chat dense and legible, reply rows, sub banner. Defects: header row crowds title, LIVE chip, viewers,
timer and chevron (title truncates to "Run nocturne : boss final s..."); chat has no bottom fade under the composer.
Emote picker (`light-player-emote-picker.png`) 7.8: sections "Cette chaîne / Émotes globales / Verrouillées", provider chips, search; the chat area above is
cut to 4 lines, so the picker eats 60 % of the screen with 3 emotes per section; locked emotes clipped at the bottom edge.
Pill (`dark-player-chat-pill.png`): "5 nouveaux messages" sits above the composer, no overlap; fix real.
Long-press (`dark-player-chat-row-longpress.png`): chat rows overlap each other behind the menu (text on text at y=990 to 1130), which the INDEX waves off as a timing
artefact without proof. Treat as a bug until a still-frame capture with the list frozen proves otherwise.
Drag mid-state (`dark-player-drag-mid.png`, `light-player-drag-mid.png`) 3.5: the engineer scored 6.5 and says the box wraps the picture. In the pixels, a 640 px wide
picture is pinned to the top-left of a full-width grey slab (`#EEEEF2` light, `#1E1E24` dark) that is 1250 px tall and covers the page behind it; there is
no title, no chrome, no radius, and an unrelated list shows above and below. It reads as a rendering bug, worse than R1 (which was ugly but contained). This is
the single most visible regression of the round and the INDEX claim is an over-claim.
Replay / clip (`*-player-replay-portrait.png`, `*-player-clip-portrait.png`): unchanged; 70 % empty page, "vod demo" as streamer and as title, no initial in the avatar
disc, no description, date or chapters, one "Partager" pill. Offline channel (`light-player-offline-channel.png`): empty page with composer for a non-live channel,
no "hors ligne" message; the Home screen avatar disc says "H" but there is nothing else to see. Errors (`light-real-error-replay-unavailable.png`): "Rediffusion
indisponible" is overlapped by the play button and still carries a LIVE badge. Fixes: drag state as one shrinking card with the title strip; description, date,
chapters and related videos for replays; offline state with next stream and last seen; error overlay without play button, no LIVE badge on a VOD.

### 2.10 iOS Mini strip (`*-strip-over-*.png`)
Right pattern and position, thumbnail flush to the strip's corner. The captures use the old home layout (R1 list), so they are stale after the Home rework. Fix:
recapture; give the strip a 12 pt corner on the thumbnail and keep it above the tab bar's glass.

### 2.11 Signed-out experience (`*-home-signed-out.png`, `*-browse-signed-out.png`, `light-real-*`)
Verified: welcome card (icon, "Vos chaînes en direct, au même endroit", honest explanation, three benefit rows, "Se connecter avec Twitch"), and a tab-specific
headline on Browse. This is a real, coherent first-run. Remaining: Following tab is hidden and Browse is gated (no anonymous browsing, decision not taken);
the CTA is the only action; the browse variant repeats the same three benefit rows (Home copy) under a different headline; all `light-real-*` captures predate the
fix and show "Non connecté / Connexion" (`light-real-browse.png`, `light-real-channel.png`) or black video (`light-real-player-portrait.png`), so there is
no evidence the real-data path was improved. Channel and player deep links for a signed-out user still land on a wall ("Connectez-vous avec Twitch pour voir
les chaînes que vous suivez" on a channel page is the wrong copy). Fix: anonymous Browse and public channel pages if the API allows, correct copy per route,
re-capture `real-*`.

### 2.12 Dynamic Type (`*-ax-*.png`)
Chrome capped at XL (search bar, tab bar, titles) while content wraps: reasonable policy. Defects: `dark-ax-browse.png` keeps three poster columns, so labels
hyphenate and ellipsize ("Val-moor", "Race..."); it should go to 2 columns at accessibility sizes; `light-ax-home.png` hero title truncates ("Run nocturne :...")
and the tab bar covers the next heading; `dark-ax-player.png` gives the chat the entire screen (two messages visible) and the composer truncates ("Envoyer un me...");
`light-ax-settings.png` title stays small while row labels hit 60 pt and truncate ("Affich...", "Chat..."); `light-ax-channel.png` stacks the status chips full width,
hiding the replays for two screens; `dark-ax-signed-out.png` is readable but the CTA is below the fold. Fix: adaptive columns by `dynamicTypeSize.isAccessibilitySize`, `ViewThatFits`
for header rows, minimum video height with chat capped at 40 %, scale the nav title.

### 2.13 Arabic RTL (`*-rtl-*.png`)
Mirrored correctly: header order, back chevron points right, tab order, rails scroll from the right, toggles on the leading edge, hero chips. Defects:
`dark-rtl-channel.png`: "4 clips" and "6 replays" stay English (missing keys); `light-rtl-player.png`: chat lines in French reverse word order (known, not fixed;
the engineer's "Arabic chat would read right" is untested), and the composer placeholder mirrors but the emoji button sits trailing, inconsistent with the picker; the date in
the channel page switches to Arabic month and Arabic-Indic punctuation inside a Latin row title (bidi mix); `light-rtl-home.png` first rail card is clipped on the left ("inois")
because of an inset that is not mirrored. Fix: localise the two count labels, wrap each chat row in `.environment(\.layoutDirection, detected-from-text)`, mirror the scroll inset.

### 2.14 Light / dark parity
Better than R1: Home, Following, Browse and Settings are matched. Outliers: light tab bar tint (2.1), accent drift (2.1), sign-in code card (2.8), drag slab (2.9). Dark
chat in the landscape and long-press captures is the more polished one.

### 2.15 macOS (`docs/screenshots/macos/*.png`)
Unchanged. Still: toolbar title "Sans titre" on Home (`home.png`) while others say "TwitchPlayer" (`browse.png`), the selected sidebar row is a black slab with no text visible in
`home.png`, `channel.png`, `settings.png` has a white block over the tab strip (harness, but unresolved); type is 10 to 12 pt throughout; search is one result in 800 pt
of void (`search.png`) and the segmented control is offset from the content edge; channel hero only 420 pt wide and a two-column replay grid that leaves a dead centre
(`channel.png`); mini player is a black rectangle over the Speedruns card with no title or controls (`mini.png`); settings has three rows in a 560x420 window, no
General/Playback/Chat/Account/About tabs although `docs/APPLE_PLATFORMS.md` lists them (`settings.png`); no vibrancy, no translucent sidebar. No Liquid Glass toolbar evidence
(macOS 26 toolbar items should be glass).
Fix to 9.5: `NavigationSplitView` with a real sidebar selection and `.navigationTitle` per route, a macOS `Settings` scene with a `TabView` and `Form(.grouped)`, 13 pt body
type (`.body`), a native `.searchable` toolbar field, grid sized to the window with min 280 pt cards, a mini player with title, play/close and PiP-style drag, `.toolbarBackground` glass
on 26 with material fallback, keyboard shortcuts (Cmd+F, Space, F), menu bar commands.

### 2.16 tvOS (`docs/screenshots/tvos/*.png`)
Verified: top tab bar is a floating glass pill (system `TabView` style on tvOS 26), compact chat column has a "Chat" header and "..." options (`player-chat-compact.png`),
rows at 27 pt with readable hierarchy. Defects:
- `player-chat-overlay.png`: transport glyphs (chat, picture, sliders, headphones, moon, waveform) sit directly on chat lines at y=525 to 555; "lunaria" appears twice
  (small label and 40 pt title); a flat grey bar floats at y=540; the scrubber and "-00:08" countdown run on a live stream; the chat overlay has no scrim, so white text sits
  on yellow/cyan bars (about 1.5:1 in places).
- `settings.png`: the "Chat masqué / compact / sur la vidéo" picker is wedged between "Lecture" rows and the "Chat" heading; "Petit / Moyen / Grand" sits under the heading but over the first
  row's rule; headings ("Lecture", "Chat", "Apparence") are low-contrast grey; the first row starts 20 pt under the tab bar; no row is focus-framed.
- `channel.png`: tab bar still highlights "Accueil" on a pushed channel; the live card sits under the label "Suivis".
- `browse.png`: "Tous" is purple on purple (about 2:1); posters are 5-up with 70 pt gutters but captions hug the next row.
- `signin.png`: step "2. Saisissez ce code..." with no step 1, Annuler's focus pill is off-centre from the column; QR good.
- No focused state in any capture, so focus rings, parallax and the 1.05 lift are unscored.
Fix: put transport in its own bottom scrim and fade chat under it, drop the duplicate title and the scrubber when live; settings as a `Form` with sections each holding its own row set; `TabView` state bound
to the stack; selected chip uses white label on filled purple; add focused captures.

### 2.17 Consistency across platforms
- Sign-in body copy is aligned between iOS and tvOS but iOS says "Ouvrir twitch.tv/activate" and tvOS "Scannez le code": expected. tvOS still numbers "2." only.
- Accent: iOS drifts blue on scroll; tvOS and macOS stay purple.
- Channel: iOS has banner and ring; tvOS has ring and name only; macOS has "Suivi" pill (see 2.6, the one control that cannot work with a viewer token) while iOS replaced it with an honest status.
  macOS channel therefore regressed relative to iOS.
- Offline state: iOS list with last seen; tvOS cards with "il y a 1 j"; macOS none.
- Settings: three different structures (cards, list, 3-row window).
- Liquid Glass: tvOS and the iOS bars look like glass; macOS none; no iOS 18 fallback capture.
- Home/Following: macOS shows the old grid with "Sans titre"; iOS has hero plus rails; tvOS has two rails.

## 3. Over-claims in `docs/screenshots/audit/apple-2/INDEX.md`
1. Drag mid-state "box wraps the picture", 6.5: pixels show a 650 px picture left-aligned in a 1250 px grey slab (3.5).
2. "Chrome capped at XL, Home, Browse, Settings readable": Browse hyphenates three-column labels, Settings truncates row names, Player shows two chat lines.
3. "Arabic RTL 7.5, layout mirrors": not for chat, counts or the first rail card.
4. Category 7.5 and Channel 7.5: page remains 70 % empty and nav title still duplicates.
5. "Real-data black picture not reproduced": the real captures were not redone, so the claim has no evidence.
6. Long-press overlap "demo timing artefact, not confirmed as not a bug": unproven.
7. It under-claims: Search no-results, Settings version key, licences row and sign-in spinner all improved without being listed.
8. Capture hygiene: INDEX says Search, Settings, sign-in captures are R1 but they contain the R2 changes; the strips are over a stale layout.

## 4. HIG, Liquid Glass, accessibility summary
- Tab bar: labelled now; custom, not `TabView`, so no system minimise, no search role, no VoiceOver tab traits; no iOS 18 to 25 fallback evidence.
- Touch targets: avatar 37 pt, copy pill 36 pt tall, chips 40 pt: below 44 pt.
- Destructive action styling and confirmation absent on iOS and macOS sign-out.
- Contrast below 4.5:1: copy-code pill light, sort text, grey labels on the tinted light tab bar, tvOS "Tous" chip, tvOS overlay chat on bright bars, tvOS section headings.
- Dynamic Type: partially adaptive; accessibility layouts do not change column counts.
- RTL: structure fine, content gaps.
- Signed-out: first-run is now good; no anonymous path.

## 5. Ranked fix list
1. iOS player drag mid-state: one shrinking card with title strip, no slab (`*-player-drag-mid.png`).
2. macOS: whole round missing; recapture after: "Sans titre", sidebar selection, Settings scene with tabs, 13 pt type, mini player chrome, glass toolbar.
3. iOS accent drift to blue on scroll (`*-home-scrolled.png`).
4. tvOS overlay chat/transport collisions, duplicate title, scrubber on live (`player-chat-overlay.png`).
5. tvOS Settings structure and heading contrast (`settings.png`).
6. Replace the custom tab bar with `TabView` (iOS 26 glass, minimise, search role) and keep a labelled opaque fallback below 26.
7. Replay/clip/offline pages: description, date, chapters, related, and the offline message (`*-player-replay-portrait.png`, `*-player-offline-channel.png`); replay error overlay and LIVE badge.
8. Category and Following rows to the card layout; category empty state; hide duplicate nav titles on Category and Channel.
9. Search Categories scope: results and empty state; loading skeleton (`*-search-filter-categories.png`, `*-search-typing-loading.png`).
10. Sign-in sheet: opaque background, dark code card, contrast on the copy pill.
11. Dynamic Type: 2 columns for Browse at accessibility sizes, chat capped, scaled Settings title.
12. RTL: localise "clips/replays", per-row chat direction, mirrored rail inset.
13. macOS channel: remove the dead "Suivi" pill, use the honest twitch.tv link.
14. tvOS channel tab state and "Suivis" label; "Tous" chip contrast; sign-in step numbering.
15. Capture hygiene: fix landscape (not rotated), re-capture `real-*`, strips, long-press frozen, tvOS focused frames, iOS 18 fallback.
