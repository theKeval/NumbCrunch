# Changelog

Releases of the NumbCrunch app (`:numbcrunch`) on Google Play, newest first.
Each entry records the version name, the version code Play consumed, and where
it shipped. The version comes from [`version.properties`](version.properties);
see [docs/releasing.md](docs/releasing.md) for the release process.

## 1.2.0 (versionCode 4) — unreleased

- Add Easy, Medium and Hard difficulty selection with round restoration.
- Easy reveals green/orange/red feedback after each submitted guess; Medium
  provides one hint per round to reveal one submitted guess; Hard keeps remarks only.
- Restore Game Play from Home and in-game help with difficulty explanations
  and worked examples, while preserving the interactive Tutorial.
- Introduce a first-launch Welcome with Tutorial and Home choices.
- Refine feedback to centered, color-only digits with orange misplaced clues;
  Medium uses the info slot for Hint, preserving full-size guesses and tappable
  remarks. Difficulty labels use the warm gold game styling.
- Google Play Games Services can sign in players with an existing profile.
  Gameplay remains available offline and without an account.
- Require Android 7.0 (API 24) or later and upgrade the Play Games SDK to 22.1.0
  for Game Stats support. Older Android devices cannot receive this update;
  Guesser V1's Android 5.0 minimum is unchanged.
- Persist round state and outcomes locally, with duplicate-safe statistics and
  retryable save errors. Save and pause ongoing rounds, resume them from Home,
  and require an explicit choice before replacing an unfinished game.
- Show Play Games connection and player selection on Home, with an optional
  Connect action and confirmed one-time guest-history import. Keep offline
  play and each player's local progress separate.
- Add offline Statistics with combined mode/difficulty filters, win and leave
  totals, weighted guess averages, hint use, streaks, and clear empty states.
- Define ten achievements and all-time Single Player Hard win scoring, with
  original Play Games artwork and validated progress projections for integration.
- Define a bounded cloud progress format that omits completed-round secrets,
  preserves lifetime totals, and requires explicit choices for conflicting saves.

- Back up connected player progress and paused games through Saved Games, with
  visible pending/error states and recoverable conflict choices.
- Deliver ten achievements, including verified tutorial completion, and show
  native achievements plus an all-time Single Player Hard wins leaderboard.
- Report five Game Stats from new round outcomes while preserving exact offline
  statistics; no old history or cloud restore is replayed as new activity.
- Update privacy and terms for Google cloud storage, shared accomplishments,
  profile visibility, separate deletion controls, and age-dependent availability.
- Recover an open game when cloud reconciliation replaces its saved version,
  and prevent stale branches or interrupted SDK callbacks from overwriting progress.
- Share decoded artwork across screens to prevent memory crashes on older
  Android devices without increasing the app's heap limit.
- Keep statistics and leaderboard filters readable in light system themes.

## 1.1.0 (versionCode 3) — internal and closed testing

- New in-game Privacy policy screen, opened from About.
- About no longer links to websites; nothing in the game takes you out of
  the app.
- Release builds optimize and obfuscate code and remove unused code and
  resources to reduce the app size.
- Debug builds install as a separate app, `io.keval.apps.numbcrunch.debug`
  ("NumbCrunch Debug"), so they no longer block installing the Play version.
- A version guard fails pull requests and `bundleRelease` when app files
  change after a release tag without moving to the next version.

## 1.0.1 (versionCode 2) — 2026-10-12, internal testing

- No player-visible changes.
- Version is read from `version.properties`, and release bundles are signed
  with the upload key when the `NUMBCRUNCH_UPLOAD_*` properties are set.
- Bundles are named `numbcrunch-<versionName>-<versionCode>`.
- Built from commit `e3cb553` (tag `v1.0.1`).

## 1.0 (versionCode 1) — 2026-10-11, internal testing

- First NumbCrunch release: Single and Double Player modes, interactive
  tutorial, Gameplay rules guide, and About screen.
- Built from commit `1391373`.
