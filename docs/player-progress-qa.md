# Player progress verification and later release handoff

This is the GUESSER-23 execution ledger for the unreleased NumbCrunch
1.2.0/code 4 player-progress sprint. It distinguishes local/test evidence,
Google test configuration, actual Google processing, and later release gates.
It is not a production sign-in certificate. No signed bundle, app release,
release tag, or new PGS publication was produced for this work.

## Recorded evidence

On 13 November 2026:

- The pure cloud implementation passed 40 domain tests, including full/local
  versus redacted/cloud statistics, account claims, divergent rounds,
  corruption, bounded partitioning, and deterministic merge properties.
- The Game Stats branch passed 58 domain tests and compiled its pinned-22.1.0
  SDK adapter and four isolated SQLite instrumentation tests. Compilation is
  not a substitute for running those tests on a device.
- All ten achievement icons and the leaderboard icon were visually checked
  at 88 and 48 pixels. All five Game Stats icons were also checked in color
  and grayscale at those sizes. Upload assets are 512-pixel transparent PNGs.
- The coordinator imported all five Game Stats definitions into Play Console.
  They are Draft / Available to testers, with the seven-property
  `roundCompleted_v1` event. The repetitive-only ZIP was accepted without a
  progression CSV. This verifies configuration import, not server event
  acceptance or gamer-profile increments. Coordinator evidence is retained
  at `/private/tmp/numbcrunch-five-stats-draft.jpg`.
- The version guard accepted 1.2.0/code 4 after release tag v1.1.0/code 3.
  No per-ticket version increment was introduced.

`PlayerExperienceMatrixTest` adds explicit cross-feature cases for all six
mode/difficulty combinations and the 4/7/10 guesses example. The latter
requires average 7, three resolved wins, a separate played paused round, one
zero-guess paused round excluded from played, no abandonment from pause,
retained hint/input, and identical scores after a cloud round trip and merge.

## Device and feature matrix

| Surface | Purpose | Current evidence |
| --- | --- | --- |
| API 24 AOSP ARM64 emulator | New minimum, no Google services, offline startup/play/pause/statistics | SDK image installation in progress; runtime checks not yet performed |
| API 36 AOSP ATD ARM64 emulator | Current API, no Google services, offline regressions | SDK image installation in progress; runtime checks not yet performed |
| Connected Play-enabled API 31 phone | Real tester identity, SDK dialogs, Saved Games, achievements, Game Stats, leaderboard | Reserved for coordinator/platform device work; do not infer completed flows from unit tests |
| Play-installed production package | App-signing certificate, ordinary non-allowlisted user, new/existing gamer profile | Deferred until a later authorized release and internal testing upload |

At setup inspection the SDK installer was actively downloading, not hung. It
had not yet installed either system image. The QA agent did not replace or
stop the coordinator's installer and used no additional large images. AVDs
are temporary test devices; never change the phone's Google accounts or clear
its game data to make a test pass.

The final combined run must record its commit SHA, actual test counts, device
API/build, and failures/retests. Check all of the following without marking an
unavailable scenario passed:

- Fresh guest and completed introduction; offline/no-services startup;
  Connect, cancellation, no-profile creation, returning profile, and supported
  versus restricted PGS features. No birthdate collection or SDK bypass.
- First valid guess, zero-guess save, win, Give up & reveal, Abandon, save and
  resume, occupied-slot guards on every new-round route, rapid taps, and write
  failure recovery. Tutorial practice remains separate from player totals.
- Help, Settings, audio, Welcome/Tutorial, normal text and large text, compact
  screens, content descriptions, TalkBack focus, background/recreation and
  force-stop/restart. Automated semantics checks do not prove spoken TalkBack
  order or audible feedback.
- One-time guest claim, two accounts and delayed callbacks, immutable round
  ownership, two offline devices, independent and divergent rounds, multiple
  unfinished games, and corrupt/oversized/future cloud snapshots. Verify
  cancellation and failure preserve local progress.
- Filtered statistics, averages/denominators, hints and streak ordering after
  import; all ten achievement predicates; genuine tutorial completion versus
  Skip; monotonic achievement reconciliation; Game Stats schema and its
  documented ambiguous-handoff loss policy; strict Single Hard leaderboard
  eligibility, lifetime ordering, duplicates, and account isolation.
- Real PGS server responses and native views. A local SDK BUFFERED result is
  not proof of Game Stats ingestion, and a locally committed snapshot is not
  proof of server backup. Record processing delay and SDK validation errors.

## Release implications already reviewed

The app keeps `io.keval.apps.numbcrunch.debug` for test builds and
`io.keval.apps.numbcrunch` for production. `PlayGamesSdk.initialize` stays in
the Application. Manifest metadata binds project 552743622678; automatic
profile-creation suppression keeps explicit Connect responsible for prompting.
Each credential still needs its own package and signing certificate match.
Debug success does not verify the Play app-signing SHA-1 used after upload.

Both Android backup XML files exclude all databases, including player-account
associations and delivery outboxes, from cloud backup and device transfer.
Saved Games is the explicit account-bound restore path. Verify the merged
manifest and dependency consumer R8 rules in the final branch; do not add broad
keep rules merely to hide an uninvestigated release problem. Review/build
release resources and lint without generating a signed APK/AAB in this phase.

The competitive Game Stats ceiling is currently a draft 600 Hard wins/hour.
The exhaustive 720-secret solver is a rules-only rehearsal. Its 5.267 mean
guesses and 495.4/hour fast-input model are not observed human performance.
Time actual experienced-player rounds before publishing this rate limit.

## Later authorized release checklist

1. Finish all unperformed integration/tester gates above and the coordinator's
   final unit, instrumentation, lint, dependency, and version checks. Fetch
   tags and run `scripts/check-version-bump.sh`; retain the existing unreleased
   code rather than inventing another release number.
2. Have the user review and publish the tested PGS resource changes through
   Play Console. Confirm all production Play app-signing SHA-1 certificates
   have matching Android credentials and the production package name.
3. Reconcile final `PRIVACY.md`, its effective date, and Play Data safety
   answers for account associations, progress backup, achievements,
   leaderboard scores, and per-round Game Stats sharing. After merge, republish
   the source policy at `https://keval.io/apps/numbcrunch/privacy`; the website
   does not update automatically.
4. Wait for an explicit later release request. Then follow
   [the release checklist](releasing.md#release-checklist), including the
   repository's signing verification and registered fingerprint. Do not read,
   print, copy, replace, or commit signing secrets. Do not reuse a released
   version code or weaken the release guard.
5. The user uploads the resulting bundle to a Play internal testing track.
   Verify the actual Play-installed production package with a non-PGS-
   allowlisted account and both new/existing gamer profiles. Confirm sign-in,
   published resources, backup/recovery, and native achievement/leaderboard
   views. Only then does the user promote that tested artifact to production.
   Agents do not upload, promote, publish releases, or push release tags.

Keep unperformed production checks as release gates even when sprint testing
is complete. Local test coverage cannot replace Google configuration and
Play-installed end-to-end verification.
