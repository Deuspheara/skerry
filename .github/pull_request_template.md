## What and why

<!-- One or two sentences. Link the issue if there is one. -->

## Checklist

- [ ] `./gradlew check` passes locally
- [ ] UI change: emulator screenshots attached (TV and, when relevant, phone)
- [ ] No new Android imports in `commonMain`; platform code sits behind an interface or `expect/actual`
- [ ] R8 / baseline-profile settings untouched (or the change is explained below)
- [ ] Versions pinned in `gradle/libs.versions.toml`, no dynamic versions
- [ ] No secrets, keystores or `local.properties` committed
