# Repository rules

Native Twitch client. Android TV app today; being split into a Kotlin Multiplatform `shared/` core plus native UIs
(see "Architecture rules"). These rules apply to every contributor and every coding agent.

## Commits, PRs, attribution

- **Never add attribution** to commits, PRs, issues, or code. No `Co-Authored-By:` or any other co-author trailer,
  no "Generated with ...", no robot emoji lines, no mention of an AI tool in commit messages, PR titles or bodies,
  or code comments. Commits are authored by the repository owner only.
- Conventional Commits: `feat:`, `fix:`, `refactor:`, `build:`, `ci:`, `chore:`, `docs:`, `test:`, `perf:`.
  Small, focused commits. Imperative subject, no trailing period.
- `.claude/settings.json` disables tool-side attribution. Do not re-enable it.

## Hygiene

- Never commit secrets, keystores (`*.jks`, `*.keystore`), `local.properties`, build outputs, or IDE state.
  Secrets go in `local.properties`, environment variables, or GitHub Actions secrets. Template:
  `local.properties.example`. The Twitch Client ID is public by design; there is never a client secret.
- Pin every version in `gradle/libs.versions.toml`. No dynamic versions (`+`, `latest.release`, ranges).
- Before any commit: `./gradlew check` must pass (detekt, ktlint via Spotless, Android Lint, unit tests).
  `./gradlew spotlessApply` fixes formatting.

## Performance guardrails

- Do **not** weaken R8 (full mode, minify, shrink resources), keep rules, baseline-profile, startup-profile,
  DEX-layout or `profileinstaller` settings without asking first. Baseline numbers: `docs/perf-baseline.md`.
- Keep rules needed by shared code live in that module's consumer rules, never as broad `-keep` in the app.

## Architecture rules

- No Android (or any `java.*`) imports in `commonMain`.
- Presenters expose `StateFlow<UiState>` plus `onEvent(...)`. UIs only render state and send events.
- Platform code goes behind an interface (injected through Koin) or `expect/actual`.
- Shared KMP modules use `com.android.kotlin.multiplatform.library`; never combine `org.jetbrains.kotlin.multiplatform`
  with `com.android.library` / `com.android.application` in one module. The Android app is its own module.
- Shared Gradle config lives in `build-logic/` convention plugins, not copy-pasted per module.
- Declare only the targets in use. Apple targets, when added: arm64 and simulatorArm64 only (no x64).

## Verifying UI changes

Capture emulator screenshots after every UI edit; reading code misses visual errors.
