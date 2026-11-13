# Play Games achievements

The ten achievements in [the approved catalog](play-games-catalog-v1.md) use the
resource IDs in `numbcrunch/src/main/res/values/play_games_achievement_ids.xml`.
These IDs were created as console drafts; including them in the app does not
publish their console configuration or release an Android build.

`AchievementCatalog.progressFacts` derives progress from validated, deduplicated
completed outcome receipts and tutorial completion receipts for the connected
account's allowed owner namespaces. The same projection includes locally
completed rounds and restored cloud outcomes without inventing secret numbers
or guess history. Tutorial practice, Double Player wins, malformed results and
legacy/unverifiable rounds never become solo win achievements. Guest results
remain local until the player explicitly imports them into the connected profile.

The Learning the Ropes receipt requires all three tutorial pages to have actually
settled on screen, a correct practice submission, and the final Get started or
Play now action. Skip, Back, viewing pages alone and an incorrect practice guess
do not award it. The existing first-run preference still means completed **or
skipped** and is not achievement evidence. The tutorial pins a local owner when
it opens, preserves page/practice evidence across recreation, and waits for the
completion receipt to commit before final navigation. A failed write is retryable.

`ProvidePlayGamesAchievements` observes committed progress only for the connected
Play Games player and validated cloud reconciliation generation. It waits for an
exact matching cloud projection stamp and for unresolved cloud choices to finish.
Only resumed Home or Statistics screens permit delivery, so an achievement popup
does not interrupt entering a guess or the reveal animation. The native
Achievements panel is available from both screens and returns normally on Cancel.
An unavailable SDK or offline connection never blocks local games.

Standard awards use `unlockImmediate`; incremental awards use the absolute
cumulative `setStepsImmediate`, never an increment replay. The Activity adapter
checks `currentPlayer.playerId` before and after each SDK task, along with the
captured connection generation. Per-player, per-catalog acknowledgement high
watermarks are stored in the existing Preferences DataStore only after success.
The durable outcome/tutorial records themselves are the pending intent. A failed
network request or lost acknowledgement can repeat the same absolute update
without inflating progress. These acknowledgements are not event-delivery
receipts and are not included in the game's cloud payload. Restoring an
acknowledgement is safe because it represents an already accepted monotonic
achievement for that same Play Games player.

Missing IDs, duplicate IDs, invalid ID syntax and SDK configuration failures are
shown as unavailable configuration rather than crashing gameplay. Authentication,
cloud merge and achievement delivery status remain separate: connected does not
mean merged or uploaded. Native UI uses the Activity Result API and never opens
a web page.

## Verification

JVM tests exercise cumulative retries, partial delivery, acknowledgement failure,
account-generation changes, configuration failure, exact tutorial evidence,
owner-pinned tutorial persistence and restored statistics deduplication. Compose
device fixtures exercise the complete tutorial, skipping a page and first-run
Skip. Real tester verification must additionally confirm the native achievements
list and an eligible unlock with the console draft configuration; unit tests do
not prove server acceptance or publication.

SDK references: [Achievements for Android games](https://developer.android.com/games/pgs/android/achievements),
[AchievementsClient](https://developers.google.com/android/reference/com/google/android/gms/games/AchievementsClient),
and [GamesClientStatusCodes](https://developers.google.com/android/reference/com/google/android/gms/games/GamesClientStatusCodes).
