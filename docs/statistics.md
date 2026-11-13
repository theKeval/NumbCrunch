# Player statistics

Home's Statistics destination reads the selected local profile's durable
progress. It works offline. A connected Play Games name is shown only when it
matches that local profile; otherwise the screen identifies local or Play Games
progress without claiming that an account is currently connected. Connection
alone does not claim cloud backup.

The screen reports games played, wins, give-ups, games left unfinished,
ongoing/paused games, resolved-game win rate, average/best winning guesses,
total guesses, hints and current/best streak. Mode and difficulty filters combine
and survive restoration. Rates and averages without a denominator show an
explanatory dash; counts, percentages, averages and tracking dates use the
device locale. Statistics do not create, finish, resume or reset games.

`StatisticsViewModel` observes the selected profile and its complete repository snapshot.
Its single projection uses the domain statistics reducer, including explicitly
claimed guest history, and counts unfinished rounds separately. Filters never
alter stored facts. A read error remains retryable without presenting made-up
zero totals. Tracking starts with the local profile, or the earliest known
retained fact after an explicit import; earlier unrecorded play is not invented.

The UI uses the existing wood panels, typography and scroll scaffold. Filter
chips wrap for large text and expose selected semantics; statistic rows merge
their label and value for screen readers. Counts and names can wrap without
truncation. Tests cover weighted averages, six mode/difficulty slices, live
updates, explicit guest claims, read errors, navigation without mutations,
compact layouts, large text and large counters.

Cloud synchronization extends the central projection with validated completed
outcome facts that omit secrets and guesses. `ProgressSnapshot.statisticsFor`
uses the same formulas and stable-ID deduplication for local and restored
history. Remote local-profile namespaces remain intact and share the selected
Play Games scope only after validation. The connected player's native
[Achievements panel](achievements.md) is also available here.
