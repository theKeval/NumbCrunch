# Development Guide

## Requirements

- JDK 17
- Android Studio with Android SDK 36, or equivalent command-line tools
- An emulator or device running Android 7.0/API 24 or later for NumbCrunch;
  Guesser V1 still supports Android 5.0/API 21 or later

Set `ANDROID_HOME`, or create an untracked `local.properties`:

```properties
sdk.dir=/absolute/path/to/Android/sdk
```

## Understand the Module Names

The new application lives in `numbcrunch/` and is Gradle project `:numbcrunch`.
The legacy source remains in `app/`, but `settings.gradle` registers that
directory as `:guesser_v1`; there is no `:app` project.

List the configured projects with:

```bash
./gradlew projects
```

## Build and Test

Primary revamp:

```bash
./gradlew :numbcrunch:assembleDebug
./gradlew :numbcrunch:lintDebug
./gradlew :gameplay:testDebugUnitTest
./gradlew :domain:test
```

Guesser V1:

```bash
./gradlew :guesser_v1:assembleDebug
./gradlew :guesser_v1:testDebugUnitTest
./gradlew :guesser_v1:lintDebug
```

Combined local verification:

```bash
./gradlew --no-daemon \
  :numbcrunch:assembleDebug \
  :numbcrunch:lintDebug \
  :gameplay:testDebugUnitTest \
  :domain:test \
  :guesser_v1:assembleDebug \
  :guesser_v1:testDebugUnitTest \
  :guesser_v1:lintDebug
```

Connected Android tests require a running emulator or device and can be
selected per application:

```bash
./gradlew :numbcrunch:connectedDebugAndroidTest
./gradlew :guesser_v1:connectedDebugAndroidTest
```

## Install and Launch

Revamp:

```bash
./gradlew :numbcrunch:installDebug
adb shell am start -n io.keval.apps.numbcrunch.debug/io.keval.apps.numbcrunch.MainActivity
```

V1:

```bash
./gradlew :guesser_v1:installDebug
adb shell am start -n com.thekeval.guesser/.MainActivity
```

The different application IDs allow both builds to be installed together. The
revamp's debug build is `io.keval.apps.numbcrunch.debug`, labelled
"NumbCrunch Debug", so it also installs beside the Play version.

### Test Play Games sign-in

NumbCrunch pins `play-services-games-v2:22.1.0`, which exposes `GameStatsClient`
and `PlayerGameEvent`. The [Google SDK setup guide](https://developers.google.com/android/guides/setup)
and [release notes](https://developers.google.com/android/guides/releases)
document this version and the Android 7.0/API 24 baseline. Shared libraries
retain their lower minimum because they do not depend on the Play Games SDK.
SDK initialization runs for both debug and release; only diagnostic sign-in
logging is debug-only. This dependency upgrade does not yet record Game Stats.

Use a Google Play-enabled device with an existing Play Games profile. The
profile's Google account must be listed in Play Console's Play Games Services
testers and, while OAuth is in Testing, in Google Cloud's OAuth test users.
The Android credential for `io.keval.apps.numbcrunch.debug` must match the
SHA-1 of the certificate that signed the installed debug APK.

Install and launch the debug build using the commands above, then run:

```bash
adb logcat -d -s NumbCrunchPlayGames:D
```

Look for `Authenticated: true` from the latest launch. The SDK signs in at
startup and suppresses automatic prompts to create a Play Games profile.

For SDK upgrades, build and lint the debug app, run domain/gameplay unit tests,
and inspect `:numbcrunch:dependencies --configuration releaseRuntimeClasspath`
and `:numbcrunch:processReleaseMainManifest` without building a signed release.
Verify Welcome, Tutorial, game and audio settings on API 24 and a current API,
including offline and unavailable Play services. Install over an existing
debug build without clearing app storage to check settings and tutorial
progress survive. Devices below API 24 cannot install this update.

## Changing the Revamp

### Domain and Data

- Put platform-independent contracts, models, and use cases in `:domain`.
- Implement repository contracts in `:data`.
- Assemble concrete dependencies only at the `:numbcrunch` application boundary.
- Keep secrets and domain decisions out of navigation route strings and logs.
- Add Kotlin/JVM tests for pure logic before wiring UI.

### Gameplay UI

- Use a stateful `*Route` composable to create/collect state and a stateless
  `*Screen` composable to render it.
- Keep destination route names in `gameplay/navigation/GameplayRoutes.kt`.
- Put shared feature UI in `gameplay/common`; put cross-feature theme tokens in
  `:core-ui`.
- Put player-visible production copy in resources, including learning and
  informational screens.
- Preserve lifecycle-aware collection and accessible control semantics.

### State and Persistence

The friend-secret setup handoff is process-memory only. Once Game starts, its
secret and round state use `SavedStateHandle`, including difficulty and Medium
hint use. This supports Android background recreation, not a persistent saved-game
database. Before adding further persistence, define:

- whether a secret survives process death;
- when it is consumed or cleared;
- whether it may be backed up;
- how sensitive values are excluded from logs and navigation arguments.

Preferences DataStore holds tutorial completion and audio settings in one
`numbcrunch_preferences` file. New preference repositories must reuse the
`numbCrunchPreferences` delegate in `:data`; a second delegate for the same file
crashes at runtime. Do not store the friend secret there without first
answering the questions above.

### Audio

Sounds are synthesized by `design_assets/audio/make_audio.py`; see its README
to regenerate them. Play effects through `LocalGameSoundPlayer` or
`rememberTapFeedback` so the sound-effects setting is respected.

### Artwork

- Preserve original supplied files in `design_assets/raw_assets`.
- Put runtime-ready copies in the owning module's Android resources.
- Avoid runtime dependencies on source-design directories.
- Maintain content descriptions for interactive image controls; decorative
  images should remain hidden from accessibility semantics.

## Changing Guesser V1

V1 changes belong under `app/` and use Gradle prefix `:guesser_v1`.

For a V1 rules change:

1. Update `GameRules`.
2. Update `GameRulesTest`.
3. Update `GameViewModel` if state transitions change.
4. Update V1 string resources, its Rules screen, and `docs/gameplay.md`.
5. Verify App and Friend modes.

Do not duplicate new V1 logic in `GameFragment`, and do not make the revamp
depend on the V1 application module.

## Test Scope

- Use `:domain` JVM tests for new pure game rules and use cases.
- Use `:gameplay` JVM tests for ViewModel state transitions and validation.
- Use Compose UI/instrumentation tests for semantics, rendering, input, and
  navigation.
- Use V1 JVM tests for its existing `GameRules` and ViewModel behavior.
- Test compact screens because the Compose Home screen still relies on
  proportional scaling and a scrolling fallback for tight heights; the
  friend-secret keypad sheet overlays Home rather than resizing it.

## Documentation Maintenance

Keep these documents synchronized:

- behavior and implementation status: `docs/gameplay.md`;
- modules, dependencies, state, or navigation: `docs/architecture.md`;
- code-level runtime flow: `docs/implementation.md`;
- completed phases and future direction: `docs/revamp.md`;
- setup and commands: this guide and the root `README.md`;
- versioning, signing, and publishing: `docs/releasing.md`, `AGENTS.md`, and
  `CHANGELOG.md`.

After a release is tagged, the first change that affects the app moves
`version.properties` to the next version; run `scripts/check-version-bump.sh`
before opening a pull request. See the
[version lifecycle](releasing.md#version-lifecycle).

Always state whether behavior belongs to the revamp or V1.
