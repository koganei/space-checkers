package com.spacecheckers;

import static org.junit.Assert.*;

import java.util.*;
import org.junit.Test;

public class AlienAITest {
  Game empty() {
    Game g = new Game(new Game.Rules());
    Arrays.fill(g.board, 0);
    return g;
  }

  boolean legal(Game g, Game.Move m) {
    for (Game.Move x : g.legalTurns())
      if (x.from == m.from && x.landings.equals(m.landings) && x.captures.equals(m.captures))
        return true;
    return false;
  }

  @Test
  public void everyAlienReturnsLegalMoveWithoutMutatingSource() {
    for (int level = 0; level < 4; level++) {
      Game g = new Game(new Game.Rules());
      int[] before = g.board.clone();
      Game.Move m = new AlienAI(9).choose(g, level);
      assertNotNull(m);
      assertTrue(legal(g, m));
      assertArrayEquals(before, g.board);
      assertFalse(g.canUndo());
      assertEquals(1, g.turn);
    }
  }

  @Test
  public void allAliensFinishForcedChain() {
    for (int level = 0; level < 4; level++) {
      Game g = empty();
      g.board[42] = 1;
      g.board[33] = -1;
      g.board[17] = -1;
      Game.Move m = new AlienAI(9).choose(g, level);
      assertEquals(Arrays.asList(24, 10), m.landings);
      assertEquals(1, AlienAI.apply(g, m).result);
    }
  }

  @Test
  public void resumedPartialChainIsLegal() {
    Game g = empty();
    g.board[42] = 1;
    g.board[33] = -1;
    g.board[17] = -1;
    g.step(42, 24);
    Game.Move m = new AlienAI(8).choose(g, 3);
    assertEquals(24, m.from);
    assertEquals(Arrays.asList(10), m.landings);
    assertTrue(g.inChain());
    assertEquals(1, AlienAI.apply(g, m).result);
  }

  @Test
  public void lookaheadAvoidsImmediateRecapture() {
    Game g = empty();
    g.board[44] = 1;
    g.board[26] = -1;
    g.board[7] = -1;
    Game.Move medium = new AlienAI(1).choose(g, 1);
    assertEquals(Integer.valueOf(35), medium.landings.get(0));
    for (int level = 2; level < 4; level++) {
      Game.Move strong = new AlienAI(1).choose(g, level);
      assertEquals(Integer.valueOf(37), strong.landings.get(0));
    }
  }

  @Test
  public void easyExploresMoreThanOneLegalChoice() {
    Set<Integer> choices = new HashSet<>();
    Game g = new Game(new Game.Rules());
    for (int i = 0; i < 30; i++) choices.add(new AlienAI(i).choose(g, 0).from);
    assertTrue(choices.size() > 1);
  }

  @Test
  public void respectsEveryHouseRuleCombination() {
    for (int mask = 0; mask < 128; mask++) {
      Game.Rules r = new Game.Rules();
      r.mandatory = (mask & 1) != 0;
      r.multiple = (mask & 2) != 0;
      r.backward = (mask & 4) != 0;
      r.flying = (mask & 8) != 0;
      r.maximum = (mask & 16) != 0;
      r.crownEnds = (mask & 32) != 0;
      r.draws = (mask & 64) != 0;
      Game g = new Game(r);
      Arrays.fill(g.board, 0);
      g.board[42] = 2;
      g.board[33] = -1;
      g.board[17] = -2;
      g.board[46] = 1;
      g.board[55] = -1;
      Game.Move move = new AlienAI(mask).choose(g, 1);
      assertTrue("mask " + mask, legal(g, move));
      Game next = AlienAI.apply(g, move);
      assertFalse(next.inChain());
    }
  }

  @Test
  public void bothPlayersAndAllLevelsCanPlayFullTurns() {
    Game g = new Game(new Game.Rules());
    for (int i = 0; i < 24 && g.result == 0; i++) {
      Game.Move m = new AlienAI(i).choose(g, i % 4);
      assertTrue(legal(g, m));
      g = AlienAI.apply(g, m);
      assertFalse(g.inChain());
    }
  }

  @Test
  public void noMoveAfterGameOver() {
    Game g = new Game(new Game.Rules());
    g.agreeDraw();
    assertNull(new AlienAI(1).choose(g, 3));
  }

  @Test
  public void isolatedCopyPreservesDrawCounter() {
    Game g = empty();
    g.board[56] = 2;
    g.board[7] = -2;
    g.quiet = 79;
    Game.Move m = new AlienAI(1).choose(g, 3);
    assertEquals(3, AlienAI.apply(g, m).result);
    assertEquals(0, g.result);
    assertEquals(79, g.quiet);
  }

  @Test
  public void interruptedSearchReturnsPromptly() {
    Thread.currentThread().interrupt();
    try {
      long start = System.nanoTime();
      assertNotNull(new AlienAI(1).choose(new Game(new Game.Rules()), 3));
      assertTrue(System.nanoTime() - start < 1_000_000_000L);
    } finally {
      Thread.interrupted();
    }
  }

  @Test(expected = IllegalArgumentException.class)
  public void invalidAlienRejected() {
    new AlienAI(1).choose(new Game(new Game.Rules()), 4);
  }
}
