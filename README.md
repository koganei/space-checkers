# Space Checkers

Download the Android installer from [GitHub Releases](https://github.com/koganei/space-checkers/releases/latest). No GitHub sign-in is needed. Open the release's **Assets**, download the `.apk`, and open it to install. Android may ask you to allow installs from your browser. The current installer is the tested debug-signed build and requires Android 8.0 or newer.

A native, offline Android checkers game for playing against four friendly computer aliens or with another person sharing one device. The Space Play concept is the visual reference. The app includes a game board, independent player colors, legal-move hints, complete-turn undo, saved games, and a rules page.

## Install

Copy `artifacts/space-checkers-debug.apk` to an Android phone or tablet and open it. Android may ask you to allow installation from the app opening the file. This is a debug-signed APK for direct testing, not a Play Store release. Android 8.0 (API 26) or later is required.

With an authorized USB debugging device:

```sh
adb install -r artifacts/space-checkers-debug.apk
```

No account, network connection, ads, analytics, or Android permissions are required. Games and colors stay in the app's private storage. Uninstalling or clearing app data removes them.

## Play

The welcome screen offers **New Game** and, when available, **Continue game**. New Game first offers **Play an alien** or **Two players**. In alien mode, choose Pip, Bloop, Ziggy, or Luma. Then choose whether you want to play as Player 1 or Player 2. Your selected side starts at the bottom and moves upward on screen; the view stays fixed throughout the match. Player 1 still moves first by default, independently of your chosen side. The starting-player rule can change this. Tap an alien to advance, then tap a player to start immediately. Browsing choices preserves the saved match; choosing the player replaces it without another confirmation. Tap a piece, then a glowing destination. A star identifies Player 1 and a ring identifies Player 2. Kings have a crown and gold rim. The symbols remain meaningful after colors change.

- **Undo** in two-player mode restores a whole turn, including all captures. In alien mode it takes back your last decision and the alien’s reply. You can also undo your move while the alien is thinking. The alien’s initial opening move alone cannot be undone. During a jump chain, it restores the position before the chain. Up to 512 turns are retained.
- **Hint** suggests a legal move, prioritizing captures and promotion. It is a teaching aid, not a competitive chess-engine-style search.
- **Colors** opens independent palettes. **More** offers all ten colors. The app prevents identical team colors. Choosing a color leaves the game intact.
- **Settings gear** opens the rules page. Edits are a draft until **Start new game** is confirmed. Back discards them.
- **Agree to draw** in two-player mode ends the game after both players confirm together. Undo can restore it.

The game saves after moves and on pause, including unfinished multi-jumps. It restores the position, player colors, rule choices, turn, chosen side, computer/two-player mode, selected alien, and undo history on relaunch. Continue game resumes it from the welcome screen.

## Alien opponents

| Alien | Difficulty | Play style |
| --- | --- | --- |
| Pip | Easy | Chooses randomly among legal complete turns |
| Bloop | Medium | Evaluates one turn, preferring material, promotion progress, and central squares |
| Ziggy | Tricky | Looks up to three complete turns ahead |
| Luma | Hard | Looks up to six complete turns ahead with a larger search budget |

The stronger aliens use iterative deepening and alpha-beta search. Searches have node limits and an 800ms time budget checked between nodes; slower devices or complex house rules can reduce the reached depth. Difficulty labels describe relative settings, not Elo ratings or guaranteed strength. Ties can vary between games. All choices come from the same rules engine as human moves, including flying kings, capture priorities, and promotion behavior.

The computer plays the other side automatically, including the first turn when applicable. You cannot move its pieces. Search runs on a background thread; visible jumps are spaced so children can follow them. Leaving the board or pausing the app cancels pending callbacks. Continuing the game resumes any unfinished computer turn. Colors and rule changes keep the chosen opponent unless you explicitly start a different mode.

## Rules

The default follows American/English checkers movement and capture rules on an 8×8 board: 12 pieces each, forward-moving men, mandatory captures, complete jump chains, short kings, any complete capture path allowed, and promotion ending the turn. Capturing all opposing pieces or leaving the opponent with no legal move wins.

The kid-friendly app automatically declares a draw on the third occurrence of the same board and player to move, or after 80 individual turns without a capture or a man moving. The WCDF handles these situations through claims; this app automates them. There are no tournament clocks, touch-move penalties, adjudication, or tournament opening ballots.

Configuration:

| Setting | Default | Alternative |
| --- | --- | --- |
| Must take a jump | On | Slides remain legal even when a capture exists |
| Finish jump chains | On | Each single jump ends the turn |
| Men jump backward | Off | Men may capture backward, but still slide forward |
| Flying kings | Off | Kings slide along open diagonals and may land on any clear square beyond one enemy |
| Take the most pieces | Off | Only maximum-length capture paths are offered; ties are allowed |
| Stop when crowned | On | A new king may continue capturing in the same turn |
| Automatic draws | On | Play continues until a win or agreed draw |
| Starting player | Player 1 | Player 2 starts |

**Practice** keeps standard rules except that taking an available capture is optional. Other combinations are custom house rules, not certified Russian, Brazilian, or international draughts. House rules remove captured pieces immediately, including flying-king chains. With optional captures, the maximum-capture switch restricts capture paths but still allows non-capturing slides. With single jumps, all capture paths have length one.

Rule source: [World Checkers/Draughts Federation, English rules](https://wcdf.net/rules/rules_of_checkers_english.pdf), especially sections 1.14–1.20 and 1.30–1.32.

## Build

The project uses Java 17, Gradle 8.9, Android Gradle Plugin 8.7.3 and compile/target SDK 35. No third-party runtime library is required. JUnit is used only by host-side tests. The Gradle wrapper is included.

On Morpheus:

```sh
cd ~/code/space-checkers
source scripts/env.sh
./gradlew testDebugUnitTest lintDebug assembleDebug
```

`scripts/env.sh` selects the isolated toolchain in `~/.local/share/space-checkers-toolchain`; it does not change your shell startup files. On another workstation, install JDK 17 and Android SDK 35, set `JAVA_HOME` and `ANDROID_HOME`, and write the SDK path to the ignored `local.properties` file or let Android Studio do it. The wrapper downloads Gradle automatically.

Debug output: `app/build/outputs/apk/debug/app-debug.apk`.

## Tests and visual review

```sh
source scripts/env.sh
./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
./scripts/start-emulator.sh space-checkers-qa 5558
# In a separate terminal after the emulator starts:
./scripts/test-device.sh emulator-5558
python3 scripts/device-smoke.py --serial emulator-5558
python3 scripts/alien-touch-smoke.py --serial emulator-5558
```

Use disposable emulators: the device tests intentionally reset this app's data. The black-box smoke test temporarily changes emulator display settings, then restores them.

`docs/TESTING.md` records the verified results and limitations. `artifacts/screenshots/` contains screenshots captured from the running APK. Open `docs/review.html` to compare them with the concept and inspect game states.

## Design and source map

Read **DESIGN.md** before adding features. **AGENTS.md** makes that design contract and the verification commands part of future work.

- `Game.java`: Android-independent rules, complete capture paths, draws, history.
- `AlienAI.java`: independent, bounded search over complete turns, using the same rules engine.
- `AlienArt.java`: four native, scalable alien portraits.
- `BoardPerspective.java`: reversible mapping between game cells and the chosen player’s view.
- `MainActivity.java`: screen state, accessible controls, persistence, rule draft flow.
- `SpaceArt.java`: shared palette, typography, pieces, board surfaces, decorative drawing.
- `app/src/main/assets/`: bundled Fredoka font and generated neutral piece artwork. The renderer recolors the artwork at runtime while preserving its lighting.
- `app/src/test/`: 61 engine/perspective/AI/sound-event tests, including random games over all 128 boolean rule combinations.
- `app/src/androidTest/`: actual Android UI and lifecycle checks.

The reference is an AI-generated mockup, so there is no original layout or asset source to import. The implementation recreates its composition and materials with native layout/drawing and custom artwork. Device system bars, aspect ratio, and font rasterization differ; it is not a literal pixel-identical copy. Short or landscape displays scroll; tablet content is centered and width-capped. Controls have native accessibility labels and focus feedback. Full manual TalkBack and physical-device testing remain to be done.

### Moving pieces

Pieces with legal moves have a soft cyan outline. Tap a piece and then a glowing destination, or drag it to that square. While dragging, the piece follows your finger outside the board as well; dropping outside a legal destination returns it unchanged. Tap moves and alien replies animate one landing at a time. Undo also works during a slide.

### Sound effects

Moves, captures, new kings and wins have short space chimes. Open the gear button and switch **Sound effects** off to mute them. The preference applies immediately, survives relaunch, and does not reset a match. Sounds follow the device's media volume and stop when the app leaves play. All four original sound files are bundled for offline use; regenerate with `python3 scripts/generate-sounds.py`.

At the end of a match, **Play again** starts immediately with the same opponent, side and settings. **Back to home** returns to the welcome screen. Wins, losses and draws all offer these actions.
