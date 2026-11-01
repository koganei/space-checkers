package com.spacecheckers;

import java.io.Serializable;
import java.util.*;

/**
 * Pure Java rules engine. A turn is a complete path, so maximum-capture rules are enforced before
 * and throughout a branching jump, not greedily per hop.
 */
public final class Game implements Serializable {
  private static final long serialVersionUID = 1L;

  public static final class Rules implements Serializable {
    private static final long serialVersionUID = 1L;
    public boolean mandatory = true,
        multiple = true,
        backward = false,
        flying = false,
        maximum = false,
        crownEnds = true,
        draws = true;
    public int first = 1;

    public Rules copy() {
      Rules r = new Rules();
      r.mandatory = mandatory;
      r.multiple = multiple;
      r.backward = backward;
      r.flying = flying;
      r.maximum = maximum;
      r.crownEnds = crownEnds;
      r.draws = draws;
      r.first = first;
      return r;
    }

    public boolean standard() {
      return mandatory
          && multiple
          && !backward
          && !flying
          && !maximum
          && crownEnds
          && draws
          && first == 1;
    }
  }

  public static final class Move implements Serializable {
    private static final long serialVersionUID = 1L;
    public final int from;
    public final List<Integer> landings, captures;

    Move(int from, List<Integer> landings, List<Integer> captures) {
      this.from = from;
      this.landings = new ArrayList<>(landings);
      this.captures = new ArrayList<>(captures);
    }

    public int count() {
      int n = 0;
      for (int i : captures) if (i >= 0) n++;
      return n;
    }

    Move tail() {
      return new Move(
          landings.get(0),
          landings.subList(1, landings.size()),
          captures.subList(1, captures.size()));
    }
  }

  private static final class Snapshot implements Serializable {
    private static final long serialVersionUID = 1L;
    int[] board;
    int turn, quiet, result;
    HashMap<String, Integer> repetitions;

    Snapshot(Game g) {
      board = g.board.clone();
      turn = g.turn;
      quiet = g.quiet;
      result = g.result;
      repetitions = new HashMap<>(g.repetitions);
    }
  }

  // 0 empty, +/-1 man, +/-2 king. Positive is Player 1 (bottom, moves up).
  public int[] board = new int[64];
  public int turn = 1, result = 0, quiet = 0; // result: 0 active, 1/2 winner, 3 draw
  public final Rules rules;
  private List<Move> pending = new ArrayList<>();
  private final ArrayList<Snapshot> history = new ArrayList<>();
  private HashMap<String, Integer> repetitions = new HashMap<>();
  private boolean turnProgress;

  public Game(Rules rules) {
    this.rules = rules.copy();
    turn = rules.first;
    for (int i = 0; i < 64; i++)
      if (dark(i)) {
        if (i / 8 < 3) board[i] = -1;
        else if (i / 8 > 4) board[i] = 1;
      }
    repetitions.put(key(), 1);
  }

  /** Independent search state. Undo history is intentionally not copied. */
  public Game searchCopy() {
    Game copy = new Game(rules);
    copy.board = board.clone();
    copy.turn = turn;
    copy.result = result;
    copy.quiet = quiet;
    copy.pending = new ArrayList<>(pending);
    copy.repetitions = new HashMap<>(repetitions);
    copy.turnProgress = turnProgress;
    return copy;
  }

  public static boolean dark(int i) {
    return i >= 0 && i < 64 && (i / 8 + i % 8) % 2 == 1;
  }

  public static int owner(int piece) {
    return piece > 0 ? 1 : piece < 0 ? 2 : 0;
  }

  private static boolean inside(int r, int c) {
    return r >= 0 && r < 8 && c >= 0 && c < 8;
  }

  private static boolean crowns(int piece, int cell) {
    return Math.abs(piece) == 1 && (piece > 0 ? cell / 8 == 0 : cell / 8 == 7);
  }

  public boolean inChain() {
    return !pending.isEmpty();
  }

  public boolean canUndo() {
    return !history.isEmpty();
  }

  public int moveCount() {
    return history.size();
  }

  public List<Move> legalTurns() {
    if (result != 0) return Collections.emptyList();
    if (inChain()) return new ArrayList<>(pending);
    List<Move> jumps = new ArrayList<>(), slides = new ArrayList<>();
    for (int i = 0; i < 64; i++)
      if (owner(board[i]) == turn) {
        jumps.addAll(jumpPaths(board, i, i, new ArrayList<>(), new ArrayList<>()));
        int p = board[i];
        for (int dr : new int[] {-1, 1})
          for (int dc : new int[] {-1, 1}) {
            if (Math.abs(p) == 1 && dr != (p > 0 ? -1 : 1)) continue;
            int r = i / 8 + dr, c = i % 8 + dc;
            while (inside(r, c) && board[r * 8 + c] == 0) {
              slides.add(new Move(i, Arrays.asList(r * 8 + c), Arrays.asList(-1)));
              if (Math.abs(p) != 2 || !rules.flying) break;
              r += dr;
              c += dc;
            }
          }
      }
    if (rules.maximum && !jumps.isEmpty()) {
      int max = 0;
      for (Move m : jumps) max = Math.max(max, m.count());
      final int n = max;
      jumps.removeIf(m -> m.count() < n);
    }
    if (!jumps.isEmpty() && rules.mandatory) return jumps;
    jumps.addAll(slides);
    return jumps;
  }

  private List<Move> jumpPaths(
      int[] b, int origin, int cell, List<Integer> path, List<Integer> taken) {
    List<Move> out = new ArrayList<>();
    int p = b[cell];
    for (int dr : new int[] {-1, 1})
      for (int dc : new int[] {-1, 1}) {
        if (Math.abs(p) == 1 && !rules.backward && dr != (p > 0 ? -1 : 1)) continue;
        int r = cell / 8 + dr, c = cell % 8 + dc;
        boolean fly = Math.abs(p) == 2 && rules.flying;
        if (fly)
          while (inside(r, c) && b[r * 8 + c] == 0) {
            r += dr;
            c += dc;
          }
        if (!inside(r, c) || b[r * 8 + c] == 0 || owner(b[r * 8 + c]) == owner(p)) continue;
        int victim = r * 8 + c;
        r += dr;
        c += dc;
        while (inside(r, c) && b[r * 8 + c] == 0) {
          int target = r * 8 + c;
          int[] next = b.clone();
          next[cell] = 0;
          next[victim] = 0;
          boolean crown = crowns(p, target);
          next[target] = crown ? p * 2 : p;
          List<Integer> np = new ArrayList<>(path), nt = new ArrayList<>(taken);
          np.add(target);
          nt.add(victim);
          List<Move> more =
              rules.multiple && !(crown && rules.crownEnds)
                  ? jumpPaths(next, origin, target, np, nt)
                  : Collections.emptyList();
          if (more.isEmpty()) out.add(new Move(origin, np, nt));
          else out.addAll(more);
          if (!fly) break;
          r += dr;
          c += dc;
        }
      }
    return out;
  }

  /** Executes exactly one legal landing. False leaves all state unchanged. */
  public boolean step(int from, int to) {
    List<Move> matches = new ArrayList<>();
    for (Move m : legalTurns()) if (m.from == from && m.landings.get(0) == to) matches.add(m);
    if (matches.isEmpty()) return false;
    if (!inChain()) {
      if (history.size() == 512) history.remove(0);
      history.add(new Snapshot(this));
      turnProgress = Math.abs(board[from]) == 1;
    }
    Move chosen = matches.get(0);
    int p = board[from];
    board[from] = 0;
    int captured = chosen.captures.get(0);
    if (captured >= 0) {
      board[captured] = 0;
      turnProgress = true;
    }
    board[to] = crowns(p, to) ? p * 2 : p;
    pending = new ArrayList<>();
    for (Move m : matches) if (m.landings.size() > 1) pending.add(m.tail());
    if (!inChain()) {
      quiet = turnProgress ? 0 : quiet + 1;
      turn = 3 - turn;
      if (turnProgress || !rules.draws) repetitions.clear();
      String k = key();
      repetitions.put(k, repetitions.getOrDefault(k, 0) + 1);
      if (legalTurns().isEmpty()) result = 3 - turn;
      else if (rules.draws && (quiet >= 80 || repetitions.get(k) >= 3)) result = 3;
    }
    return true;
  }

  public boolean undo() {
    if (history.isEmpty()) return false;
    Snapshot s = history.remove(history.size() - 1);
    board = s.board;
    turn = s.turn;
    quiet = s.quiet;
    result = s.result;
    repetitions = s.repetitions;
    pending.clear();
    return true;
  }

  public void agreeDraw() {
    if (result == 0 && !inChain()) {
      history.add(new Snapshot(this));
      result = 3;
    }
  }

  private String key() {
    return Arrays.toString(board) + ":" + turn;
  }

  /** Deterministic teaching hint: favor captures and promotion, then the center. */
  public Move hint() {
    Move best = null;
    int value = Integer.MIN_VALUE;
    for (Move m : legalTurns()) {
      int dest = m.landings.get(m.landings.size() - 1);
      int v =
          m.count() * 100
              + (crowns(board[m.from], dest) ? 40 : 0)
              + 7
              - Math.abs(7 - 2 * (dest % 8));
      if (v > value) {
        value = v;
        best = m;
      }
    }
    return best;
  }
}
