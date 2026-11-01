package com.spacecheckers;

import java.util.*;

/** Offline, bounded search over complete legal turns, including every house-rule combination. */
public final class AlienAI {
  public static final String[] NAMES = {"Pip", "Bloop", "Ziggy", "Luma"};
  public static final String[] LEVELS = {"Easy", "Medium", "Tricky", "Hard"};
  public static final String[] BLURBS = {
    "A curious little beginner.",
    "Learning clever little tricks.",
    "Always thinking ahead.",
    "A tiny master strategist."
  };
  private static final int[] DEPTHS = {0, 1, 3, 6}, BUDGETS = {0, 2000, 8000, 40000};
  private final Random random;
  private long deadline;
  private int nodes, budget, rootPlayer;

  private static final class Stop extends RuntimeException {
    private static final long serialVersionUID = 1L;
  }

  public AlienAI(long seed) {
    random = new Random(seed);
  }

  public Game.Move choose(Game state, int difficulty) {
    if (difficulty < 0 || difficulty > 3) throw new IllegalArgumentException("Unknown alien");
    Game g = state.searchCopy();
    List<Game.Move> choices = g.legalTurns();
    if (choices.isEmpty()) return null;
    Collections.shuffle(choices, random);
    if (difficulty == 0) return choices.get(0);
    rootPlayer = g.turn;
    nodes = 0;
    budget = BUDGETS[difficulty];
    deadline = System.nanoTime() + 800_000_000L;
    Game.Move best = choices.get(0);
    for (int depth = 1; depth <= DEPTHS[difficulty]; depth++) {
      Game.Move iteration = best;
      int top = Integer.MIN_VALUE;
      try {
        for (Game.Move move : choices) {
          checkBudget();
          Game next = apply(g, move);
          int score = search(next, depth - 1, -200000, 200000);
          if (score > top) {
            top = score;
            iteration = move;
          }
        }
        best = iteration;
        // Search the previous principal move first in the next iteration.
        choices.remove(best);
        choices.add(0, best);
      } catch (Stop stop) {
        break;
      }
    }
    return best;
  }

  private void checkBudget() {
    if (Thread.currentThread().isInterrupted() || ++nodes > budget || System.nanoTime() > deadline)
      throw new Stop();
  }

  private int search(Game g, int depth, int alpha, int beta) {
    checkBudget();
    if (g.result != 0)
      return g.result == 3 ? 0 : (g.result == rootPlayer ? 100000 + depth : -100000 - depth);
    if (depth == 0) return evaluate(g, rootPlayer);
    List<Game.Move> moves = g.legalTurns();
    moves.sort((a, b) -> Integer.compare(b.count(), a.count()));
    boolean maximize = g.turn == rootPlayer;
    int best = maximize ? -200000 : 200000;
    for (Game.Move m : moves) {
      int score = search(apply(g, m), depth - 1, alpha, beta);
      if (maximize) {
        best = Math.max(best, score);
        alpha = Math.max(alpha, best);
      } else {
        best = Math.min(best, score);
        beta = Math.min(beta, best);
      }
      if (beta <= alpha) break;
    }
    return best;
  }

  static Game apply(Game source, Game.Move move) {
    Game next = source.searchCopy();
    int from = move.from;
    for (int to : move.landings) {
      if (!next.step(from, to)) throw new IllegalStateException("Search generated an illegal move");
      from = to;
    }
    return next;
  }

  static int evaluate(Game g, int player) {
    int total = 0;
    for (int i = 0; i < 64; i++) {
      int piece = g.board[i];
      if (piece == 0) continue;
      int own = Game.owner(piece), row = i / 8, col = i % 8;
      int value = Math.abs(piece) == 2 ? 180 : 100 + (own == 1 ? 7 - row : row) * 4;
      value += Math.min(col, 7 - col) * 3;
      if (Math.abs(piece) == 1 && (own == 1 ? row == 7 : row == 0)) value += 5;
      total += (own == player ? value : -value);
    }
    return total;
  }
}
