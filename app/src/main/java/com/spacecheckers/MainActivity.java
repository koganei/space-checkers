package com.spacecheckers;

import android.animation.ValueAnimator;
import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** Offline pass-and-play checkers. Rendering and hit regions share logical geometry. */
public class MainActivity extends Activity {
  static final int[] PALETTE = {
    0xff21d7e9,
    0xff3198f2,
    0xff8155e9,
    0xfff05db0,
    0xff43ce8a,
    0xffff526b,
    0xffffd336,
    0xffff9b28,
    0xffa5dc65,
    0xffa352ed
  };
  static final String[] NAMES = {
    "Turquoise", "Blue", "Purple", "Pink", "Mint", "Red", "Yellow", "Orange", "Lime", "Violet"
  };
  Game game;
  int color1 = 0, color2 = 6, selected = -1, hintTo = -1;
  String page = "welcome", message = "";
  int localPlayer = 1, chosenPlayer = 1;
  boolean hasMatch = false;
  boolean vsComputer = false, chosenComputer = false, active = false, aiBusy = false;
  int alien = 0, chosenAlien = 0, aiGeneration = 0;
  final Handler aiHandler = new Handler(Looper.getMainLooper());
  final ExecutorService aiWorker = Executors.newSingleThreadExecutor();
  Future<?> aiTask;
  AlienArt aliens;
  Game.Rules draft;
  SpaceArt art;
  GameSounds sounds;
  AlertDialog resultDialog;
  boolean soundEnabled;
  ScrollView scroll;
  Scene scene;

  private static final class Saved implements Serializable {
    private static final long serialVersionUID = 1L;
    Game game;
    int c1, c2;
    int schemaVersion = 3, bottomPlayer;
    boolean computer;
    int opponent;
    boolean hasMatch;

    Saved(Game g, int a, int b, int player, boolean active, boolean ai, int opponent) {
      computer = ai;
      this.opponent = opponent;
      bottomPlayer = player;
      hasMatch = active;
      game = g;
      c1 = a;
      c2 = b;
    }
  }

  @Override
  public void onCreate(Bundle b) {
    super.onCreate(b);
    art = new SpaceArt(getAssets());
    aliens = new AlienArt(art);
    load();
    soundEnabled = getPreferences(MODE_PRIVATE).getBoolean("soundEnabled", true);
    sounds = new GameSounds(this, soundEnabled);
    setVolumeControlStream(android.media.AudioManager.STREAM_MUSIC);
    if (b != null) {
      page = b.getString("page", "welcome");
      chosenPlayer = b.getInt("chosenPlayer", localPlayer);
      chosenComputer = b.getBoolean("chosenComputer", vsComputer);
      chosenAlien = b.getInt("chosenAlien", alien);
      selected = b.getInt("selected", -1);
      draft = (Game.Rules) b.getSerializable("draft");
    }
    if (page.equals("rules") && draft == null) draft = game.rules.copy();
    getWindow().setStatusBarColor(SpaceArt.BG);
    getWindow().setNavigationBarColor(SpaceArt.BG);
    getWindow().getDecorView().setSystemUiVisibility(0);
    FrameLayout root = new FrameLayout(this);
    root.setBackgroundColor(SpaceArt.BG);
    // API 35 enforces edge-to-edge; consume real system-bar insets for all versions.
    root.setOnApplyWindowInsetsListener(
        (v, insets) -> {
          if (android.os.Build.VERSION.SDK_INT >= 35) {
            android.graphics.Insets i =
                insets.getInsets(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            v.setPadding(i.left, i.top, i.right, i.bottom);
          }
          return insets;
        });
    scroll = new ScrollView(this);
    scroll.setFillViewport(true);
    scroll.setClipToPadding(false);
    scroll.setVerticalScrollBarEnabled(false);
    root.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
    setContentView(root);
    show();
  }

  private void load() {
    try (ObjectInputStream in = new ObjectInputStream(openFileInput("match-v1.bin"))) {
      Saved s = (Saved) in.readObject();
      if (s.game == null
          || s.c1 < 0
          || s.c1 >= PALETTE.length
          || s.c2 < 0
          || s.c2 >= PALETTE.length) throw new IOException("Invalid save");
      game = s.game;
      color1 = s.c1;
      color2 = s.c2;
      localPlayer = s.bottomPlayer == 2 ? 2 : 1;
      chosenPlayer = localPlayer;
      hasMatch = s.schemaVersion < 2 || s.hasMatch;
      vsComputer = s.computer;
      alien = Math.max(0, Math.min(3, s.opponent));
      chosenComputer = vsComputer;
      chosenAlien = alien;
    } catch (IOException | ClassNotFoundException | ClassCastException e) {
      game = new Game(new Game.Rules());
    }
  }

  void save() {
    try {
      File temp = new File(getFilesDir(), "match-v1.tmp");
      try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(temp))) {
        out.writeObject(new Saved(game, color1, color2, localPlayer, hasMatch, vsComputer, alien));
      }
      if (!temp.renameTo(new File(getFilesDir(), "match-v1.bin")))
        throw new IOException("Save rename failed");
    } catch (IOException e) {
      message = "Could not save this game.";
    }
  }

  @Override
  protected void onResume() {
    super.onResume();
    active = true;
    scene.rebuild();
    maybeStartComputer();
  }

  @Override
  protected void onDestroy() {
    cancelComputer();
    aiWorker.shutdownNow();
    sounds.release();
    super.onDestroy();
  }

  @Override
  protected void onPause() {
    dismissResult();
    active = false;
    cancelComputer();
    save();
    super.onPause();
  }

  @Override
  protected void onSaveInstanceState(Bundle b) {
    save();
    b.putString("page", page);
    b.putInt("chosenPlayer", chosenPlayer);
    b.putBoolean("chosenComputer", chosenComputer);
    b.putInt("chosenAlien", chosenAlien);
    b.putInt("selected", selected);
    b.putSerializable("draft", draft);
    super.onSaveInstanceState(b);
  }

  @Override
  public void onBackPressed() {
    if (page.equals("welcome")) {
      super.onBackPressed();
      return;
    }
    page =
        page.equals("aliens")
            ? "mode"
            : page.equals("game") || page.equals("choose") || page.equals("mode")
                ? "welcome"
                : "game";
    draft = null;
    show();
  }

  void chooseNewGame() {
    chosenPlayer = localPlayer;
    chosenComputer = vsComputer;
    chosenAlien = alien;
    page = "mode";
    show();
  }

  void playChosen() {
    localPlayer = chosenPlayer;
    vsComputer = chosenComputer;
    alien = chosenAlien;
    start(game.rules);
  }

  void dismissResult() {
    if (resultDialog != null) {
      resultDialog.dismiss();
      resultDialog = null;
    }
  }

  void resultHome() {
    page = "welcome";
    show();
  }

  void showResult() {
    if (!active
        || !page.equals("game")
        || game.result == 0
        || scene.moving()
        || isFinishing()
        || (resultDialog != null && resultDialog.isShowing())) return;
    String title =
        game.result == 3
            ? "A friendly draw!"
            : !vsComputer
                ? "Player " + game.result + " wins!"
                : game.result == localPlayer ? "You win!" : AlienAI.NAMES[alien] + " wins!";
    String body =
        game.result == 3
            ? "A great adventure together. Try another?"
            : vsComputer && game.result != localPlayer
                ? "Good try, space explorer! Ready for a rematch?"
                : "Great playing, space explorer! Ready for another round?";
    float density = getResources().getDisplayMetrics().density;
    int pad = Math.round(24 * density);
    LinearLayout content = new LinearLayout(this);
    content.setOrientation(LinearLayout.VERTICAL);
    content.setPadding(pad, pad, pad, pad);
    TextView heading = new TextView(this);
    heading.setText(title);
    heading.setTextSize(28);
    heading.setTypeface(art.bold);
    heading.setTextColor(SpaceArt.WHITE);
    heading.setGravity(Gravity.CENTER);
    content.addView(heading);
    TextView detail = new TextView(this);
    detail.setText(body);
    detail.setTextSize(18);
    detail.setTypeface(art.regular);
    detail.setTextColor(SpaceArt.MUTED);
    detail.setGravity(Gravity.CENTER);
    detail.setPadding(0, pad / 2, 0, pad);
    content.addView(detail);
    for (int i = 0; i < 2; i++) {
      boolean primary = i == 0;
      Button button = new Button(this);
      button.setText(primary ? "Play again" : "Back to home");
      button.setContentDescription(primary ? "Play again" : "Back to home");
      button.setAllCaps(false);
      button.setTextSize(20);
      button.setTypeface(art.bold);
      button.setTextColor(primary ? SpaceArt.BG : SpaceArt.WHITE);
      button.setSoundEffectsEnabled(false);
      GradientDrawable background = new GradientDrawable();
      background.setColor(primary ? SpaceArt.CYAN : 0xff142e50);
      background.setCornerRadius(28 * density);
      if (!primary) background.setStroke(Math.round(density), 0xff41678f);
      button.setBackground(
          new RippleDrawable(
              android.content.res.ColorStateList.valueOf(0x33ffffff), background, null));
      LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
      params.topMargin = Math.round(12 * density);
      button.setMinHeight(Math.round(56 * density));
      content.addView(button, params);
      button.setOnClickListener(
          v -> {
            if (primary) start(game.rules);
            else resultHome();
          });
    }
    ScrollView viewport = new ScrollView(this);
    viewport.addView(content);
    resultDialog = new AlertDialog.Builder(this).setView(viewport).create();
    resultDialog.setCanceledOnTouchOutside(false);
    resultDialog.setOnCancelListener(d -> resultHome());
    resultDialog.show();
    GradientDrawable background = new GradientDrawable();
    background.setColor(0xff142e50);
    background.setCornerRadius(24 * density);
    background.setStroke(Math.round(density), 0xff36567e);
    resultDialog.getWindow().setBackgroundDrawable(background);
  }

  int modelCell(int displayed) {
    return BoardPerspective.cell(displayed, localPlayer);
  }

  void show() {
    dismissResult();
    cancelComputer();
    scroll.removeAllViews();
    scene = new Scene();
    scroll.addView(scene, new ScrollView.LayoutParams(-1, -2));
    scroll.post(() -> scroll.scrollTo(0, 0));
    maybeStartComputer();
  }

  void refresh() {
    save();
    scene.rebuild();
    maybeStartComputer();
  }

  int playerColor(int player) {
    return PALETTE[player == 1 ? color1 : color2];
  }

  void openColors() {
    page = "colors";
    show();
  }

  void openRules() {
    draft = game.rules.copy();
    page = "rules";
    show();
  }

  void confirm(String title, String body, String positive, Runnable action) {
    AlertDialog dialog =
        new AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(body)
            .setNegativeButton("Keep playing", null)
            .setPositiveButton(positive, (d, w) -> action.run())
            .create();
    dialog.setOnShowListener(d -> styleDialog(dialog));
    dialog.show();
  }

  void styleDialog(AlertDialog dialog) {
    GradientDrawable bg = new GradientDrawable();
    bg.setColor(0xff142e50);
    bg.setCornerRadius(20 * getResources().getDisplayMetrics().density);
    bg.setStroke(1, 0xff36567e);
    dialog.getWindow().setBackgroundDrawable(bg);
    styleText(dialog.getWindow().getDecorView());
  }

  void styleText(View v) {
    v.setSoundEffectsEnabled(false);
    if (v instanceof TextView) {
      TextView t = (TextView) v;
      t.setTypeface(art.regular);
      t.setTextColor(v instanceof Button ? SpaceArt.CYAN : SpaceArt.WHITE);
    }
    if (v instanceof ViewGroup) {
      ViewGroup g = (ViewGroup) v;
      for (int i = 0; i < g.getChildCount(); i++) styleText(g.getChildAt(i));
    }
  }

  void start(Game.Rules r) {
    cancelComputer();
    hasMatch = true;
    game = new Game(r);
    selected = -1;
    hintTo = -1;
    message = "";
    page = "game";
    save();
    show();
  }

  void pick(int player, int index) {
    if (index == (player == 1 ? color2 : color1)) {
      Toast.makeText(this, "Choose a different color from the other player.", Toast.LENGTH_SHORT)
          .show();
      return;
    }
    if (player == 1) color1 = index;
    else color2 = index;
    refresh();
  }

  void allColors(int player) {
    showDialog(
        new AlertDialog.Builder(this)
            .setTitle("Player " + player + " color")
            .setItems(NAMES, (d, w) -> pick(player, w))
            .setNegativeButton("Back", null));
  }

  void showDialog(AlertDialog.Builder builder) {
    AlertDialog d = builder.create();
    d.setOnShowListener(ignored -> styleDialog(d));
    d.show();
  }

  int swatch(int player, int slot) {
    int base = player == 1 ? 0 : 5, chosen = player == 1 ? color1 : color2;
    return slot == 0 && (chosen < base || chosen >= base + 5) ? chosen : base + slot;
  }

  void tap(int cell) {
    if (game.result != 0 || isComputerTurn() || scene.moving()) return;
    int from = selected;
    int[] before = game.board.clone();
    if (selected >= 0 && game.step(selected, cell)) {
      selected = game.inChain() ? cell : -1;
      hintTo = -1;
      message = "";
      save();
      scene.animateMove(
          from,
          cell,
          before,
          () -> {
            scene.rebuild();
            scene.announceForAccessibility(turnText());
            maybeStartComputer();
          });
      return;
    }
    if (Game.owner(game.board[cell]) == game.turn) {
      boolean legal = false;
      for (Game.Move m : game.legalTurns()) if (m.from == cell) legal = true;
      if (legal) {
        selected = cell;
        message = "Choose a glowing square.";
      } else
        message =
            game.inChain()
                ? "Keep jumping with the same piece."
                : "Another piece has a jump to take.";
    } else {
      if (!game.inChain()) selected = -1;
      message = "Tap one of your pieces.";
    }
    hintTo = -1;
    scene.rebuild();
  }

  String turnText() {
    if (game.result == 3) return "A friendly draw!";
    if (game.result != 0)
      return vsComputer && game.result != localPlayer
          ? AlienAI.NAMES[alien] + " wins!"
          : "Player " + game.result + " wins!";
    if (isComputerTurn()) return AlienAI.NAMES[alien] + "’s turn!";
    return game.inChain()
        ? "Keep jumping!"
        : game.turn == localPlayer ? "Your turn!" : "Player " + game.turn + "’s turn!";
  }

  String helpText() {
    if (game.result != 0) return "Great game, space explorers!";
    if (isComputerTurn())
      return game.inChain()
          ? "Watch " + AlienAI.NAMES[alien] + "’s jump!"
          : AlienAI.NAMES[alien] + " is thinking…";
    if (!message.isEmpty()) return message;
    if (game.inChain()) return "Finish your jump with this piece.";
    if (selected >= 0) return "Tap or drag to a glowing square.";
    return game.moveCount() == 0
        ? "Tap or drag a glowing piece."
        : "Tap a piece to keep exploring.";
  }

  boolean isComputerTurn() {
    return vsComputer && game.result == 0 && game.turn != localPlayer;
  }

  boolean canUndoTurn() {
    return game.canUndo()
        && !(vsComputer && game.rules.first != localPlayer && game.moveCount() == 1);
  }

  void undoTurn() {
    if (!canUndoTurn()) return;
    dismissResult();
    cancelComputer();
    game.undo();
    // Take back the alien reply plus the preceding human turn, or an unfinished human turn.
    if (vsComputer) while (game.turn != localPlayer && game.canUndo()) game.undo();
    selected = -1;
    hintTo = -1;
    message = "Try a different move.";
    refresh();
  }

  void cancelComputer() {
    if (sounds != null) sounds.stop();
    if (scene != null) scene.cancelMotion();
    aiGeneration++;
    if (aiTask != null) aiTask.cancel(true);
    aiTask = null;
    aiHandler.removeCallbacksAndMessages(null);
    aiBusy = false;
  }

  void maybeStartComputer() {
    showResult();
    if (!active
        || !page.equals("game")
        || !isComputerTurn()
        || aiBusy
        || scene.moving()
        || isFinishing()) return;
    aiBusy = true;
    selected = -1;
    hintTo = -1;
    final int generation = aiGeneration;
    final Game original = game, snapshot = game.searchCopy();
    final int difficulty = alien;
    aiTask =
        aiWorker.submit(
            () -> {
              Game.Move move = new AlienAI(System.nanoTime()).choose(snapshot, difficulty);
              if (Thread.currentThread().isInterrupted()) return;
              aiHandler.postDelayed(
                  () -> {
                    if (generation != aiGeneration
                        || game != original
                        || !active
                        || !page.equals("game")) return;
                    if (move == null) {
                      aiBusy = false;
                      return;
                    }
                    playComputerLanding(move, 0, move.from, generation, original);
                  },
                  450);
            });
  }

  private void playComputerLanding(
      Game.Move move, int index, int from, int generation, Game original) {
    if (generation != aiGeneration
        || game != original
        || !active
        || !page.equals("game")
        || !isComputerTurn()) return;
    int to = move.landings.get(index);
    int[] before = game.board.clone();
    if (!game.step(from, to)) {
      aiBusy = false;
      return;
    }
    selected = game.inChain() ? to : -1;
    save();
    scene.animateMove(
        from,
        to,
        before,
        () -> {
          scene.rebuild();
          if (index + 1 < move.landings.size())
            aiHandler.postDelayed(
                () -> playComputerLanding(move, index + 1, to, generation, original), 250);
          else {
            aiBusy = false;
            aiTask = null;
            scene.announceForAccessibility(turnText());
          }
        });
  }

  void playLandingSound(int[] before, int from, int to) {
    if (active) sounds.play(SoundCue.landing(before, from, game.board, to, game.result));
  }

  void toggleSound() {
    soundEnabled = !soundEnabled;
    getPreferences(MODE_PRIVATE).edit().putBoolean("soundEnabled", soundEnabled).apply();
    sounds.setEnabled(soundEnabled);
    scene.rebuild();
  }

  private interface Toggle {
    void change();
  }

  private final class Hit {
    final String name;
    final RectF rect;
    final Runnable run;
    final boolean enabled;

    Hit(String n, float x, float y, float w, float h, boolean e, Runnable r) {
      name = n;
      rect = new RectF(x, y, x + w, y + h);
      run = r;
      enabled = e;
    }
  }

  class Scene extends FrameLayout {
    float scale = 1, origin = 0, height = 760;
    final ArrayList<Hit> hits = new ArrayList<>();
    View drawing;
    ValueAnimator motion;
    int[] motionBoard;
    int motionFrom = -1, dragFrom = -1;
    int floatingPiece;
    float floatingX, floatingY, downX, downY;
    boolean dragging;

    boolean moving() {
      return motion != null;
    }

    Set<Integer> movablePieces() {
      Set<Integer> result = new HashSet<>();
      if (game.result == 0) for (Game.Move move : game.legalTurns()) result.add(move.from);
      return result;
    }

    float centerX(int cell) {
      return 12 + modelCell(cell) % 8 * 45.75f + 22.875f;
    }

    float centerY(int cell) {
      return 148 + modelCell(cell) / 8 * 45.75f + 21.875f;
    }

    int cellAt(float x, float y) {
      if (x < 12 || x >= 378 || y < 148 || y >= 514) return -1;
      return modelCell((int) ((y - 148) / 45.75f) * 8 + (int) ((x - 12) / 45.75f));
    }

    void cancelMotion() {
      if (motion != null) {
        motion.removeAllListeners();
        motion.removeAllUpdateListeners();
        motion.cancel();
        motion = null;
      }
      motionBoard = null;
      motionFrom = -1;
      dragFrom = -1;
      dragging = false;
      scroll.requestDisallowInterceptTouchEvent(false);
      drawing.invalidate();
    }

    void animateMove(int from, int to, int[] before, Runnable done) {
      motionBoard = before;
      motionFrom = from;
      floatingPiece = before[from];
      floatingX = centerX(from);
      floatingY = centerY(from);
      motion = ValueAnimator.ofFloat(0, 1);
      motion.setDuration(240);
      motion.addUpdateListener(
          value -> {
            float t = (float) value.getAnimatedValue();
            floatingX = centerX(from) + (centerX(to) - centerX(from)) * t;
            floatingY = centerY(from) + (centerY(to) - centerY(from)) * t;
            drawing.invalidate();
          });
      motion.addListener(
          new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
              motion = null;
              motionBoard = null;
              motionFrom = -1;
              playLandingSound(before, from, to);
              done.run();
              showResult();
              drawing.invalidate();
            }
          });
      rebuild();
      motion.start();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
      float x = (event.getX() - origin) / scale, y = event.getY() / scale;
      int action = event.getActionMasked();
      if (action == MotionEvent.ACTION_DOWN
          && page.equals("game")
          && !moving()
          && !isComputerTurn()
          && game.result == 0) {
        int cell = cellAt(x, y);
        if (movablePieces().contains(cell)) {
          dragFrom = cell;
          downX = x;
          downY = y;
          scroll.requestDisallowInterceptTouchEvent(true);
          return true;
        }
      }
      if (dragFrom >= 0) {
        if (action == MotionEvent.ACTION_MOVE) {
          if (!dragging
              && Math.hypot(x - downX, y - downY) * scale
                  > ViewConfiguration.get(getContext()).getScaledTouchSlop()) {
            dragging = true;
            selected = dragFrom;
            hintTo = -1;
            message = "Drop on a glowing square.";
            floatingPiece = game.board[dragFrom];
          }
          floatingX = x;
          floatingY = y;
          drawing.invalidate();
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
          int from = dragFrom, to = cellAt(x, y);
          boolean wasDragging = dragging;
          dragFrom = -1;
          dragging = false;
          scroll.requestDisallowInterceptTouchEvent(false);
          if (action == MotionEvent.ACTION_UP) {
            if (!wasDragging) tap(from);
            else {
              int[] before = game.board.clone();
              if (to >= 0 && game.step(from, to)) {
                playLandingSound(before, from, to);
                selected = game.inChain() ? to : -1;
                message = "";
                hintTo = -1;
                refresh();
                announceForAccessibility(turnText());
              } else {
                selected = from;
                message = "Drop on a glowing square.";
                rebuild();
              }
            }
          }
          drawing.invalidate();
        }
        return true;
      }
      return super.dispatchTouchEvent(event);
    }

    Scene() {
      super(MainActivity.this);
      setClipChildren(false);
      setClipToPadding(false);
      setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
      drawing =
          new View(MainActivity.this) {
            @Override
            protected void onDraw(Canvas c) {
              c.save();
              c.translate(origin, 0);
              c.scale(scale, scale);
              paint(c);
              c.restore();
            }
          };
      drawing.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
      addView(drawing);
    }

    @Override
    protected void onMeasure(int w, int h) {
      int width = MeasureSpec.getSize(w);
      float density = getResources().getDisplayMetrics().density;
      scale = Math.min(width, 500 * density) / 390f;
      origin = (width - 390 * scale) / 2;
      height = page.equals("rules") ? 1244 : 760;
      art.textScale =
          Math.min(
              1.25f,
              Math.max(1, 390 * density / width) * getResources().getConfiguration().fontScale);
      int hh = (int) Math.ceil(height * scale);
      setMeasuredDimension(width, hh);
      drawing.measure(
          MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
          MeasureSpec.makeMeasureSpec(hh, MeasureSpec.EXACTLY));
      for (int i = 1; i < getChildCount(); i++) {
        View v = getChildAt(i);
        Hit hit = (Hit) v.getTag();
        v.measure(
            MeasureSpec.makeMeasureSpec(Math.round(hit.rect.width() * scale), MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(
                Math.round(hit.rect.height() * scale), MeasureSpec.EXACTLY));
      }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
      drawing.layout(0, 0, getWidth(), getHeight());
      if (getChildCount() == 1) post(this::rebuild);
      for (int i = 1; i < getChildCount(); i++) {
        View v = getChildAt(i);
        Hit h = (Hit) v.getTag();
        int left = Math.round(origin + h.rect.left * scale), top = Math.round(h.rect.top * scale);
        v.layout(left, top, left + v.getMeasuredWidth(), top + v.getMeasuredHeight());
      }
    }

    void add(String label, float x, float y, float w, float h, boolean enabled, Runnable run) {
      if (!label.startsWith("Row ")) {
        float min = 48 * getResources().getDisplayMetrics().density / scale;
        float nw = Math.max(w, min), nh = Math.max(h, min);
        x -= (nw - w) / 2;
        y -= (nh - h) / 2;
        w = nw;
        h = nh;
      }
      if (page.equals("rules") && y >= 100 && !label.startsWith("Sound effects")) y += 104;
      hits.add(new Hit(label, x, y, w, h, enabled, run));
    }

    void rebuild() {
      hits.clear();
      drawing.setContentDescription(
          page.equals("game")
              ? "Space Checkers. " + turnText() + " " + helpText()
              : page.equals("colors")
                  ? "Piece colors. Choose a swatch or More for all colors."
                  : page.equals("welcome")
                      ? "Welcome to Space Checkers. Start a new game or continue your saved game."
                      : page.equals("choose")
                          ? "Choose Player 1 or Player 2. Your pieces always start at the bottom."
                          : page.equals("mode")
                              ? "Choose Play an alien or Two players."
                              : page.equals("aliens")
                                  ? "Choose an alien opponent. Pip Easy, Bloop Medium, Ziggy"
                                      + " Tricky, Luma Hard."
                                  : "Game rules. Changes apply to a new game only.");
      if (page.equals("game")) gameHits();
      else if (page.equals("colors")) colorHits();
      else if (page.equals("welcome")) welcomeHits();
      else if (page.equals("choose")) chooseHits();
      else if (page.equals("mode")) modeHits();
      else if (page.equals("aliens")) alienHits();
      else rulesHits();
      if (getChildCount() > 1) removeViews(1, getChildCount() - 1);
      for (Hit hit : hits) {
        Button button = new Button(MainActivity.this);
        button.setSoundEffectsEnabled(false);
        button.setText("");
        button.setContentDescription(hit.name);
        button.setTag(hit);
        button.setEnabled(hit.enabled);
        button.setMinWidth(0);
        button.setMinHeight(0);
        button.setMinimumWidth(0);
        button.setMinimumHeight(0);
        button.setPadding(0, 0, 0, 0);
        StateListDrawable state = new StateListDrawable();
        GradientDrawable active = new GradientDrawable();
        active.setColor(0x3321d7e9);
        active.setCornerRadius(12 * scale);
        active.setStroke(Math.max(2, (int) (2 * scale)), SpaceArt.CYAN);
        state.addState(new int[] {android.R.attr.state_focused}, active);
        state.addState(new int[] {android.R.attr.state_pressed}, active);
        state.addState(new int[] {}, new ColorDrawable(Color.TRANSPARENT));
        button.setBackground(state);
        button.setStateListAnimator(null);
        button.setOnClickListener(v -> hit.run.run());
        addView(button);
      }
      requestLayout();
      drawing.invalidate();
    }

    void welcomeHits() {
      add("New Game", 24, 499, 342, 62, true, MainActivity.this::chooseNewGame);
      if (hasMatch)
        add(
            "Continue game",
            24,
            581,
            342,
            62,
            true,
            () -> {
              page = "game";
              show();
            });
    }

    void modeHits() {
      add(
          "Back to welcome",
          12,
          20,
          48,
          48,
          true,
          () -> {
            page = "welcome";
            show();
          });
      add(
          "Play an alien",
          24,
          194,
          342,
          186,
          true,
          () -> {
            chosenComputer = true;
            page = "aliens";
            show();
          });
      add(
          "Two players",
          24,
          414,
          342,
          186,
          true,
          () -> {
            chosenComputer = false;
            page = "choose";
            show();
          });
    }

    void alienHits() {
      add(
          "Back to modes",
          12,
          20,
          48,
          48,
          true,
          () -> {
            page = "mode";
            show();
          });
      for (int i = 0; i < 4; i++) {
        final int choice = i;
        add(
            "Choose " + AlienAI.NAMES[i] + ", " + AlienAI.LEVELS[i],
            24,
            125 + i * 119,
            342,
            104,
            true,
            () -> {
              chosenAlien = choice;
              page = "choose";
              show();
            });
      }
    }

    void chooseHits() {
      add(
          "Back to welcome",
          12,
          20,
          48,
          48,
          true,
          () -> {
            page = "welcome";
            show();
          });
      for (int player = 1; player <= 2; player++) {
        final int choice = player;
        add(
            "Choose Player " + player,
            24 + (player - 1) * 180,
            238,
            162,
            207,
            true,
            () -> {
              chosenPlayer = choice;
              playChosen();
            });
      }
    }

    void gameHits() {
      add(
          "Back to welcome",
          12,
          20,
          48,
          48,
          true,
          () -> {
            page = "welcome";
            show();
          });
      add("Game rules and settings", 328, 22, 48, 48, true, MainActivity.this::openRules);
      for (int i = 0; i < 64; i++) {
        final int cell = modelCell(i);
        int p = game.board[cell];
        if (!Game.dark(i)) continue;
        String label =
            "Row "
                + (i / 8 + 1)
                + ", column "
                + (i % 8 + 1)
                + ", "
                + (p == 0
                    ? "empty"
                    : ("Player " + Game.owner(p) + (Math.abs(p) == 2 ? " king" : " piece")));
        add(
            label,
            12 + i % 8 * 45.75f,
            148 + i / 8 * 45.75f,
            45.75f,
            45.75f,
            game.result == 0 && !isComputerTurn() && !moving(),
            () -> tap(cell));
      }
      add("Undo last turn", 36, 630, 72, 94, canUndoTurn(), MainActivity.this::undoTurn);
      add(
          "Show a legal move hint",
          159,
          630,
          72,
          94,
          game.result == 0 && !isComputerTurn() && !moving(),
          () -> {
            Game.Move m = game.hint();
            if (m != null) {
              selected = m.from;
              hintTo = m.landings.get(0);
              message = m.count() > 0 ? "Try this jump!" : "Try this move!";
              scene.rebuild();
            }
          });
      add("Change piece colors", 282, 630, 72, 94, true, MainActivity.this::openColors);
      if (game.result != 0)
        add("Play again", 90, 527, 210, 91, true, MainActivity.this::showResult);
    }

    void colorHits() {
      add(
          "Back to game",
          12,
          20,
          48,
          48,
          true,
          () -> {
            page = "game";
            show();
          });
      for (int p = 1; p <= 2; p++) {
        final int player = p;
        int base = p == 1 ? 0 : 5;
        for (int i = 0; i < 5; i++) {
          final int index = swatch(p, i);
          add(
              "Player "
                  + p
                  + " color "
                  + NAMES[index]
                  + ((p == 1 ? color1 : color2) == index ? ", selected" : ""),
              32 + i * 66,
              p == 1 ? 408 : 550,
              54,
              54,
              true,
              () -> pick(player, index));
        }
        add(
            "More colors for Player " + p,
            284,
            p == 1 ? 354 : 496,
            78,
            48,
            true,
            () -> allColors(player));
      }
      add(
          "Let’s play",
          24,
          682,
          342,
          62,
          true,
          () -> {
            page = "game";
            show();
          });
    }

    void rulesHits() {
      add(
          "Sound effects, " + (soundEnabled ? "on" : "off"),
          24,
          107,
          342,
          80,
          true,
          MainActivity.this::toggleSound);
      add(
          "Back to game, discard rule edits",
          12,
          20,
          48,
          48,
          true,
          () -> {
            page = "game";
            draft = null;
            show();
          });
      add(
          "Use standard American checkers rules",
          24,
          107,
          166,
          48,
          true,
          () -> {
            draft = new Game.Rules();
            rebuild();
          });
      add(
          "Use gentle practice rules",
          200,
          107,
          166,
          48,
          true,
          () -> {
            draft = new Game.Rules();
            draft.mandatory = false;
            rebuild();
          });
      Toggle[] changes = {
        () -> draft.mandatory = !draft.mandatory,
        () -> draft.multiple = !draft.multiple,
        () -> draft.backward = !draft.backward,
        () -> draft.flying = !draft.flying,
        () -> draft.maximum = !draft.maximum,
        () -> draft.crownEnds = !draft.crownEnds,
        () -> draft.draws = !draft.draws
      };
      String[] labels = {
        "Must take a jump",
        "Finish every jump chain",
        "Men can jump backward",
        "Flying kings",
        "Take the most pieces",
        "Stop when crowned",
        "Automatic draws"
      };
      boolean[] values = values();
      for (int i = 0; i < 7; i++) {
        final int k = i;
        add(
            labels[i] + ", " + (values[i] ? "on" : "off"),
            24,
            180 + i * 87,
            342,
            80,
            true,
            () -> {
              changes[k].change();
              rebuild();
            });
      }
      add(
          "Starting player, Player " + draft.first,
          24,
          798,
          342,
          66,
          true,
          () -> {
            draft.first = 3 - draft.first;
            rebuild();
          });
      add(
          "Apply rules and start new game",
          24,
          904,
          342,
          62,
          true,
          () ->
              confirm(
                  "Start with these rules?",
                  "This replaces the current game. Your piece colors stay the same.",
                  "Start new game",
                  () -> start(draft)));
      add(
          "How to play",
          24,
          982,
          162,
          52,
          true,
          () ->
              showDialog(
                  new AlertDialog.Builder(MainActivity.this)
                      .setTitle("How to play")
                      .setMessage(
                          "Play an alien or share this device with a friend. Your chosen player"
                              + " starts at the bottom and moves toward the top. The other player"
                              + " starts at the top.\n\n"
                              + "Tap your piece, then a glowing square. Men move one square"
                              + " diagonally forward. Jump over an opponent to capture it. Kings"
                              + " carry a crown and can move in both directions.\n\n"
                              + "Win by capturing every opponent piece or leaving them with no"
                              + " legal move. Your selected rules control jumps and kings. Hint"
                              + " shows a legal idea. Undo takes back a whole turn.\n\n"
                              + "Automatic draws: the same position and turn three times, or 40"
                              + " turns each without a capture or a man moving. These are automatic"
                              + " here, rather than referee claims.")
                      .setPositiveButton("Got it", null)));
      if (!vsComputer)
        add(
            "Agree to a draw",
            204,
            982,
            162,
            52,
            game.result == 0 && !game.inChain(),
            () ->
                confirm(
                    "Call it a draw?",
                    "Both players should agree before ending the game.",
                    "We both agree",
                    () -> {
                      game.agreeDraw();
                      page = "game";
                      save();
                      show();
                    }));
    }

    boolean[] values() {
      return new boolean[] {
        draft.mandatory,
        draft.multiple,
        draft.backward,
        draft.flying,
        draft.maximum,
        draft.crownEnds,
        draft.draws
      };
    }

    void paint(Canvas c) {
      art.background(c, height, !page.equals("game"));
      if (page.equals("game")) drawGame(c);
      else if (page.equals("colors")) drawColors(c);
      else if (page.equals("welcome")) drawWelcome(c);
      else if (page.equals("choose")) drawChoose(c);
      else if (page.equals("mode")) drawMode(c);
      else if (page.equals("aliens")) drawAliens(c);
      else drawRules(c);
    }

    void heading(Canvas c, String title) {
      art.icon(c, "back", 36, 45, 25, SpaceArt.WHITE);
      art.text(c, title, 210, 62, 36, SpaceArt.WHITE, true, true);
    }

    void drawWelcome(Canvas c) {
      art.text(c, "Space", 195, 117, 46, SpaceArt.WHITE, true, true);
      art.text(c, "Checkers", 195, 168, 46, SpaceArt.WHITE, true, true);
      art.text(c, "A little strategy. A big adventure.", 195, 208, 18, SpaceArt.MUTED, false, true);
      art.fill(0xff407db1);
      art.p.setStyle(Paint.Style.STROKE);
      art.p.setStrokeWidth(2);
      c.save();
      c.rotate(-16, 195, 337);
      c.drawOval(48, 267, 342, 411, art.p);
      c.restore();
      art.piece(c, 134, 328, 68, playerColor(1), 1, false);
      art.piece(c, 267, 356, 60, playerColor(2), 2, false);
      art.primary(c, "New Game", 499);
      if (hasMatch) {
        art.stroke(c, 24, 581, 366, 643, 31, 0xff41678f, 1.5f);
        art.text(c, "Continue game", 195, 621, 25, SpaceArt.WHITE, true, true);
      }
      art.text(c, "Play solo or with a friend", 195, 698, 18, SpaceArt.MUTED, false, true);
    }

    void drawMode(Canvas c) {
      heading(c, "Let’s play");
      art.text(c, "Who’s joining you?", 195, 129, 27, SpaceArt.WHITE, true, true);
      art.panel(c, 194, 380);
      aliens.draw(c, 0, 85, 278, 48);
      art.text(c, "Play an alien", 157, 267, 25, SpaceArt.WHITE, true, false);
      art.text(c, "Four friendly challengers", 157, 302, 15, SpaceArt.MUTED, false, false);
      art.text(c, "Easy to hard", 157, 331, 16, SpaceArt.CYAN, true, false);
      art.panel(c, 414, 600);
      art.piece(c, 67, 497, 28, playerColor(1), 1, false);
      art.piece(c, 108, 524, 26, playerColor(2), 2, false);
      art.text(c, "Two players", 157, 489, 25, SpaceArt.WHITE, true, false);
      art.text(c, "Play with someone", 157, 524, 17, SpaceArt.MUTED, false, false);
      art.text(c, "on this device.", 157, 549, 17, SpaceArt.MUTED, false, false);
      art.text(c, "A new adventure, your way.", 195, 684, 18, SpaceArt.MUTED, false, true);
    }

    void drawAliens(Canvas c) {
      heading(c, "Meet the aliens");
      art.text(c, "Pick a friendly challenger.", 195, 97, 18, SpaceArt.MUTED, false, true);
      for (int i = 0; i < 4; i++) {
        float top = 125 + i * 119;
        art.panel(c, top, top + 104);

        aliens.draw(c, i, 75, top + 56, 37);
        art.text(c, AlienAI.NAMES[i], 133, top + 34, 25, SpaceArt.WHITE, true, false);
        art.text(c, AlienAI.LEVELS[i], 133, top + 59, 17, AlienArt.COLORS[i], true, false);
        art.text(c, AlienAI.BLURBS[i], 133, top + 84, 14.5f, SpaceArt.MUTED, false, false);
      }
      art.text(c, "Tap an alien to choose your side.", 195, 660, 18, SpaceArt.WHITE, false, true);
      art.text(c, "Every alien plays by your rules.", 195, 734, 17, SpaceArt.MUTED, false, true);
    }

    void drawChoose(Canvas c) {
      heading(c, "Choose your side");
      art.text(c, "Who would you like to be?", 195, 149, 24, SpaceArt.WHITE, true, true);
      art.text(c, "Your pieces start at the bottom.", 195, 185, 19, SpaceArt.MUTED, false, true);
      for (int player = 1; player <= 2; player++) {
        float left = 24 + (player - 1) * 180;
        art.round(c, left, 238, left + 162, 445, 20, 0xff142e50);
        art.stroke(c, left, 238, left + 162, 445, 20, 0xff36567e, 1);
        art.piece(c, left + 81, 310, 46, playerColor(player), player, false);
        art.text(c, "Player " + player, left + 81, 390, 25, SpaceArt.WHITE, true, true);
        art.text(c, "Tap to play", left + 81, 423, 16, SpaceArt.CYAN, false, true);
      }
      art.text(
          c,
          "Player " + game.rules.first + " makes the first move.",
          195,
          503,
          19,
          SpaceArt.MUTED,
          false,
          true);
      art.text(c, "Tap your player to start.", 195, 535, 20, SpaceArt.WHITE, true, true);

      art.text(
          c,
          chosenComputer
              ? "You vs " + AlienAI.NAMES[chosenAlien] + " · " + AlienAI.LEVELS[chosenAlien]
              : "Two players on this device",
          195,
          683,
          17,
          SpaceArt.MUTED,
          false,
          true);
    }

    void drawGame(Canvas c) {
      art.icon(c, "back", 36, 45, 25, SpaceArt.WHITE);
      art.text(c, "Checkers", 203, 65, 40, SpaceArt.WHITE, true, true);
      art.icon(c, "gear", 352, 45, 26, SpaceArt.WHITE);
      art.round(c, 124, 94, 266, 136, 21, 0xff132e4d);
      art.stroke(c, 124, 94, 266, 136, 21, 0xff35557e, 1);
      if (vsComputer) aliens.draw(c, alien, 145, 115, 17);
      else art.piece(c, 145, 114, 15, playerColor(3 - localPlayer), 3 - localPlayer, false);
      art.text(
          c,
          vsComputer ? AlienAI.NAMES[alien] : "Player " + (3 - localPlayer),
          206,
          121,
          20,
          SpaceArt.WHITE,
          true,
          true);
      float size = 45.75f;
      c.save();
      Path boardClip = new Path();
      boardClip.addRoundRect(new RectF(12, 148, 378, 514), 6, 6, Path.Direction.CW);
      c.clipPath(boardClip);
      Set<Integer> movable = movablePieces();
      Set<Integer> targets = new HashSet<>();
      for (Game.Move m : game.legalTurns()) if (m.from == selected) targets.add(m.landings.get(0));
      for (int i = 0; i < 64; i++) {
        int cell = modelCell(i);
        float x = 12 + i % 8 * size, y = 148 + i / 8 * size;
        boolean dark = Game.dark(i);
        art.fill(dark ? 0xff173859 : 0xff537fac);
        art.p.setShader(
            new LinearGradient(
                x,
                y,
                x + size,
                y + size,
                dark ? 0xff1c4066 : 0xff608db9,
                dark ? 0xff112b48 : 0xff48739f,
                Shader.TileMode.CLAMP));
        c.drawRect(x, y, x + size, y + size, art.p);
        art.p.setShader(null);
        if (cell == selected) {
          art.round(c, x + 1, y + 1, x + size - 1, y + size - 1, 5, 0x6621d7e9);
          art.stroke(c, x + 2, y + 2, x + size - 2, y + size - 2, 5, 0xff8df8ff, 2);
        }
        if (targets.contains(cell)) {
          art.fill(cell == hintTo ? 0xffffdc65 : 0xff7cebf2);
          c.drawCircle(x + size / 2, y + size / 2, 7, art.p);
          art.fill(0x337cebf2);
          c.drawCircle(x + size / 2, y + size / 2, 15, art.p);
        }
        int piece = motionBoard == null ? game.board[cell] : motionBoard[cell];
        if (movable.contains(cell) && !moving() && !(dragging && cell == dragFrom)) {
          art.fill(0x3321d7e9);
          c.drawCircle(x + size / 2, y + size / 2 - 1, 21.5f, art.p);
          art.stroke(c, x + 2, y + 2, x + size - 2, y + size - 2, 21, 0xff8df8ff, 1.5f);
        }
        if (piece != 0 && cell != motionFrom && !(dragging && cell == dragFrom))
          art.piece(
              c,
              x + size / 2,
              y + size / 2 - 1,
              18.3f,
              playerColor(Game.owner(piece)),
              Game.owner(piece),
              Math.abs(piece) == 2);
      }
      c.restore();
      art.stroke(c, 12, 148, 378, 514, 6, 0xff3a5d86, 1);
      art.text(c, turnText(), 195, 574, game.result == 0 ? 35 : 31, SpaceArt.WHITE, true, true);
      art.text(c, helpText(), 195, 608, 19, SpaceArt.MUTED, false, true);
      if (game.result != 0)
        art.text(c, "Tap here to play again", 195, 629, 15, SpaceArt.CYAN, false, true);
      String[] types = {"undo", "hint", "colors"}, labels = {"Undo", "Hint", "Colors"};
      for (int i = 0; i < 3; i++) {
        float x = 72 + i * 123;
        art.round(c, x - 29, 645, x + 29, 703, 29, 0xff102947);
        art.stroke(c, x - 29, 645, x + 29, 703, 29, 0xff41678f, 1.5f);
        art.icon(c, types[i], x, 674, 26, SpaceArt.WHITE);
        art.text(c, labels[i], x, 727, 19, SpaceArt.WHITE, true, true);
      }
      drawFloatingPiece(c);
    }

    void drawFloatingPiece(Canvas c) {
      if (!dragging && !moving()) return;
      art.piece(
          c,
          floatingX,
          floatingY,
          dragging ? 21 : 18.3f,
          playerColor(Game.owner(floatingPiece)),
          Game.owner(floatingPiece),
          Math.abs(floatingPiece) == 2);
    }

    void drawColors(Canvas c) {
      heading(c, "Piece colors");
      c.save();
      c.rotate(-16, 195, 202);
      art.fill(0xff407db1);
      art.p.setStyle(Paint.Style.STROKE);
      art.p.setStrokeWidth(2.6f);
      c.drawOval(47, 131, 348, 278, art.p);
      c.restore();
      art.piece(c, 127, 204, 66, playerColor(1), 1, false);
      art.piece(c, 266, 220, 66, playerColor(2), 2, false);
      art.text(c, "Pick a color for each player.", 195, 328, 20, SpaceArt.MUTED, false, true);
      for (int player = 1; player <= 2; player++) {
        float top = 350 + (player - 1) * 142;
        art.panel(c, top, top + 128);
        if (player == 1) {
          art.fill(playerColor(1));
          c.drawPath(art.star(47, top + 31, 15, 5, .48f), art.p);
        } else {
          art.fill(playerColor(2));
          art.p.setStyle(Paint.Style.STROKE);
          art.p.setStrokeWidth(6);
          c.drawCircle(47, top + 31, 11, art.p);
        }
        art.text(c, "Player " + player, 79, top + 39, 24, SpaceArt.WHITE, true, false);
        art.text(c, "More", 322, top + 37, 16, SpaceArt.MUTED, true, true);
        for (int i = 0; i < 5; i++) {
          int index = swatch(player, i);
          float x = 59 + i * 66, y = top + 85;
          int col = PALETTE[index];
          art.fill(col);
          art.p.setShader(
              new LinearGradient(
                  x,
                  y - 24,
                  x,
                  y + 24,
                  art.blend(col, Color.WHITE, .18f),
                  art.blend(col, Color.BLACK, .1f),
                  Shader.TileMode.CLAMP));
          c.drawCircle(x, y, 24, art.p);
          art.p.setShader(null);
          if ((player == 1 ? color1 : color2) == index) {
            art.fill(SpaceArt.WHITE);
            art.p.setStyle(Paint.Style.STROKE);
            art.p.setStrokeWidth(3);
            c.drawCircle(x, y, 28, art.p);
            art.icon(c, "check", x, y, 24, SpaceArt.WHITE);
          }
        }
      }
      art.primary(c, "Let’s play", 682);
    }

    void drawRules(Canvas c) {
      heading(c, "Settings");
      art.text(c, "Sounds and game rules.", 195, 90, 19, SpaceArt.MUTED, false, true);
      art.panel(c, 107, 187);
      art.text(c, "Sound effects", 30, 136, 20, SpaceArt.WHITE, true, false);
      art.text(c, "Changes now. Your game stays.", 30, 164, 14.5f, SpaceArt.MUTED, false, false);
      art.round(c, 312, 125, 356, 150, 13, soundEnabled ? SpaceArt.CYAN : 0xff476080);
      art.fill(soundEnabled ? SpaceArt.WHITE : 0xffb5c4d8);
      c.drawCircle(soundEnabled ? 343 : 325, 137.5f, 9, art.p);
      c.save();
      c.translate(0, 104);
      art.round(c, 24, 107, 190, 155, 24, draft.standard() ? 0xff24566c : 0xff142e50);
      art.stroke(c, 24, 107, 190, 155, 24, 0xff426b91, 1);
      art.text(c, "Standard", 107, 138, 21, SpaceArt.WHITE, true, true);
      art.round(c, 200, 107, 366, 155, 24, 0xff142e50);
      art.stroke(c, 200, 107, 366, 155, 24, 0xff426b91, 1);
      art.text(c, "Practice", 283, 138, 21, SpaceArt.WHITE, true, true);
      String[] titles = {
        "Must take a jump",
        "Finish jump chains",
        "Men jump backward",
        "Flying kings",
        "Take the most pieces",
        "Stop when crowned",
        "Automatic draws"
      };
      String[][] descriptions = {
        {"Take a capture when one is available."},
        {"Keep jumping until no jumps remain."},
        {"Regular pieces may capture backward."},
        {"Kings travel along an open diagonal."},
        {"Choose a path with the most captures."},
        {"A new king waits until its next turn."},
        {"Three repeats or 40 quiet turns each."}
      };
      boolean[] vals = values();
      for (int i = 0; i < 7; i++) {
        float y = 180 + i * 87;
        art.panel(c, y, y + 80);
        art.text(c, titles[i], 30, y + 29, 20, SpaceArt.WHITE, true, false);
        art.text(c, descriptions[i][0], 30, y + 57, 14.5f, SpaceArt.MUTED, false, false);
        art.round(c, 312, y + 18, 356, y + 43, 13, vals[i] ? SpaceArt.CYAN : 0xff476080);
        art.fill(vals[i] ? SpaceArt.WHITE : 0xffb5c4d8);
        c.drawCircle(vals[i] ? 343 : 325, y + 30.5f, 9, art.p);
      }
      art.panel(c, 798, 864);
      art.text(c, "Who starts?", 30, 827, 21, SpaceArt.WHITE, true, false);
      art.text(c, "Player " + draft.first, 307, 839, 20, SpaceArt.CYAN, true, true);
      art.text(
          c,
          draft.standard() ? "American / English checkers" : "Custom house rules",
          195,
          891,
          17,
          SpaceArt.MUTED,
          false,
          true);
      art.primary(c, "Start new game", 904);
      art.text(c, "How to play", 105, 1014, 19, SpaceArt.WHITE, true, true);
      if (!vsComputer) art.text(c, "Agree to draw", 285, 1014, 19, SpaceArt.WHITE, true, true);
      art.text(c, "Rule changes start a fresh game.", 195, 1061, 17, SpaceArt.MUTED, false, true);
      art.text(
          c,
          "Colors and your current game stay until then.",
          195,
          1088,
          15,
          SpaceArt.MUTED,
          false,
          true);
      c.restore();
    }
  }
}
