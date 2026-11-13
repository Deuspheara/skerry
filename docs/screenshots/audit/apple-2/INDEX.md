# iOS screen audit 2 (iPhone 17 simulator `TwitchAuditPhone`, iOS 26.5, French UI)

Re-capture after the fixes answering `docs/audit/apple-review-1.md`. Same harness as audit 1 (`apple/UITests/AuditCaptureUITests.swift`, scheme
`TwitchIOSPerf`, run by name): the seven demo tests in light and dark (96 PNGs, 800 px on the short side, palette-compressed). Landscape captures are
rotated back after capture. Not re-captured here: the real-data (`real-*`) set (every one is signed out and now shows the sign-in cards, see audit 1's INDEX for
the old ones), macOS and tvOS (checked by hand on the tvOS simulator for Browse and Settings, nothing else).

Scores are mine, from the screenshots, and deliberately not inflated: a screen gets 9 only if nothing is left to criticise in the capture.

| Screen | Before (review 1) | Now | What changed / what is still wrong |
|---|---|---|---|
| Signed-out Home, Browse, Category, Channel, Following | 4.5 | 7.0 | Designed card per screen (why, three perks, primary button) instead of "Non connecté". Still no content without a session (needs an anonymous catalogue, not approved), no "Parcourir sans compte". |
| Search no-results | 6.5 | 8.0 | Symbol, hint and a "Effacer la recherche" action (`light-search-no-results`). Categories scope and loading state untouched. |
| Error states (retry) | 4.0 | 7.0 | One `MessageState.failed` with symbol, advice and Retry on every platform. Replay-unavailable overlay not re-captured; unknown channel still the generic error. |
| Replay / clip page | 4.0 | 7.5 | Title, kind chip, date, views, length, share and "more from the channel" (`light/dark-player-replay-portrait`). No description or chapters (the shared layer does not expose them), no chat replay. The clip capture has an empty channel name (demo data). |
| Settings | 7.5 | 8.5 | The leaked `phone_about_version` key is fixed (`light-settings-bottom`: "Version 1.0 (1)"), the Discover setting is gone, a test fails if a string equals its key. Still custom cards, not `Form`; Language still hands off to the system. |
| Sign-in sheet | 6.0 | 7.5 | Waiting row, tap code to copy, deeper CTA purple (6.3:1), "Copier le code" in the 4.5:1 accent. Still no QR, no success capture. |
| Tab bar | 7.0 | 8.0 | Labelled; tinted glass removes the cyan/pink refraction blobs in light; lists now leave room for bar and strip. Still a custom bar, not `TabView` / `tabViewBottomAccessory`. |
| Home / Following | 7.0 / 6.5 | 7.5 / 7.0 | Purple text now passes contrast. Same one-line-title row layout; Home and Following are still near copies. |
| Browse / Category / Channel pages | 7.5 / 5.0 / 6.0 | 7.5 / 5.0 / 6.0 | Not changed (no poster header, no banner, "Suivi" still reads as a state). |
| Player live portrait / landscape | 7.5 / 7.0 | 8.0 / 8.0 | Light/dark correct; landscape side panel, overlay and hidden work with a chat button. Header row still crowded, no scrim under the landscape title. |
| Drag mid-state | 4.5 | 5.0 | Strip text now appears only near the strip. The frozen mid frame still shows the picture detached from the slab and a black band; not fixed. |
| Emote picker, chat long-press | 6.5 | 6.5 | Not changed. |
| Contrast (all) | about 5 | 8.0 | `ContrastTests` measure 9 text/ground pairings at 4.5:1 or more; purple text on light moved from 3.1 to 6.0. |
| tvOS Browse / Settings | 6.5 / 4.5 | 7.5 / 7.0 | Selected chip inverts with a check; settings rows are labelled with a menu on the right, chat mode moved under Chat. Section headings still grey (about 2.5:1), no focus frames captured. |

Not done from the review's list: system `TabView` / `tabViewBottomAccessory`, `.searchable` with scopes, Home as discovery with 16:9 cards, Following roster,
category and channel redesign, macOS toolbar/sidebar/settings work, tvOS focus captures and live "EN DIRECT" transport, Dynamic Type and RTL captures,
first-run welcome with "Parcourir sans compte", anonymous browsing. Average of the screens above: about 7.1 (review 1: 6.1 for iOS).
