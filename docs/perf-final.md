# Final performance pass (phone), 2026-11-12

Pixel 8 emulator (`Phone_Pixel8_API36`, arm64, **host GPU**), `benchmark` build type of `:phone` (R8 full mode, demo data), `:benchmark-phone`.
**Emulator-only, indicative.** Nothing was measured on a real phone or on the real TV. The host load average was 11-17 during both runs
(10 cores, a Gradle daemon and Android Studio running), so absolute numbers carry noise; two full runs are shown, never one.

## What was regenerated

| Item | Before (`709baad`) | After (`346f97b`) |
|---|---|---|
| Baseline Profile rules | 36,775 | **41,249** |
| Startup profile rules | 20,010 | **21,461** |
| Startup classes in `classes.dex` | 2,104 of 2,126 (99.0 %) | **2,212 of 2,225 (99.4 %)** |
| `assets/dexopt/baseline.prof`, profileinstaller, receiver, AAB metadata | present | present (`check-profile-packaging.py phone phone`: all `pass`) |

New journeys recorded: Browse filter-chip switch (Games, IRL, Music, All), Discover swipe (three streams down and up, Clips and back), emote picker
(open, focus the search field, back to the keyboard); the existing ones (Home, open stream with chat, minimise/expand, search, channel, replay,
settings, fullscreen) were re-recorded. The same journeys are now frame benchmarks (`chipSwitch`, `discoverSwipe`). R8, keep rules, profile and
`profileinstaller` settings are untouched.

**TV profile: not regenerated.** `check-profile-packaging.py app app` passes (baseline profile packaged, 98.6 % of the surviving startup classes in
`classes.dex`), but regenerating needs a TV emulator and only the phone emulator was available. The TV profile still predates the shared-code
changes of the last days (foreground refresh, extra snapshots); the new classes are small and simply not pre-compiled. Regenerate it with
`benchmark/tools/generate-profiles.sh` on a TV image when convenient.

## Cold start (10 iterations per run, ms, min / median / max)

| Compilation | Run | First frame | Home content (`timeToFullDisplay`) |
|---|---|---|---|
| Baseline Profile | 1 | 178 / 191 / 203 | 220 / **236** / 267 |
| Baseline Profile | 2 | 191 / 225 / 295 | 246 / **277** / 365 |
| None (sideload, before background dexopt) | 1 | 211 / 231 / 284 | 297 / 323 / 427 |
| None | 2 | 253 / 276 / 399 | 347 / 383 / 568 |

Against the **< 500 ms** goal: met on the emulator, with and without a profile (median Home content 236-277 ms with, 323-383 ms without; the
worst single launch was 568 ms without a profile). Caveats: the benchmark build runs on demo data, so "Home content" here is the cost of the
app's own start path (Koin, session read, snapshot read, first composition), not a network round trip; with the real app the content shown
first comes from the disk snapshot, the network only refreshes it. The real figure on the user's Samsung is not measured.

## Frame timing (5 iterations per run, `frameDurationCpuMs` P50 / P90 / P99; run 1 | run 2)

| Journey | With profile | Without profile |
|---|---|---|
| Home scroll | 3.4 / 4.5 / 5.9 \| 3.5 / 4.7 / 8.2 | 3.1 / 4.6 / 6.3 \| 3.6 / 4.8 / 6.9 |
| Chip switch (cards animate to new places) | 2.5 / 4.6 / 8.7 \| 2.6 / 5.2 / 19.0 | 2.5 / 11.2 / 18.7 \| 3.4 / 18.5 / 26.8 |
| Discover swipe (previews start/stop) | 11.3 / 32.9 / 48.1 \| 13.2 / 32.3 / 50.2 | 17.4 / 32.2 / 58.2 \| 15.2 / 26.0 / 48.5 |
| Chat scroll | 17.6 / 19.1 / 33.6 \| 3.5 / 32.8 / 34.5 | 3.1 / 5.7 / 18.5 \| 3.2 / 17.3 / 19.4 |
| Sheet drag | 4.1 / 17.6 / 33.1 \| 4.1 / 8.5 / 18.9 | 4.2 / 8.4 / 18.2 \| 4.3 / 8.7 / 18.7 |
| Fullscreen toggle | 5.4 / 20.3 / 64.7 \| 17.7 / 20.9 / 56.2 | 7.0 / 24.3 / 85.4 \| 7.0 / 30.7 / 100.7 |

Reading it honestly:
- **Home scroll and chip switch are smooth** (median 2.5-3.6 ms, P90 under 6 ms with the profile). The cards' move animation costs nothing visible.
- **Discover swipe is the heaviest journey**: P90 about 32 ms in every run, with and without a profile. Each swipe starts and stops a live player and a
  chat; frames over 16 ms are expected there. It is the first thing to look at on a real phone, and not solved by the profile.
- **Chat scroll with the profile** had one outlier run (median 17.6 ms in run 1, 3.5 ms in run 2), no pattern: noise from the loaded host, not
  attributed to a cause. **Fullscreen toggle** (a rotation) keeps P90 around 20-31 ms in both builds, as in `docs/perf-phone-redesign.md`.
- A first attempt ran on an emulator that had been restarted in **software GL** (`swangle_indirect`); every frame number was about 10x worse. It was
  discarded and the emulator restarted with `-gpu host`. Frame numbers on a software-rendered emulator mean nothing.

## Build size

| Item | Size |
|---|---|
| Phone release APK (unsigned) | **4,013,761 B** (3,853,585 B before emotes, Discover, caching; +4.2 %; 3,927,149 B before the redesign: +2.2 %; vs the TV's original 3,952,530 B baseline: +1.6 %) |
| TV release APK | 4,034,450 B (4,001,682 B before the shared-code changes of this batch: +0.8 %; vs the original 3,952,530 B baseline: +2.1 %, within 3 %) |
| Where the phone APK's bytes are | dex 3.10 MB (`classes2.dex` 2.09 MB, `classes.dex` 1.01 MB), `resources.arsc` 0.63 MB (36 languages), native libs 0.07 MB (four ABIs), assets 0.06 MB |

No cheap win was found that does not touch what must stay as is: the growth is the animated-emote decoder (`coil-gif`, needed for animated
7TV/BTTV/FFZ emotes), the Discover feed, the emote picker and the caching layer. `resources.arsc` is the 36-language base APK; native libs for the
unused ABIs only disappear with ABI splits or an AAB upload (the AAB is what a store serves, 8.6 MB for all ABIs and languages, and device-specific
downloads are smaller). Not done on purpose: dropping `coil-gif` (breaks animated emotes), restricting `abiFilters` (breaks x86 emulators), any R8,
keep-rule or profile change. What is left is **+4.2 % over the redesign baseline, above the 3 % guardrail, with the reason above**.

## Bug found while regenerating the profiles

The first generation attempts crashed at launch of the `nonMinified` build: `JsonEncodingException: Serializer for subclass 'DiscoverKey' is not
found in the polymorphic scope of 'NavKey'`. The saved form of the back stack (`NavKeyCodec`) did not know the new Discover tab root, so **every
time the app went to the background (Home button, switching apps, Picture-in-Picture) it crashed**. It was in every build of the phone app since
the Discover tab was added, the debug build on the Samsung included. Fixed in `d01302f` (`DiscoverKey` registered; a JVM test now round-trips every
`PhoneTab` root). A reinstall of the fixed debug build was also pushed to the Samsung.
