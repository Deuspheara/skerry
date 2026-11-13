# iOS screen audit (iPhone 17, iOS 26.5, French UI)

Captured with `apple/UITests/AuditCaptureUITests.swift` (run by name, not part of any test plan). Files are named
`<light|dark>-<state>.png`; every demo state exists in both appearances (48 dark, 48 light) plus 16 light captures against real
Twitch data (`light-real-*`). Landscape captures were rotated 90 degrees after capture (XCUIScreen returns portrait pixels).
Images are downscaled to 800 px on the short side and palette-compressed.

Run (needs the iOS app built for testing, see `apple/README.md`):

```sh
TEST_RUNNER_AUDIT_OUT=docs/screenshots/audit/apple TEST_RUNNER_AUDIT_TAG=light TEST_RUNNER_AUDIT_CHANNEL=<live channel> \
  xcodebuild test-without-building -project apple/TwitchPlayerApple.xcodeproj -scheme TwitchIOSPerf \
  -destination 'id=<udid>' -only-testing:TwitchIOSUITests/AuditCaptureUITests/<testName>
```

## States captured and first rating (out of 10, honest, from the screenshots)

| Screen | State files | Rating | What holds it back |
|---|---|---|---|
| Home (signed in, demo) | home, home-scrolled | 8.5 | Fine; avatar ring and LIVE badge good. The floating tab bar covers the last row until scrolled. |
| Following | following, following-scrolled-offline, following-bottom | 8 | Offline section is plain text rows; no "last seen" accent or schedule hint. |
| Browse | browse, browse-chip-games(-mid), -irl, -music, browse-scrolled | 8.5 | Grid is clean; sort control "Tri : popularité" is easy to miss. |
| Category | category, category-scrolled | 7.5 | Sparse header (no box art, no viewer total, no tags). |
| Search | search-empty, -typing-loading, -results, -filter-channels, -filter-categories, -no-results, -recents | 8 | "Aucun résultat" is a bare centered line (6.5): no suggestion, no clear action. The system keyboard intro card can overlay recents on a fresh simulator. |
| Channel (live / offline) | channel-live(-scrolled), channel-offline(-scrolled) | 7.5 | Small avatar header, "Suivi" button reads as a state but follow is not possible with a viewer token (opens twitch.tv); no banner, no about text. |
| Settings | settings-top, -2, -3, -bottom | 8.5 | Good grouping; "phone_about_version" style row is fine. Language row opens the system Settings app (see unreachable). |
| Sign-in sheet | signin-sheet | 7 | Half-height sheet over a dimmed page, "Copier le code" pill is low contrast, code is not selectable by long press. |
| Player live, portrait | player-live-portrait | 8.5 | Strong. Details row crowds LIVE, viewers and uptime on the right. |
| Emote picker | player-emote-picker | 7.5 | One short row of emotes with a large empty area; no section headers or recents tab until used. |
| Chat row long press | player-chat-row-longpress | 7.5 | Reply affordance good; the "new messages" pill overlaps the quoted line. |
| Player landscape | player-landscape-chat-default/-controls/-mode1..3 | 8.5 | Side panel, overlay and hidden modes work; overlay chat is dense over bright video. |
| Replay / clip portrait | player-replay-portrait, player-clip-portrait | 5.5 | Below the title row the page is empty: no description, no chapters, no related videos, no chat. |
| Drag mid-state | player-drag-mid | 6 | Title text overlaps the video while the sheet shrinks and the details area is a flat slab. |
| Strip over Home / Browse / Following | strip-over-* | 8 | Docked correctly above the tab bar; thumbnail edge is clipped by the corner radius. |
| Offline channel opened as a player | player-offline-channel | 6.5 | Demo data plays a test pattern for an offline channel; real build shows a message. |
| Signed-out Home / Browse (demo flag off) | home-signed-out, browse-signed-out | 5 | Dead end: "Non connecté" plus one button. Signed-out viewers cannot browse public categories. |
| Real data, signed out | real-home-signed-out, real-browse(-scrolled, -chip-games), real-category, real-search-* , real-channel(-scrolled), real-settings | 4 | Browse, category, channel page and search all show "Non connecté" or "Une erreur est survenue" without a session. |
| Real data, player | real-player-portrait, real-player-landscape, real-strip-over-home | 5 | After 14 s the picture area is still black with the pause icon (the audit simulator had no signed-in session; playback of a live channel was not confirmed in this run). Chat is replaced by a sign-in prompt. |
| Errors | real-error-replay-unavailable, real-error-channel-unknown | 6.5 | Message and retry exist; copy is generic. |

Overall (mean of the above, demo and real weighted equally): about 7.0. Target 9.5.

## Unreachable states (not captured)

- Signed-in screens with real Twitch data: no Twitch account is available to the audit, so every `real-*` capture is signed out.
- The in-app language picker: the Language row opens the iOS Settings app (`light-settings-language-picker.png`, `dark-settings-language-picker.png`), not an app screen.
- Picture in Picture window, lock screen / Now Playing, AirPlay, background audio: no way to capture the system UI from XCUITest.
- Real chat traffic, emote loading from 7TV/BTTV/FFZ, real replay and clip pages with real thumbnails.
- Keyboard-in-landscape and iPad layouts; macOS and tvOS screens (covered in `docs/APPLE_PLATFORMS.md`).
