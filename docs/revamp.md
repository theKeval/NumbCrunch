# Revamp History and Roadmap

## Project Eras

### 2021: Original Prototype

The application, originally named Guesser, established the hidden-number game,
App and Friend modes, `R`/`W`/`SMYLE` scoring, XML navigation, and a RecyclerView
guess log.
Most interaction and state logic lived in `GameFragment`, and the build used
Android 30-era plugins and dependencies.

### April 2026: Compatibility Pass

Commit `c6fc98b` updated the original project for modern Gradle and Android
tooling and removed obsolete build configuration.

### May 2026: V1 Gameplay and UX Modernization

Commit `5b7cecd` substantially improved the original application:

- extracted pure `GameRules`;
- centralized round state in `GameViewModel`;
- modernized the guess model and adapter;
- refreshed Material layouts, themes, copy, and navigation;
- added a custom keypad, sound, vibration, animation, and confetti;
- added feedback settings and focused rule tests.

This version remains in `app/` and is now registered as `:guesser_v1`.

### August 2026: Documentation Baseline

PR #2 added the first comprehensive gameplay, architecture, implementation,
development, and roadmap documentation. Those documents originally described
the V1 application as the repository's only app.

### August 2026: Separate Modular Compose Revamp

PR #3, merged as `9288a48`, changed the project boundary rather than
incrementally rewriting V1. It:

- preserved `app/` as the independent `:guesser_v1` application;
- introduced the new Compose application, originally named Guesser (now
  `:numbcrunch`, with application ID `io.keval.apps.numbcrunch`);
- added `:gameplay`, `:domain`, `:data`, and `:core-ui`;
- implemented the Compose Home screen and player-mode selection;
- added masked, validated Double Player secret setup;
- handed friend secrets to Game through an in-memory repository and use cases;
- added animated navigation and shared secondary-screen scaffolding;
- preserved original design artwork separately from runtime drawables;
- added Home ViewModel and domain use-case tests;
- improved secret privacy, selector accessibility, validation consistency, and
  wood-background contrast during review;
- redesigned Home controls to be Compose-drawn and screen-size independent
  while retaining the background/title artwork.

## Current Baseline

The 1.2.0 sprint adds Easy/Medium/Hard setup and immutable round difficulty,
a first-run Welcome using the existing onboarding preference, and an expanded
Game Play guide. Easy provides automatic submitted-tile feedback; Medium
provides one hint for one submitted row per round; Hard preserves neutral tiles
and aggregate remarks. New Home selections default to Easy, while legacy
restored rounds and manual tutorial games use Hard. Hint use survives Android
round restoration, and the Tutorial keeps its existing neutral practice flow.

The primary revamp now includes a playable game. Its Home screen, setup
handoff, and Game screen are implemented. Tutorial, Gameplay, and About now
provide resource-backed learning, rules, and project content in the game theme.

The repository therefore has two valid but different baselines:

| Concern | Revamp `:numbcrunch` | V1 `:guesser_v1` |
| --- | --- | --- |
| UI toolkit | Jetpack Compose | XML Views |
| Architecture | Multi-module, contracts/use cases, manual container | Single app module, MVVM-style |
| Home/setup | Implemented | Implemented within game screen |
| Complete guessing loop | Implemented, including quit-and-reveal | Implemented |
| Rules/scoring engine | Implemented (`GuessRules`) | Implemented |
| Feedback/settings | Sounds, haptics, confetti, background music; music/sound-effect settings | Implemented |
| Session persistence | Round in `SavedStateHandle`; friend secret in memory; tutorial completion and audio settings in Preferences DataStore | Round in memory; feedback preferences persisted |
| Package | `io.keval.apps.numbcrunch` | `com.thekeval.guesser` |

## Current Risks and Gaps

1. **Partial game domain.** `GuessRules` covers validation, generation, and
   scoring, but round status and player mode live in the Game ViewModel rather
   than in domain models.
2. **Session-only handoff.** A friend secret is lost on process death and has no
   explicit consume lifecycle.
3. **Learning interaction coverage.** Tutorial deductions, example scores,
   and first-run completion use cases have unit tests, but practice/navigation/
   accessibility still need automated Compose coverage.
4. **Limited test depth.** Home/Game ViewModels, rules, and use cases are
   covered, but Compose behavior, navigation, and repository lifetime have no
   tests.
5. **Parallel app maintenance.** Build and documentation changes must avoid
   accidentally coupling or confusing V1 with the revamp.
6. **No automated delivery pipeline.** CI, signing, release automation, and
   store publishing remain undocumented/unimplemented.

## Recommended Next Phases

These phases describe a technically coherent path, not committed product
scope.

### Phase 1: Establish the Revamp Game Domain (largely done)

- Done: `GuessRules` ports validation, generation, and structured scoring
  without importing V1, with tests for the full remark table.
- Remaining: model round status and player mode explicitly in `:domain`, and
  define when the friend secret is consumed or cleared.

### Phase 2: Implement the Compose Game Loop (largely done)

- Done: stateful `GameRoute` + stateless `GameScreen`, both modes, keypad,
  scrolling history, win dialog, quit confirmation, help with state
  preservation, and ViewModel tests.
- Done: quit-and-reveal, swipe-back, sound, haptics, and win confetti.
- Remaining: Compose UI tests.

### Phase 3: Finish Product Content and Settings

- Done: resource-backed Tutorial with optional interactive practice, Gameplay
  visual rules reference, and About with story, credits, version, and links.
- Done: Settings with persisted music and sound-effect toggles.
- Decide whether a vibration setting belongs in the revamp.
- Add further settings storage only after each setting's lifetime and migration
  behavior are defined.

### Phase 4: Expand Product Capabilities Deliberately

- Decide whether rounds should survive configuration, process death, or full
  restarts.
- Evaluate profiles, scoreboards, statistics, and Google Play Games against
  concrete product requirements.
- Add CI, versioning, release notes, signing, and store metadata.

## Revamp Guardrails

- Keep `:guesser_v1` independent; do not use the application module as a
  library.
- Preserve established rules unless a product decision intentionally changes
  them.
- Represent secrets as strings so leading zeroes remain valid.
- Keep domain decisions free of Compose and Android dependencies.
- Pass sensitive setup through typed state/repositories, not navigation route
  text or logs.
- Distinguish implemented behavior from intended behavior in code, UI copy,
  tests, and documentation.
