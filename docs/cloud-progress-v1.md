# Cloud progress format v1

GUESSER-15 defines the pure format and merge contract. GUESSER-16 owns the
account-bound Saved Games adapter, SQLite transactions, conflict UI, retry,
and acknowledgement tracking. No cloud operation is required to play locally.

## Data and ownership

`CloudProgress` contains a PGS account binding, independent local profile
namespaces associated with that same account, explicit `GuestProgressClaim`
records, round versions, and genuine `TutorialCompletion` evidence. The SDK
adapter supplies the authenticated account to every read and import. A file's
self-declared account is insufficient. Guest-only progress cannot upload;
unclaimed guest facts and contradictory claims fail validation.

Round IDs and original owner IDs remain immutable across devices and guest
claims. Two installations can have different local profile UUIDs for the same
PGS account. Import preserves those namespaces rather than rewriting round
owners. Claim provenance grants statistics scope, never permission to send old
activity events. Imports cannot select another account or claim guest data on
that account's behalf.

Completed rounds become `OutcomeFact` receipts: ID, owner, mode, difficulty,
origin, rules version, revision, terminal outcome, guess count, hint usage, and
creation/start/completion times. They contain no secret, individual guesses,
pending input, player name, email, or delivery state. Only unfinished rounds
carry the full state needed to resume. Receipts are validated source facts, not
aggregate counters or fabricated `RoundRecord` objects. The common reducers
`PlayerStatistics.fromFacts`, `AchievementCatalog.progressFacts`, and
`LifetimeHardWins.scoreFacts` consume them. Statistics preserve all modes;
competitive Hard wins continue to exclude Double, Easy, Medium, and legacy
rounds.

## Merge and explicit choices

`CloudMerge.merge(local, remote, expectedAccountPlayerId)` unions independent
rounds and exact duplicates. Its result retains every divergent candidate.
Conflicting immutable identities are rejected. A `CloudRoundVersion` has a
content hash and bounded ancestor hashes. The adapter durably retains the last
observed version alongside the locally committed round and calls
`version.advance(newRound)` for a proven local successor. This supports normal
single-device autosave and batches of local revisions without self-conflicts.
Do not reconstruct ancestry from revision numbers, timestamps, or guessed
prefix relationships after reconnect; use `initial` only for a new lineage.

A proven successor supersedes its ancestor. Unknown or divergent branches of
the same round require `CloudMerge.resolve(progress, roundId, chosenVersionId)`.
For example, two devices that resume the same round and finish differently
produce one explicit outcome choice, never two played games. A terminal fact
cannot be replaced by an unfinished candidate, even if that candidate claims a
newer time or ancestry. Resolution records both branches as ancestors so stale
copies do not reopen the same conflict. SHA-256 detects damage and establishes
structural lineage; client-authored hashes are not server anti-cheat proof.

Independent unfinished rounds are all retained. More than one requires the
player to select which to resume. Selecting one neither deletes another nor
invents an abandonment. `retainPendingMerge` saves both alternatives before
showing a choice. `importResolvedProgress` atomically imports validated profile
associations, claims, receipts, unfinished rounds, tutorial evidence, and the
selected round. A failed or cancelled operation preserves current local play.
No externally delivered event or achievement command is represented in the
format. The import transaction must not enqueue Game Stats events.

Streaks sort resolved facts by completion timestamp, then stable round ID.
This is deterministic across arrival order and preserves weighted averages,
best results, hints, and lifetime totals. Offline device clock skew can change
apparent chronology; these streaks are not authoritative real-world timing or
competitive streak ranks. Never use timestamps as a conflict winner.

## Partitioned wire format

`CloudCodec.encode(progress)` returns `CloudSaveBundle(manifest, chunks)`.
The fixed root name is `numbcrunch_progress_v1`; immutable chunk names are
`nc1_` followed by a lowercase SHA-256 digest of their bytes. All names are
ASCII alphanumeric/underscore. The binary format is versioned, big-endian,
length-prefixed UTF-8, with explicit collection and byte bounds. The root has
a SHA-256 checksum; each referenced chunk and round version is hash checked.
`chunkNames(manifest, expectedAccountPlayerId)` lists required files;
`decode(manifest, chunks, expectedAccountPlayerId)` requires exactly all of them.

Round ID hashes select 32 stable buckets; each bucket is sorted by round ID
and version ID, then split at 1,000,000 bytes or 10,000 versions. A new round
normally rewrites only its bucket's chunk. The manifest holds identity and
tutorial metadata plus chunk references. Every file is at most 1,000,000
bytes, below PGS's documented 3 MB data limit. The adapter also checks the
runtime `getMaxDataSize()` before committing. A bundle allows at most 256
chunks and 64,000,000 total bytes, 1,000 identity/claim/tutorial entries per
collection, 50,000 guesses in an unfinished round, 1,024 bytes per string, and
4,096 ancestry hashes per round version. Excess data fails the backup; it is
never silently trimmed, approximated, or reset. Local history remains intact.

Publish immutable chunks first, then commit the fixed root manifest. PGS has
no multi-file transaction: a reader fetches and validates every referenced
chunk before importing anything. A missing chunk, interrupted upload, quota
error, future schema, malformed UTF-8, checksum failure, unexpected account,
or invalid fact leaves local data and the previous valid backup untouched.
If the root contains conflicting manifests, decode both complete bundles and
apply the same domain merge; do not let the SDK select by timestamp.

`commitAndClose` confirms local snapshot synchronization and a background
upload request, not server acknowledgement. Keep upload state pending until a
forced metadata refresh reports non-stale data without pending changes and the
expected content digest is confirmed. Commit the root only after its chunks
meet that acknowledgement condition. Do not claim that data is backed up while
only local SDK cache has it. A timeout or offline device retains durable retry
state and local progress.

Do not delete historical chunks merely because one locally cached manifest no
longer references them: another device or unresolved server conflict may still
need them. Version 1 has no lossy compaction or automatic remote garbage collection. Local
cached chunks may be removed only after no retained state, pending or recovery
manifest references them.
Storage/quota exhaustion pauses backup visibly while local play continues.
Future acknowledged checkpoint/GC work must prove that no valid manifest or
offline branch loses needed receipts, IDs, or deduplication evidence. Retain
full local facts until that policy exists; terminal cloud receipts already
remove the much larger secret and guess history. This is a bounded archive,
not a promise of unlimited cloud storage.

## Validation and rollout

There is no previously published cloud format to migrate. Unknown versions
must wait for a compatible app; never reinterpret or overwrite them with v1.
The tests exercise independent unions, exact duplicate delivery, algebraic
merge properties, same-round forks, terminal-versus-pause conflicts, account
and claim validation, multiple unfinished games, redacted round trips,
deterministic partitioning, corrupt/future/missing files, size bounds, and
equivalence of local and imported statistics/achievement/leaderboard reducers.
Device/SDK transaction and retry behavior is described in
[Cloud backup](cloud-backup.md).

Official limits and API semantics, checked 13 November 2026:

- [Saved Games limits](https://developer.android.com/games/pgs/savedgames)
- [SnapshotsClient, including commitAndClose and getMaxDataSize](https://developers.google.com/android/reference/com/google/android/gms/games/SnapshotsClient)
