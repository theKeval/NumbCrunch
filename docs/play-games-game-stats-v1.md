# Game Stats v1

NumbCrunch reports new completed-round activity to Google gamer profiles while
its local Statistics screen remains authoritative and works offline. **Games
played** starts at the first guess. Google's **Rounds finished** counts only
resolved, started rounds. A pause is neither a completion nor a leave.

## Console contract

The event name is `roundCompleted_v1`. Its seven properties are `playerMode`,
`difficulty`, and `outcome` (STRING); `guessCount` (INT64); and `hintUsed`,
`hardSoloWin`, and `unaidedMediumSoloWin` (BOOL). Values use the app's uppercase
enum names. The two booleans express exact conjunctions because a Console stat
accepts one filter property. All values describe that occurrence, never a
running lifetime total. No secret, individual guesses, name, email, local round
ID, local owner ID, or client timestamp is an event property.

| Stat | Aggregation | Filter | Competitive |
| --- | --- | --- | --- |
| Rounds finished | COUNT guessCount | None | No |
| Puzzles solved | COUNT guessCount | outcome = WON | No |
| Hard solo puzzles solved | COUNT guessCount | hardSoloWin = true | Yes |
| Unaided Medium solo solves | COUNT guessCount | unaidedMediumSoloWin = true | No |
| Guesses in finished rounds | SUM guessCount | None | No |

Hard means Single Player, Hard difficulty, won. Every Double Player round and
Single Player Easy/Medium round is excluded from the competitive stat. The
separate lifetime Hard-wins leaderboard uses reconciled source facts; it is
not calculated from Game Stats deliveries. NumbCrunch has no progression-level
mechanic, so it sends no `progressUpdate` and imports no artificial level stat.

`design_assets/game_stats/upload/PlayerGameEvent.csv` is the event schema.
`design_assets/game_stats/game-stats-v1.zip` contains only
`RepetitiveStatsConfig.csv` and five distinct transparent 512-pixel icons. The
original SVGs and generator are included. `check_catalog.py` checks the real
ZIP, image bounds, duplicate bytes, and SDK property names. The preview was
visually checked at 88 and 48 pixels, including grayscale silhouettes.
The [current import guide](https://developer.android.com/games/pgs/integrate-gamestats)
explicitly permits a repetitive-only archive. Event/stat identifiers in these
files are developer-defined logical IDs, unlike generated achievement IDs.

## Competitive rate calibration

The draft Hard stat uses minimum 0 and maximum 600 solves **per hour**, not a
lifetime limit. `rehearse_hard_rate.py` exhaustively solves all 720 valid secrets
using Hard's R/W feedback: mean 5.267 guesses, range 1–9. A deliberately fast
engineering assumption of one second per guess plus two seconds to restart
gives 495.4 solves/hour. The draft 600 ceiling leaves margin around that
model. This is a provisional generous threshold, not a measured human limit
or anti-cheat proof. Lucky first guesses remain possible.

Before publishing, time several natural Hard rounds on the tester's phone,
including an experienced player's fast play. Record guess counts and full
round/restart duration, compare the fastest sustained pace with the draft,
and adjust the Console hourly threshold if needed. Do not call the simulated
rate a human playtest. Client-only events cannot prevent modified-client
cheating; a more valuable competition would need server validation.

## One local delivery owner

The first local transition to a started terminal round inserts a READY event
in the same SQLite transaction as the round. Guest-only, legacy, historical,
and imported facts do not create events. Account binding is captured at the
occurrence and never rewritten by sign-in, guest claiming, or backup restore.
The database helper is `GameStatsOutboxStore`; its interface is
`GameStatsOutbox`. Only a round created on this installation can enqueue:
remote namespaces/imported unfinished games cannot create a second delivery
owner on another device. A transferred round finished only on the other
device may therefore be absent from Google's counters. Local merged statistics
and the lifetime leaderboard still include its resolved outcome.

The foreground `GameStatsSyncEffect` drains READY entries only while the
selected profile matches the connected account. It never blocks gameplay.
Missing/offline identity leaves READY work intact. Before each SDK call, the
gateway checks the stable authenticated player again. Account changes stop
the old collector and cannot transfer its queue. The SDK has no public method
for targeting a different player; external account changes during a handoff
remain subject to the SDK's authenticated session semantics.

Delivery atomically changes READY to HANDOFF **before** calling the SDK.
The [GameStatsClient reference](https://developers.google.com/android/reference/com/google/android/gms/games/GameStatsClient)
says `recordEventImmediate` confirms local buffering. Its success marks
BUFFERED, after which the SDK owns upload retry. `requestEventsUpload` merely
requests a background upload and has no server response. The
[event builder](https://developers.google.com/android/reference/com/google/android/gms/games/playergameevent/PlayerGameEvent.Builder)
exposes no caller-set deduplication ID. Thus the app never claims exactly-once
external ingestion or treats BUFFERED as accepted by Google.

A crash between HANDOFF and the SDK call can lose that one report. A timeout,
cancellation, service failure, or process death after handoff leaves UNKNOWN
or HANDOFF, which never auto-replays. A definite local validation/developer
error becomes REJECTED. This intentionally favors avoiding duplicate external
counts over recovering an ambiguous event. Repeated callbacks, recomposition,
and relaunch cannot claim it again. Only READY events retry. Never clear these
tombstones or reconstruct a queue from old round history. Outbox data is
excluded from cloud payloads and Android application backup.

## Eligibility and verification

Google's [Level Up guidelines](https://developer.android.com/games/guidelines)
exclude under-13 players from Game Stats. The app collects no birthdate or age
and does not bypass SDK restrictions; local play and local statistics remain
available when Game Stats is unavailable. A gamer profile not showing stats
does not imply local data loss.

Domain tests cover strict filters, first terminal eligibility, duplicate
callbacks, missing identity, offline retry, another account, rejection,
ambiguous delivery, crash-at-handoff, and a restored device with no queue.
`GameStatsOutboxStoreTest` uses isolated in-memory SQLite to verify rollback,
immutable account binding, atomic claims, terminal markers, and exclusions.
Run it in the combined instrumentation suite after GUESSER-16's transaction
wiring. The Android adapter compiles against pinned SDK 22.1.0.

Actual Console/tester verification remains a delivery gate:

1. Confirm the seven-property event and five-stat definitions are available to
   the authorized tester. Inspect existing drafts before importing duplicates.
2. Enable `adb shell setprop log.tag.Games INFO`, then observe
   `adb logcat -s 'PlayGamesServices[GameStatsAgent]:I'` and the app's redacted
   `NumbCrunchGameStats` debug tag. No gamer IDs or payload values are logged.
3. Complete a new Single Hard win. Confirm local statistics, a BUFFERED marker,
   and SDK upload logs without schema-validation errors. A batch-success log
   alone does not prove every event was valid; inspect preceding errors.
4. Check gamer-profile changes in Play Store; record actual processing delay.
   Exercise Medium with/without hint, Double wins, give-up, abandonment, and
   pause to confirm each filter and exclusion. Do not fabricate successful
   profile updates from local mocks.
5. Test offline completion/reconnect, unavailable service, account switching,
   duplicate callbacks, relaunch during handoff, and cloud restore. Confirm
   exact local totals even when external delivery is lost or delayed.
6. Finish human hourly-rate calibration. The user reviews and publishes PGS
   configuration through the normal Console workflow before production use.
   No Android release is built or uploaded for this task.

The same integration PR must disclose per-round activity sharing with Google
in `PRIVACY.md`, update its effective date and Data safety answers, and remind
the user to republish the website policy after merge. This adds no Firebase
Analytics and no duplicate legacy Events counters. Sources checked 13 November
2026. Buffered SDK calls, Console drafts, actual server acceptance, and
published configuration are separate milestones.


## Recorded phone verification

On 13 November 2026, the authorized API 31 tester phone performed these normal
UI actions under its selected Play Games profile:

- A Single Player Medium game submitted one guess, spent its hint, entered a
  partial next guess, paused and resumed, then was abandoned normally.
- A Single Player Hard game was won in six guesses.
- All three Tutorial pages were visited, its visible practice puzzle was solved,
  and the final Play now action was completed. The untouched game opened by that
  action was abandoned with zero guesses.
- A Single Player Medium game was won in six guesses without using a hint.

The played games used visible feedback only. No hidden secret, outcome, event,
achievement or score was injected. The two wins used a candidate-filtering
solver to select normal keypad guesses; this is functional integration testing,
not the outstanding natural-human hourly-rate calibration.

The app's `NumbCrunchGameStats` diagnostics reported SDK buffering. Separately,
the actual Play Store **You → Game stats → All** view for NumbCrunch displayed:

| Displayed stat label | Observed value |
| --- | ---: |
| Rounds finished | 3 |
| Puzzles solved | 2 |
| Hard solo puzzles solved | 1 |
| Unaided Medium solo solves | 1 |
| Guesses in finished rounds | 13 |

These visible profile counters matched the three real played outcomes:
`1 + 6 + 6` guesses, two wins, one Hard win, and one unaided Medium win.
The Tutorial and its zero-guess discarded round did not inflate the totals.
This observation confirms actual profile ingestion for these events; it does
not turn future BUFFERED callbacks into server acknowledgements or promise
exactly-once ingestion. The specific `PlayGamesServices[GameStatsAgent]` log
capture contained no retained lines, so no batch-upload-log claim is made.
No fixed processing delay or waiting-period promise is inferred.

In parallel, local statistics showed played 3, wins 2, games left 1, ongoing 0,
and average guesses in wins 6. The all-time Single Player Hard leaderboard
remained at score 1 after the Medium win. These counters survived an in-place
debug APK upgrade; a final read-only cloud restore into a disposable database
preserved the source facts without recreating delivery commands.

Actual Double Player, give-up, offline/handoff failure, supervised restrictions,
and account-switch ingestion cases remain separate checks from the observed
profile values. Automated domain and transaction tests cover their local
predicates and delivery boundaries. The user limited remaining verification to
the existing phone, so no independent second-device result is claimed. Account
and profile settings were not changed, and no signed release was generated or
uploaded for this verification.
