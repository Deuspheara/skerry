# Twitch TV Design System

Status: research baseline for a third-party Twitch client; not an official Twitch product. This document describes a TV adaptation, not a claim of endorsement. Confirm brand asset licensing before distribution.

Reference: Twitch's own system is Core UI (version "Ultraviolet", 2019): semantic design tokens stored as JSON and built with StyleDictionary, light and dark themes, 15-step gray and purple ramps, a "creator" color token, the Roobert typeface and a 200+ icon set ([Beyond Purple](https://blog.twitch.tv/en/2019/12/03/beyond-purple/)). Its token values are not public, so every value below is an adaptation for TV, structured like Core UI but not copied from it.

## Principles

- Keep live video primary and browsing calm, dark, and content-led.
- Use Twitch's actual brand purple and logo only from the official brand asset package; do not recolor, distort, or imply affiliation.
- Make every action discoverable by D-pad focus, with no hover-only meaning.
- Prefer large artwork and short metadata at sofa distance; defer secondary details to channel pages.
- Preserve the official embedded player, its advertisements, controls, and visibility requirements.

## Tokens

Use these as initial Compose tokens, then compare against the current official brand asset package and obtain visual review before release. Brand purple follows the value shown on brand.twitch.tv (`#9146FF`; verify against the downloaded brand package before release). All other values, including every dark surface, are TV adaptation values, not official brand colors.

Tokens are semantic, like Core UI: name them by role, not by hex. Background layers go from lowest to highest: Body, Base, Alt, Alt 2. Ship a dark theme first; keep names theme-agnostic so a light theme can be added later.

| Token | Value | Use |
|---|---|---|
| Brand purple | `#9146FF` | Primary actions, focused accents |
| Purple hover/pressed | `#772CE8` | Action interaction (adaptation) |
| Background Body | `#0E0E10` | Main canvas, lowest layer |
| Background Base | `#18181B` | Cards and panels |
| Background Alt | `#1F1F23` | Dialogs, menus |
| Background Alt 2 | `#26262C` | Highest layer, selected rows (adaptation) |
| Divider | `#303032` | Subtle separation |
| Text primary | `#EFEFF1` | Titles and body |
| Text secondary | `#ADADB8` | Metadata |
| Text muted | `#7D7D86` | Tertiary labels |
| Live red | `#EB0400` | Live state only |
| Focus ring | `#BF94FF` | D-pad focus outline |

Check contrast for each actual pairing; never rely on color alone to convey live, selected, or error state.

## Typography

Twitch's own typeface is Roobert (a geometric sans-serif by Displaay). It is proprietary, so do not bundle or imitate it without a license. Use a bundled, freely licensed geometric or neutral sans-serif (for example Inter, or the Android system font as fallback); Twitch brand guidance governs official wordmarks, not the app's general UI font. TV starting scale: display 36/42 semibold, title 28/34 semibold, section 22/28 semibold, body 18/26 regular, metadata 15/20 medium. Chat is the exception to the 16 sp floor: it is a dense, scrolling column read in a glance, 14 / 15 / 17 sp (user setting, 15 default) with 18-22 sp line height; keep the setting for a larger size. Use tabular numerals for viewer counts where available. Avoid all-caps for long labels.

## Spacing and shapes

Spacing base 4dp; tokens 4, 8, 12, 16, 24, 32, 48, 64dp. TV page gutters 48dp minimum, larger on 4K layouts. Cards use 8dp corner radius; compact chips 4dp; dialogs 12dp. Prefer square artwork and restrained rounding over pill-shaped cards. Avoid decorative borders; use surface contrast and spacing.

## Elevation and surfaces

Body → Base → Alt → Alt 2 is the hierarchy. Elevation is conveyed primarily by color, not shadows. The player remains unobscured; native overlays must sit outside its embed viewport. Use dividers only to clarify grouping.

## Iconography

Use a consistent 24dp line icon family, with clear silhouettes and labels for unfamiliar actions. Use only official Twitch logos/marks from the brand asset bundle and follow its clear-space, color, and placement guidance. Live status pairs a red dot/badge with the word “LIVE”. Subscriber/moderator/broadcaster badges come from Twitch badge metadata and art, not approximations.

## Components

| Component | Structure and purpose | States |
|---|---|---|
| TV navigation rail | Icon plus short label; persistent destinations | focused, selected, disabled |
| Search | Large field with visible prompt and native TV keyboard | idle, focused, entered, loading, empty/error |
| Live stream card | 16:9 thumbnail, live badge, title, channel/category, viewer count | default, focused (scale + ring), loading, unavailable |
| Category card | Artwork, category name, live viewer total when provided | default, focused, loading |
| Channel header | Avatar, name, live state, title/category/count, follow action | live/offline, followed, busy/error |
| Chat row | Badge images, colored username, rich text/emote fragments, optional time | normal, highlight, mention, system, bits; deleted/held where received |
| Chat composer | Text field and explicit send action | empty, editing, sending, sent/error |
| Dialog/menu | Short title, focused actions, explicit cancel/back behavior | open, focused item, dismissed |
| Loading/empty/error | Stable layout, concise status and retry where useful | loading, no results, recoverable error |

Cards should not show every available field. Use image placeholders with stable aspect ratios and avoid layout jumps.

## Player overlays added later (2026-11-07)

Over the video, never focusable, `Black85` surface, 2 dp corners, a 16 dp glyph says what kind of thing it is, one short line of text:

- **Top-start corner: one pill at a time**, by priority: ad break (`Ad · 18 s`), then what happened on the channel (raid, new category, stream ended). A notice returns when the ad ends. Raid says `Raiding X` plus the viewer count; with "Follow raids" on it says `Joining X…` and zaps after 6 s.
- **Top-end corner of the video: the stats block**, 3 x 2 cells (latency, video, bitrate over network, buffer, dropped), value 16 sp over label 13 sp for the couch. Off by default (Settings > Player). On a video narrower than 660 dp (a large chat column) it sits under the pill instead, so the two never overlap.
- **An ad break holds the corner**: a channel notice waits for it and its 12 s only count once the ad is over (`PlayerViewModel.setAdActive`). Changing pill cross-fades with a small slide; the ad countdown ticking does not re-animate. Mode chips scale in and out. All of it comes from `TvMotion` and snaps when animations are off.
- **Right-to-left**: the video sits on the side away from the chat (`placeRelative`), pills and the stats block follow the start / end edges, the clip progress line runs from the right, and key hints keep physical arrows (they name physical keys).
- **Coming back from a clip** restores focus to the clip's card and scrolls its row into view.
- **Chat header: room-mode chips** (slow and followers-only carry their value, subs / emotes / unique are glyphs only), after the "Chat" title; the row is announced as one sentence.
- **Write** is a control-bar button (always there, so the bar never reflows); it shows the chat first if it was hidden. The composer is the on-screen keyboard with Shift (one-shot capital, purple glyph while armed) and the microphone; Send is purple only once there is text; the counter appears only near the 500-character limit.
- **Clip player**: full screen, no chrome until a key is pressed, then title, `m:ss / m:ss`, a progress line with a handle and two key hints (`‹ › 10 s`, `OK`). Paused or finished: a large play / replay glyph in the middle and the bar stays.
- **Schedule rows** (channel page): time and day in a fixed column, title over category; the next one is brighter.

## Interaction states

Focused controls use a visible 2–3dp light-purple outline and a scale increase that depends on size (see Focus below: 1.05 to 1.08); focused text remains high contrast. Selected navigation gets a persistent marker distinct from focus. Press has a short tonal response; disabled state is visibly muted and announced. Restore focus after dialogs and channel transitions. Back closes the topmost overlay before leaving the current screen.

## TV adaptations

- Landscape-first layouts; one navigation rail and broad horizontal content rows.
- Keep initial visible actions within a few D-pad moves; avoid dense desktop sidebars.
- Player view assigns focus deliberately between native controls and embedded player controls. Never place native controls over the player viewport.
- Chat is optional, with a low-density readable mode and a no-chat viewing mode. Use bounded recent events only; do not imply history is available.
- Search supports voice input when the platform offers it, plus a concise on-screen keyboard path.
- Use predictable left/right row movement, up/down section movement, and Back behavior. Avoid focus traps and restore the originating card after returning.

## Accessibility

Meet WCAG AA contrast as a baseline; support system font scaling where layout allows, content descriptions for artwork/actions, semantics for live/viewer states, and screen-reader order matching visual order. Do not encode status by color alone. Focus indicators must remain visible at distance. Respect reduced-motion settings and provide a larger chat text option.

## References

- [Twitch Brand Assets](https://brand.twitch.tv/) (redirects to Twitch brand site)
- [Twitch Embedding requirements](https://dev.twitch.tv/docs/embed/)


## Implementation notes (validated prototype, 6 design rounds)

Source of truth for tokens in code: `core/designsystem/theme` (`TvColors`, `TvDimens`, `TvType`, `TvMotion`). The validated prototype is 1920x1080 px = 960x540 dp at 320 dpi, so every prototype px maps to px / 2 dp (type: px / 2 sp).

- **Secondary text** is `#C8C8D0` (not `#ADADB8`): >= 4.5:1 on Body, Base and Alt 2. `#A8A8B3` is the floor.
- **Focus**: one style everywhere (`core/ui/TvFocusable` + `FocusRing`): 2 dp `#BF94FF` ring (4 px on screen) outside a 1.5 dp dark gap, whole-element scale through the draw phase, **size-aware** (`TvDimens.FocusScale*`): the smaller the element, the more it grows. Small posters (category cards, 120 dp wide) 1.08, cards (live, clip, zapper, 150-200 dp wide) 1.05, controls (buttons, chips, control-bar buttons) 1.06, anything else 1.04; rows of tiles and list rows that fill the width stay at 1. **Reduced motion** (app setting or system animator scale 0): nothing grows; focus is shown by a 10 % white lift of the element and a ring 1 dp thicker, so focus never depends on movement. **Pressed** (buttons and chips): a short tonal response over the element (80 ms, `TvMotion.press`: purple darkens to `PurplePressed`, others get a 22 % black veil); the primary button also shifts to `PurpleFocused` while focused. Nothing in layout depends on focus. Selected (chip: purple fill + check; rail: purple glyph + bar) is never confused with focus.
- **Safe area**: 48 dp left/right, 27 dp top/bottom (`TvDimens`). Rows run off the right edge on purpose and fade out; the rail is full-bleed chrome like the prototype.
- **Motion**: 160 / 240 / 320 ms, `cubic-bezier(.2,.8,.2,1)` out, `(.4,0,1,1)` in. `LocalMotion.enabled` is false when the app setting "Animations" is off or the system animator scale is 0; every animation spec comes from `TvMotion`.
- **Type**: Inter (variable, SIL OFL 1.1, `docs/licenses/Inter-OFL.txt`) with the optical-size axis tied to the text size, as a browser does for the prototype.
- **No explanatory copy**: icon + one or two words; the only sentence-like text on screen is the Twitch device code (URL + code).
- **Player**: control bar anchored at the left safe margin (never re-centred by the chat mode), hides after ~4 s of inactivity, wakes on the first key (focus returns to the last used button). While hidden, a 2 dp line along the bottom of the video marks it. Chat: hidden / compact / large (see Chat below); the video always keeps at least 2/3 of the width.

### Chat

- **Column**: text area of 24 % (compact) / 28 % (large) of the screen width, 8 dp start inset, text stopping at the right safe margin (48 dp) while the background runs to the screen edge. The panel is outside the embed viewport; its background is `Base` at the chosen opacity (40 / 60 / 80 / 100 %, default 80), which over the `Body` canvas only shifts the tone (nothing is behind it). Top 28 dp fade is a gradient, not an offscreen layer.
- **Density**: text 14 / 15 / 17 sp, line height x1.3, nickname semibold in the viewer's Twitch colour (lightened to 4.5:1 on `Base` when too dark; users without a colour get a stable one from a 12-colour palette), message `Text` colour. Emotes 24 / 26 / 30 dp high, badges 15-16 dp, gap between messages 3 / 5 / 8 dp (user setting, 5 default). No separators, no per-message cards, timestamps off by default.
- **States, never colour alone**: highlight (channel points / intro) = 16 % purple tint + 2 dp left rule in `Focus`; mention of the viewer = 30 % purple tint + 2 dp purple rule, and the `@name` is bold; bits = bold light-purple `◆100`; system line (sub, raid) = italic secondary text, no nickname.
- **Focus**: the list is one focus target with the standard ring (2 dp `Focus`, gap, no scale); it is entered with Right from the control bar and never takes focus by itself. "Back to live" is a pill at the bottom (arrow + unread count, `Purple` fill while the list has focus; OK acts on it). Chat options are rows (icon, name, value) with the same ring; Left / Right step the value.
- **Motion**: new messages jump to the bottom while following live (no animation); history scroll animates with `TvMotion.fast` (instant when reduced motion is on); the pill fades. Animated emotes are content, not UI motion: they follow the "animated emotes" option and a visible-lines budget, not the Animations setting.
- **Copy**: the only chat text besides messages is the options' labels (two words max) and the preview line `Aa: Bb 123`.
