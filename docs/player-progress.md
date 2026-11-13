# Durable player progress

`ProgressRepository` is the source of truth for real rounds. `:domain` owns
round validation, lifecycle actions and statistics; `:data` implements it with
Android SQLite transactions. `AppContainer` owns one repository instance.
Settings and Tutorial still use the existing Preferences DataStore. Tutorial
practice is isolated; its normal Play Now route starts a real round.

## Identity and restoration

A profile has an opaque local UUID, a statistics tracking start time and an
optional unique Play Games player association. The association is not a
NumbCrunch account or primary key. Round ownership never changes. Guest and
associated profiles have separate namespaces; account claiming/switching belongs
to the subsequent connection and synchronization work.

Each round has a UUID, immutable owner, mode, difficulty, rules version and
origin. Ordered guesses, input, hint occurrence, outcome and revision commit
together. Updates require the previous revision; identical retries are accepted,
conflicting writes require reload and terminal outcomes cannot change. A round
starts counting at its first valid guess. Zero-guess rounds can be saved or
discarded without changing played/outcome totals.

Only the round ID and a dismissed-dialog ID remain in SavedStateHandle. An old
saved round can be validated and imported once, marked LEGACY, then its old game
facts are removed. Legacy facts do not establish leaderboard eligibility. No
past rounds or statistics are invented when upgrading. Database errors keep the
previous committed UI and expose retry; a failed exit never reports success.
An initial load failure also permits returning Home.

The database enforces one unfinished round per local owner. Home shows safe
metadata and Resume for each available round. Explicit guest claims may retain
both the guest and account round; neither is silently selected or deleted.
Home Start, Tutorial Play Now and replay share `RoundEntryViewModel`: an occupied
slot requires Resume or a separate confirmed abandonment before creating a
new round. Cancelling keeps the saved game, including zero-guess games.

Back/leave offers Save & pause, Continue playing or Abandon game; Give up &
reveal remains a distinct outcome. Save & pause commits before navigating Home.
Resuming restores the same ID, owner, mode, difficulty, secret, guesses, pending
input and Medium hint. Help, Settings and backgrounding record no outcome.
Round navigation carries only opaque owner/round IDs, never secrets or guesses.
Existing rounds retain their owner after account selection changes. Each new
round (including replay) pins the selected local owner at its start decision.

## Statistics and retention

Statistics project distinct valid round IDs. Played includes unfinished rounds
with a guess. Resolved is wins plus give-ups plus abandonments; win rate uses
resolved rounds. Winning average divides cumulative winning guesses by wins.
Both rates are absent when their denominator is zero. Counters use checked Long
arithmetic. Filter before calculating a mode/difficulty slice or its streak.
Completion time followed by round ID defines deterministic streak ordering;
unfinished rounds do not interrupt a streak. Duplicate identical imports count
once; conflicting copies reject the entire import transaction.

Schema 1 retains all rounds; it performs no destructive migration or pruning.
`exportProgress` provides a versioned snapshot with tracking start date, complete
round records and a recomputable aggregate. Cloud backup work must define an
acknowledged checkpoint/compaction protocol before deleting history. No delivery
ledger or cloud transfer is active in this ticket. Progress databases are
excluded from Android backup and device transfer to avoid restoring identities
or future delivery state independently of progress. Settings and tutorial backup
remain enabled. Clearing app data deletes local statistics.

Never log round secrets or guesses, or place them in navigation parameters or
telemetry. RoundRecord's string representation redacts game facts.

## Validation

Domain tests cover formulas, six mode/difficulty slices, duplicate IDs, completion
ordering, invalid transitions and overflow. GameViewModel tests cover failed
first guesses, failed leave/retry, replay IDs and restoration without game facts
in SavedStateHandle. `ProgressDatabaseTest` exercises actual SQLite reopening,
transaction rollback via an injected failing trigger, win/quit races, identity
isolation and atomic duplicate imports on an Android device/emulator.

Use the existing debug build/test commands in the development guide. Run the
device checks with `:numbcrunch:connectedDebugAndroidTest` and the instrumentation
class argument `io.keval.apps.numbcrunch.ProgressDatabaseTest` when isolating
database validation. No signed bundle is needed.
