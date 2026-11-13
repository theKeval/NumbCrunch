# Player connection and local ownership

The connection UI uses the actual Play Games v2 authentication result. It never
blocks a new local game on network availability. Google provides account choice
and profile creation through `GamesSignInClient.signIn()` after the player taps
**Connect Play Games**. No email scope, custom login, Firebase, or separate Google
Sign-In SDK is used. Manage accounts in device Play Games settings and return to
the app; there is no invented PGS v2 sign-out operation or external website flow.

## Integration

`MainActivity` owns each short-lived `AndroidPlayGamesGateway`. A retained
`PlayerConnectionViewModel` holds the platform-independent controller, with no
Activity or credentials. Home includes `PlayGamesConnectionRoute()`; its
`LocalPlayerConnection` binding comes from the Activity. The component uses the
existing wood panel and gold palette, text status, accessible buttons and an
explicit guest import confirmation. Authenticated gamer names come from
`PlayersClient`; they are held in memory, not stored in the progress database.
The official controller image is from the [Play Games downloads](https://developer.android.com/games/pgs/downloads)
branding archive, used unchanged in white on a dark panel.

Each refresh clears delivery eligibility before asynchronous authentication.
The controller generation rejects late callbacks, and the Android adapter
rechecks the player ID after loading their profile. Consumers must capture the
`PlayerSession.generation` and `connectedAccountId`, then recheck both before
applying callbacks or delivering account-specific queued work. Cloud backup, achievements, Game Stats and leaderboard adapters each enforce
their own captured account and generation before submission. Noninteractive checks run on resume and do not
open profile creation. Manual requests are debounced, bounded by a timeout, and
cancelled when their Activity is destroyed.

## Durable ownership

SQLite schema 2 adds `guest_claims`; the schema 1 migration preserves all
existing profiles, rounds and metadata. The selected local profile is persisted
in the same database. Authentication failure does not select or create a
replacement guest. A change from Google account A to B selects B's local
profile; reconnecting the same account preserves a deliberate local selection.
An already-open round continues using its original `ownerId`.

An explicit guest claim stores `(guestProfileId, accountProfileId, claimedAt)`.
It is idempotent for the same account and rejected for another account. It does
not rewrite IDs/owners, copy rounds, or send historical Game Stats events.
When that connected account is selected, the claimed history participates in
cloud backup and reconciled achievements/leaderboard totals.
Account-scoped reads and `ProgressSnapshot` include the claimed namespace;
`guestClaims` and `ownerIds` preserve provenance through cloud reconciliation.
A later guest profile is separate, so claimed progress cannot be claimed again.

When both namespaces contain unfinished rounds, both remain available through
`unfinishedRounds(profileId)`. The UI chooses a round explicitly and resumes its
original owner/ID. New rounds are rejected while any associated unfinished round
exists. `unfinishedRound(profileId)` throws when there is more than one choice;
callers must not silently choose a winner or discard a saved round.

## Verification

`PlayerConnectionControllerTest` exercises account switches, failed storage,
failed/cancelled/unavailable authentication, suspended requests, late callbacks
and a stale import confirmation. `PlayerOwnershipDatabaseTest` exercises actual
SQLite persistence, namespace isolation, claims and rollback, dual unfinished
rounds, and the schema 1 migration. Run the latter on an Android device:

```bash
./gradlew :numbcrunch:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=io.keval.apps.numbcrunch.PlayerOwnershipDatabaseTest
```

Real Play Games checks additionally require a configured Google Play-enabled
device and suitable accounts: returning player, fresh profile creation,
cancellation, no network, unavailable services, supervised/unsupported accounts,
recreation during sign-in, and account A → B → A. Unit tests do not establish
that Google's account or console configuration is correct.
