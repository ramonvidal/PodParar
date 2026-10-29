# PodOuvir: project contract

Rules every change must follow. Product decisions and the roadmap live in `PLAN.md`;
setup and build instructions in `README.md`. Modelled on ChoreMe's contract.

The app was renamed from PodParar to PodOuvir; internal identifiers (applicationId
`com.podparar`, packages `com.podparar.*`, `podparar.*` plugins, `podparar.db`, the repo name)
intentionally keep the old name so installed copies keep updating and keep their data.

## 1. Modular structure

Keep the code as modular as possible: small Gradle modules with one job each and
narrow public APIs. **Breaking the structure is never a silent side effect of
another change.** If a task seems to need it, stop and tell the user what rule is
in the way and what you propose, before writing the code.

### Module structure

| Module | Job | May depend on |
|---|---|---|
| `:core:model` | Models and rules in **plain Kotlin** (no Android): podcasts, episodes, played/resume rules, queue order, date/duration parsing, OPML | nothing |
| `:core:database` | Room: tables, DAOs, migrations | `:core:model` |
| `:core:network` | OkHttp: feed download and RSS parsing, Apple Podcasts search | `:core:model` |
| `:core:data` | Repositories joining network and database | `:core:model`, `:core:database`, `:core:network` |
| `:core:playback` | Media3 `MediaSessionService`, the player connection the UI uses, sleep timer, speed | `:core:model`, `:core:data`, `:core:settings` |
| `:core:downloads` | Episode downloads, background feed refresh, new-episode alerts and storage clean-up (WorkManager) | `:core:model`, `:core:data`, `:core:settings` |
| `:core:settings` | This phone's choices (DataStore) | `:core:model` |
| `:core:ui` | Theme, shared composables, strings used by more than one feature | `:core:model` |
| `:core:update` | Updating the app itself from the GitHub release page | `:core:model` |
| `:feature:<name>` | One tab or screen: `home`, `library`, `discover`, `player`, `settings`, `update` | `:core:*` only |
| `:app` | Wires features together: activity, root screen, navigation | anything; nothing depends on it |

- **Features never depend on each other.** What two features share moves down into
  a `:core` module.
- **Each feature exposes one entry point**, its `XxxNavigation.kt` (a route constant
  and a `NavGraphBuilder` extension). Screens and helpers are `internal`; view models
  stay public because Hilt needs them to be. Known exceptions, keep the list short:
  - `:feature:player`: `MiniPlayer()`, which `:app` shows above the bottom bar.
  - `:feature:update`: `UpdateBanner()`, which `:app` shows at the top of the screen.
- **Features talk to each other only through `:app`.** If one needs to open another
  (an episode on Home opening its show in Library), it takes a callback like
  `onOpenPodcast` and `:app` does the navigation.
- **Logic goes in `:core:model`** whenever it doesn't need Android, so it stays testable
  on the plain JVM. View models orchestrate; they don't hold business rules.
- **Media3 types stay inside `:core:playback`**, Room types inside `:core:database`,
  OkHttp inside `:core:network`. Other modules see `:core:model` types only.
- **No emoji in the UI.** Controls are Material Symbols vector drawables in `:core:ui`
  (`ic_*`), copied from the library, not drawn by hand.
- **Each module owns its strings.** Shared ones (Cancel, Retry, error messages) live in
  `:core:ui` and are imported as `com.podparar.core.ui.R as CoreUiR`.
- **New modules use the convention plugins** in `build-logic/convention`
  (`podparar.android.feature`, `podparar.android.library`, `podparar.jvm.library`, ...).
- **New core modules** must be added to `ModuleRules.CORE_DEPENDENCIES` with what they
  may depend on. That's a deliberate decision, not a workaround.

### How breaking it is caught

`build-logic/rules` (`podparar.module-rules`, applied in the root `build.gradle.kts`)
checks these rules every time Gradle configures the project, so any build fails with a
message naming the broken rule. Never weaken or bypass it to get a build green.

## 2. Data

- The Room database is the source of truth; screens observe it, network calls only
  write to it. A feed refresh must never reset an episode's position or played state.
- Every schema change comes with a Room migration and a migration test. Schemas are
  exported to `core/database/schemas/`.

## 3. Checks before pushing

- `./gradlew :core:model:test testDebugUnitTest`
- `./gradlew assembleDebug`
- Screenshot tests (Roborazzi) for each screen's `XxxContent`; look at them after a UI change
- `./gradlew -p build-logic :rules:test` when the module rules change

CI runs all of these. The Android SDK can't be downloaded in Claude's cloud sessions,
so there CI is the only full Android build: don't call an Android change done until
CI is green.

## 4. How Claude works on this repo (the owner's standing requirement)

- **The main Claude session is the coordinator** for each task or phase (subagents
  can't start subagents of their own). It writes a short contract for the phase (the
  exact public APIs each part exposes), splits the work into specific, self-contained
  subtasks, hands each to its own subagent with a clear scope (the modules and files
  it owns), and merges and checks the results. It doesn't write the bulk of the code
  itself.
- **Subagents get narrow jobs**, usually one module or one feature each, with the
  module rules above in their instructions. Subagents whose files don't overlap run
  in parallel.
- **Tests are written by a separate agent**, never by the agent that wrote the code
  under test. The test agent works from the spec (`PLAN.md`, the public API and the
  rules), not from the implementation, so tests catch what the implementer missed.
- The coordinator runs the checks in section 3 (through CI) and sends failures back
  to the agent that owns the failing code or test.
