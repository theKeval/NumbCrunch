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
- Final integrated validation passed 172 JVM tests (91 domain, 79 gameplay,
  and two app tests), debug assembly, instrumentation assembly, and debug
  lint after the memory and filter-contrast fixes. The command was
  `./gradlew :domain:test :gameplay:testDebugUnitTest :numbcrunch:testDebugUnitTest :numbcrunch:assembleDebug :numbcrunch:assembleDebugAndroidTest :numbcrunch:lintDebug`.
  Output is retained at `/private/tmp/numbcrunch-integrated-validation.log`.
- Both `PlayerExperienceMatrixTest` acceptance cases passed. Release manifest
  processing and the release runtime dependency report also succeeded without
  assembling a release artifact. The merged manifest retains the production
  package, API 24 minimum/API 36 target, project metadata and backup rules;
  the dependency graph contains pinned PGS 22.1.0 and no Firebase SDK.

`PlayerExperienceMatrixTest` adds explicit cross-feature cases for all six
mode/difficulty combinations and the 4/7/10 guesses example. The latter
requires average 7, three resolved wins, a separate played paused round, one
zero-guess paused round excluded from played, no abandonment from pause,
retained hint/input, and identical scores after a cloud round trip and merge.

## Device and feature matrix

| Surface | Purpose | Current evidence |
| --- | --- | --- |
| API 24 AOSP ARM64 emulator | New minimum, no Google services, offline startup/play/pause/statistics | 37 targeted fixtures and eight ordinary resume cycles passed on 92b97ad at unchanged 48 MB heap; six contrast fixtures and rendered Statistics passed on b4f0736 |
| API 36 AOSP ATD ARM64 emulator | Current API, no Google services, offline regressions | 28 focused fixtures passed on 9c0a2f7; 18 new/changed fixtures passed on 92b97ad; eight ordinary offline resume cycles passed |
| Connected Play-enabled API 31 phone | Real tester identity, SDK dialogs, Saved Games, achievements, Game Stats, leaderboard | Verified Saved Games, exact scratch restore, three native achievements, a real Single Hard score/rank, and all five visible Play Store Game Stats totals |
| Play-installed production package | App-signing certificate, ordinary non-allowlisted user, new/existing gamer profile | Deferred until a later authorized release and internal testing upload |

Android Emulator 37.2.13 and both requested system images installed. Its
Hypervisor.Framework check passed. API 24 default ARM64 revision 9 is in a
separate temporary SDK root; API 36 AOSP ATD ARM64 revision 1 uses the normal
SDK image directory. Existing accepted license records were reused; no new
terms were accepted. The coordinator-authorized interruption of the redundant
API 24 download avoided duplicating the already installed image. Temporary
AVDs use one-gigabyte data images through the supported `-data` option,
preventing the emulator's six-gigabyte new-image default from filling the disk.
No phone account settings were changed or existing game data cleared.
After testing, both temporary emulators were stopped. The disposable AVDs and
separate API 24 staging SDK were removed, recovering about 3.89 GiB. Logs,
screenshots and copied test artifacts were retained; the normal SDK's API 36
image remains installed. No further emulator or system-image download followed
the user's explicit instruction to keep remaining verification to the phone.

The copied coordinator artifacts for compatibility testing represent
`9c0a2f7`, the memory fix `92b97ad`, and contrast fix `b4f0736` (1.2.0/code 4), retained
under matching `/private/tmp/numbcrunch-qa-<commit>/` directories. Both emulators have no
`com.google.android.gms` package. API 24's stock application heap limit is
48 MB; API 36's is 192 MB. Both reported no active default network during
checks. These are emulator checks, not Google sign-in or Play-installed
production coverage. API 36 ATD disables hardware rendering, and its direct
screen capture was black. Its successful accessibility/instrumentation checks
do not establish pixel rendering, animation, or screenshot correctness. See
[Android's ATD limitations](https://developer.android.com/studio/test/managed-devices).

API 24 failure evidence is preserved: repeated ordinary save/Home/resume
crashed while decoding the 896 by 1600 background (5,734,412-byte allocation)
on the third resume; the app heap limit was not enlarged. The pause/restoration
instrumentation fixture also failed both in the batch and by itself.
The shared decoded-artwork fix in `92b97ad` closed this defect: all 37 targeted
fixtures passed in 9.011 seconds at the unchanged 48 MB heap, including the
previously failing restoration case. Eight ordinary save/Home/resume cycles
preserved the original guess and partial input. Java heap allocation
stayed approximately 23.3–24.3 MB. Rendered Statistics retained played 1,
wins 0, and given up 0; its image was visually inspected. No `largeHeap`
manifest flag or reduced artwork resolution was used.
API 36 passed all 28 focused fixtures in 46.108 seconds. Eight ordinary
save/Home/resume cycles then preserved submitted guess `123` and partial input
`4`, including force-stop/restart. The Statistics screen reported played 1,
wins 0, given up 0, left unfinished 0, and ongoing/paused 1. Explicit Connect
returned the truthful unavailable/device-only state without losing the round.
Java heap allocation stayed approximately 12.3–13.6 MB during the cycles;
this ATD observation does not establish rendered bitmap memory behavior.
After integration of the memory/cloud fixes, all 18 new/changed API 36 fixtures passed in
4.626 seconds: artwork sharing (3), snapshot task cancellation (4), active-game
cloud recovery (1), cloud database cases (7), and all-time ranking semantics (3).
Actual API 24 visual review found low-contrast default FilterChip labels on
the dark wood panels in light mode. `b4f0736` gives Statistics and the all-time
ranking filters explicit cream/gold colors and borders. All six affected
screen fixtures passed in 1.921 seconds, including explicit light-theme
coverage. The corrected actual Statistics screenshot was visually checked at
`/private/tmp/numbcrunch-api24-contrast-statistics.png`. Its selected and
unselected filters are readable. The connected ranking fixture passed; an
attempted API 24 screen recording failed in the emulator encoder, so it did
not supply additional visual proof of that connected-only view.

Logs are
`/private/tmp/numbcrunch-api24-five-fixtures.log`,
`/private/tmp/numbcrunch-api24-offline-fixtures.log`,
`/private/tmp/numbcrunch-api24-real-oom.log`,
`/private/tmp/numbcrunch-api24-fixed-manual-nav.log`,
`/private/tmp/numbcrunch-api24-final-fixtures.log`,
`/private/tmp/numbcrunch-api24-contrast-fixtures.log`,
`/private/tmp/numbcrunch-api36-offline-fixtures.log`,
`/private/tmp/numbcrunch-api36-manual-nav.log`, and
`/private/tmp/numbcrunch-api36-final-fixtures.log`.

The coordinator recorded actual tester-phone evidence on API 31, including a
final in-place upgrade to the `b4f0736` contrast build: a six-guess Single Player Hard win produced an
acknowledged lifetime score of 1, read back as rank 1 in both Top 25 and Near
me. Native Achievements showed Learning the Ropes, First Solo Solve, and Hard
Earned earned (3/10), with Hard Regular at 10%. A real paused Medium round,
including its spent hint and partial input, restored exactly into an isolated
scratch database twice. Saved Games reached server-confirmed status and
retained that state after restart. A normal abandonment reached SDK
`BUFFERED` for Game Stats. The tester's Play Store gamer profile subsequently
showed all five matching totals: rounds finished 3, puzzles solved 2, Hard solo
solves 1, unaided Medium solo solves 1, and guesses in finished rounds 13.
This visible-profile check is distinct from the earlier SDK buffering receipt.
Evidence includes `/private/tmp/numbcrunch-guesser16-live-restore.log` and
`/private/tmp/numbcrunch-sprint-evidence/final-contrast-hard-board.png`,
`final-contrast-near-me.png`, `native-three-earned-achievements.png`,
`play-store-all-game-stats.png`, and `play-store-all-game-stats-guesses.png`
in the same directory. A later
unaided Medium win left the Hard leaderboard at 1. The final local phone totals
were played 3, wins 2, given up 0, left unfinished 1, ongoing 0, win rate 66.7%,
and winning average 6. A final read-only server download restored four terminal
receipts and one tutorial completion into a disposable database; the zero-guess
discard remained excluded from played totals, and repeated import enqueued no
Game Stats events. The final phone Home showed Saved. This is one phone and one
tester account, not a two-device or account-switch proof.

The user explicitly chose current-phone-only live verification on 13 November
and declined a second device or Google Play-enabled emulator download. The
implementation handoff uses that agreed scope; the original two-device check
is deferred and is not marked passed.

The recorded checks above cover the stated configurations only. Account A/B
switching on real devices, a newly created or supervised gamer profile, true
two-device offline reconciliation, audible feedback/spoken TalkBack order,
and human rate-limit calibration
remain unverified. Preserve these acceptance/release gates and check the full
matrix below without marking unavailable scenarios passed:

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
   published resources, backup/recovery, native Achievements, and the custom
   all-time rankings view. Only then does the user promote that tested artifact to production.
   Agents do not upload, promote, publish releases, or push release tags.

Keep unperformed production checks as release gates even when sprint testing
is complete. Local test coverage cannot replace Google configuration and
Play-installed end-to-end verification.
