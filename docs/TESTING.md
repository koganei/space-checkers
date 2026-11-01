# Verification — November 1, 2026

The delivered APK is `artifacts/space-checkers-debug.apk`. Its SHA-256 is recorded in the adjacent `.sha256` file. It is debug-signed and its APK v2 signature was verified with Android's `apksigner`. APK inspection confirms it requests no Android permissions.

## Final results

| Check | Result | Evidence |
| --- | --- | --- |
| Java rules and sound-event tests | 61 passed, no failures or skips | `app/build/test-results/testDebugUnitTest/TEST-*.xml` |
| Rule combinations | Random games across all 128 combinations, up to 100 turns each | `GameTest.randomizedAllRuleCombinations` |
| Android lint | No issues found | `artifacts/lint-final.log` and `app/build/reports/lint-results-debug.html` |
| Android 15 / API 35 | 268 checks passed | `artifacts/test-emulator-5558.log` |
| Android 8.0 / API 26 | 268 checks passed | `artifacts/test-emulator-5556.log` |
| Black-box touch tests at 360dp | Passed | `artifacts/device-smoke.log` |
| APK signature | Verified | `artifacts/apk-signature.log` |
| APK permissions | None | `artifacts/apk-permissions.log` |

## Covered behavior

Engine fixtures cover setup, edge boundaries, both player directions, legal and illegal moves, compulsory/optional captures, own-piece and blocked-landing rejection, chained captures, branch choices, global maximum-length selection, promotion, promotion stopping/continuing captures, short/flying kings, backward captures, wins by capture and blocking, draws by repetition and 80 quiet plies, agreed draws, disabled draws, hints, partial/full-turn undo, serialized mid-jump recovery, and bounded undo history during long games.

On-device checks exercise the actual native controls and lifecycle: initial moves, hint selection, undo, both player palettes, duplicate-color rejection, expanded palette opening, all rule switches, both presets, starting player, draft isolation/discard, confirmation cancel/accept, saved colors, multi-jump continuation after reopening, complete-chain undo after reload, promotion, win/undo, and stored board/turn recovery.

The separate black-box script uses Android accessibility bounds and real touch coordinates. It verifies moving a piece, scrolling to controls, selecting colors, relaunching after a force-stop, confirming that board/colors survived, reaching the new-game dialog, canceling it, and rotating the rules screen. It temporarily uses a 720×1280 screen at 320dpi (360dp width) and restores the emulator settings afterward.

## Visual review

Screenshots in `artifacts/screenshots/` come from the final API 35 APK run (QA emulator 5558). API 26 captures are in `artifacts/emulator-5556/screenshots/`. The small-screen and landscape captures are in `artifacts/device-smoke/`. `docs/review.html` shows the selected reference and the game, colors, rules, custom-color, multi-jump, king, and win screens.

Inspection led to corrections to font weight, piece highlights, shaded piece artwork, decorative placement, rules wording, and color-control discoverability. The reference composition is closely recreated, but generated artwork and real Android system bars are not literally pixel-identical to the mockup. Layout on short/landscape screens intentionally scrolls. Tablet content is width-capped; no tablet hardware was tested.

## Limits

These are emulator results, not a guarantee covering every Android device or all possible games. No physical phone, full manual TalkBack session, Play Store upload, release-signing workflow, or exhaustive game-state proof was performed. This app supports local two-player games and four offline computer opponents; online matchmaking is not implemented. National draughts variants beyond the documented 8×8 house rules are not claimed.

The test harness waits for dialog accessibility content and game controls to become ready, rather than assuming a fixed animation/launch delay. Early harness timing failures and intermediate screenshots are retained under `artifacts/history/`; they are not the final results above.

## Welcome and player selection update

Version 1.1 adds the welcome screen, New Game player picker, saved-match Continue, and a fixed perspective for either player. Four perspective unit tests check all 64 cells, reversibility, playable-square parity, starting ranks, and forward screen movement. The device suite now includes first launch, canceling selection, Player 2 positioning, rotated touch input for both teams, turn labels, undo/hints, relaunch persistence, and canceling replacement of an existing game. The existing interactive emulator is updated without clearing its saved game. The isolated API 35 QA AVD is `space-checkers-qa`; the interactive emulator is not used for destructive tests. Latest small-screen evidence is `artifacts/welcome-smoke.log`.

## Alien opponents update (1.2)

The suite includes 11 AI-specific tests: all four profiles return legal moves, full and resumed capture chains, a tactical recapture trap distinguishing greedy play from lookahead, random easy choices, all 128 house-rule combinations, play by both teams, terminal draws, draw-counter preservation, interrupted search, and invalid difficulty rejection. The 227-check Android suite now covers selecting all four aliens, computer-first play with Player 2 at the bottom, input ownership, automatic replies, paired undo, opponent persistence, pausing and resuming computer jump chains, rejecting stale search results after starting another match, and absence of automatic moves in two-player mode.

Latest verification logs: `artifacts/aliens-build.log`, `artifacts/test-emulator-5558.log`, `artifacts/test-emulator-5556.log`. Screens 11–14 show the mode picker, opponent list and an alien match. New portraits are native code artwork. Small-screen touch verification: `artifacts/aliens-smoke.log`.

The additional `scripts/alien-touch-smoke.py` test passed on a 360dp Android 8 emulator using actual touch coordinates: alien selection, Player 2 placement, automatic opening and reply, and paired Undo. Evidence: `artifacts/alien-touch.log` and `artifacts/alien-touch/`.

## Move interaction verification (v1.3.0)

The full 227-check suite passed on Android 15 and Android 8. Motion checks cover both perspectives, all initially movable pieces, off-board drops, gesture cancellation, legal drags, tap animation, input blocking, undo during a slide, forced-capture highlighting, continued-jump highlighting, and mixed drag/tap capture chains. Captured sprites remain visible until the animated landing. Screens 15 and 16 document movable outlines and a lifted piece outside the board; both were visually reviewed. Logs: `artifacts/motion-device.log` and `artifacts/motion-android8.log`.

Real Android touch injection also passed for an off-board drop, a legal drag through the ScrollView, Undo, and a tap move. Reproduce on a disposable emulator with `python3 scripts/motion-touch-smoke.py`; this script clears that emulator’s app data. Evidence: `artifacts/motion-touch.log`.

## Sound verification (v1.4.0)

Five new JVM tests cover ordinary movement, capture + promotion + victory ordering, both teams’ promotions, existing kings, draws, and wins by blocking an opponent. Android checks verify that all four WAV files decode and SoundPool accepts playback, muting cancels current/queued effects, muted playback is suppressed, preferences survive activity relaunch, and toggling does not alter the match. The settings screenshot was visually reviewed. Logs: `artifacts/sounds-build.log`, `artifacts/sounds-android15.log`, `artifacts/sounds-android8.log`. Generated PCM was checked for unclipped peaks and silent endpoints. Physical-device speaker quality has not been auditioned.

## One-tap setup and game-over flow (v1.5.0)

61 unit tests and 268 checks on each of Android 8 and 15 pass. Alien taps advance immediately; player taps begin immediately, including replacing an existing match without the old adventure confirmation. Result checks cover human wins after the animation, computer wins, draws, duplicate-popup prevention, replay preserving opponent/side/rules/colors/sound, Back to home, reopening a finished match, and activity recreation. Screens 18–19 show the new popup. Logs: `artifacts/flow-android15.log`, `artifacts/flow-android8.log`.

The updated 360dp real-touch alien/setup/gameplay check passed: `artifacts/flow-touch.log`.
