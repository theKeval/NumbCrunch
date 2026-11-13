# Lifetime Hard wins — scoring contract v1

Logical key: `lifetime_single_hard_wins_v1`. PGS project: **552743622678**.
The configured tester-draft leaderboard ID is `CgkIlrDZkIsQEAIQDA`.
The Android adapter uses this real resource; public availability still requires
the owner's PGS publication step.

## Player-facing contract

Name: **Lifetime Hard wins**.
Description: **All-time Single Player Hard wins. Every eligible win counts once.**
Rule text: **Only Single Player Hard wins count. Pausing does not affect your score.**
Trust text: **An accomplishments leaderboard based on recorded wins. Scores are
reported by the game and are not independently verified.**

Highest total ranks first. The score rewards persistence; it is not a speed,
fewest-guesses or pure-skill rating. No prizes or monetary rewards attach to it.

## Formula and provenance

`LifetimeHardWins.score(snapshot.ownerIds, snapshot.rounds)` returns the number
of distinct eligible winning round IDs, as a nonnegative `Long` with no scaling.
The function validates every record, rejects foreign owners and conflicting
copies of one ID, and deduplicates identical records. Corruption or an unresolved
revision conflict fails closed; the integration must defer submission and keep
offline play available. Never convert validation failure to a score of zero.

Runtime restored history uses `LifetimeHardWins.scoreFacts` over validated
completed outcomes, so a redacted cloud receipt need not reconstruct its hidden
number or guesses. `ProvideHardWinsLeaderboard` requires the selected connected
player, the current generation and the exact server-confirmed cloud projection
stamp before submitting. Android SDK boundaries recheck that live scope.

Eligibility requires LOCAL provenance, supported rules version 1, immutable
Single Player mode, Hard difficulty, valid secret/guess data, at least one guess,
and a valid WON outcome. Easy/Medium, all Double modes, practice, zero-guess,
given-up, abandoned and legacy/unverifiable records contribute zero. A valid
paused/resumed round remains eligible once won. The record ID and owner do not
change. Tutorial practice never enters the round repository.

The storage boundary prevents mode/difficulty/secret/provenance mutation within
a recorded round. The scorer also rejects conflicting copies. A completely
fabricated but internally consistent client record cannot be detected by this
design: these checks are data integrity, not server anti-cheat.

## Import, synchronization and submission

The caller first validates exclusive guest claims and completes cloud
reconciliation. Allowed owners include the active local profile and its claimed
guest namespaces, with original round owners preserved. A guest win can enter
one PGS-associated scope exactly once. An arbitrary owner-ID set is not proof
of a valid claim. While a claim or save conflict remains unresolved, freeze
leaderboard submissions; do not guess which side to keep.

Submit the derived total with the PGS score API, never an increment. Seven Hard
wins, three Easy wins, two Medium wins and four Double wins produce **7**.
Restoring/retrying those same records still produces **7**. One new Hard win
produces **8**. At 0 show “No Hard wins yet”; do not submit a zero merely to
populate the board. Counts such as 10,000 remain 10,000. Equal totals are tied;
show the rank supplied by PGS and never invent a hidden guess/time tiebreaker.

Use integer counts in **0…9,223,372,036,854,775,807**; arithmetic fails on overflow
instead of wrapping. Do not impose an arbitrary lifetime ceiling such as 999.
The current list-backed source cannot practically approach the numeric limit.

## Ranking UI and reset strategy

The in-game view is **All time** only, with Public Top 25 and Near me up to 25,
plus the current player's score when available. Use the SDK's returned rank,
display name and formatted values; support Unicode names and unavailable ranks.
A private or unranked player can still see their local eligible count, clearly
labelled local until acknowledged. Errors/cancellation leave offline stats intact.

PGS creates daily, weekly and all-time records for each board. A daily record of
the submitted lifetime total is not “wins today.” Unless native UI can be proven
to prevent changing timeframe, use a minimal custom list loading only the
all-time time span. Do not expose daily/weekly selectors or the native all-board
picker for this resource. [Leaderboard behavior](https://developer.android.com/games/pgs/leaderboards)
supports querying top and nearby entries and custom presentation.

PGS keeps a better submitted score. Deleting local data or lowering a recomputed
total cannot reliably reduce a previously published high score. Preserve
validated cloud history; never use the server's old score as synthetic local wins.
Do not reset this board during normal upgrades. A future incompatible scoring
policy needs a separately versioned logical key and a newly configured resource,
with explicit migration communication. Do not reuse v1 for another metric.

## Console checklist

1. Inspect existing resources for matching name/metric before creating any draft.
2. Create **Lifetime Hard wins**, numeric integer format, zero decimal places,
   larger-is-better ordering. Avoid optional score bounds that truncate legitimate
   lifetime totals. Keep available tamper protection enabled; it is not a guarantee.
3. Upload `design_assets/play_games/upload/lifetime_single_hard_wins_v1.png`.
4. Save draft; export and record the actual leaderboard ID. No fake ID is supplied.
5. With testers and the matching debug credential, verify eligible versus excluded
   outcomes, overlapping restores, equal scores, all-time-only Top/Near me,
   service errors and account changes. Confirm submission receipts separately
   from local counts. Never test with the upload signing key.
6. The user reviews and publishes the tested PGS changes. No Android store
   release is needed to publish the resource, but players need an app version
   that implements it. No publication is performed by this task.

Automated coverage includes the six combinations, zero and large totals,
duplicates, paused/resumed wins, invalid facts, immutable-mode rejection,
guest scope and conflicting copies. Real service ranks and visibility require
the configured resource and device verification. Source checked November 13, 2026.
