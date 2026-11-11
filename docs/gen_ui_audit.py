import statistics as st

CATS = {
    "M": ("Motion", 25), "F": ("Focus", 20), "L": ("Layout", 20),
    "C": ("Continuity", 15), "S": ("States", 10), "R": ("Robustness", 10),
}
RUBRIC = [
 ("M1","Timing","Durations match the role: feedback 100-200 ms, panels 200-300, large travel 300-400, nothing over 400"),
 ("M2","Easing","Decelerate in, accelerate out, a distinct curve per role (not one curve for everything)"),
 ("M3","Choreography","Related elements are sequenced or overlapped (30-60 ms stagger, whole sequence under ~150 ms)"),
 ("M4","Purpose","Every motion explains cause, hierarchy or location; nothing decorative"),
 ("M5","Coverage","Every appear / disappear / reorder is animated, in both directions"),
 ("F1","Visibility","Focus is legible at 3 m on any content or background"),
 ("F2","Response","Focus changes more than an outline: scale, elevation, content reveal"),
 ("F3","State separation","Focused, selected, pressed and disabled are four visibly different things"),
 ("F4","Path","D-pad order is predictable, focus is restored, no traps"),
 ("F5","Latency","First visible reaction under ~100 ms, no jank while moving"),
 ("L1","Hierarchy","One clear focal point and three levels of visual weight"),
 ("L2","Grid & rhythm","Consistent columns, gutters, baselines, spacing scale"),
 ("L3","Legibility","TV-minimum type size, contrast at least 4.5:1, sensible line length"),
 ("L4","Safe area & edges","Safe margins respected, soft edges, nothing clipped"),
 ("L5","Imagery & space","Content-led: art gets room, no dead zones"),
 ("C1","Spatial model","Direction of motion tells you where you are in the hierarchy"),
 ("C2","Shared elements","The thing you pressed travels into the next screen"),
 ("C3","Context","Focus, scroll and state restored on return"),
 ("C4","Coherence","Screens in one flow share one transition language"),
 ("S1","Loading","Layout-matched skeleton, no blank flash"),
 ("S2","Empty","Says what is missing and offers a focused action"),
 ("S3","Error","Recoverable, focused retry, stale data kept"),
 ("S4","Live update","New data animates in without jumping"),
 ("S5","Images","Placeholder matches the art, crossfade, no layout jump"),
 ("R1","Reduced motion","A designed alternative cue, not only a snap"),
 ("R2","Frame budget","UI thread under 5 ms per frame, no offscreen layers or blur"),
 ("R3","RTL & a11y","Mirrored, semantics, announcements, content descriptions"),
]

SCREENS = []
def screen(name, files, rows): SCREENS.append((name, files, rows))

screen("1. Global shell: NavRail + screen transitions", "TwitchTvApp.kt, NavRail.kt", [
 ("M1",8,"Rail 150 ms, page fade 200 ms: right for each role"),
 ("M2",8,"Push / pop: Glide in, GlideIn out over 240 ms; tab and player fades keep EaseOut 200 ms"),
 ("M3",5,"Panel, labels and item widths change on the same frame; labels never lag the panel"),
 ("M4",7,"Growing in = deeper, receding = back, fade = sideways (tab). Rail growth says 'navigation'. ⚠ Built and unit-tested, not watched on the TV."),
 ("M5",7,"Outgoing page scales to 97 % and dims on push, grows away on pop; a tab switch and the player entry still have no exit. ⚠ Built and unit-tested, not watched on the TV."),
 ("F1",8,"2 dp #BF94FF ring + 1.5 dp dark gap, readable on Body and Alt"),
 ("F2",6,"Collapsed items scale 1.04; expanded items are ring only, no label or colour response"),
 ("F3",9,"Selected = purple glyph + 2 dp bar, focus = ring: never confused"),
 ("F4",8,"Entry lands on the selected item; two-press edge guard stops accidental entry (can surprise a first-time user)"),
 ("F5",8,"150 ms draw-phase animation, no recomposition"),
 ("L1",7,"Minimal chrome, content leads; icons only when collapsed"),
 ("L2",8,"48 dp rail, 36 dp items, 8 dp gaps, identical on every screen"),
 ("L3",7,"22 dp icons, labels only when expanded: relies on icon recognition"),
 ("L4",8,"Expanded panel is drawn over content, nothing reflows"),
 ("C1",7,"Push and pop are directional (grow in with a fade / recede and dim, 240 ms Glide), tab switches and the player stay a fade. ⚠ Built and unit-tested, not watched on the TV."),
 ("C2",3,"Nothing travels between screens"),
 ("C3",8,"FocusMemory + saveable state restore the card; a channel page keeps its origin's rail item"),
 ("C4",7,"One transition language (direction = depth) for push and pop; fade for tabs and the player. ⚠ Built and unit-tested, not watched on the TV."),
 ("S4",8,"'X is live now' toasts are queued (repeat of the last pending one dropped, dwell 2 s, 1.2 s while others wait)"),
 ("R1",8,"Every spec snaps; system animator scale 0 honoured"),
 ("R2",9,"Draw-phase only, no offscreen layers"),
 ("R3",8,"Panel origin and shade mirrored in RTL; content descriptions on items"),
])

screen("2. Home", "HomeScreen.kt, CardRows.kt, BringIntoViewSpecs.kt, SnapshotCache", [
 ("M1",8,"Row scroll 200 ms, focus 150 ms, fresh-data fade 200 ms"),
 ("M2",7,"One decelerate curve for scroll and focus: no differentiated feel"),
 ("M3",6,"First four cards of each row fade + rise 8 dp at 0 / 40 / 80 / 100 ms after a loading state; skipped when content is already there. ⚠ Built and unit-tested, not watched on the TV."),
 ("M4",8,"Anchored scroll pins the focused row on one line; animateItem explains the cache-to-fresh swap"),
 ("M5",7,"Cards fade in / slide / fade out; section headers and the empty state just pop"),
 ("F1",8,"Shared ring, not covered by the edge fades"),
 ("F2",6,"1.05 on cards, 1.08 on category posters; title, viewers and avatar still do not react; no elevation"),
 ("F3",6,"Focus is clear; press has no feedback (indication = null)"),
 ("F4",9,"defaultFirst, focusRestorer, FocusMemory by key; Left leaves to the rail through the guard"),
 ("F5",9,"Draw-phase scale; emulator ui p99 2.46 ms"),
 ("L1",5,"No hero or focal point: three equal rows"),
 ("L2",8,"200 dp cards, 12 dp gap, 16 dp lead-in, header at a fixed anchor"),
 ("L3",7,"Two-line title, secondary text #C8C8D0 (4.5:1 or better); metadata is 15 sp"),
 ("L4",8,"27 dp bottom, 48 dp right, rows run off under a 32 dp fade, top-bar gradient"),
 ("L5",6,"Thumbnails are the only art; no backdrop or context imagery"),
 ("C1",6,"Category push grows in; pop mirrors; the player entry is still a fade ⚠ Built and unit-tested, not watched on the TV."),
 ("C2",3,"The pressed card does not travel into the category or the player"),
 ("C3",9,"Focus restored to the exact card (by channel key) on return"),
 ("C4",6,"Directional push / pop shared with every screen; player entry still a fade ⚠ Built and unit-tested, not watched on the TV."),
 ("S1",7,"Three skeleton sections (static tone, card geometry) after 120 ms, no shimmer; snapshot still shows content at ~520 ms. ⚠ Built and unit-tested, not watched on the TV."),
 ("S2",7,"Empty 'following' row: icon + line, no action"),
 ("S3",8,"Loader keeps shown data on failure; retry takes focus"),
 ("S4",8,"Cached copy replaced by fresh: fade in, slide, fade out per card"),
 ("S5",7,"Stable Base placeholder, previous thumbnail kept as placeholder, 150 ms crossfade; no dominant-colour placeholder"),
 ("R1",9,"Motion off: nothing grows; 10 % white lift and a 1 dp thicker ring (focusLift); system animator 0 honoured"),
 ("R2",9,"Emulator: ui p50 1.27 / p99 2.46 ms, 0 frames over 5 ms"),
 ("R3",8,"Merged semantics on cards, headings, live count"),
])

screen("3. Follows", "FollowsScreen.kt", [
 ("M1",8,"Same specs as Home"),
 ("M2",7,"One curve"),
 ("M3",6,"First four cards stagger as Home. ⚠ Built and unit-tested, not watched on the TV."),
 ("M4",6,"Anchored scroll as Home; nothing explains a channel going live or offline"),
 ("M5",6,"Grid items animateItem (fade in / out, slide); the offline row and headers still pop. ⚠ Built and unit-tested, not watched on the TV."),
 ("F1",8,"Shared ring"),
 ("F2",6,"Offline avatars brighten 0.6 to 1.0 with focus: one of the few content responses in the app"),
 ("F3",7,"Offline dimmed at rest vs live cards: clear; no press feedback"),
 ("F4",7,"⚠ Grid, Up / Down by row, Left to the rail; focus memory assumed same as Home"),
 ("F5",8,"Draw-phase"),
 ("L1",6,"Live cards then offline tiles: clear split, but no heading weight"),
 ("L2",8,"4 columns x 200 dp, same gutters"),
 ("L3",7,"Same type as Home"),
 ("L4",8,"Same safe margins"),
 ("L5",6,"Thumbnails and avatars only"),
 ("C1",6,"Channel push grows in, pop mirrors ⚠ Built and unit-tested, not watched on the TV."),
 ("C2",3,"Avatar does not travel into the channel page"),
 ("C3",7,"⚠ Restore assumed as Home"),
 ("C4",6,"Directional push / pop shared with every screen ⚠ Built and unit-tested, not watched on the TV."),
 ("S1",7,"Skeleton grid (4 columns, header) after 120 ms. ⚠ Built and unit-tested, not watched on the TV."),
 ("S2",8,"Signed-out and empty states have a focused action (LoadGate)"),
 ("S3",8,"Retry focused"),
 ("S4",6,"A channel going live fades in and the grid slides; offline tiles do not animate. ⚠ Built and unit-tested, not watched on the TV."),
 ("S5",7,"As Home"),
 ("R1",9,"Motion off: nothing grows; 10 % white lift and a 1 dp thicker ring (focusLift); system animator 0 honoured"),
 ("R2",8,"Grid not benchmarked separately (Browse grid p99 2.15 ms)"),
 ("R3",8,"Descriptions on tiles"),
])

screen("4. Browse", "BrowseScreen.kt", [
 ("M1",8,"As Home"),
 ("M2",7,"One curve"),
 ("M3",6,"First four posters stagger as Home. ⚠ Built and unit-tested, not watched on the TV."),
 ("M4",6,"Anchored scroll"),
 ("M5",5,"Grid items animateItem: filter and sort changes slide / fade posters. ⚠ Not watched on the TV; a whole-list swap may still read as a flash."),
 ("F1",8,"Ring on poster"),
 ("F2",6,"1.08 on 120 dp posters (about 5 dp per side): the ring no longer does all the work"),
 ("F3",8,"Selected chip = tonal purple fill + check, focus = ring"),
 ("F4",8,"Chips to grid, Left to the rail"),
 ("F5",9,"Emulator ui p99 2.15 ms over 67 frames"),
 ("L1",6,"Chips over a poster wall: clear, flat"),
 ("L2",8,"7 columns x 120 dp, 3:4 posters, fixed gutters"),
 ("L3",6,"One-line name ellipsised at 120 dp; long names truncate"),
 ("L4",8,"Top padding reserves chip row; end padding aligned to safe area"),
 ("L5",8,"Poster art is the content"),
 ("C1",6,"Category push grows in, pop mirrors ⚠ Built and unit-tested, not watched on the TV."),
 ("C2",2,"Poster does not travel into the Category header (the clearest shared-element case in the app)"),
 ("C3",8,"Focus restored"),
 ("C4",6,"Directional push / pop shared with every screen ⚠ Built and unit-tested, not watched on the TV."),
 ("S1",7,"Skeleton wall (chips + 7 columns of posters) after 120 ms. ⚠ Built and unit-tested, not watched on the TV."),
 ("S2",6,"⚠ Not read in detail"),
 ("S3",8,"Retry focused"),
 ("S4",5,"Paged data: ⚠ append animation not verified"),
 ("S5",7,"Placeholder + crossfade"),
 ("R1",9,"Motion off: nothing grows; 10 % white lift and a 1 dp thicker ring (focusLift); system animator 0 honoured"),
 ("R2",9,"Measured, 0 frames over 5 ms"),
 ("R3",8,"Descriptions per poster"),
])

screen("5. Category", "CategoryScreen.kt", [
 ("M1",8,"As Home"),
 ("M2",7,"One curve"),
 ("M3",6,"First four cards stagger as Home. ⚠ Built and unit-tested, not watched on the TV."),
 ("M4",6,"Header stays under the grid scroll"),
 ("M5",6,"Grid items animateItem; language chip reload ⚠ still not watched on the TV."),
 ("F1",8,"Ring"),
 ("F2",6,"1.05 on 200 dp cards"),
 ("F3",8,"Language chip names what is listed now; OK toggles"),
 ("F4",8,"Back button gets initial focus (LaunchedBackFocus)"),
 ("F5",8,"Draw-phase"),
 ("L1",7,"80 dp header with art + name gives context"),
 ("L2",8,"4 columns, shared gutters"),
 ("L3",7,"Same type scale"),
 ("L4",7,"HEADER_HEIGHT lets the grid scroll under the header"),
 ("L5",6,"Header art is small relative to the page"),
 ("C1",6,"Grows in from Browse; Back mirrors it (recedes) ⚠ Built and unit-tested, not watched on the TV."),
 ("C2",2,"Header art is the poster just pressed and it does not travel"),
 ("C3",7,"Focus restored on back"),
 ("C4",6,"Directional push / pop shared with every screen ⚠ Built and unit-tested, not watched on the TV."),
 ("S1",7,"Skeleton grid below the header (header stays) after 120 ms. ⚠ Built and unit-tested, not watched on the TV."),
 ("S2",6,"Category with no live streams: ⚠ unverified"),
 ("S3",8,"Retry focused"),
 ("S4",6,"Streams arriving or leaving fade / slide; viewer counts still update silently. ⚠ Built and unit-tested, not watched on the TV."),
 ("S5",7,"Placeholder + crossfade"),
 ("R1",9,"Motion off: nothing grows; 10 % white lift and a 1 dp thicker ring (focusLift); system animator 0 honoured"),
 ("R2",8,"Same grid pipeline as Browse"),
 ("R3",8,"Descriptions"),
])

screen("6. Channel", "ChannelScreen.kt", [
 ("M1",7,"No timing of its own: inherits focus and scroll only"),
 ("M2",7,"Inherited"),
 ("M3",3,"All sections appear at once"),
 ("M4",4,"No motion links header, schedule, replays, clips"),
 ("M5",4,"Sections pop; no reveal"),
 ("F1",8,"Shared ring"),
 ("F2",6,"Buttons 1.06, clip cards 1.05; schedule rows have no focus response (non-focusable)"),
 ("F3",8,"Watch button: pressed tone (PurplePressed) and a focus fill shift on top of the ring; follow state distinct"),
 ("F4",7,"Row spec keeps the next row peeking; long page means many Down presses to reach clips"),
 ("F5",8,"Draw-phase"),
 ("L1",6,"Header and action buttons lead, then five stacked sections"),
 ("L2",7,"Mixed widths: 200 dp live, 180 dp replays, list schedule"),
 ("L3",7,"Info chips and schedule: time column fixed, next slot brighter"),
 ("L4",8,"SafeTop padding, peek = SafeBottom + 24 dp"),
 ("L5",5,"No banner or large avatar hero; the page is text-led"),
 ("C1",6,"Push from Follows / Search / Category grows in; pop mirrors ⚠ Built and unit-tested, not watched on the TV."),
 ("C2",2,"Avatar does not travel from the card"),
 ("C3",8,"restoreClip: coming back from a clip refocuses its card"),
 ("C4",6,"Directional push / pop shared with every screen ⚠ Built and unit-tested, not watched on the TV."),
 ("S1",7,"Skeleton header + clip row after 120 ms. ⚠ Built and unit-tested, not watched on the TV."),
 ("S2",6,"Empty schedule / clips hide their section ⚠ not verified"),
 ("S3",8,"Retry focused"),
 ("S4",5,"Live state change silent"),
 ("S5",7,"Placeholder + crossfade"),
 ("R1",9,"Motion off: nothing grows; 10 % white lift and a 1 dp thicker ring (focusLift); system animator 0 honoured"),
 ("R2",8,"LazyColumn with typed rows"),
 ("R3",8,"Schedule rows read as one sentence"),
])

screen("7. Search (keyboard, results, voice)", "SearchScreen.kt, Keyboard.kt, SearchResultRows.kt, VoiceDialog.kt", [
 ("M1",8,"Key tint 150 ms, voice pulse 1.6 s loop, bars 600-1100 ms: each right for its job"),
 ("M2",8,"FastOutSlowIn on bars, linear on the caret blink, EaseOut on UI"),
 ("M3",8,"Bar durations offset by index x 37 ms: organic, not mechanical"),
 ("M4",8,"Pulse and bars say 'listening', caret blink says 'input'"),
 ("M5",5,"Results list swaps with no entry or exit"),
 ("F1",8,"Ring plus key fill"),
 ("F2",8,"Key glyph cross-fades tint to white; result chevrons reveal with focus"),
 ("F3",8,"Armed Shift = purple glyph; disabled keys dimmed"),
 ("F4",9,"Left from results redirects to the keyboard group; no traps"),
 ("F5",9,"Emulator ui p99 1.93 ms"),
 ("L1",7,"Query box on top, keyboard left, results right (same split as YouTube TV)"),
 ("L2",7,"Fixed key grid, 6 dp result gaps"),
 ("L3",6,"26 dp glyphs, 28 dp tall recents: small at 3 m"),
 ("L4",8,"Content start / end constants"),
 ("L5",5,"Rows with a 36 dp avatar: little art"),
 ("C1",6,"Voice dialog and composer share the same equalizer bars"),
 ("C2",3,"Result does not travel into channel or category"),
 ("C3",6,"⚠ Return from a channel: query kept, result focus restore unverified"),
 ("C4",6,"Dialogs consistent with the rest"),
 ("S1",6,"⚠ Debounce, no skeleton row"),
 ("S2",7,"'No result' header variant exists"),
 ("S3",6,"⚠ Not read in detail"),
 ("S4",5,"Results swap, no cross-fade"),
 ("S5",7,"Avatar initial fallback"),
 ("R1",8,"Bars rest at 0.5 scale, pulse off"),
 ("R2",9,"Measured"),
 ("R3",8,"Voice on the mic key; descriptions"),
])

screen("8. Settings / Profile", "SettingsScreen.kt, TileCard.kt", [
 ("M1",8,"Switch thumb 150 ms"),
 ("M2",7,"EaseOut"),
 ("M3",4,"Tiles appear together"),
 ("M4",7,"Thumb travel shows the state change; RTL-aware direction"),
 ("M5",5,"Toggle and segment values change but nothing confirms the change"),
 ("F1",7,"Fill change plus ring; fill is subtle on dark tiles"),
 ("F2",5,"focusScale = 1 on tiles: no growth, fill only (flatter than the rest of the app)"),
 ("F3",8,"Switch state, segment, focus all distinct"),
 ("F4",8,"firstFocus requester, tile order"),
 ("F5",8,"Draw-phase"),
 ("L1",6,"Tile grid with no grouping or section headers"),
 ("L2",7,"280 x 156 dp tiles"),
 ("L3",8,"32 dp icons, one-word labels"),
 ("L4",7,"Content start minus 8 dp lead-in"),
 ("L5",5,"Icons only"),
 ("C1",4,"Fade"),
 ("C2",2,"Nothing travels"),
 ("C3",7,"Focus and values persist"),
 ("C4",5,"Flat"),
 ("S1",7,"Local state, nothing to load"),
 ("S3",7,"⚠ Sign-in dialog path not read in detail"),
 ("S4",6,"Values apply live; chat tile previews the real layout"),
 ("R1",8,"Snaps; RTL switch direction"),
 ("R2",7,"One deliberate Offscreen layer (SettingsScreen.kt:333): ⚠ cost on the TCL not measured"),
 ("R3",8,"36-language picker, RTL"),
])

screen("9. Player: full screen + control bar", "PlayerScreen.kt, PlayerControlBar.kt, ControlBarButton.kt", [
 ("M1",9,"Bar in 200 ms, out 150 ms, 12 dp rise"),
 ("M2",8,"toward(): decelerate in, accelerate out"),
 ("M3",6,"Bar and header fade together; no stagger across buttons"),
 ("M4",8,"2 dp line along the bottom marks the hidden bar: motion plus a resting cue"),
 ("M5",8,"Bar, header, rows, overlays all animate both ways"),
 ("F1",8,"Ring on every control"),
 ("F2",7,"Buttons scale 1.06, fill and glyph swap"),
 ("F3",8,"Disabled = struck glyph, no fill, dimmed ring"),
 ("F4",8,"First key wakes the bar and returns to the last button; Right enters chat"),
 ("F5",9,"Draw-phase"),
 ("L1",8,"Video is the focal point; bar anchored at the left safe margin"),
 ("L2",8,"40 dp buttons, 8 dp gaps"),
 ("L3",7,"22 dp glyphs, no labels: learned icons"),
 ("L4",8,"Safe margins; bar never re-centred by chat mode"),
 ("L5",9,"Video first"),
 ("C1",8,"Rows slide beneath as one sheet (one graphicsLayer translation)"),
 ("C2",8,"One video surface from mini to full screen"),
 ("C3",8,"Session keeps playing as a mini player while you browse"),
 ("C4",8,"Same glide family as chat"),
 ("S1",6,"⚠ Buffering overlay not read in detail"),
 ("S3",6,"⚠ Error overlay exists, not read in detail"),
 ("S4",7,"Ad and raid pills"),
 ("R1",8,"Snaps"),
 ("R2",7,"Compose spikes measured on old chat (p99 11 ms); ⚠ re-measure on a compiled TCL install"),
 ("R3",8,"Control descriptions"),
])

screen("10. Player: side chat + mini / full glide", "PlayerScreen.kt L314 & L738-775, ChatPanel.kt, TvMotion.glide", [
 ("M1",9,"Glide 380 ms in, 280 ms out"),
 ("M2",9,"(0.05, 0.7, 0.1, 1) = Material emphasized decelerate; GlideIn for the exit"),
 ("M3",8,"Waits for steady frames, video resizes once, chat slides after"),
 ("M4",9,"Slide and resize show that chat takes space from the video"),
 ("M5",9,"Show, hide, overlay mode, dim all animate"),
 ("F1",7,"Chat list ring, no scale"),
 ("F2",6,"Ring only (by design)"),
 ("F3",8,"'Back to live' pill purple only while the list has focus"),
 ("F4",8,"Entered with Right, never steals focus"),
 ("F5",8,"Hold-Up/Down history scroll ui p99 6.5 ms"),
 ("L1",8,"Video keeps at least 2/3 of the width; chat 24 / 28 %"),
 ("L2",8,"8 / 24 dp insets, line height x1.3, 3 / 5 / 8 dp gaps"),
 ("L3",8,"14 / 15 / 17 sp, nickname lightened to 4.5:1 on Base"),
 ("L4",8,"Background to the screen edge, text to the safe margin"),
 ("L5",7,"Opacity 40-100 % setting"),
 ("C1",9,"Chat comes from the side it lives on (RTL aware)"),
 ("C2",10,"Mini to full screen is one continuous animation: rare on TV"),
 ("C3",8,"Chat position and messages kept while hidden"),
 ("C4",9,"Mode changes all use glide"),
 ("S1",6,"⚠ Connecting state not read in detail"),
 ("S3",7,"⚠ Reconnect covered by unit tests, not by a visual check"),
 ("S4",8,"Messages batched every 250 ms, jump to bottom; history scroll 150 ms"),
 ("S5",8,"Emote stills at exact slot size; animated emotes within a budget"),
 ("R1",9,"Animated emotes are content with their own option"),
 ("R2",7,"Known residual chat-show spike; 8 animated emotes: ui p99 ~10 ms emulator"),
 ("R3",8,"Row announced as one sentence; RTL placement"),
])

screen("11. Player: zapper + overlays (ad, raid, stats)", "ChannelZapper.kt, PlayerOverlays.kt, PlayerScreen.kt L879-897", [
 ("M1",8,"Pill swap 200 ms, stats 200 ms"),
 ("M2",8,"Decelerate in, accelerate out"),
 ("M3",7,"Pill swap = fade + small slide; zapper cards in view enter at 0 / 40 / 80 / 100 ms. ⚠ Built and unit-tested, not watched on the TV."),
 ("M4",9,"One pill at a time by priority; the ad holds the corner, notices wait"),
 ("M5",8,"Chips scale 0.8 in and out; stats and pills fade"),
 ("F1",7,"Zapper cards use the ring"),
 ("F2",6,"1.05 on 152 dp cards"),
 ("F3",8,"Overlays are non-focusable by rule"),
 ("F4",8,"CHANNEL +/- zaps from anywhere"),
 ("F5",6,"Opening composes ~11 image cards: measured spike on the old build"),
 ("L1",8,"Corner pill and stats block never overlap"),
 ("L2",8,"3 x 2 stats cells, 16 / 13 sp"),
 ("L3",8,"16 sp on Black85 over video"),
 ("L4",8,"Inside the safe area, stats move under the pill on narrow video"),
 ("L5",7,"Dim chat under zapper (flat veil)"),
 ("C1",7,"Sheet translation keeps the player visible"),
 ("C2",6,"Zapped channel card does not become the video"),
 ("C3",8,"Returns to the same channel"),
 ("C4",7,"Same family as chat glide"),
 ("S1",6,"⚠ Zapper loading not read in detail"),
 ("S3",6,"⚠ Unverified"),
 ("S4",8,"Raid auto-join in 6 s, ad countdown, 12 s notice"),
 ("S5",7,"Cards share TvAsyncImage"),
 ("R1",8,"Snaps"),
 ("R2",6,"Zapper open = new subtree compose; spike measured"),
 ("R3",8,"RTL start / end pills"),
])

screen("12. Clip player", "ClipScreen.kt", [
 ("M1",8,"Chrome in 200 ms, out 150 ms, glyph 150 ms"),
 ("M2",8,"EaseOut / EaseIn"),
 ("M3",5,"Title, progress and hints fade as one block"),
 ("M4",7,"Glyph scale-in 1.2 to 1 = state change"),
 ("M5",7,"Chrome and glyph animate both ways"),
 ("F1",7,"Progress handle visible"),
 ("F2",6,"No buttons: keys act directly"),
 ("F3",7,"Paused / finished: glyph and bar stay"),
 ("F4",7,"OK, Left, Right, Back only: predictable"),
 ("F5",8,"Light layer"),
 ("L1",9,"No chrome until a key"),
 ("L2",8,"Bottom bar aligned to the safe area"),
 ("L3",8,"m:ss / m:ss, two key hints"),
 ("L4",8,"Safe margins"),
 ("L5",10,"Full-bleed video"),
 ("C1",5,"Fade"),
 ("C2",3,"Clip thumbnail does not become the video"),
 ("C3",8,"Back refocuses the clip card"),
 ("C4",5,"Flat"),
 ("S1",6,"⚠ Resolving state not read in detail"),
 ("S3",6,"⚠ Not read in detail"),
 ("R1",8,"Snaps"),
 ("R2",8,"Single layer"),
 ("R3",7,"Progress from the right in RTL; hints keep physical arrows"),
])

screen("13. Dialogs, toast, loading / empty / error", "TvDialog.kt, TvToast.kt, LoadGate.kt, EmptyState.kt, TvSpinner.kt", [
 ("M1",7,"Dialog 200 ms fade, toast 200 in / 150 out"),
 ("M2",8,"Dialog panel 0.96 to 1 scale + 8 dp rise (EaseOut in, EaseIn out); toast fade + 8 dp rise"),
 ("M3",7,"Scrim (150 ms) and panel (200 ms) on separate clocks; the veil outlasts the panel on exit. ⚠ Built and unit-tested, not watched on the TV."),
 ("M4",4,"Nothing says where the dialog came from"),
 ("M5",6,"Dialog exits through DialogClose (Back and its own close / cancel / pick actions); dialogs removed by state (voice result, option rows) still vanish. A toast queues instead of re-animating. ⚠ Built and unit-tested, not watched on the TV."),
 ("F1",8,"Ring on actions"),
 ("F2",6,"Shared ring; primary buttons shift fill, chips and buttons press"),
 ("F3",9,"Primary purple vs Alt2; focused primary lighter, pressed darker; selected distinct"),
 ("F4",10,"Own window traps focus, Back dismisses, focus restored"),
 ("F5",8,"Draw-phase"),
 ("L1",7,"320 dp dialog, title + rows"),
 ("L2",7,"24 dp padding, 16 dp gaps"),
 ("L3",8,"Short-copy rule: title + one line"),
 ("L4",7,"Centred, toast 60 dp from the bottom"),
 ("C1",3,"No origin"),
 ("C3",9,"Focus returns to the previous element"),
 ("C4",5,"Fade, same language"),
 ("S1",7,"Skeletons on Home, Follows, Browse, Category, Channel after 120 ms; spinner remains on the clip screen. ⚠ Built and unit-tested, not watched on the TV."),
 ("S2",7,"Icon + title (+ line) + focused action"),
 ("S3",8,"Retry takes focus"),
 ("S4",7,"Toast queue: in order, repeat dropped, shorter dwell while others wait"),
 ("R1",9,"Motion off: nothing grows; 10 % white lift and a 1 dp thicker ring (focusLift); system animator 0 honoured; toast and dialog snap"),
 ("R2",9,"Flat veil, no blur"),
 ("R3",8,"Polite live region, paneTitle"),
])

def mean(xs): return sum(xs)/len(xs)
def r5(x): return round(x*2)/2

out = []
w = out.append
w("# UI / Motion / Layout audit (detailed): 2026-11-08\n")
w("Branch `feature/tv-ui-redesign` after `74deb4b`, with backlog items 1, 2, 3, 6, 7, 8, 9 and 10 implemented (see section 6; scores that changed carry ⚠ unless they are plain facts of the code) and the uncommitted NavRail / PlayerScreen / TwitchTvApp edits left alone. Generated by a script so every average is computed, not typed.\n")
w("## How to read this\n")
w("- **27 sub-criteria** in 6 categories, each scored 0-10 on every screen where it applies (a missing row = not applicable).")
w("- **Category score** = mean of its sub-criteria on that screen. **Overall** = weighted mean of the categories (weights below), rounded to 0.5.")
w("- **Anchors:** 0-2 absent or broken · 3-4 weak · 5-6 works, generic · 7-8 polished · 9-10 reference quality (Apple TV / Netflix level).")
w("- **⚠ = unverified.** I read the code, I did not watch the app on the TV. A ⚠ row is capped at 7 unless a measurement backs it. Re-score those on the TCL first.")
w("- **Evidence sources:** the Kotlin files named under each screen, `docs/PERFORMANCE.md` measurements (emulator numbers, not TCL), and public guidance listed at the end. No source found gives official millisecond values for TV focus motion, so timing anchors are working targets, not specs.\n")
w("### Category weights\n")
w("| Code | Category | Weight |\n|---|---|---|")
for k,(n,wt) in CATS.items(): w(f"| {k} | {n} | {wt} % |")
w("\n### The 27 sub-criteria: what a 10 looks like\n")
w("| ID | Sub-criterion | A 10 means |\n|---|---|---|")
for i,n,d in RUBRIC: w(f"| {i} | {n} | {d} |")

# compute
results = []
for name, files, rows in SCREENS:
    cat = {}
    for i,s,_ in rows: cat.setdefault(i[0], []).append(s)
    cm = {k: mean(v) for k,v in cat.items()}
    tw = sum(CATS[k][1] for k in cm)
    ov = sum(cm[k]*CATS[k][1] for k in cm)/tw
    results.append((name, files, rows, cm, ov))

w("\n## 1. Scoreboard\n")
w("| Screen | M | F | L | C | S | R | **Overall** | ⚠ |\n|---|---|---|---|---|---|---|---|---|")
for name, files, rows, cm, ov in results:
    unv = sum(1 for _,_,n in rows if n.startswith("⚠") or "⚠" in n)
    w(f"| {name} | " + " | ".join(f"{cm[k]:.1f}" if k in cm else "–" for k in CATS) + f" | **{r5(ov):.1f}** | {unv} |")
app = mean([r[4] for r in results])
w(f"\n**App average: {app:.1f} / 10** (unweighted mean of the {len(results)} screens). Screens are listed in navigation order; the player screens score highest because their motion was designed as a system, the browsing screens are tidy but generic.\n")

# systemic
sub = {}
for _,_,rows,_,_ in results:
    for i,s,_ in rows: sub.setdefault(i, []).append(s)
rank = sorted(sub.items(), key=lambda kv: mean(kv[1]))
names = {i:n for i,n,_ in RUBRIC}
w("## 2. Systemic view: weakest and strongest sub-criteria across all screens\n")
w("| Rank | ID | Sub-criterion | Mean | Min | Max | Screens |\n|---|---|---|---|---|---|---|")
for n,(i,v) in enumerate(rank[:10],1): w(f"| {n} | {i} | {names[i]} | {mean(v):.1f} | {min(v)} | {max(v)} | {len(v)} |")
w("\nStrongest:\n")
w("| Rank | ID | Sub-criterion | Mean |\n|---|---|---|---|")
for n,(i,v) in enumerate(reversed(rank[-5:]),1): w(f"| {n} | {i} | {names[i]} | {mean(v):.1f} |")

w("\n## 3. Screen by screen\n")
for name, files, rows, cm, ov in results:
    w(f"### {name}: {r5(ov):.1f} / 10\n")
    w(f"Files: `{files}`  ")
    w("Categories: " + " · ".join(f"**{k}** {CATS[k][0]} {cm[k]:.1f}" for k in CATS if k in cm) + "\n")
    w("| ID | Sub-criterion | Score | Evidence / what is missing |\n|---|---|---|---|")
    for i,s,n in rows: w(f"| {i} | {names[i]} | {s} | {n} |")
    low = sorted([r for r in rows], key=lambda r: r[1])[:3]
    w("\nLowest three: " + "; ".join(f"{i} {names[i]} ({s})" for i,s,_ in low) + ".\n")

# components
w("## 4. Component sheet (every reusable part)\n")
w("Scored on the sub-criteria that apply to a part. `–` = not applicable. F = focus, M = motion, L3 = legibility, R = robustness.\n")
w("| Component | F1 vis | F2 resp | F3 state | M1 time | M2 ease | L3 legib | R1 reduced | R2 budget | Notes |\n|---|---|---|---|---|---|---|---|---|---|")
comps = [
 ("StreamCard (live card)",8,6,7,8,7,7,9,9,"TvFocusable ring + 1.05 (size-aware). Title, avatar, viewer count inert on focus. LIVE badge = red + the word, never colour alone. Two-line title reserved (minLines = 2) so rows never jump."),
 ("CategoryCard (poster)",8,6,7,8,7,6,9,9,"Ring on the poster only, scale 1.08 on the whole card. 120 dp wide: name truncates."),
 ("Clip / Replay / Similar cards",8,6,7,8,7,7,9,9,"Clip cards 1.05, avatar tiles 1.04, replays not focusable; no focus-time info reveal (duration, views)."),
 ("Chip",8,6,9,8,7,8,9,9,"Selected = tonal purple fill + check, focus = ring + 1.06, press = 22 % veil: best state separation in the system."),
 ("IconChip",8,6,9,8,7,6,9,9,"44 x 36 dp, 18 dp glyph: small for a 3 m target. Press veil as Chip."),
 ("TvButton",8,6,8,8,7,8,9,9,"Primary lightens to PurpleFocused on focus and darkens to PurplePressed on press; others get a 22 % veil on press; scale 1.06."),
 ("ControlBarButton",8,8,9,8,8,6,8,9,"Scale 1.06 + fill + glyph cross-fade; disabled variant quieter. Icon-only."),
 ("NavRail item",8,6,9,8,7,7,8,9,"Selected bar + glyph, expanded labels, scale only when collapsed."),
 ("Keyboard key",8,9,9,8,8,6,8,9,"Tint to white glyph cross-fade tied to focus progress; Shift armed state."),
 ("Settings switch",7,5,8,8,7,8,8,8,"Thumb translation 150 ms, direction mirrored in RTL."),
 ("TileCard (settings)",7,5,8,8,7,8,8,7,"Fill change only (scale 1), one Offscreen layer in the screen."),
 ("Chat line",-1,-1,8,-1,-1,8,9,7,"Highlight / mention / bits distinct without colour alone; emote budget; no per-line animation by design."),
 ("Toast",-1,-1,-1,7,6,8,8,9,"Fade + 8 dp rise; queue (repeat dropped, shorter dwell while others wait); polite live region."),
 ("Dialog",8,5,8,7,8,8,9,9,"0.96 to 1 scale + 8 dp rise, scrim on its own clock, exit animation via DialogClose; focus trap is excellent."),
 ("Spinner",-1,-1,-1,7,8,-1,9,9,"Linear 1.1 s rotation (correct for a spinner); static arc when reduced; delayed 300 ms by LoadGate."),
 ("SectionHeader",-1,-1,-1,-1,-1,8,8,9,"Icon + short label + live-count pill (red dot + number): readable at a glance."),
 ("TvAsyncImage",-1,-1,-1,8,7,-1,8,9,"Decodes at target size, keeps the previous image as placeholder, 150 ms crossfade; flat colour placeholder, no blurhash."),
 ("Avatar / LiveBadge",-1,-1,-1,-1,-1,8,-1,9,"Initial fallback, decorative semantics; badge 2 dp corners, red only for live."),
]
for c in comps:
    cells = ["–" if (isinstance(x,int) and x<0) else str(x) for x in c[1:9]]
    w(f"| {c[0]} | " + " | ".join(cells) + f" | {c[9]} |")

w("""
## 5. Reference points (what the scores are measured against)

- **Focus:** Android TV defines focus by scale, border, glow and colour change; the Compose TV standard card zoom is reportedly about 1.1x; tvOS guidance gives 1.05-1.1x plus shadow or brightness and layered parallax. This app: size-aware 1.05-1.08 + ring (posters 1.08, cards 1.05, controls 1.06), so F2 stays at 5-6 until focus also reveals content or adds elevation.
- **Tile motion:** Netflix-style tiles animate transform and opacity only and reveal more information on focus. This app matches the technique; it does not match the information reveal.
- **Easing:** Material's emphasized decelerate `cubic-bezier(0.05, 0.7, 0.1, 1)` is what `TvMotion.Glide` uses, hence M2 9 on the chat glide.
- **Limits:** no official millisecond table for TV focus motion was found; no teardown of Disney+, Prime Video or YouTube TV was found. Those comparisons are design-pattern knowledge (focus-driven hero, shared poster transition), not sourced numbers.

## 6. Improvement backlog, tied to sub-criteria

| # | Change | Raises | Screens | Effort | Status |
|---|---|---|---|---|---|
| 1 | Directional push / pop transition (incoming 4 % scale + fade, outgoing 3 % scale + dim, 240 ms Glide); keep a fade for rail switches | C1, C4, M5 | all but player | S | Done (`ScreenTransitions.kt`); player entry and tab switches stay a fade; not watched on the TV |
| 2 | Skeleton cards (static tone, layout-matched) instead of a delayed spinner | S1 | Home, Follows, Browse, Category, Channel | S | Done (`Skeletons.kt`, `LoadGate`); geometry matched by dimensions, not compared on screen |
| 3 | Dialog 0.96 to 1 scale + 8 dp rise, add an exit, toast queue | M2, M3, M5 | dialogs | S | Done; the exit runs for Back and the dialogs' own close actions, not when the caller removes the dialog by state |
| 4 | Shared element: poster to Category header, avatar to Channel header | C2, M4 | Browse, Category, Follows, Channel | M | Skipped: Compose shared elements animate bounds through layout, a hand-made draw-phase flight cannot be checked without the TV |
| 5 | Focus-driven backdrop on Home | L1, L5, F2 | Home | M | Skipped: a full-screen image behind scrolling rows adds overdraw the TV GPU has already shown to matter, and its look and cost need the TV |
| 6 | Stagger the first four cards (STAGGER_MS), zapper open | M3 | Home, grids, zapper | S | Done (`Stagger.kt`): delays 0 / 40 / 80 / 100 ms, only after a loading state |
| 7 | animateItem on Follows / Browse / Category grids | M5, S4 | grids | S | Done (`ItemMotion.kt`, shared with the rows) |
| 8 | Size-aware focus scale (posters 1.08, cards 1.05, controls 1.06) + quiet shadow tone | F2 | all | S | Done for the scale; the shadow tone was not part of the request and is not done |
| 9 | Reduced-motion alternative cue (brightness / border) instead of snap | R1 | all | S | Done in `TvFocusable`: no scale, 10 % lift, ring +1 dp |
| 10 | Pressed state for TvButton / chips (tonal response 80 ms) | F3 | buttons, chips | S | Done; TvButton primary also gets a focus fill |
| 11 | Skip-seek animation in clips; 'Back to live' bounce | M4, M3 | Clip, Player | S | Open |
| 12 | Verify every ⚠ row on the TCL and re-score | all ⚠ | all | S | Open, and now covers the rows changed above |

## 7. How to re-score

1. Record each screen on the TCL at 60 fps; step frame by frame for M1-M5 and C1-C4.
2. Edit the data in `docs/gen_ui_audit.py` (scores and one-line evidence), clear the ⚠ when verified, re-run to regenerate this file.
3. Re-run `benchmark/tools/replay-keys.sh` for R2. A change that adds frames over 5 ms on the UI thread lowers R2 by 1.
4. Keep this file in git: the diff of the scoreboard is the history.

## Sources

- [Android TV focus system](https://developer.android.com/design/ui/tv/guides/styles/focus-system)
- [Build adaptive apps for TV](https://developer.android.com/develop/adaptive-apps/guides/tv/build-adaptive-apps-for-tv)
- [Leanback to Compose TV migration playbook (third party, 1.1x card scale)](https://www.claudecodehq.com/playbooks/leanback-to-compose-tv-migration)
- [tvOS design guidelines summary (third party)](https://skills.cat/skills/ehmo/platform-design-skills/tvos-design-guidelines)
- [Netflix redesign performance write-up (Wealthfront engineering)](https://eng.wealthfront.com/2015/06/30/implementing-netflix-redesign/)
- [Re-creating Netflix's hover animation (CSS-Tricks)](https://css-tricks.com/how-to-re-create-a-nifty-netflix-animation-in-css/)
- [Material motion: duration and easing](https://m1.material.io/motion/duration-easing.html)
- [Material Components Android: Motion theming](https://github.com/material-components/material-components-android/blob/master/docs/theming/Motion.md)
""")
open(__import__("os").path.join(__import__("os").path.dirname(__import__("os").path.abspath(__file__)),"UI_MOTION_AUDIT.md"),"w").write("\n".join(out))
