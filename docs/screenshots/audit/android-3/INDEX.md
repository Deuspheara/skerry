# Android phone screens, round 3 (Pixel 8 emulator API 36, `:phone` debug, demo data, final build)

Captured 2026-11-13 on `emulator-5554`, 94 frames, shrunk to 810 px wide and 160 colours. Every frame was looked at (as contact sheets of up to six).
Dark theme at 1.0x font unless the name says otherwise. The emulator ran on a host under load (up to 99): `TwitchPlayer isn't responding` dialogs
appeared in some takes; those takes were discarded and retaken.

| Area | Frames |
|---|---|
| Home: hero, follows, For you, categories | 01, 02 recommendations, 03 categories; light 130; Arabic 110; 1.5x 120-font15; 2.0x 120-font20; tablet 140 light, 144 dark |
| Following: live-now ring row, offline "last seen live" | 10, 11; light 131; Arabic 111; 1.5x / 2.0x 121-font15 / 121-font20 |
| Discover: live card + chat, clips empty state with "Go to live" | 12, 13, 14; light 132 (light status bar icons) |
| Browse | 15; light 133; Arabic 112 |
| Category (art, viewers, labelled favourite) | 16; light 134; Arabic 113 |
| Search: pill morph (4 frames, animations slowed 8x), empty, results, no results | 20-1..4, 21, 22, 23; 1.5x / 2.0x 123-search |
| Channel: offline, live, scrolled, clips row | 40, 41, 60, 61, 62, 63 (live), 63 (offline) |
| Player live: title block, controls, settings sheet, sleep sheet | 50, 51, 53; light 136, 137; Arabic 115; 1.5x / 2.0x 124, 125 |
| Chat: emote picker, reply bar | 160, 161 |
| Landscape: side chat, overlay chat, hidden | 70, 72, 73, 74 |
| Replay and clip players | 90, 91, 92 (replay); 95, 96 (clip) |
| Strip over a tab, drag in four frames while the finger is down, the strip it settles in | 162 dark, 138 / 139 light; 80-drag-mid-1..4, 81; 126 at 1.5x / 2.0x |
| PiP over the launcher | 98 |
| Settings and dialogs | 100, 102 (top, mid); 101 sign-out; 103 chat display; 104 theme; 105 language; light 135; Arabic 114; 1.5x / 2.0x 122 |
| Signed out per tab | 150 Home, 151 Discover (no Live/Clips tabs), 152 Following, 153 Browse |
| Error (a connection, requests fail) and offline (airplane mode) | 154 error, 155 / 156 offline |
| Tablet class (1600x2560 at 320 dpi): two-pane player | 141 light, 145 dark, 143 landscape (light) |

## Not captured (or not what the name suggests)
- **Quality sheet:** the demo stream has no renditions, so "Quality" has nothing to list; the settings sheet (51) shows its value ("Auto").
- **Cached content shown while offline:** the demo graph replaces the repositories that read the saved lists, so offline always shows the empty
  offline state (155, 156). The saved-list path is covered by JVM tests, not by a frame.
- **Sign-in code dialog:** the demo session answers at once; the dialog was not reachable.
- **Long category title (marquee), real tablet hardware, a real account, real streams:** not captured.
- The drag frames (80) are four screenshots taken at about 0.4 s intervals during a 4 s swipe; the emulator's `screenrecord` produced no usable video.
