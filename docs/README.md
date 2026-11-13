# NumbCrunch Documentation

These documents cover both applications in the repository:

- **NumbCrunch revamp** is the primary `:numbcrunch` Compose application.
- **Guesser V1** is the complete legacy application in `app/`, registered with
  Gradle as `:guesser_v1`.

The revamp currently implements its home, navigation, dependency boundaries,
Double Player setup handoff, and a playable Game screen. Tutorial is shown on
first launch, with persistent completion and an option to revisit it from Home;
Gameplay is the visual rules reference; About contains the family-origin story,
credits, runtime version, and an in-game privacy policy.

| Document | Purpose |
| --- | --- |
| [Gameplay and implementation status](gameplay.md) | Established rules, current revamp behavior, and V1 round flow |
| [Architecture](architecture.md) | Module graph, runtime wiring, state ownership, navigation, and V1 isolation |
| [Implementation guide](implementation.md) | Code walkthrough for the Compose foundation and legacy game |
| [Revamp history and roadmap](revamp.md) | Project eras, PR #3 scope, current gaps, and recommended next phases |
| [Development guide](development.md) | Setup, module-specific commands, testing, and change conventions |
| [Versioning and release guide](releasing.md) | Version numbers, upload-key signing, release checklist, and Play Console steps |
| [Player progress](player-progress.md) | Durable rounds, ownership, pause and local statistics |
| [Cloud backup](cloud-backup.md) | Saved Games, confirmed backup status and recoverable conflicts |
| [Achievements](achievements.md) | Truthful tutorial evidence and account-bound progress delivery |
| [Game Stats](play-games-game-stats-v1.md) | Five repetitive stats and delivery limitations |
| [Lifetime Hard leaderboard](play-games-leaderboard-v1.md) | Strict eligibility, score meaning and client trust limits |
| [Player data publication handoff](player-data-publication.md) | Privacy, Data safety, console resources and later publication steps |

## Sources of Truth

### Revamp

| Concern | Authoritative source |
| --- | --- |
| Included modules and V1 alias | `settings.gradle` |
| App version name and code | `version.properties` |
| Privacy policy text (website and in-game) | `PRIVACY.md`, bundled by `gameplay/build.gradle` |
| Version bump enforcement | `scripts/check-version-bump.sh` and `.github/workflows/version-guard.yml` |
| Release signing and bundle naming | `numbcrunch/build.gradle` |
| Shipped versions | `CHANGELOG.md` |
| App assembly and dependency wiring | `numbcrunch/src/main/java/io/keval/apps/numbcrunch/` |
| Navigation graph | `numbcrunch/.../NumbCrunchNavHost.kt` |
| Home state and validation | `gameplay/.../home/HomeUiState.kt` and `HomeViewModel.kt` |
| Home Compose UI | `gameplay/.../home/HomeScreen.kt` |
| Validation, secret generation, and remarks | `domain/.../game/GuessRules.kt` |
| Game round state | `gameplay/.../game/GameViewModel.kt` and `GameUiState.kt` |
| Game Compose UI | `gameplay/.../game/GameScreen.kt` and `GameComponents.kt` |
| Session handoff contracts | `domain/.../repository/GameSessionRepository.kt` |
| Session implementation | `data/.../repository/InMemoryGameSessionRepository.kt` |
| Shared theme | `core-ui/.../theme/` |
| Original artwork | `design_assets/raw_assets/` |
| Runtime artwork | `gameplay/src/main/res/drawable-nodpi/` (`home_background`, `home_title`, `game_board_wood`, `game_keypad_wood`) |

### Guesser V1

| Concern | Authoritative source |
| --- | --- |
| Validation, secret generation, and remarks | `app/.../domain/GameRules.kt` |
| Secret, guesses, and round state | `app/.../viewmodel/GameViewModel.kt` |
| Interaction and device feedback | `app/.../ui/GameFragment.kt` |
| Player-facing rules and copy | `app/src/main/res/values/strings.xml` |
| Feedback preferences | `app/.../data/UserPreferences.kt` |

When changing behavior, update the source, tests, player-facing copy, and the
corresponding document together. Do not describe planned revamp behavior as
implemented until it exists in `:numbcrunch` or its supporting modules.
