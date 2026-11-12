# NumbCrunch 1.2.0 device acceptance

This checklist is for the debug build (`io.keval.apps.numbcrunch.debug`), which
installs beside the Play app. Jira GUESSER-8 records 53 passing JVM tests and
10 passing Android tests on the OnePlus 7T, plus visual Home/Easy/Medium checks.
The full manual checklist below still needs acceptance, especially TalkBack,
large fonts and real Android process recreation.

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
