# Android phone screens after the review-1 fixes (Pixel 8 emulator, API 36, `:phone` debug, demo data)

Captured 2026-11-13 on `emulator-5554`. 1080x2400, shrunk to 810 px wide and 128 colours. Every file was looked at (as contact sheets of up to
five screens). Dark theme at 1.0x font unless the name says otherwise.

| Area | Files |
|---|---|
| Loading skeleton (was missing) | 00 |
| Home (hero, two-line titles, no LIVE badge) | 01 dark, 30 light, 20 font 1.5 / 2.0, 50 Arabic |
| Following (title, live-now ring row, offline without repeated label) | 02, 18 offline section, 31 light, 22 font 1.5 / 2.0 |
| Discover live (card, labelled actions, pager hint) / clips empty state | 03, 04 |
| Browse (one chip style, 2 columns at 2.0x) | 05, 21 font 1.5 / 2.0, 32 light, 51 Arabic (shows Home: see gaps) |
| Category (art, name, viewers header) | 06, 52 Arabic (shows the player: see gaps) |
| Search (empty, results, no results with the query) | 07, 08, 09 |
| Player (title block, settings gear, sheets) | 10, 11 controls, 12 settings sheet, 13 sleep sheet, 33 light |
| Strip over a tab | 14 dark, 34 light |
| Settings (section icons, aligned rows) | 15, 16, 17, 35 light |
| Signed out, per tab | 40 Home, 41 Discover, 42 Following, 43 Browse |
| Error with Retry (every request fails) | 44 Home, 45 Browse |
| Tablet class (1600x2560 at 320 dpi) | 70 Home with rail and two columns |

## Not captured (or not trustworthy)
- Landscape player in its three chat modes, the replay and clip players, the channel page, the category marquee and the search pill morph in
  motion: the capture script lost the player after a rotation, and these were not retaken.
- 51 and 52 (Arabic Browse and Category) show Home and a player instead: in a mirrored layout the bar order is reversed and the taps landed
  on other tabs. The Arabic Home (50) and the Arabic player title block (52) are real.
- A tablet-width player (side by side): not built, see docs/audit/android-fix-plan.md.
- A real account, real streams and a real tablet.
