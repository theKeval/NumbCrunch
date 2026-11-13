# Player data and publication handoff

GUESSER-22; reconciled 13 November 2026 for unreleased 1.2.0 (code 4).
This is preparation for the owner's later publication/release decision. It
does not publish policies, store declarations, PGS drafts, or an Android build.

## Data flows implemented

| Data | Destination and purpose | Scope, retention and deletion |
| --- | --- | --- |
| Sound settings, first-launch completed/skipped flag | Preferences on device; Android backup if enabled | Installation; clear storage/uninstall removes local copy; Android controls its backup |
| Gamer name; game-scoped PGS player ID | Name in memory for connection display; ID in local profile/claim and achievement acknowledgment keys | Google-selected profile; no email request; name not saved in history |
| Local UUIDs, namespace, immutable owner, mode, difficulty, origin, times, revisions | SQLite; restoration, isolation, migration and duplicate-safe statistics | Local profiles; no destructive history pruning; database excluded from Android cloud backup/device transfer |
| Hidden number, ordered guesses, pending input, hint state | SQLite for exact round restoration; unfinished records uploaded to Saved Games | Includes Double Player secret; never logs, leaderboard score or Game Stats payload. New completed facts omit secrets/guesses, but older immutable backup chunks retain unfinished details until separately deleted through Google controls |
| Completed outcome facts, tutorial completion, guest ownership claims, tracking start | SQLite plus Google Saved Games for the selected connected account | Guest import requires confirmation; separate PGS player namespaces; same-round conflicts require a recoverable choice |
| Tutorial visit/practice evidence | Temporary tutorial state; completion receipt in SQLite/cloud | Skipping/back never awards completion; no real-game outcome from practice |
| Achievement absolute progress/acknowledgments | Google achievements; local Preferences acknowledgment cache can enter Android backup | Keys bound to PGS player; monotonic progress, no event replay; cloud reset is not implemented |
| Eligible lifetime Hard solo wins | Google leaderboard; local total from reconciled facts | No Double, Easy, Medium, tutorial or unverifiable legacy score; gamer name/rank visible according to Google profile settings |
| Seven-property roundCompleted_v1 event | Google Game Stats; local SQLite delivery queue/tombstones | New started terminal rounds only; mode, difficulty, outcome, guess count, hint use, Hard/unaided Medium eligibility. No secret, individual guesses, app UUID, email, name or client timestamp. Queue never enters cloud backup |
| SDK analytics and diagnostics | Google SDK service operation/improvement | Automatic PGS SDK behavior; no separate Firebase/Cloudflare analytics, ads or own backend |

Deleting app data does not delete Google's data. Reconnection can restore a
cloud save; retained local progress may also be submitted again after a Google
reset. There is no in-app reset or cloud-account deletion API. Google's current
[SDK disclosure](https://developer.android.com/games/pgs/data-collection)
documents encrypted transport, profile-controlled visibility and deletion
through Play Games/Google account controls. Follow the current
[Google game-data deletion guidance](https://support.google.com/googleplay/answer/9130646)
for the user's device; do not promise that uninstall performs this action.

## Data safety form changes for owner review

Replace the old blanket “no data collected” answer before this version ships.
The following proposed mapping is based on the actual implementation; final
answers must include all versions currently distributed and the SDK's behavior.

| Form item | Proposed answer and reason |
| --- | --- |
| Collects or shares required user data types | Yes: PGS identity, online game progress and SDK diagnostics |
| Personal info → User IDs | Collected: game-scoped PGS identifier; account management/app functionality. Online gameplay data is optional through player selection; do not assume automatic SDK identity collection stops when Guest is selected |
| Personal info → Name | Gamer tag/display name, not real-name collection: identify/display the connected player. Review Google's account/profile handling in the form |
| App activity → Other actions | Gameplay outcomes, accomplishments, scores, saves; app functionality and Google gamer-profile statistics/analytics |
| App activity → App interactions | SDK analytics and interaction telemetry as disclosed by Google; analytics/functionality. Do not mark all SDK collection optional without verifying an effective control |
| App info and performance → Diagnostics | SDK diagnostics; analytics/functionality. Confirm current SDK crash-log/device-ID details rather than inventing an exact field inventory |
| Encrypted in transit | Yes for PGS transmission using HTTPS; no app-operated endpoint |
| Ephemeral | No for stored identity/progress/events. A memory-only name does not make Google's profile data ephemeral |
| Advertising/marketing | Not used by NumbCrunch; no ads SDK or advertising-ID permission added |
| Sharing | Review Google’s service-provider and user-initiated transfer exemptions against actual terms. PGS public gamer name/scores can be visible to others. Do not automatically answer “not shared” merely because Google is the vendor |
| Deletion | Explain local clearing separately from Google game/profile deletion; link the public privacy page. Do not claim an in-game deletion feature |

The [Data safety form guide](https://support.google.com/googleplay/android-developer/answer/10787469?hl=en)
defines collection, optionality and sharing exemptions. These form categories
are a proposed mapping, not proof of policy approval. Resolve SDK-specific
optional/required and sharing answers in Console before production readiness.
No email, contacts, location, financial data, photos or message content is
requested by this app. No screenshot is attached to a Saved Games snapshot.

## Children and unsupported accounts

All gameplay, difficulty modes, save/pause and local statistics work without
sign-in. Do not change the existing target audience to bypass restrictions.
The [Level Up guidelines](https://developer.android.com/games/guidelines)
state that Game Stats is not enabled below age 13. The app does not collect an
age or override platform eligibility. Supervised-profile creation/cancellation
and age-restricted feature behavior still require suitable real accounts in
the final QA matrix; a successful adult tester login does not establish these.

## Console resource inventory

Observed in the signed-in Console on 13 November 2026:

- Cloud project `numbcrunch-1`; PGS project `552743622678`.
- Production Android credential (`io.keval.apps.numbcrunch`) and separate debug
  credential (`io.keval.apps.numbcrunch.debug`) show Published. Verify the
  production credential against the **Play app-signing** certificate before
  the later rollout; the upload certificate is a different role.
- Saved Games is enabled; properties show Published. Firebase remains unlinked;
  Recall remains off. No new publication action was performed in this sprint.
- Ten achievements, 450 points total, all revealed: drafts available to testers.
- Lifetime Hard wins board: draft, number/zero decimals/largest first,
  tamper protection enabled; ID `CgkIlrDZkIsQEAIQDA`.
- `roundCompleted_v1` event and five repetitive stats: Draft, Available to testers.
  IDs: `rounds_finished_v1`, `puzzles_solved_v1`,
  `hard_solo_puzzles_solved_v1`, `unaided_medium_solo_solves_v1`,
  `guesses_in_finished_rounds_v1`. No artificial level/progression event.
- Hard solo competitive stat has provisional 0–600/hour bounds. The engineering
  solver rehearsal is not human calibration; complete the phone timing check
  in [Game Stats v1](play-games-game-stats-v1.md) before publishing.

Achievement IDs are in `numbcrunch/src/main/res/values/play_games_achievement_ids.xml`;
catalog rules/artwork are in [Achievements](achievements.md) and
[catalog v1](play-games-catalog-v1.md). The leaderboard and stats have their own
versioned configuration documents. Do not import duplicate resources.

## Copy-ready website handoff

In the keval.io project, replace the body at
`https://keval.io/apps/numbcrunch/privacy` with this repository's **PRIVACY.md**,
including its effective date and contact. Replace the terms page body with
**TERMS.md**. Preserve the site's navigation/layout and canonical URLs. The
in-game renderer treats policy links as plain text and opens no website.

Replace stale app-page claims such as “no accounts”, “no internet permission”,
“collects no data” and “all progress stays on the device” with:

> Play NumbCrunch offline with local statistics and resumable games. Optional
> Google Play Games features add cloud saves, achievements, Game Stats, and an
> all-time Single Player Hard wins leaderboard. Availability depends on your
> Play Games profile. No ads or in-app purchases.

Use that summary when editing PGS properties too; remove any outdated fixed
age claim and keep the declared target audience consistent. The separate
website project has not been edited or deployed by this sprint. After merge,
**republish the website policy and terms**, then verify the live effective date
and content. Do not report the live website as updated until that check passes.

## Owner-managed publication order

1. Review final QA and outstanding configuration/disclosure gates. Complete
   real-account checks and human rate calibration; confirm credential signing.
2. Republish the website documents and reconcile Play Data safety/target-audience
   declarations with this version. Review PGS property copy.
3. In Play Console → Grow users → Play Games Services → Setup and management →
   Publishing, review and publish the achievement, leaderboard, event and stat
   changes. Check for validation errors first; keep tester evidence separate
   from public availability.
4. Allow at least two hours before any later Android rollout, as stated by
   [Google's publishing guide](https://developer.android.com/games/pgs/console/publish).
   PGS publication itself does not release an APK/AAB or update the store app.
5. Only after a separate release request, follow [releasing.md](releasing.md).
   No signed bundle, Play upload, release promotion or tag is part of this work.

The “Add the Play Games Services SDK to your production APK” setup reminder
cannot establish whether unpublished source is correct. It remains expected
until a production artifact containing the SDK is processed by Play; this
sprint deliberately produces debug builds only.
