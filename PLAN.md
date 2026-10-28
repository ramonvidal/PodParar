# PodParar plan

A podcast player for Android: **clean, easy, functional**. Find a show, subscribe,
press play, and pick up exactly where you left off.

Built the same way as ChoreMe: small Gradle modules with rules checked on every
build (see `CLAUDE.md`), logic in plain Kotlin with unit tests, every screen drawn
from its state and screenshotted, and GitHub Actions publishing an installable APK.

## Decisions so far

| Topic | Decision |
|---|---|
| Platform | Android only: Kotlin + Jetpack Compose (Material 3). Minimum Android 8.0 (API 26) |
| Structure | **As modular as possible**: `:core:*` and `:feature:*` Gradle modules, rules in `CLAUDE.md`, checked on every build (same `module-rules` check as ChoreMe) |
| Accounts and sync | **None.** Everything lives on the phone: no sign-in, no server, no Firebase. Subscriptions move between phones with OPML export/import |
| Finding shows | The **Apple Podcasts directory** search (free, no API key), plus "Add by RSS link" for anything not listed. Results show artwork, title and author; one tap subscribes |
| Feeds | Plain RSS 2.0 with the iTunes tags, parsed on the phone. `http://` feeds and audio are allowed, since many podcasts still use them. A refresh never touches progress on episodes already known |
| Tab names | **Home, Library, Discover** |
| Home | "Continue listening" (started, not finished) at the top, then **Up next** (the queue), then **New episodes** from every subscription, newest first |
| Library | A grid of subscribed shows' artwork, sorted by name; a dot on shows with unplayed new episodes. Tap a show for its page: description, episodes, "Mark all played", Unsubscribe |
| Playback | **Media3 ExoPlayer in a `MediaSessionService`**: keeps playing with the screen off, shows on the lock screen and in the notification, works with Bluetooth and headphone buttons, pauses when headphones are unplugged and for calls (audio focus) |
| Player | A **mini player** above the tabs on every screen; tap it for the full player: big artwork, title, a seek bar with elapsed and remaining time, **back 10 s / forward 30 s**, play/pause, speed, sleep timer |
| Resuming | Position saved every few seconds, on pause and when switching episodes. Starting an episode resumes where it stopped |
| Played | An episode counts as **played** once it ends or fewer than 30 seconds are left. Played episodes restart from the beginning. Mark played/unplayed by hand from the episode's menu |
| Queue | **Up next**: "Play next" or "Add to queue" on any episode; drag to reorder, swipe to remove. When an episode ends the next one starts on its own |
| Speed | 0.8× to 3×, remembered **per show** (defaults to the last speed used). "Skip silence" switch (Media3 built in) |
| Sleep timer | 15, 30, 45, 60 min or **end of episode**; a quiet fade over the last few seconds; shake the phone to add 5 minutes |
| Downloads | Streaming by default. **Download** any episode to listen offline; optional **auto-download** of the newest episode per show, **Wi-Fi only** by default. Played downloads are deleted after a day |
| New episodes | Feeds refresh every few hours in the background (WorkManager) and on pull-to-refresh. An optional notification "3 new episodes" (one per refresh, not one per episode) |
| Settings | One **Settings** screen from the Library tab's top bar: skip lengths, auto-download, Wi-Fi only, new episode alerts, OPML import/export, app updates |
| Look | ChoreMe's **"Soft cards"**: light grey page, white cards, black and grey text, **one accent colour** for the main action and the selected tab. Artwork is the only other colour. Dark mode from day one. Font: Figtree |
| Pictures | **No emoji.** Controls use Google's **Material Symbols** (Apache 2.0), copied as vector drawables into `:core:ui`. Show and episode art comes from the feeds |
| Screenshots | Every screen (light, dark, the largest text size, empty) is drawn from its state alone (`XxxContent`) and screenshotted with Roborazzi on each release (`screenshots.zip`) |
| Offline | Room database on the phone is the source of truth; screens read it, the network only fills it. Downloaded episodes play with no signal |
| Updates | Same as ChoreMe: a release per CI build; the app checks the release page once a day and offers "Install" (`:core:update`, `:feature:update`) |
| Cost | **Everything free**: Apple's public search, GitHub Actions and Releases. No paid service, no server of our own |
| UI language | English |

## Data on the phone

Room database, owned by `:core:database`. Plain-Kotlin models in `:core:model`.

```
podcasts       feedUrl (key), title, author, description, artworkUrl, lastRefreshed,
               speed (null = use default), autoDownload
episodes       id (feedUrl#guid, key), podcastUrl, title, description, audioUrl, imageUrl,
               pubDate, durationMs, positionMs, played, lastPlayedAt, isNew
queue          episodeId (key), position
downloads      episodeId (key), state (QUEUED | RUNNING | DONE | FAILED), filePath, bytes
```

Episode states: `NEW → IN_PROGRESS → PLAYED`; marking unplayed goes back to the start.
The rules for "played", "resume from", queue order and sleep timer timing live in
`core/model` (`PlaybackRules`, `parse/`) and are unit tested on the plain JVM.

## Modules

```
app/                Activity, root screen, bottom navigation, mini player slot
core/model/         Plain Kotlin: podcasts, episodes, played/resume rules, queue order,
                    date and duration parsing, OPML
core/database/      Room: tables, DAOs, migrations
core/network/       OkHttp: feed download + RSS parsing, Apple Podcasts search
core/data/          Repositories joining network and database (subscriptions, episodes, queue)
core/playback/      Media3 service, player connection for the UI, sleep timer, speed
core/downloads/     Downloads, background refresh, new-episode alerts, clean-up (WorkManager)
core/settings/      This phone's choices (DataStore): skip lengths, auto-download, alerts
core/ui/            Theme, artwork, episode row, progress ring, shared strings
core/update/        Updating the app itself from the release page (ported from ChoreMe)
feature/
  home/             Continue listening, Up next, New episodes
  library/          Subscribed shows and each show's page
  discover/         Search, add by RSS link, OPML import
  player/           Full player and the mini player
  settings/         The Settings screen
  update/           "PodParar 0.12 is available – Install" banner
build-logic/
  convention/       Shared Gradle setup for each kind of module
  rules/            The module structure check
```

## Phases

- [x] **Phase 0: Skeleton.** Modules above (empty where not needed yet), convention
      plugins and the module-rules check copied from ChoreMe, theme and Figtree,
      bottom navigation with Home / Library / Discover placeholders, GitHub Actions
      building the APK, running the tests and publishing a release.
- [x] **Phase 1: Subscribe.** Discover tab: search the Apple Podcasts directory, add
      by RSS link. Feed parsing with tests against real-world feeds (CDATA, missing
      guids, odd dates, `HH:MM:SS` and seconds durations). Library grid, show page with
      episodes, pull-to-refresh, unsubscribe.
- [x] **Phase 2: Play.** Media3 service with lock screen, notification and headphone
      controls; mini player and full player; back 10 / forward 30; speed; resume
      where you stopped; played rules; mark played/unplayed.
- [x] **Phase 3: Home and queue.** Continue listening, Up next (play next, add, drag to
      reorder, swipe away, auto-continue), New episodes, "Mark all played".
- [x] **Phase 4: Offline and background.** Downloads (Wi-Fi only option, progress,
      delete), auto-download newest, background refresh every few hours, new-episode
      notification, clean-up of played downloads.
- [ ] **Phase 5: Comfort.** Sleep timer (with end of episode and shake to extend),
      per-show speed, skip silence, Settings screen, OPML import/export.
- [ ] **Phase 6: Updates from inside the app.** Port ChoreMe's `:core:update` and
      `:feature:update`; README with the fixed "latest APK" link.
- [ ] **Extras (later).** Chapters, Android Auto, a home-screen widget, episode search
      inside a show.

## Settled questions

- **Tabs**: Home / Library / Discover, with the queue (Up next) on Home.
- **Accent colour**: ChoreMe's green.
- **Downloads**: in Phase 4, as planned.
- **Sync**: local only; OPML moves subscriptions between phones.

## Open questions

- None right now.
