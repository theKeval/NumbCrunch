# NumbCrunch 1.2.0 device acceptance

This checklist is for the debug build (`io.keval.apps.numbcrunch.debug`), which
installs beside the Play app. Jira GUESSER-8 records 53 passing JVM tests and
10 passing Android tests on the OnePlus 7T, plus visual Home/Easy/Medium checks.
Real Android process recreation also passed the checks recorded below.
Manual TalkBack and large-font acceptance remain open in GUESSER-8.

## Process-restart results — November 12, 2026

Tested the 1.2.0/code 4 debug APK on the wireless OnePlus 7T (HD1901).
For each of the nine cases, backgrounded the app with Home, ran `am kill`
against the debug package, confirmed its PID disappeared, and opened its
original Android Recents card. Every case resumed the same task with a new
PID and matching visible text/accessibility descriptions. No app fixes were
needed.

| Case | Result |
| --- | --- |
| Double / Medium, unused hint | Both identical `527` guesses stayed neutral, input `48` survived, one hint remained |
| Double / Medium, spent hint | Only the second `527` occurrence retained revealed clues, input `48` survived, zero hints remained |
| Double / Medium, won | Win dialog, secret and guess count restored |
| Double / Easy | Colored submitted clues, aggregate remark, difficulty and partial input restored |
| Double / Hard | Neutral submitted clues, aggregate remark, difficulty and partial input restored; no hints |
| Single / Easy | Round, colored clues and partial input restored |
| Single / Hard, Settings open | Settings restored; Back returned to the unchanged round and partial input |
| Single / Medium, Game Play open | Guide restored; Back returned to the unchanged round, partial input and spent hint |
| Single / Medium, quit | Quit-result dialog and revealed secret restored; no hint action |

The deterministic Double Player checks used secret `573`. After both Medium
hint-restoration checks, completing `481` scored SMYLE and submitting `573`
won, confirming that the original secret survived.

These checks exercise actual process death, beyond the existing Compose
state-restoration tests. They use Recents rather than a new explicit Activity
launch, which can create another Activity instead of resuming the saved one.

A separate force-stop/cold-launch check passed: completed onboarding stayed
completed and the app opened Home. A new Medium round had no old guesses and
exactly one hint. Force-stop is a fresh-launch boundary; saved rounds are
task-scoped, not a durable saved-game database. No app data was cleared.
The installed APK's SHA-256 matched the tested revision, and the app was left
on Home.

## First launch

- With a fresh debug installation, confirm Welcome offers View tutorial and
  Go to Home. Back exits; reopening still shows Welcome.
- Choose View tutorial. Verify all three existing pages and the practice keypad.
  Complete or skip it; Home opens and Back does not reopen onboarding.
- In a fresh debug installation, choose Go to Home. Relaunch and confirm Home
  opens directly. Manual Tutorial must remain available on Home.
- If upgrading an existing debug installation with onboarding already handled,
  verify that Welcome does not reappear.

## Game modes

Try each difficulty in both Single and Double Player. Double Player makes
feedback reproducible: choose secret `573`, then submit guess `527`.

| Difficulty | Expected result for `527` |
| --- | --- |
| Easy | Green, red, orange; remark `1R, 1W` |
| Medium | Neutral tiles until the lightbulb Hint is used on this row, then green, red, orange |
| Hard | Neutral tiles and remark `1R, 1W`; no hint action |

- Before pressing Enter, all typed digits must remain neutral.
- In Medium, submit `527` twice. Redeem a hint on only the second occurrence.
  Only that row reveals; hint count becomes zero. Repeated taps or tapping a
  different row cannot reveal more. New/replayed rounds receive one hint.
  No hint can be redeemed after a win or quit.
- Open Game Play and Settings and return: partial input, guesses, difficulty
  and spent hint must remain. Also check Android background process recreation.
- Submit `481` (SMYLE), `735` (3W), and `573` (Winner). Check quit confirmation,
  secret disclosure, and replay. Try a separate code with a leading zero.
- Difficulty remains fixed during a round. Home difficulty remains independent
  of Single/Double Player. Tutorial's Play Now starts Hard.

## Learning and accessibility

- Open Game Play from both Home and the in-game help button. Check the worked
  examples, feedback legend, mode comparison and one-hint explanation.
- In Medium, the lightbulb Hint replaces the info icon; digit and remark boxes
  stay the same size as Easy/Hard. Tap the remark to open its explanation.
- With TalkBack, reach each Medium Hint control separately from remark help.
  Hard and unrevealed Medium must not announce per-digit answers.
- Check a compact phone and large system font size. No clipped controls or
  overlapping hint/remark targets; guide and Welcome remain scrollable.
- Verify spoken feedback describes each revealed tile, sound settings
  still apply, and the game remains usable offline.

## Release boundary

This build is for acceptance testing, not a Play publication. Before production,
finish device acceptance, update Play Console Data safety for the local stored
state changes, and republish PRIVACY.md after merge. No release tags or Play
promotion are performed by this checklist.
