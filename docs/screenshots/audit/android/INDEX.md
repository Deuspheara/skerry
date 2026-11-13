# Android phone screen audit (emulator Pixel 8 API 36, `:phone` debug build, demo data)

Captured 2026-11-13 on `emulator-5554` only. 86 PNGs, quantised to 128 colours and downscaled to keep the folder at ~8 MB.
Every file was checked for blank frames by a script; the ones marked **viewed** were also opened and looked at one by one.
Dark theme and 1080x2400 at 1.0x font unless the name says otherwise.

| Area | Files |
|---|---|
| Home | 01 (signed-in demo, viewed), 57 light (viewed), 64 font 1.5 (viewed), 67 font 2.0, 52 Arabic (viewed), 78 signed-out (viewed) |
| Discover | 02 live (viewed), 03 clips (viewed), 04 page 2, 59 light, 81 offline (viewed) |
| Following | 05 (viewed, includes offline section), 06 scrolled, 60 light |
| Browse | 07 (viewed), 08 Games, 09 IRL, 10 Music chips, 53 Arabic, 58 light, 65 font 1.5, 66 font 2.0, 71 wide, 82 offline (viewed) |
| Category | 11 (viewed), 72 wide, 12 search pill (see gaps) |
| Search | 13 empty (viewed), 14 typing (viewed), 15 Categories filter, 16 Channels filter, 17/17b submitted, 18 recents (viewed), 19 no results (viewed), 63 light, 67-search 2.0x font (viewed) |
| Channel | 41 live + strip (viewed), 83 offline (viewed), 44 scrolled with Similar (viewed) |
| Player portrait | 20 live (viewed), 21 controls (viewed), 25, 40 re-expanded, 42/43 replay (43 viewed), 61 light, 68 font 2.0 (viewed), 54/55 Arabic (55 viewed), 73 wide |
| Chat | 22/22b emote picker (viewed), 23 emote inserted (viewed), 24 reply bar (viewed) |
| Landscape | 26 default, 27 controls (viewed), 28 overlay chat (viewed), 28b overlay no controls (viewed), 29 chat hidden (viewed) |
| Sheets | 31 quality (viewed), 32 sleep timer (viewed) |
| Strip / drag | 34 Home (viewed), 35 Discover (viewed), 36 Following, 37 Browse, 30 Search, 62 light, 69 font 2.0, 75 light Home, 38 mid-drag (viewed), 39 after drag |
| Settings | 45 top (viewed), 46 chat-display dialog, 47 mid, 48 bottom (viewed), 49 theme dialog, 50 language dialog (viewed), 51 Arabic, 56 light, 75b sign-out dialog (viewed) |
| Sign-in | 79 device code dialog (viewed; the code is blacked out) |
| PiP | 74 (cropped check: the window shows only the picture, no bar) |
| Wide layout (density 240) | 70 rail (viewed), 71, 72, 73 |

## Findings from looking at them
- **One-line titles at 2.0x font:** category cards cut names to "Valmo...", "Pixel ...", "Musiq..." in the 3-column Browse grid (66). Readable but lossy; consider 2 columns above 1.5x.
- **Home rows at 1.5x/2.0x:** title cut to "Run nocturne : bo..." (64, 67); acceptable, no overlap.
- **PiP (74):** the window is only the picture, rounded; no tab bar or strip.
- **Player Back/Chat button:** the chat button in landscape cycles side panel -> overlay -> hidden (26, 28, 29).
- **Wide layout (73):** the expanded player on a wide window is a full-width portrait layout, not a side-by-side one. The rail is hidden while expanded.

## Not reached or not trustworthy
- **Search pill morph in motion (12):** the frame was taken after the animation ended; the transition itself is not captured (it needs screen recording).
- **Category title marquee:** not captured; no demo category has a title long enough to overflow.
- **Clip player (expanded):** not captured; the demo clips were not reachable from a tap path I found (Discover > Clips opens the feed, 03, not the clip player).
- **Real loading skeleton, real error with Retry:** not captured; demo data never fails and loads instantly. Offline (81, 82) shows the signed-out state, not an error.
- **Continue-watching row on Following:** not visible in the demo data.
- **Tablet class device:** only a density change (240 dpi) was used, not a real tablet AVD.
- **Arabic Category and Channel pages, Arabic landscape:** not captured.
- **Mid-drag (38):** shows the sheet part-way down on Home; the drag mid-state on other tabs is not captured.
- **Signed-in real account, real network streams:** the emulator uses demo data only.
