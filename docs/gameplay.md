# Gameplay and Implementation Status

## Product Rule Set

NumbCrunch is a deduction game in which a seeker identifies a hidden
three-character digit code. Each guess returns positional information that
narrows the remaining possibilities.

These rules are fully implemented in Guesser V1 and are ported to the revamp
as `GuessRules` in `:domain`, which drives the revamp Game screen.

### Valid Codes

A secret or guess is valid when it:

1. contains exactly three characters;
2. contains only ASCII digits `0`–`9` (the revamp rejects other Unicode
   digits so every valid secret can be typed on the game keypad);
3. contains three different digits.

Codes are strings, not integers, so a leading zero is preserved. Examples:
`358`, `907`, and `012` are valid; `33`, `331`, and `3a1` are invalid.

### Remarks

- Count one `R` when a digit and its position match the secret.
- Count one `W` when a digit is present in the secret at a different position.
- Display `SMYLE` when no guessed digit is present.
- Display `Winner` when all three positions match.

| Secret | Guess | Result | Explanation |
| --- | --- | --- | --- |
| `245` | `325` | `1R, 1W` | `5` is placed correctly; `2` is present elsewhere |
| `245` | `542` | `1R, 2W` | `4` is placed correctly; `5` and `2` are misplaced |
| `358` | `147` | `SMYLE` | No digit appears in the secret |
| `245` | `245` | `Winner` | Every digit and position matches |

`SMYLE` expands to "Simply Make Your Life Easy." V1's in-app rules say "3R
means Winner," while its scoring engine displays the word `Winner`.

## Revamp Behavior Today

The new `:numbcrunch` application implements round setup and a complete
guessing round.

### Difficulty

Home offers Easy, Medium, and Hard independently of Single/Double Player.
Easy is selected by default; changing player mode keeps the selected difficulty.
The choice is shown in Game and cannot change during that round. Single Player
**PLAY AGAIN** retains it. A restored round from an older version without saved
difficulty uses Hard, and a game opened from the manual tutorial also uses Hard.

- Easy: each submitted guess reveals green for an exact position, yellow for
  a digit elsewhere in the secret, and red for an absent digit.
- Medium: one hint per round reveals all three tiles of one submitted guess.
  That row stays revealed; other rows stay gray. Repeated identical guesses
  are distinct rows. Hint use survives Android round restoration; a new round
  resets it. Hints cannot be redeemed after winning or quitting.
- Hard: submitted tiles stay gray and only aggregate remarks are available.

All modes retain aggregate remarks. Pending input never reveals feedback.
Gray means feedback is hidden, not that the digit is absent.

### Single Player

1. Single Player is selected by default.
2. Tapping **Start** clears any previously held friend secret.
3. Navigation opens the Game destination, which generates a random secret of
   three unique digits.

### Double Player

1. Selecting Double Player shows a secret slot (three small game tiles) and
   slides up a game-styled keypad sheet: three large digit tiles, an eye
   button to peek, and the game keypad with DELETE and DONE. No system
   keyboard is used.
2. Digits are masked as dots. Digits already used are disabled, so the code is
   always three unique digits; DONE enables at three digits and closes the
   sheet. Back or tapping outside also closes it.
3. Every key plays the same note while hiding the secret, so the guesser cannot
   hear which digits were chosen (in the game each digit has its own note).
4. Tapping the slot reopens the sheet. A short secret shows `Enter exactly 3
   digits`, and pressing Start with an incomplete secret reopens the sheet.
5. An empty slot remains quiet until Start is pressed.
6. A three-unique-digit value is saved to an in-memory repository.
7. Navigation opens the Game destination, which uses the friend's secret
   without displaying it.
8. Starting the game clears the Home secret field (Double Player stays
   selected), so returning Home is ready for a new secret.

The secret is not placed in a route argument, log, or visible copy. It
remains available through `GetFriendSecretUseCase` only while the application
process and `AppContainer` live.

### Game Screen

- A header shows a back button, the centered NumbCrunch title, and a settings
  (gear) button. Below it, the Single/Double Player mode badge sits on the
  left and a gold **SHOW** button (quit and reveal) on the right.
- The guess board lists numbered rows of three digit tiles plus a remark box.
  Each submitted row has a small gold **i** beside its remark. Tapping the
  remark or the **i** opens an iOS-style popup (zoom/fade in, fade/shrink out) explaining
  that remark in the tutorial's terms: each `R`/`W` part ("1R: 1 digit is in the
  hidden number AND in the RIGHT place."), SMYLE with "Simply Make Your Life
  Easy", or Winner. Tap **GOT IT**, outside the card, or Back to close.
  Guesses are unlimited; the board scrolls and keeps the active row visible.
- The custom keypad has digits 0–9, with **DELETE** bottom-left and **ENTER** bottom-right. Digits already
  in the current guess are disabled, so duplicates are impossible. ENTER is
  enabled only after three digits.
- Empty and typed tiles use the original neutral wood color. ENTER submits
  the guess and flips its tiles in sequence. Easy shows per-digit feedback;
  Medium shows it only for the row whose hint was redeemed; Hard stays gray.
  Feedback colors are separate from neutral tiles and keypad colors.
- Each digit key plays its own note (a C-major pentatonic scale from 0 up to
  9) with a light haptic, and the key bounces and glows while pressed; a typed
  digit pops into its tile. DELETE and other buttons play soft taps.
- On ENTER, each tile ticks as it flips. Once the last tile lands, the remark
  pops in: `SMYLE` turns teal, plays a single heartbeat pulse and a cheerful
  jingle; R/W remarks play a short chime.
- A winning guess disables the keypad and opens a win dialog showing the
  secret and guess count, with applause, a confirm haptic, and full-screen
  confetti. Single Player offers **PLAY AGAIN** and **HOME**; Double Player
  offers **HOME**.
- Back (header or system) or a horizontal swipe in either direction during an
  unfinished round asks for confirmation before returning Home and discarding
  the round.
- **SHOW** asks for confirmation, then ends the round and shows a popup
  revealing the secret number; **HOME**, Back, or tapping outside returns Home.
- Settings opens Settings. Returning resumes the same round, because the Game
  back-stack entry and its `SavedStateHandle` state are kept.

### Other Destinations

- **Welcome:** first launch offers **View tutorial** as the primary action and
  **Go to Home** as the secondary action. Going Home saves the existing onboarding
  completion preference before navigation. A failed save stays retryable on
  Welcome. Players who already completed or skipped onboarding continue directly
  to Home; Welcome is removed from the back stack when leaving it.
- **Tutorial:** three swipeable steps explain the game and
  both modes, explain the R/W/SMYLE clues in text, and offer an interactive
  practice keypad against the shown practice secret `245`. Like a real round,
  practice guesses show gray tiles and only the remark (no per-digit colors).
  The practice step is the last; it offers **GET STARTED** on first run and
  **PLAY NOW** otherwise. Progress dots and Back/Next buttons
  provide alternate navigation. Skip, Get Started, or system Back from the
  first step completes onboarding and opens Home; completion is saved across
  restarts. Tutorial remains available from Home. Practice accepts any valid
  guess and displays the latest remark; it is independent of real rounds.
  The header back button (beside the TUTORIAL title) leaves the tutorial from
  any step: to Home, and on first run it also completes onboarding like Skip.
  The footer **BACK** button and system Back step back through the pages, then
  leave from the first step. **PLAY NOW**
  clears any friend secret and replaces Tutorial with a new Single Player Hard game.
- **Game Play:** Home's GAME PLAY button and the in-game help button open
  the existing scrollable guide. It explains code constraints, leading zeroes,
  unlimited guesses, feedback colors, R/W/SMYLE/Winner, player modes, difficulty,
  Medium hints, controls and strategy. Worked examples use `GuessRules` with
  secret `245`. Returning from help preserves the Game navigation entry,
  including partial input, difficulty and spent hint.
- **Settings:** opened from the Home gear or the in-game gear. Toggles for
  background music and sound effects are saved in Preferences DataStore and
  default on. A soothing music loop plays only while the app is in the
  foreground and music is enabled.
- **About:** the family origin story credits Paresh Langalia, the inventor of
  NumbCrunch, alongside developer Keval Langalia, the Compose revamp, installed
  app version, and a Privacy policy button.
- **Privacy policy:** opened from About. Shows the full privacy policy in the
  same wood-panel style, one panel per section, with selectable text. Web
  addresses appear as plain text; the app never opens a website.

All of these use resource-backed copy, safe-area padding, dark wood panels, and
a 520 dp maximum reading width. Example rows expose spoken per-digit feedback
so clue meaning does not depend on color.

## Guesser V1 Behavior

Guesser V1 remains playable and has two equivalent setup modes:

- **App mode** generates and hides a random unique three-digit code.
- **Friend mode** lets one player enter and hide a valid secret before passing
  the device to the seeker.

### V1 Round Flow

1. Start App mode with **Auto**, or enter a Friend-mode secret and tap
   **Hide**.
2. Enter a valid guess with the custom keypad.
3. Tap **Check** to append the guess and its remark to newest-first history.
4. Continue without a guess limit until the result is `Winner`.
5. Optionally tap **Show**; confirmation reveals the secret and abandons the
   round.
6. Tap **Reset** to clear history and return the selected mode to its initial
   state.

Switching modes during an active round requires confirmation and resets that
round.

### V1 States

| State | Meaning |
| --- | --- |
| `NOT_STARTED` | No active secret |
| `STARTED` | Secret is hidden and guesses are accepted |
| `WON` | The latest guess matched the secret |
| `ABANDONED` | The player revealed the secret |

### V1 Feedback

- The keypad refuses a fourth digit and a repeated digit.
- Invalid submissions display a dialog and clear the guess field.
- Winning triggers sound, vibration, status/card animation, and confetti.
- A non-winning result containing `R` uses the positive-result effect.
- Other results use the wrong-result effect.
- Settings independently control result sounds, keypad sounds, and vibration.

The revamp carries forward the keypad duplicate/length guards (as disabled
keys), a win state, sound, haptics, reveal, and confetti, with its own
synthesized sounds and music/sound-effect settings (no vibration setting).

## Scope Not Yet Implemented in the Revamp

The Compose app currently has no vibration setting, cross-launch round
persistence, profiles, scoreboard, Play Games integration, timer,
or daily puzzle.
