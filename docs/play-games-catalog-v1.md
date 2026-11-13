# Play Games achievement catalog v1

Status: predicates, copy, original artwork and draft import bundle are prepared.
Real Console IDs, service testing and a timed human first-hour playtest remain
publication gates. This document does not claim that the resources are published.

## Catalog

All ten start **Revealed**, in the order below. Every win is a validated,
distinct, recorded Single Player win. Double Player, Tutorial practice and
legacy/unverifiable rounds never contribute. All difficulties count for solo
milestones. Logical keys identify app configuration; they are not PGS IDs.

| Logical key | English name | Exact condition | Type / total steps | Points | Icon |
| --- | --- | --- | --- | --- | --- |
| `learning_the_ropes` | Learning the ropes | Actual Tutorial completion evidence | Standard / 1 | 5 | Open book and check |
| `first_solo_solve` | First solo solve | 1 solo win | Standard / 1 | 10 | Star medal |
| `getting_the_hang_of_it` | Getting the hang of it | 3 solo wins | Incremental / 3 | 15 | Three rising tiles |
| `double_digits` | Double digits | 10 solo wins | Incremental / 10 | 30 | Ten-bead abacus |
| `persistent_puzzler` | Persistent puzzler | 25 solo wins | Incremental / 25 | 50 | Joined puzzle |
| `century_solver` | Century solver | 100 solo wins | Incremental / 100 | 150 | Crown |
| `medium_mastered` | Medium mastered | 1 solo Medium win | Standard / 1 | 20 | Lantern |
| `hard_earned` | Hard earned | 1 solo Hard win | Standard / 1 | 30 | Faceted gem |
| `hard_regular` | Hard regular | 10 solo Hard wins | Incremental / 10 | 65 | Mountain peaks |
| `unaided_deduction` | Unaided deduction | 10 solo Medium wins with `hintedGuessIndex == null` | Incremental / 10 | 75 | Observant eye |

Total: **450 points**. Exact English descriptions are in
`design_assets/play_games/catalog.json`; localization keys are
`achievement_<logical_key>_name` and `achievement_<logical_key>_description`.
Translations must retain difficulty, mode, threshold and hint restrictions.

`AchievementCatalog.progress(snapshot.ownerIds, snapshot.rounds, tutorialCompletions)`
validates source records, rejects conflicting duplicate IDs and foreign ownership,
then returns cumulative progress capped at each target. Do not pass aggregates
from the Statistics screen. The catalog mirrors `catalog.json`; the export
validator checks their targets and points agree. Use SDK `unlock` for standard
achievements and `setSteps` for incremental achievements, never repeated increments.
Skip zero progress; `setSteps` requires a positive count. Use the immediate Task
variants when recording an acknowledged submission; an enqueued SDK call is not
proof of a remote save. Repeated updates and cloud overlap must produce the same
result. See the [AchievementsClient contract](https://developers.google.com/android/reference/com/google/android/gms/games/AchievementsClient).

## Actual Tutorial completion

`TutorialCompletion(ownerId, completedAt, tutorialVersion = 1)` is a durable fact
separate from the existing introduction-completed-or-skipped preference.
The integration must create it only when the same Tutorial session has:

1. Visited each of its three pages, including after page-dot navigation.
2. Submitted the correct practice answer through the practice keypad.
3. Used the final Get started / Play now action after those conditions are met.

Do not award for Skip, header Back, system Back, opening the last page, or merely
changing the onboarding flag. Practice remains excluded from round history.
Remember page visits and practice success across recreation; use the original
local owner even if automatic PGS authentication completes during the tutorial.
Persist completion before queuing an award. Manual Tutorial is always available
so returning players can earn it. Keep only the first valid completion per local
owner/version; retries must not create extra progress. A skipped intro has no
completion evidence. Previously completed-or-skipped flags cannot be backfilled.

## Ownership and backfill

Evaluate only after identity claims and cloud conflicts are resolved. Scope is
the selected independent local profile plus guest namespaces exclusively claimed
by that profile. A claim never rewrites `RoundRecord.ownerId`. The caller must
validate claim provenance before passing `snapshot.ownerIds`; a set of IDs is
not itself proof of ownership. A pending/disputed claim freezes submissions.
Validated recorded guest wins and actual tutorial evidence can contribute once
after explicit import; they may not be claimed by a second account. Existing
recorded LOCAL wins may backfill these achievements, including offline wins.
Legacy and unrecorded history cannot. This does not authorize retroactive
Game Stats event replay.

No awards are sent simply because an empty first launch finishes authentication.
After an explicit historical import, a batch of legitimately earned awards is
possible; explain the import first and do not overlay an extra app popup flood.

## First-hour attainability evidence

The early route is: complete Tutorial, win two Easy rounds, then win one Medium
round (its hint is permitted). It unlocks Learning the ropes, First solo solve,
Getting the hang of it and Medium mastered. Hard mode is not required.

The pure test verifies progressive unlocks of 0, 1, and 4 along that route.
`design_assets/play_games/check_catalog.py` rehearses a deduction strategy across
all 720 possible secrets: select the first remaining candidate and eliminate
inconsistent candidates using only the feedback available to the player. Easy
takes 4.704 guesses on average and at most 8; Medium with its first-row hint takes
5.024 on average and at most 8. At an assumed 30 seconds per guess, the worst
three-round route plus five minutes for Tutorial is 17 minutes.

This is an exhaustive rules/attainability rehearsal, **not measured novice human
play time**. Before publishing, record a timed first-hour playtest on a real
device with a new player: actual tutorial completion, two Easy wins and one
Medium win; confirm those four awards and cancellation/offline behavior. Retain
the thresholds unless that test demonstrates a concrete accessibility problem.

## Assets and Console setup

Project: NumbCrunch, PGS **552743622678**, Cloud **numbcrunch-1**.

1. Inspect existing Achievements by name, condition and generated ID. Do not
   blindly import a second copy if drafts already exist.
2. Import `design_assets/play_games/achievements-v1.zip` only for a fresh catalog,
   or create/update missing entries individually from `catalog.json`. Save drafts.
3. Verify ten Revealed entries, exact types/steps/order and 450 total points.
4. Export actual Android resources from Console. Map every logical key to its
   real generated ID in the integration configuration. Missing IDs must keep
   the feature explicitly unavailable; never ship invented IDs or fake success.
5. Ensure the debug credential and tester profile can access the drafts. Test
   standards, partial cumulative progress, retries, imports and cancellation.
6. Run the timed first-hour test above. The user then reviews and publishes the
   service changes. Creating a signed app or uploading an Android release is
   outside this catalog task.

Original SVG source, native renderer and reproducible export script live under
`design_assets/play_games`. Run `python3 design_assets/play_games/generate_assets.py`
on macOS, then `python3 design_assets/play_games/check_catalog.py`. The PNGs are
512 × 512 with transparent corners, strong gold/cream pictograms and distinct
silhouettes. Critical shapes fit the circular toast crop. The earned/locked
preview was visually inspected at 88 and 48 pixels; small details remain
secondary to the silhouettes. Google generates its own locked grayscale images;
the preview is an approximation, not a screenshot of Google's UI.

Google's current limits allow points in multiples of five (5–200 each), a total
2,000-point budget, and up to 10,000 incremental steps. Our ten visible entries
use unique names, descriptions and icons. See the [achievement concepts](https://developer.android.com/games/pgs/achievements)
and [quality checklist](https://developer.android.com/games/pgs/quality).
The import archive uses headerless metadata and icon-mapping CSVs, without
subdirectories; the images and CSVs are each under 1 MB. Only unlocked art is
uploaded. Consult [Console import and icon specifications](https://developer.android.com/games/pgs/integrate-achievements)
before changing the bundle format; type and initial visibility become fixed on
publication. Sources checked November 13, 2026.
