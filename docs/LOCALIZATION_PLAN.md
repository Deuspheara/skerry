# Localization plan (plan only, nothing migrated yet)

Goal: one source of truth for UI strings that serves the Android TV / phone apps (Compose), the Apple apps (SwiftUI) and
the desktop / web Compose apps, without the 35 translations drifting apart. Phase 1 deliberately did **not** move any
string: all of them still live in `app/src/main/res`. This document is the plan for when the second UI platform starts.

## Inventory (measured 2026-11-11)

| Item | Value |
|---|---|
| Locales | 36: English (base) + 35 translations (`values-*` folders) |
| Keys in `values/strings.xml` | 156 `<string>` (151 translatable, 5 `translatable="false"`: app name, chat preview sample) + 1 `<plurals>` (`chat_new_messages`) |
| Complete? | Every checked translation (fr, ar, ja, zh-CN) has all 151 strings and the plural |
| Placeholders | 20 positional or sequential format specifiers (`%d`, `%s`, `%1$s`...) |
| Call sites | about 212 uses of `stringResource` / `pluralStringResource` / `R.string` in the app |
| Android legacy codes | `values-in` (Indonesian), `values-iw` (Hebrew), `values-b+fil` (Filipino): Apple, CLDR and the web use `id`, `he`, `fil` |
| RTL | Arabic and Hebrew; `android:supportsRtl="true"`; the TV layouts mirror |
| Language selection | Android 13+: platform `LocaleManager` per-app language; older: own preference applied in `attachBaseContext` (`AppLocales`), plus an in-app picker because TV settings rarely expose the system screen. `generateLocaleConfig = true`, `unqualifiedResLocale=en`, language splits disabled |
| Locale-dependent code | on-screen keyboard layouts per language (`keyboardRows`), Twitch language filter (`twitchLanguage`, fed by `DeviceProfile`), `NumberFormatter` |
| Translation quality | **unreviewed**: the translations were produced without native-speaker review |

## Proposed single source of truth

Keep the Android resource XML format as the source (it is what all 35 translations already are, it supports plurals and
`translatable="false"`) and move it from `app/` into the shared module:

```
shared/resources/src/commonMain/composeResources/
  values/strings.xml          English, the only file developers edit by hand
  values-fr/strings.xml ...   35 translations (same folder names as today, see "locale codes")
```

Compose Multiplatform resources (`org.jetbrains.compose.components:components-resources`) then give desktop and web
Compose apps `Res.string.*` / `stringResource` directly, and the Android app keeps working through generated or
mirrored `res/` files. SwiftUI cannot read Compose resources, so the Apple and web targets get **generated** artifacts:

| Consumer | Artifact | Generator |
|---|---|---|
| Android apps | `res/values*/strings.xml` | none if the module is an Android library with the folder as `res` source set; otherwise a copy task. Android Lint keeps checking placeholders, missing translations, `MissingQuantity` |
| Compose desktop / web | `composeResources` | native Compose resources tooling |
| Apple (iOS, iPadOS, macOS, tvOS) | `Localizable.xcstrings` (String Catalog, JSON) | a Kotlin script (`./gradlew :tools:generateXcstrings`) reading the XML |
| Web (if a non-Compose page is ever added) | `i18n/<locale>.json` | same tool, extra output format |

The generated Apple and web files are **not committed** (built in CI and by a Gradle task wired into the Xcode build
phase), so there is only one place to edit.

## Details that need decisions or care

- **Locale codes.** Normalize on BCP 47 in the source folder names (`values-id`, `values-he`, `values-fil`) only if
  Compose resources and AGP accept them; AGP still needs the legacy folder names (`in`, `iw`, `b+fil`) for old Android
  versions. Plan: keep today's folder names as the source, and put the mapping (`in -> id`, `iw -> he`, `b+fil -> fil`,
  `pt` = `pt-BR`, `zh-rCN` -> `zh-Hans`, `zh-rTW` -> `zh-Hant`) in one table in the generator. Verify every code on a
  real Apple device: `zh-Hans` / `zh-Hant` and `pt-BR` / `pt-PT` are the usual traps.
- **Format specifiers.** Android uses `%1$s`, `%d`; Apple String Catalogs use `%1$@` for strings and `%1$lld` for 64-bit
  integers, `%@` is not valid in Android. The generator converts `s -> @` and `d -> lld` and fails on any specifier it does
  not know. Escapes: `\'`, `\"`, `&amp;`, `\n`, `\u...` must be decoded to real characters for JSON targets.
- **Plurals.** Android and Compose resources use CLDR categories per language (Arabic has six: zero, one, two, few, many,
  other; Russian, Polish four; French one, many, other; Japanese only other). `.xcstrings` expresses the same with
  `variations.plural` and the same category names, so the mapping is direct, but the generator must check that each
  language's file provides exactly the categories CLDR requires (Lint `MissingQuantity` does this on Android today).
- **RTL.** Compose (`LocalLayoutDirection`) and SwiftUI (`.environment(\.layoutDirection)` / automatic) mirror from the
  locale. The risks are not the strings but the layouts: focus order on TV, arrow icons, progress bars. Each new UI needs
  an Arabic and Hebrew screenshot pass. The directional text of chat messages (mixed LTR/RTL) is a separate chat-rendering
  topic, not a string topic.
- **Per-app language.** Android 13+: `LocaleManager` plus `locale-config` (generated from the folders; keep
  `generateLocaleConfig`). Apple: per-app language lives in system Settings; the app only declares its localizations
  (derived from the `.xcstrings` content) and reads `Locale.current` / `Bundle.main.preferredLocalizations`. An in-app
  picker is unnecessary on Apple platforms, except tvOS where the language is a system-wide setting (use the system
  language). Desktop and web: the picker the app already has (`AppLocales` becomes an interface in `DeviceProfile`
  terms: `supported`, `current`, `set(tag)`).
- **Non-string locale logic.** `keyboardRows(lang)` (10-column on-screen keyboards) and `twitchLanguage` are locale logic,
  not translations: they belong in `shared/presenters` (pure Kotlin, already tested) and are consumed by every UI.
- **Number and date formatting** stays behind `NumberFormatter` and the per-platform formatters; do not put formatted
  numbers in strings.

## Translation review process (currently missing)

1. Tag every string file with its review state (a `docs/translations-status.csv`: locale, reviewer, date, status).
2. Machine translations stay allowed as a first pass, but a locale only ships as "reviewed" in the picker once a native
   speaker has signed off on at least the 30 most visible strings (navigation, errors, sign-in, chat options).
3. Review happens on screenshots (strings truncate differently in German, Finnish, Russian: TV layouts are tight), not
   on a spreadsheet alone.
4. Optional: a translation-management tool (Weblate, Crowdin) can read and write the Android XML directly, so adopting
   one later does not change the source format.

## CI checks (to add with the migration)

- Every translation file has exactly the keys of `values/strings.xml` (minus `translatable="false"`): fail on missing,
  extra or renamed keys. Android Lint already reports `MissingTranslation` and `ExtraTranslation` for the Android build.
- Placeholders match the base string (same count, same types, same positions) in every language.
- Plural categories match CLDR for each language.
- The generator runs on every PR (cheap) and the generated `.xcstrings` passes `xcstringstool` / an Xcode build on the
  `macos-latest` job once Apple targets exist.
- A pseudo-locale (`en-XA` accented, `ar-XB` mirrored) build catches hard-coded strings and layout overflow on all UIs.

## Migration steps and effort (solo developer, rough)

1. Create `shared/resources`, move `values*` folders with `git mv`, make `:app` consume them, keep generated
   `R.string` working (1 day; Lint and the 36-locale picker test must stay green).
2. Add the key / placeholder / plural CI check (1 day).
3. Write the `.xcstrings` generator with a golden-file test (2 days).
4. Move `keyboardRows` and `twitchLanguage` into presenters (0.5 day).
5. Add the translation status file and reviewer workflow (0.5 day, then ongoing).
6. First Apple or desktop UI consumes the artifacts (part of that phase; budget 1 day for locale-code surprises).

Not decided: whether to adopt a translation-management service, and whether the web target (if it is Compose for Web)
uses the Compose resources directly (likely yes, then no JSON output is needed).


## Addendum: the phone app (Phase 3, step B)

The Android phone app already reuses this inventory without moving it: the `twitchplayer.shared-strings` build plugin generates the TV
app's text resources (`strings.xml`, plurals, arrays: every `values*` file whose top-level entries are all text) into the phone app, so
the 36 languages, plurals and `translatable="false"` entries stay in one place. The phone adds its own `phone_*` keys in
`phone/src/main/res/values/phone_strings.xml` (English) and `values-fr/` (French): 34 other languages fall back to English for those
keys until they are translated (`MissingTranslation` is ignored for that one file, and a unit test keeps English and French in step, with
the same placeholders). When the strings move to Compose Multiplatform resources (this plan), the plugin goes away.

## Addendum: phone-only strings (Phase 3, step C)

The phone's own `phone_*` strings (about 60) are now available in **English, French and Arabic** (`phone/src/main/res/values{,-fr,-ar}/phone_strings.xml`). A unit test (`PhoneStringsTest`) fails if French or Arabic miss a key or change a placeholder, or if the code uses a `phone_*` string that English does not define. The other **33 languages** (the 36 of the TV app minus English, French and Arabic) fall back to English for these keys until they are translated; every other string comes from the TV app's reviewed-by-nobody translations as before. When the single-source migration above happens, the `phone_*` keys move with the rest.
