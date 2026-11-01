package com.spacecheckers;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.os.*;
import android.view.*;
import java.io.*;
import java.util.*;

/** Actual device UI/lifecycle acceptance tests, with no network or test-only app hooks. */
public class AppInstrumentation extends Instrumentation {
  MainActivity a;
  int checks = 0;
  StringBuilder log = new StringBuilder();

  @Override
  public void onCreate(Bundle args) {
    super.onCreate(args);
    start();
  }

  void check(boolean value, String label) {
    checks++;
    if (!value) throw new AssertionError(label);
    log.append("PASS ").append(label).append('\n');
  }

  void ui(Runnable r) {
    Throwable[] failure = new Throwable[1];
    runOnMainSync(
        () -> {
          try {
            r.run();
          } catch (Throwable t) {
            failure[0] = t;
          }
        });
    waitForIdleSync();
    if (failure[0] != null) throw new AssertionError("UI assertion failed", failure[0]);
  }

  void click(String label) {
    if (label.startsWith("Row ") || label.equals("Show a legal move hint")) {
      for (int i = 0; i < 100; i++) {
        boolean[] moving = {false};
        ui(() -> moving[0] = a.scene.moving());
        if (!moving[0]) break;
        SystemClock.sleep(20);
      }
    }
    ui(
        () -> {
          View v = find(a.scene, label);
          check(v != null, "control exists: " + label);
          check(v.isEnabled(), "control enabled: " + label);
          v.performClick();
        });
  }

  View find(View v, String label) {
    if (v.getContentDescription() != null && v.getContentDescription().toString().equals(label))
      return v;
    if (v instanceof android.view.ViewGroup) {
      android.view.ViewGroup g = (android.view.ViewGroup) v;
      for (int i = 0; i < g.getChildCount(); i++) {
        View got = find(g.getChildAt(i), label);
        if (got != null) return got;
      }
    }
    return null;
  }

  void launch() {
    a =
        (MainActivity)
            startActivitySync(
                new Intent(getTargetContext(), MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    waitForIdleSync();
    ui(() -> a.scene.rebuild());
    if (a.hasMatch) click("Continue game");
  }

  void shot(String name) throws IOException {
    waitForIdleSync();
    android.os.SystemClock.sleep(350);
    Bitmap image = getUiAutomation().takeScreenshot();
    File folder = new File(getTargetContext().getExternalFilesDir(null), "screenshots");
    folder.mkdirs();
    try (FileOutputStream f = new FileOutputStream(new File(folder, name + ".png"))) {
      image.compress(Bitmap.CompressFormat.PNG, 100, f);
    }
    image.recycle();
  }

  @Override
  public void onStart() {
    Bundle results = new Bundle();
    try {
      getTargetContext().deleteFile("match-v1.bin");
      launch();
      check(
          a.page.equals("welcome") && !a.hasMatch,
          "fresh launch shows intro without a saved match");
      shot("00-welcome");
      click("New Game");
      click("Two players");
      shot("09-choose-player");
      check(!a.hasMatch, "browsing player choices preserves saved match");
      click("Back to welcome");
      click("New Game");
      click("Two players");
      click("Choose Player 1");
      check(a.localPlayer == 1 && a.hasMatch, "new game starts chosen player at bottom");
      check(a.game.legalTurns().size() == 7, "initial seven legal moves");
      shot("01-game");
      click("Show a legal move hint");
      check(a.selected >= 0 && a.hintTo >= 0, "hint selects a legal source and destination");
      ui(
          () -> {
            a.tap(a.hintTo);
          });
      check(a.game.turn == 2, "move changes active player");
      click("Undo last turn");
      check(a.game.turn == 1 && a.game.moveCount() == 0, "undo restores full initial turn");
      click("Change piece colors");
      check(a.page.equals("colors"), "colors screen opens");
      shot("02-colors");
      click("Player 1 color Pink");
      check(a.color1 == 3, "player 1 color updates");
      click("Player 2 color Orange");
      check(a.color2 == 7, "player 2 color updates");
      ui(() -> a.pick(1, 7));
      check(a.color1 == 3, "duplicate opponent color rejected");
      android.os.SystemClock.sleep(2200);
      click("More colors for Player 1");
      getUiAutomation()
          .performGlobalAction(
              android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK);
      waitForIdleSync();
      click("Let’s play");
      check(a.game.moveCount() == 0, "colors preserve game");
      shot("03-custom-colors");
      click("Game rules and settings");
      check(a.draft.standard(), "standard preset is default");
      shot("04-rules-top");
      click("Finish every jump chain, on");
      click("Stop when crowned, on");
      click("Automatic draws, on");
      click("Starting player, Player 1");
      check(
          !a.draft.multiple && !a.draft.crownEnds && !a.draft.draws && a.draft.first == 2,
          "remaining rule switches and first player update");
      click("Use standard American checkers rules");
      click("Use gentle practice rules");
      check(!a.draft.mandatory, "practice disables forced capture");
      check(a.game.rules.mandatory, "rule edits do not affect current game");
      click("Back to game, discard rule edits");
      check(a.game.rules.mandatory, "back discards rule edits");
      click("Game rules and settings");
      click("Flying kings, off");
      click("Men can jump backward, off");
      click("Take the most pieces, off");
      check(
          a.draft.flying && a.draft.backward && a.draft.maximum,
          "house rule switches update draft");
      ui(() -> a.scroll.fullScroll(View.FOCUS_DOWN));
      shot("05-rules-bottom");
      // Test the actual destructive-action dialog, including cancel and confirm.
      click("Apply rules and start new game");
      check(awaitDialog() != null, "new game confirmation shown");
      getUiAutomation()
          .performGlobalAction(
              android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK);
      waitForIdleSync();
      check(a.game.rules.standard(), "cancel keeps original match");
      click("Apply rules and start new game");
      pressDialogPositive();
      check(a.game.rules.flying && a.game.rules.maximum, "confirmation applies rules");
      check(a.color1 == 3 && a.color2 == 7, "new game preserves colors");
      ui(
          () -> {
            a.game = new Game(new Game.Rules());
            Arrays.fill(a.game.board, 0);
            a.game.board[42] = 1;
            a.game.board[33] = -1;
            a.game.board[17] = -1;
            a.game.board[7] = -1;
            a.selected = -1;
            a.message = "";
            a.refresh();
          });
      click("Row 6, column 3, Player 1 piece");
      click("Row 4, column 1, empty");
      check(a.game.inChain() && a.selected == 24, "jump chain holds selection");
      shot("06-multi-jump");
      ui(
          () -> {
            a.save();
            a.finish();
          });
      launch();
      check(a.game.inChain(), "unfinished jump survives process-style reload");
      click("Row 4, column 1, Player 1 piece");
      click("Row 2, column 3, empty");
      check(!a.game.inChain() && a.game.turn == 2, "reloaded chain can finish");
      click("Undo last turn");
      check(
          a.game.board[42] == 1 && a.game.board[33] == -1 && a.game.board[17] == -1,
          "undo after reload restores entire chain");
      ui(
          () -> {
            a.game = new Game(new Game.Rules());
            Arrays.fill(a.game.board, 0);
            a.game.board[17] = 1;
            a.game.board[10] = -1;
            a.game.board[12] = -1;
            a.selected = -1;
            a.message = "";
            a.refresh();
          });
      click("Row 3, column 2, Player 1 piece");
      click("Row 1, column 4, empty");
      check(a.game.board[3] == 2 && !a.game.inChain(), "promotion ends standard turn");
      shot("07-king");
      ui(
          () -> {
            a.game = new Game(new Game.Rules());
            Arrays.fill(a.game.board, 0);
            a.game.board[42] = 1;
            a.game.board[33] = -1;
            a.selected = -1;
            a.message = "";
            a.refresh();
          });
      click("Row 6, column 3, Player 1 piece");
      click("Row 4, column 1, empty");
      check(a.game.result == 1, "winning move displays result");
      shot("08-win");
      click("Undo last turn");
      check(a.game.result == 0, "undo leaves finished state");
      // Save exact user-visible settings, recreate Activity and verify persisted state.
      ui(
          () -> {
            a.game = new Game(new Game.Rules());
            a.game.step(40, 33);
            a.save();
            a.finish();
          });
      launch();
      check(a.game.board[33] == 1 && a.game.turn == 2, "board and turn survive reload");
      check(a.color1 == 3 && a.color2 == 7, "colors survive reload");
      ui(
          () -> {
            a.color1 = 0;
            a.color2 = 6;
            a.start(new Game.Rules());
          });
      shot("01-game");
      click("Back to welcome");
      click("New Game");
      click("Two players");
      click("Choose Player 2");
      check(
          a.localPlayer == 2 && a.game.turn == 1,
          "choosing Player 2 preserves the configured starting turn");
      check(
          a.turnText().equals("Player 1’s turn!"),
          "opponent turn is named from the selected perspective");
      check(find(a.scene, "Row 6, column 1, Player 2 piece") != null, "Player 2 renders at bottom");
      click("Row 3, column 8, Player 1 piece");
      click("Row 4, column 7, empty");
      check(
          a.game.board[33] == 1 && a.turnText().equals("Your turn!"),
          "rotated opponent input and your-turn prompt agree");
      click("Row 6, column 7, Player 2 piece");
      click("Row 5, column 8, empty");
      check(
          a.game.board[24] == -1 && a.localPlayer == 2,
          "Player 2 moves upward and perspective stays fixed");
      click("Undo last turn");
      check(a.game.board[17] == -1 && a.localPlayer == 2, "undo preserves perspective");
      click("Show a legal move hint");
      check(a.selected >= 0 && a.hintTo >= 0, "hints work from rotated view");
      shot("10-player-two");
      ui(
          () -> {
            a.save();
            a.finish();
          });
      launch();
      check(
          a.localPlayer == 2 && a.game.board[33] == 1,
          "saved perspective and position survive relaunch");
      click("Back to welcome");
      click("New Game");
      click("Two players");
      click("Choose Player 1");
      check(
          a.localPlayer == 1 && a.game.moveCount() == 0 && a.page.equals("game"),
          "single player choice immediately replaces active match without confirmation");
      ui(
          () -> {
            a.localPlayer = 1;
            a.start(new Game.Rules());
            a.page = "welcome";
            a.show();
          });
      click("New Game");
      shot("11-mode-picker");
      click("Play an alien");
      shot("12-alien-picker");
      for (int level = 1; level < 4; level++) {
        click("Choose " + AlienAI.NAMES[level] + ", " + AlienAI.LEVELS[level]);
        check(
            a.chosenAlien == level && a.page.equals("choose"),
            "alien choice advances immediately " + level);
        if (level < 3) {
          click("Back to welcome");
          click("New Game");
          click("Play an alien");
        }
      }
      shot("13-luma-selected");

      click("Choose Player 2");
      ui(
          () -> {
            check(
                a.vsComputer && a.alien == 3 && a.localPlayer == 2,
                "alien match stores mode, level and side");
            int[] before = a.game.board.clone();
            a.tap(40);
            a.tap(33);
            check(Arrays.equals(before, a.game.board), "human cannot move during computer turn");
          });
      waitHuman();
      check(
          a.game.moveCount() == 1 && a.game.turn == 2,
          "alien plays first when human chooses Player 2");
      check(!a.canUndoTurn(), "opening alien move cannot be undone into an endless replay");
      shot("14-alien-game");
      int[] opening = a.game.board.clone();
      ui(
          () -> {
            Game.Move m = a.game.hint();
            a.tap(m.from);
            a.tap(m.landings.get(0));
          });
      waitHuman();
      check(a.game.moveCount() == 3, "alien replies to human move");
      click("Undo last turn");
      check(
          Arrays.equals(opening, a.game.board) && a.game.turn == a.localPlayer,
          "undo restores the human decision before both moves");
      ui(
          () -> {
            a.save();
            a.finish();
          });
      launch();
      check(
          a.vsComputer && a.alien == 3 && a.localPlayer == 2,
          "opponent mode and level survive reload");
      click("Game rules and settings");
      check(
          find(a.scene, "Agree to a draw") == null,
          "two-person draw agreement is hidden in alien mode");
      click("Back to game, discard rule edits");
      // Leave while a multi-jump is in flight, then restore its remaining jumps.
      ui(
          () -> {
            a.cancelComputer();
            Game.Rules r = new Game.Rules();
            r.first = 2;
            a.game = new Game(r);
            Arrays.fill(a.game.board, 0);
            a.game.board[17] = -1;
            a.game.board[26] = 1;
            a.game.board[44] = 1;
            a.game.board[56] = 1;
            a.localPlayer = 1;
            a.alien = 0;
            a.selected = -1;
            a.refresh();
          });
      waitChain();
      ui(
          () -> {
            a.page = "welcome";
            a.show();
          });
      int[] paused = a.game.board.clone();
      android.os.SystemClock.sleep(800);
      check(Arrays.equals(paused, a.game.board), "leaving board cancels remaining alien callbacks");
      ui(
          () -> {
            a.save();
            a.finish();
          });
      launch();
      waitHuman();
      check(
          a.game.board[53] == -1 && a.game.board[26] == 0 && a.game.board[44] == 0,
          "saved computer chain resumes and completes legally");
      // A fresh local game invalidates a pending computer search.
      ui(
          () -> {
            a.vsComputer = true;
            a.alien = 3;
            a.localPlayer = 2;
            a.start(new Game.Rules());
            a.vsComputer = false;
            a.start(new Game.Rules());
          });
      android.os.SystemClock.sleep(1300);
      check(
          a.game.moveCount() == 0 && a.game.turn == 1 && !a.aiBusy,
          "stale search cannot move in a new two-player game");
      ui(
          () -> {
            a.localPlayer = 1;
            a.vsComputer = true;
            a.alien = 1;
            a.game = new Game(new Game.Rules());
            a.hasMatch = true;
            a.page = "welcome";
            a.show();
          });
      click("New Game");
      click("Two players");
      click("Choose Player 1");
      android.os.SystemClock.sleep(800);
      check(
          !a.vsComputer && a.game.moveCount() == 0,
          "two-player mode never schedules a computer move");
      ui(
          () -> {
            a.page = "welcome";
            a.show();
          });
      motionChecks();
      soundChecks();
      resultChecks();
      results.putString("stream", "\n" + log + "\n" + checks + " checks passed.\n");
      finish(Activity.RESULT_OK, results);
    } catch (Throwable t) {
      results.putString(
          "stream", "\n" + log + "\nFAILED: " + t + "\n" + android.util.Log.getStackTraceString(t));
      finish(Activity.RESULT_CANCELED, results);
    }
  }

  void resultAction(String label) {
    ui(
        () -> {
          check(a.resultDialog != null && a.resultDialog.isShowing(), "result popup visible");
          View button = find(a.resultDialog.getWindow().getDecorView(), label);
          check(button != null, "result action: " + label);
          button.performClick();
        });
  }

  void resultChecks() throws IOException {
    ui(
        () -> {
          a.vsComputer = true;
          a.alien = 2;
          a.localPlayer = 2;
          a.color1 = 3;
          a.color2 = 7;
          a.soundEnabled = false;
          a.sounds.setEnabled(false);
          Game.Rules rules = new Game.Rules();
          rules.flying = true;
          rules.first = 2;
          a.start(rules);
          Arrays.fill(a.game.board, 0);
          a.game.board[21] = -1;
          a.game.board[30] = 1;
          a.selected = -1;
          a.refresh();
          a.tap(21);
          a.tap(39);
          check(a.resultDialog == null, "result waits for final animation");
        });
    SystemClock.sleep(350);
    ui(
        () -> {
          check(a.game.result == 2 && a.resultDialog != null, "human victory opens result popup");
          AlertDialog first = a.resultDialog;
          a.showResult();
          check(a.resultDialog == first, "result popup is not duplicated");
        });
    shot("18-result-win");
    resultAction("Play again");
    ui(
        () ->
            check(
                a.game.result == 0
                    && a.game.moveCount() == 0
                    && a.vsComputer
                    && a.alien == 2
                    && a.localPlayer == 2
                    && a.game.rules.flying
                    && a.game.rules.first == 2
                    && a.color1 == 3
                    && a.color2 == 7
                    && !a.soundEnabled,
                "rematch preserves all match settings and starts immediately"));
    ui(
        () -> {
          a.cancelComputer();
          a.localPlayer = 1;
          a.alien = 0;
          a.game = new Game(new Game.Rules());
          Arrays.fill(a.game.board, 0);
          a.game.board[17] = -1;
          a.game.board[26] = 1;
          a.game.turn = 2;
          a.refresh();
        });
    waitHuman();
    ui(() -> check(a.game.result == 2 && a.resultDialog != null, "alien victory opens loss popup"));
    shot("19-result-loss");
    resultAction("Back to home");
    check(a.page.equals("welcome") && a.game.result == 2, "home preserves finished match");
    ui(
        () -> {
          a.page = "game";
          a.show();
        });
    ui(() -> check(a.resultDialog != null, "returning to finished match shows result"));
    ui(
        () -> {
          a.save();
          a.finish();
        });
    launch();
    ui(() -> check(a.resultDialog != null, "finished match popup survives relaunch"));
    resultAction("Back to home");
    ui(
        () -> {
          a.vsComputer = false;
          a.game.result = 3;
          a.page = "game";
          a.show();
        });
    ui(() -> check(a.resultDialog != null, "draw also offers rematch and home"));
    resultAction("Back to home");
  }

  void soundChecks() throws IOException {
    for (int i = 0; i < 100 && !a.sounds.allLoaded(); i++) SystemClock.sleep(20);
    check(a.sounds.allLoaded(), "all four bundled sound assets decode");
    if (!a.soundEnabled) ui(() -> a.toggleSound());
    for (int cue = 0; cue < 4; cue++) {
      final int event = cue;
      ui(
          () -> {
            a.sounds.stop();
            a.sounds.play(Collections.singletonList(event));
          });
      SystemClock.sleep(80);
      ui(() -> check(!a.sounds.streams.isEmpty(), "SoundPool accepts effect " + event));
    }
    ui(
        () -> {
          a.sounds.play(Arrays.asList(SoundCue.CAPTURE, SoundCue.KING, SoundCue.WIN));
          a.openRules();
        });
    int[] before = a.game.board.clone();
    click("Sound effects, on");
    check(
        !a.soundEnabled && Arrays.equals(before, a.game.board),
        "mute applies without replacing match");
    shot("17-sound-settings");
    SystemClock.sleep(1100);
    ui(
        () -> {
          check(a.sounds.streams.isEmpty(), "mute and navigation stop current and queued effects");
          a.sounds.play(Collections.singletonList(SoundCue.WIN));
        });
    SystemClock.sleep(80);
    ui(() -> check(a.sounds.streams.isEmpty(), "muted playback stays silent"));
    ui(
        () -> {
          a.save();
          a.finish();
        });
    launch();
    check(
        !a.soundEnabled && Arrays.equals(before, a.game.board),
        "mute and match survive activity relaunch");
    click("Game rules and settings");
    click("Sound effects, off");
    check(a.soundEnabled, "sound can be enabled again");
    click("Back to game, discard rule edits");
    check(a.soundEnabled, "leaving settings retains immediate sound preference");
  }

  void touch(int action, float x, float y) {
    MotionEvent e =
        MotionEvent.obtain(
            SystemClock.uptimeMillis(),
            SystemClock.uptimeMillis(),
            action,
            a.scene.origin + x * a.scene.scale,
            y * a.scene.scale,
            0);
    a.scene.dispatchTouchEvent(e);
    e.recycle();
  }

  void motionChecks() throws IOException {
    for (int player = 1; player <= 2; player++) {
      final int perspective = player;
      ui(
          () -> {
            a.vsComputer = false;
            a.localPlayer = perspective;
            a.start(new Game.Rules());
          });
      ui(
          () -> {
            check(a.scene.movablePieces().size() == 4, "four initial movable pieces highlighted");
            Game.Move m = a.game.legalTurns().get(0);
            int from = m.from, to = m.landings.get(0);
            float x = a.scene.centerX(from), y = a.scene.centerY(from);
            touch(MotionEvent.ACTION_DOWN, x, y);
            touch(MotionEvent.ACTION_MOVE, 390, 560);
            check(a.scene.dragging && a.scene.floatingX == 390, "piece follows drag outside board");
            check(a.selected == from, "drag selects legal destinations");
            touch(MotionEvent.ACTION_UP, 390, 560);
            check(
                a.game.moveCount() == 0 && a.game.board[from] != 0,
                "off-board drop preserves position");
            touch(MotionEvent.ACTION_DOWN, x, y);
            touch(MotionEvent.ACTION_MOVE, a.scene.centerX(to), a.scene.centerY(to));
            touch(MotionEvent.ACTION_CANCEL, a.scene.centerX(to), a.scene.centerY(to));
            check(
                a.game.moveCount() == 0 && !a.scene.dragging,
                "cancelled gesture preserves position");
            touch(MotionEvent.ACTION_DOWN, x, y);
            touch(MotionEvent.ACTION_MOVE, a.scene.centerX(to), a.scene.centerY(to));
            touch(MotionEvent.ACTION_UP, a.scene.centerX(to), a.scene.centerY(to));
            check(
                a.game.board[from] == 0 && a.game.board[to] != 0,
                "legal drag commits in either perspective");
            a.undoTurn();
            a.tap(from);
            a.tap(to);
            check(
                a.scene.moving() && a.scene.motionBoard[from] != 0,
                "tap move has animated original piece");
            a.tap(a.game.legalTurns().get(0).from);
            check(a.selected == -1, "input blocked during slide");
          });
      SystemClock.sleep(330);
      ui(() -> check(!a.scene.moving(), "slide finishes cleanly"));
    }
    ui(
        () -> {
          a.undoTurn();
          Game.Move m = a.game.legalTurns().get(0);
          a.tap(m.from);
          a.tap(m.landings.get(0));
          a.undoTurn();
          check(!a.scene.moving() && a.game.moveCount() == 0, "undo cancels in-flight animation");
        });
    shot("15-movable-pieces");
    ui(
        () -> {
          Game.Move m = a.game.legalTurns().get(0);
          touch(MotionEvent.ACTION_DOWN, a.scene.centerX(m.from), a.scene.centerY(m.from));
          touch(MotionEvent.ACTION_MOVE, 195, 540);
        });
    shot("16-drag-outside-board");
    ui(() -> touch(MotionEvent.ACTION_CANCEL, 195, 540));
    ui(
        () -> {
          a.localPlayer = 1;
          a.game = new Game(new Game.Rules());
          Arrays.fill(a.game.board, 0);
          a.game.board[42] = 1;
          a.game.board[46] = 1;
          a.game.board[33] = -1;
          a.game.board[17] = -1;
          a.game.board[7] = -1;
          a.selected = -1;
          a.refresh();
          check(
              a.scene.movablePieces().equals(Collections.singleton(42)),
              "forced capture highlights only capturing piece");
          touch(MotionEvent.ACTION_DOWN, a.scene.centerX(42), a.scene.centerY(42));
          touch(MotionEvent.ACTION_MOVE, a.scene.centerX(24), a.scene.centerY(24));
          touch(MotionEvent.ACTION_UP, a.scene.centerX(24), a.scene.centerY(24));
          check(
              a.game.inChain() && a.game.board[33] == 0 && a.selected == 24,
              "drag capture continues jump chain");
          check(
              a.scene.movablePieces().equals(Collections.singleton(24)),
              "only chain piece stays highlighted");
          a.tap(10);
          check(
              a.scene.moving() && a.scene.motionBoard[17] == -1 && a.game.board[17] == 0,
              "tap capture retains captured sprite until landing");
        });
    SystemClock.sleep(330);
    ui(() -> check(!a.game.inChain() && !a.scene.moving(), "mixed drag and tap chain completes"));
  }

  void waitHuman() {
    for (int i = 0; i < 200; i++) {
      boolean[] done = {false};
      ui(() -> done[0] = !a.isComputerTurn() && !a.aiBusy);
      if (done[0]) return;
      android.os.SystemClock.sleep(50);
    }
    throw new AssertionError("Alien did not finish its turn");
  }

  void waitChain() {
    for (int i = 0; i < 300; i++) {
      boolean[] done = {false};
      ui(() -> done[0] = a.game.inChain());
      if (done[0]) return;
      android.os.SystemClock.sleep(10);
    }
    throw new AssertionError("Expected alien jump chain");
  }

  android.view.accessibility.AccessibilityNodeInfo awaitDialog() {
    for (int i = 0; i < 30; i++) {
      android.view.accessibility.AccessibilityNodeInfo root =
          getUiAutomation().getRootInActiveWindow();
      if (root != null && !root.findAccessibilityNodeInfosByText("Keep playing").isEmpty())
        return root;
      android.os.SystemClock.sleep(100);
    }
    return null;
  }

  void pressDialogPositive() {
    android.view.accessibility.AccessibilityNodeInfo root = awaitDialog();
    if (root == null) throw new AssertionError("Dialog root missing");
    java.util.List<android.view.accessibility.AccessibilityNodeInfo> nodes =
        root.findAccessibilityNodeInfosByViewId("android:id/button1");
    if (nodes.isEmpty()) nodes = root.findAccessibilityNodeInfosByText("Start new game");
    check(!nodes.isEmpty(), "confirmation button available");
    nodes.get(0).performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK);
    waitForIdleSync();
  }
}
