# Escutaê

A clean, easy podcast player for Android: find a show, subscribe, press play, and pick
up exactly where you left off. No account, no server: everything stays on your phone.

The app was called PodParar and then PodOuvir; the GitHub repository keeps the first name.

See [PLAN.md](PLAN.md) for the roadmap and design decisions, and [CLAUDE.md](CLAUDE.md)
for the rules every change follows.

## Installing on a phone

Download **[Escutae.apk](https://github.com/ramonvidal/PodParar/releases/latest/download/Escutae.apk)**
on the phone and open it (allow installing from unknown sources). That link always
points at the newest build and needs no GitHub account. Each build installs over the
previous one.

The app ID changed from `com.podparar` to `com.punyplatypus.escutae` for Google Play, so
a copy installed before that change is a separate app and won't update to the new one.
To move your shows, use **Settings → Export to OPML** in the old app and
**Settings → Import from OPML** in the new one, then uninstall the old app.

### Updates

After that first install, Escutaê checks for new builds itself (every few hours, or
right away from **Settings → Check for updates**) and offers to install them: tap
**Install** on the banner at the top of the screen and confirm. The first time, Android
asks you to allow Escutaê to install apps.

## Google Play

The same code is published two ways, as the `distribution` product flavor:

- **github**: the APK on the GitHub release page (`Escutae.apk`), with the in-app
  self-updater described above.
- **play**: the Android App Bundle for Google Play (internal testing for now). Play
  installs its updates, so this build has no self-updater, no "Check for updates" row in
  Settings, and no `REQUEST_INSTALL_PACKAGES` permission (Play restricts it).

CI builds both (`assembleGithubRelease`, `bundlePlayRelease`); the signed bundle is the
`escutae-play-aab` artifact. Both are signed with the same key (below); for Play it is
the upload key, and Play App Signing holds the key Play signs the app with.

To have CI upload each build to the internal testing track, add the repository secret
`PLAY_SERVICE_ACCOUNT_JSON`: the JSON key of a Google Cloud service account that has
release access to the app in the Play Console. Google requires the **first** bundle of
a new app to be uploaded by hand in the Play Console (download `escutae-play-aab` from a
CI run); after that, CI uploads every build it publishes as a release. Without the
secret the upload step is skipped.

## Tech stack

Kotlin, Jetpack Compose (Material 3), Hilt; Media3 (ExoPlayer), Room and OkHttp as
the phases that need them land. Minimum Android 8.0 (API 26).

## Building

Every push runs the **Android build** workflow: module rules tests, unit and screenshot
tests, the APKs (artifact `podparar-debug-apk`), the Play bundle (artifact
`escutae-play-aab`), and a release with `Escutae.apk`,
`PodOuvir.apk` (for builds from before the rename) and `screenshots.zip`. Locally,
open the folder in Android Studio and run `app` (pick the `githubDebug` or `playDebug`
build variant).

### Signing key

Android only installs an update signed with the same key as the installed app, so every
published build is signed with one fixed key (`podouvir-release.keystore`, alias and
passwords `podouvir`). It is never committed: CI writes it from the repository secret
`SIGNING_KEYSTORE_BASE64` (the keystore file, base64-encoded), and refuses to publish a
release without it. Keep a copy of the keystore somewhere safe: losing it means the next
build can't update installed copies. To build a signed APK locally, put the keystore in
`app/` (it's git-ignored).

## Tests

- App logic: `./gradlew :core:model:test testDebugUnitTest` (every module's tests; `:app`
  has flavors, so its own task would be `testGithubDebugUnitTest`)
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

The app icon is the Escutaê microphone-and-headphones artwork
(`app/src/main/res/mipmap-*/ic_launcher_foreground.png`, on a matching yellow background, sized so
nothing is cut off under any launcher shape);
`store/play-icon-512.png` is the same artwork for the Play Console.
