# Play Games cloud backup

Local SQLite progress is always the source for play. The optional Play Games
Saved Games connection restores the selected connected player's progress on
another installation without replacing immutable round owners. Guest selection
stops account sync; importing guest progress requires the existing explicit
confirmation. A failed connection never removes local rounds or totals.

## Runtime integration

`ProvideCloudBackup(progressRepository, session) { content() }` supplies
`LocalCloudBackup` beneath the Activity's connection provider. Its Activity-bound
SDK gateway exists only in the foreground. The controller serializes sync and
captures the authenticated `Player.playerId`, connection generation and selected
local account profile. Account changes, Guest selection, backgrounding and
Activity destruction cancel the old job. Late callbacks cannot change the new
session or select a different owner.

`CloudBackupRoute` shows Device only, Saving, Saved, Offline or waiting to back
up, or Needs attention. Saved means a forced, non-stale metadata refresh found
no pending changes and the exact manifest and chunks were read back. PGS
`commitAndClose` alone only queues upload in the SDK. A last-success timestamp
stays visible when a later update is pending. Retry is available without
blocking the keypad, local statistics, or saved-round resume.

Writes are debounced; transient failures receive three delayed attempts before
waiting for another local change, foreground visit, or explicit retry. Every
write remains in SQLite, so process death does not depend on an in-memory retry
queue. The server archive is reread and merged before each attempt. The gateway
uses the manual SDK conflict policy and checks account identity before and after
asynchronous SDK operations. Debug logs expose only stage, result and numeric
status; never IDs, gamer names, round contents, hashes or error messages.

## Persistence and recovery

SQLite schema 3 adds cloud namespaces, secret-free imported outcomes, tutorial
evidence, observed lineage, pending choices and one local recovery archive.
Schema 4 adds the separate Game Stats outbox. Upgrades preserve schema 1 and 2
profiles and rounds. An import atomically validates and merges fresh local data,
associations, receipts, unfinished rounds and tutorial completion. Failed imports
roll back all changes. Imported receipts are not fabricated full rounds and
never enqueue activity events. Finishing a round imported from another
installation also does not replay an event for that remote installation.

Same-round forks remain durable until a player explicitly chooses a version.
A completed version cannot be replaced with an unfinished one. Independent
unfinished rounds are all retained; the player chooses which appears first on
Home. Closing the dialog makes no choice. A local write after the prompt is
merged again inside the transaction, so an outdated choice cannot overwrite it.
Candidates are shown by mode, difficulty, outcome and guess count without
revealing a secret or pending input.

The cloud format and bounds are in [Cloud progress v1](cloud-progress-v1.md).
Immutable chunks are uploaded and confirmed before a root manifest can refer
to them. This preserves the previous usable root when a chunk upload is
interrupted. No remote chunk is automatically deleted: another device's pending
manifest may still require it. Local cached chunks are pruned only when none of
the state, pending-choice or recovery manifests references them. Full local
facts and provenance are retained. A corrupt, unsupported or oversized archive
shows Needs attention and leaves local play intact; there is no destructive
reset or timestamp-based winner.

Cloud readiness for achievement/leaderboard delivery is stricter than a
connected account. Consumers must match account, generation,
`reconciliationReady`, and `reconciledStamp == snapshot.cloudProjectionStamp()`.
The stamp is captured inside the import transaction and granted readiness only
after exact cloud confirmation. Computing this bounded canonical projection
belongs off the UI thread and should fail closed on invalid/oversized data. A
new local win, guest claim or tutorial completion cannot use an older ready flag.
A local-change revision also fences the final coverage read: a write observed
while that read is in flight keeps the status Pending even if the read returned
an older snapshot.

If cloud reconciliation replaces the revision currently displayed on the game
screen, the next attempted action opens an explicit recovery choice. Every local
write compares the full expected record in the SQLite transaction, because two
cloud branches can have the same numeric revision. The old
view remains visible until the player reloads the committed successor or returns
Home. A completed cloud receipt offers Home and updated statistics rather than
inventing missing guess history, another outcome, or a successful pause. SDK
results that arrive after cancellation are closed/released, and archive download
limits are enforced while fetching chunks rather than after accumulating them.

## Console and privacy

Enable Saved Games for the game's Play Games Services project. Google documents
that activation can take up to 24 hours. The PGS Android SDK handles its games
and application-data scope; no email scope, Firebase project, OAuth server auth
code or separate Google Sign-In SDK is added here. NumbCrunch never opens an
external website. Google's device account/settings UI remains responsible for
account creation, selection and Play Games data controls.

Only the selected connected account's claimed progress uploads. Completed
rounds upload summary receipts. Unfinished rounds upload the state needed to
resume, including the puzzle secret, guesses, hint state and pending input;
this includes a Double Player secret entered on the device. Settings and
account credentials are not part of this archive. Clearing app storage removes
local history but may be followed by cloud restoration. Deleting Google game
data does not clear NumbCrunch's local database, and reconnecting with retained
local progress can back it up again. Privacy copy and Play Console Data safety
must describe this behavior before release.

## Verification

`CloudBackupControllerTest` covers buffered versus confirmed writes, exact
coverage races, account changes, recoverable conflicts, retained unfinished
choices, and retries. `CloudProgressDatabaseTest` uses two isolated actual SQLite
databases to verify namespace-preserving restore, stats, paused state, tutorial
evidence, rollback and migrations. `GameStatsTransactionTest` verifies that local
terminal facts and their outbox write commit together, retries deduplicate, and
remote namespaces do not replay activity. `CloudBackupCardTest` checks pending
copy, retry, secret-free conflict controls, cancellation and account changes.
`SnapshotTaskCancellationTest` verifies cleanup of undelivered SDK results.
`GameCloudRecoveryTest` exercises stale-game reload and receipt-only completion
through the real screen and an isolated SQLite database.
These fixtures do not prove real cross-device Saved Games behavior.

`LiveCloudRestoreTest` is opt-in and skipped by default. With an already
authenticated, authorized tester and an existing confirmed backup, run only
that class with `-Pandroid.testInstrumentationRunnerArguments.liveCloudRestore=true`.
It uses SDK load/open/discard calls, never commit or conflict resolution, and
restores into a disposable SQLite database. It verifies facts, exact unfinished
state, tutorial evidence, statistics and no event replay, then deletes only its
own temporary database. Counts are logged without IDs or payloads. This proves
a fresh local-store restore on one device, not an independent second-device
server restore. An empty archive does not exercise round/hint preservation.

Before release, use two Google Play-enabled installations and the intended
tester account to confirm initial backup, another-device restore, offline play
then reconnect, interrupted upload, simultaneous branch resolution, app process
restart and account A/B isolation. Verify Saved through both visible UI and safe
SDK diagnostics. Do not clear a person's local or Google data just to simulate a
fresh installation. Use a separate authorized test installation instead.

Official behavior checked 13 November 2026:

- [Saved Games overview and limits](https://developer.android.com/games/pgs/savedgames)
- [Android Saved Games integration](https://developer.android.com/games/pgs/android/saved-games)
- [SnapshotsClient API semantics](https://developers.google.com/android/reference/com/google/android/gms/games/SnapshotsClient)
- [Play Games activation](https://support.google.com/googleplay/android-developer/answer/2990418)
- [Google Play Games data deletion](https://support.google.com/googleplay/answer/9130646)

## Recorded development verification

On 13 November 2026, the integrated debug build on the authorized API 31 tester
phone authenticated successfully and reached `server_confirmed` after pending
uploads. Home displayed Saved and a last-backup time. A force-stop/relaunch
reached server confirmation again without clearing app data.

A normal Single Player Medium game was played through the UI: one guess, the
single hint spent, one partial input digit, then Save & pause. After the visible
Saved result, the opt-in read-only fixture with `liveCloudRequirePaused=true`
downloaded the confirmed archive and imported it twice into a separate temporary
SQLite database. The exact paused record, hint/input, statistics and ownership
matched, and no Game Stats event was enqueued. The fixture passed with one
unfinished round. No secret, ID or snapshot payload was logged. This is evidence
for real Saved Games plus a fresh local store on one phone; two independent
Google Play-enabled devices, supervised accounts and actual account A/B isolation
remain separate release checks.


The same phone then completed a real Single Player Hard win in six guesses,
a genuine three-page Tutorial completion with its practice solved and final
Play now action, and a real Single Player Medium win in six guesses without
using a hint. The game was played through normal visible controls; the two
wins used candidate filtering from displayed R/W clues, never a stored secret,
injected outcome, or fabricated score. The untouched round opened by Tutorial
completion was abandoned through the normal UI and excluded from played totals.

The final debug APK was upgraded in place with data preserved. Its local
Statistics screen showed three games played, two wins, one game left, zero
give-ups, zero ongoing/paused rounds, and six average guesses in wins. The Hard
leaderboard stayed at one after the Medium win; Play Games acknowledged that
score and returned first place with score one in both Top 25 and Near me.
Google's native achievements UI showed Learning the ropes, Hard earned, and
First solo solve unlocked. Home returned to Saved after foreground
reconciliation. The final contrast-build APK tested had SHA-256
`f7f6fdbffad8b50784054183d306daa201be992047717c55488c689e84a56d9e`.

A second opt-in read-only restore passed against that final build and confirmed
four terminal receipts, no unfinished rounds, and one tutorial completion in a
fresh temporary local database. One receipt was the unplayed Tutorial-start
abandonment; it remained excluded from the three played games. Repeated import
preserved statistics and ownership without enqueueing Game Stats events. All
five server-visible Game Stats were also observed; see
[Game Stats verification](play-games-game-stats-v1.md#recorded-phone-verification).

The user explicitly limited remaining verification to the current phone.
No independent second-device cloud restoration, fresh account/profile creation,
supervised account, or live account A/B switch is claimed by this evidence.
The isolated-database tests and single-phone server reads are not substitutes
for those scenarios. No signed release or Play production upload was made.
