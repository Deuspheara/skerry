# Contributing

Read [AGENTS.md](AGENTS.md) first: it holds the rules for everyone, humans and coding agents alike.

## Setup

- JDK 21 (Temurin) to build; the code targets Java 17 bytecode.
- Android SDK with platform 37. Copy `local.properties.example` to `local.properties` and set `sdk.dir`.

## Everyday commands

```sh
./gradlew check            # detekt, spotless (ktlint), Android Lint, unit tests: must pass before any commit
./gradlew spotlessApply    # fix formatting
./gradlew detektBaseline   # only to record existing issues, never to hide new ones
```

## Commits and PRs

- Conventional Commits (`feat:`, `fix:`, `refactor:`, `build:`, `ci:`, `chore:`, `docs:`, `test:`, `perf:`), small and focused.
- No attribution trailers of any kind.
- A PR needs green CI. UI changes come with screenshots.

## Branch protection (repository owner, GitHub settings)

Settings > Branches > add rule for `main`:

- Require a pull request before merging
- Require status checks to pass: `static`, `test`, `build-release`; require branches to be up to date
- Require linear history; block force pushes and deletions
- Do not allow bypassing the rules (temporarily disable only when rewriting history)
