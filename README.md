# NumbCrunch

NumbCrunch is an Android number-deduction game in active redevelopment. The
repository now contains two independent applications:

| Application | Gradle module | Source | Status |
| --- | --- | --- | --- |
| **NumbCrunch revamp** | `:numbcrunch` | `numbcrunch/` | Primary Compose app; home, playable game, interactive tutorial, rules guide, and about implemented |
| **Guesser V1** | `:guesser_v1` | `app/` | Preserved, fully playable legacy game |

The revamp does not reuse V1 source or resources. Both applications build
independently from the same Gradle project.

## Current Revamp

The new app uses the package and application ID `io.keval.apps.numbcrunch`;
debug builds install separately as `io.keval.apps.numbcrunch.debug`
("NumbCrunch Debug"). Its current implemented experience includes:

- a responsive, scrollable, portrait-first Jetpack Compose home screen;
- Compose-drawn, resolution-independent controls for Single Player and Double
  Player modes;
- an independent Easy/Medium/Hard difficulty selection, saved with each round;
- a masked Double Player secret field with three-unique-digit validation;
- in-memory handoff of a valid friend secret to the Game destination;
- a playable Game screen with a custom keypad, scrollable guess board,
  difficulty-aware digit tiles with a staggered flip reveal,
  V1-style `R`/`W`/`SMYLE`/`Winner` remarks,
  quit confirmation, win dialog, and a help button that opens Gameplay while
  preserving the round;
- animated navigation to Game, Tutorial, Gameplay, and About destinations;
- a shared wood-background scaffold, back control, and Compose theme;
- clean module boundaries for UI, domain contracts, data, and app assembly.

First launch offers a wooden Welcome screen with **View tutorial** and **Go to
Home**. Tutorial is a three-step swipeable walkthrough with a separate practice
keypad and remains available from Home afterward.
Gameplay explains rules with scored tile examples, modes, controls, and tips.
New Home selections default to Easy; older restored rounds remain Hard.
Easy reveals green/orange/red feedback after submission. Medium offers one hint
per round to reveal one chosen submitted guess, while Hard keeps neutral tiles
and aggregate remarks. Game Play explains these rules and can be opened from
Home or in-game help without losing the round.
About presents NumbCrunch's family origin story, credits, installed version, and
a Privacy policy button that opens the full policy inside the game; nothing in
the app links out to a website. All three reuse the wooden game theme.
The new app includes music and sound-effect settings, but does **not yet
implement vibration settings, profiles, or scoreboards**.

## Game Rules

The established NumbCrunch rules, implemented in both applications, are:

- The secret and every guess contain exactly three distinct digits.
- `R` means a digit is correct and in the correct position.
- `W` means a digit occurs in the secret but in another position.
- `SMYLE` means the guess shares no digits with the secret.
- `Winner` means all three digits and positions match.
- Leading zeroes are valid because codes are represented as strings.

Example: secret `245` and guess `325` produce `1R, 1W`.

See [Gameplay and implementation status](docs/gameplay.md) for the complete
rules and a precise distinction between V1 behavior and revamp behavior.

## Architecture

```text
:numbcrunch (Compose application and navigation)
├── :gameplay (Home and destination UI)
│   ├── :core-ui (Compose theme)
│   └── :domain (repository contracts and use cases)
├── :data (in-memory repository implementations)
│   └── :domain
├── :domain
└── :core-ui

:guesser_v1 (independent legacy XML/View application)
```

| Module | Purpose |
| --- | --- |
| `:numbcrunch` | Application entry point, manual dependency container, and Compose navigation |
| `:gameplay` | Home state/UI and Game, Tutorial, Gameplay, and About destinations |
| `:domain` | Pure Kotlin repository contracts and use cases |
| `:data` | Repository implementations; currently app info and in-memory session data |
| `:core-ui` | Shared Material 3 color and typography foundation |
| `:guesser_v1` | Complete legacy game, physically retained in `app/` |

See the [architecture guide](docs/architecture.md) and
[implementation guide](docs/implementation.md) for the runtime flow.

## Technology

- Kotlin 2.2
- Android Gradle Plugin 9.0.1 and Gradle 9.1
- Java 17
- Compile/target SDK 36; minimum SDK 21
- Jetpack Compose and Navigation Compose for the revamp
- XML Views, Navigation Component, LiveData, and ViewModel for V1
- JUnit 4 tests

The current revamp has no backend, account system, analytics, or network
integration. Friend secrets are handed to Game in memory; once a round starts,
Android saved state can restore its secret, guesses, difficulty and hint use
after background process recreation.

## Build and Test

Prerequisites:

1. JDK 17
2. Android SDK 36
3. `ANDROID_HOME` configured, or `sdk.dir` set in an untracked
   `local.properties`

Build both applications and run their JVM tests:

```bash
./gradlew :numbcrunch:assembleDebug \
  :gameplay:testDebugUnitTest \
  :domain:test \
  :guesser_v1:assembleDebug \
  :guesser_v1:testDebugUnitTest
```

Build only the primary revamp:

```bash
./gradlew :numbcrunch:assembleDebug
```

Install and launch the revamp:

```bash
./gradlew :numbcrunch:installDebug
adb shell am start -n io.keval.apps.numbcrunch.debug/io.keval.apps.numbcrunch.MainActivity
```

Install and launch V1:

```bash
./gradlew :guesser_v1:installDebug
adb shell am start -n com.thekeval.guesser/.MainActivity
```

## Release

The app version lives in `version.properties` and is read by
`numbcrunch/build.gradle`. Signed Play bundles are built with:

```bash
./gradlew :numbcrunch:bundleRelease
```

Signing requires one-time setup outside the repository. See the
[release guide](docs/releasing.md) for versioning rules, signing, and the Play
Console process, and [`CHANGELOG.md`](CHANGELOG.md) for shipped versions.

## Repository Layout

```text
numbcrunch/       # New Compose application
gameplay/         # Revamp screens, home state, and navigation route names
domain/           # Revamp repository contracts and use cases
data/             # Revamp repository implementations
core-ui/          # Revamp Compose theme
app/              # Guesser V1 source, registered as :guesser_v1
design_assets/    # Original source artwork; not runtime resources
docs/             # Architecture, gameplay, implementation, and revamp docs
```

## Documentation

- [Documentation index](docs/README.md)
- [Gameplay and implementation status](docs/gameplay.md)
- [Architecture](docs/architecture.md)
- [Implementation guide](docs/implementation.md)
- [Revamp history and roadmap](docs/revamp.md)
- [Development guide](docs/development.md)
- [Versioning and release guide](docs/releasing.md)
- [Changelog](CHANGELOG.md)
- [Agent guide](AGENTS.md) for AI coding agents
- [Privacy policy](PRIVACY.md) for the published app
