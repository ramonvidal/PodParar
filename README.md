# PodParar

A clean, easy podcast player for Android: find a show, subscribe, press play, and pick
up exactly where you left off. No account, no server: everything stays on your phone.

See [PLAN.md](PLAN.md) for the roadmap and design decisions, and [CLAUDE.md](CLAUDE.md)
for the rules every change follows.

## Installing on a phone

Download **[PodParar.apk](https://github.com/ramonvidal/PodParar/releases/latest/download/PodParar.apk)**
on the phone and open it (allow installing from unknown sources). That link always
points at the newest build and needs no GitHub account. Each build installs over the
previous one.

## Tech stack

Kotlin, Jetpack Compose (Material 3), Hilt; Media3 (ExoPlayer), Room and OkHttp as
the phases that need them land. Minimum Android 8.0 (API 26).

## Building

Every push runs the **Android build** workflow: module rules tests, unit and screenshot
tests, the APKs (artifact `podparar-debug-apk`), and a release with `PodParar.apk` and
`screenshots.zip`. Locally, open the folder in Android Studio and run `app`.

## Tests

- App logic: `./gradlew :core:model:test testDebugUnitTest`
- Module structure rules: `./gradlew -p build-logic :rules:test`

The UI tests also save screenshots of each tab (light, dark, largest text) to
`build/outputs/roborazzi`; CI uploads them as the `screenshots` artifact.

## Project layout

The app is split into small Gradle modules; the rules for what may depend on what are
in [CLAUDE.md](CLAUDE.md) and are checked on every build.

```
app/              Activity, root screen, bottom navigation: wires the features together
core/model/       Plain Kotlin: podcasts, episodes, played/resume rules, date and duration parsing
core/database/    Room (Phase 1)
core/network/     Feeds and directory search (Phase 1)
core/data/        Repositories (Phase 1)
core/playback/    Media3 service and player connection (Phase 2)
core/downloads/   Episode downloads (Phase 4)
core/settings/    This phone's choices
core/ui/          Theme, shared composables, icons, shared strings
core/update/      Updating the app from the release page (Phase 6)
feature/
  home/           Continue listening, Up next, New episodes
  library/        Subscribed shows and each show's page
  discover/       Search and add by RSS link
  player/         Full player and the mini player
  settings/       Settings screen
  update/         "New version available" banner
build-logic/
  convention/     Shared Gradle setup for each kind of module
  rules/          The module structure check
```

## Icons and font

App controls use Google's [Material Symbols](https://fonts.google.com/icons) (Apache 2.0);
the font is [Figtree](https://fonts.google.com/specimen/Figtree) (SIL Open Font License).
Their licenses ship with the app in `core/ui/src/main/assets/licenses`.
