# KMP migration log (Phase 1)

One entry per step: what moved, decisions, what was verified. Baseline numbers: `docs/perf-baseline.md`.

## Step 1a: consumer keep rules and Compose stability file (before any code moves)

- `twitchplayer.kmp-library` publishes `consumer-rules.pro` (AGP `optimization { consumerKeepRules }`) when the file exists.
  `shared/core/consumer-rules.pro` now carries the `-dontwarn` rules for kotlinx.serialization and Ktor core
  (slf4j, `io.ktor.util.debug`, `java.lang.management`). OkHttp-related `-dontwarn` rules and the `Log` stripping stay in
  `app/proguard-rules.pro`. No `-keep` rules anywhere.
- Verified: the release R8 `configuration.txt` includes the section "Local project :::shared:core
  (extracted file: .../consumer_proguard_dir/androidMain/exportAndroidMainConsumerProguardFiles/lib0/proguard.txt)"
  with those rules; `:app:analyzeReleaseR8Config` still runs.
- `config/compose/stability.conf` (wired through `twitchplayer.compose`) marks the immutable data types that will live in
  shared modules as stable. Compose report of the app before any move (debug, `-PcomposeReports`):
  233 stable / 30 runtime / 44 unstable classes (unstable = services, view models, activities), 130 restartable skippable
  composables.
