package com.spacecheckers;

import static org.junit.Assert.*;

import java.io.*;
import java.util.*;
import org.junit.Test;

public class GameTest {
  Game empty(Game.Rules r) {
    Game g = new Game(r);
    Arrays.fill(g.board, 0);
    return g;
  }

  Game empty() {
    return empty(new Game.Rules());
  }

  boolean has(Game g, int from, int to) {
    for (Game.Move m : g.legalTurns()) if (m.from == from && m.landings.get(0) == to) return true;
    return false;
  }

  @Test
  public void initialPosition() {
    Game g = new Game(new Game.Rules());
    int a = 0, b = 0;
    for (int i = 0; i < 64; i++) {
      if (g.board[i] != 0) assertTrue(Game.dark(i));
      if (g.board[i] == 1) a++;
      if (g.board[i] == -1) b++;
    }
    assertEquals(12, a);
    assertEquals(12, b);
    assertEquals(7, g.legalTurns().size());
  }

  @Test
  public void forwardOnly() {
    Game g = empty();
    g.board[42] = 1;
    g.board[1] = -1;
    assertTrue(has(g, 42, 33));
    assertTrue(has(g, 42, 35));
    assertFalse(has(g, 42, 49));
  }

  @Test
  public void opponentDirection() {
    Game.Rules r = new Game.Rules();
    r.first = 2;
    Game g = empty(r);
    g.board[17] = -1;
    assertTrue(has(g, 17, 24));
    assertFalse(has(g, 17, 8));
  }

  @Test
  public void cannotMoveOpponent() {
    Game g = new Game(new Game.Rules());
    int[] before = g.board.clone();
    assertFalse(g.step(17, 24));
    assertArrayEquals(before, g.board);
    assertFalse(g.canUndo());
  }

  @Test
  public void noWrapAcrossEdges() {
    Game g = empty();
    g.board[40] = 1;
    assertTrue(has(g, 40, 33));
    assertFalse(has(g, 40, 31));
  }

  @Test
  public void jumpMandatoryGlobally() {
    Game g = empty();
    g.board[42] = 1;
    g.board[33] = -1;
    g.board[46] = 1;
    assertTrue(has(g, 42, 24));
    assertFalse(has(g, 46, 37));
    assertFalse(has(g, 42, 35));
  }

  @Test
  public void optionalJump() {
    Game.Rules r = new Game.Rules();
    r.mandatory = false;
    Game g = empty(r);
    g.board[42] = 1;
    g.board[33] = -1;
    g.board[46] = 1;
    assertTrue(has(g, 42, 24));
    assertTrue(has(g, 46, 37));
  }

  @Test
  public void cannotJumpOwnPiece() {
    Game g = empty();
    g.board[42] = 1;
    g.board[33] = 1;
    assertFalse(has(g, 42, 24));
  }

  @Test
  public void cannotLandOccupied() {
    Game g = empty();
    g.board[42] = 1;
    g.board[33] = -1;
    g.board[24] = -1;
    assertFalse(has(g, 42, 24));
  }

  @Test
  public void completeChainKeepsPlayer() {
    Game g = empty();
    g.board[42] = 1;
    g.board[33] = -1;
    g.board[17] = -1;
    g.board[7] = -1;
    assertTrue(g.step(42, 24));
    assertTrue(g.inChain());
    assertEquals(1, g.turn);
    assertEquals(0, g.board[33]);
    assertFalse(g.step(24, 33));
    assertTrue(g.step(24, 10));
    assertEquals(2, g.turn);
    assertFalse(g.inChain());
  }

  @Test
  public void undoPartialChain() {
    Game g = empty();
    g.board[42] = 1;
    g.board[33] = -1;
    g.board[17] = -1;
    int[] before = g.board.clone();
    g.step(42, 24);
    assertTrue(g.undo());
    assertArrayEquals(before, g.board);
    assertFalse(g.inChain());
    assertEquals(1, g.turn);
  }

  @Test
  public void undoFullChain() {
    Game g = empty();
    g.board[42] = 1;
    g.board[33] = -1;
    g.board[17] = -1;
    int[] before = g.board.clone();
    g.step(42, 24);
    g.step(24, 10);
    assertTrue(g.undo());
    assertArrayEquals(before, g.board);
    assertEquals(1, g.turn);
    assertEquals(0, g.result);
  }

  @Test
  public void singleJumpHouseRule() {
    Game.Rules r = new Game.Rules();
    r.multiple = false;
    Game g = empty(r);
    g.board[42] = 1;
    g.board[33] = -1;
    g.board[17] = -1;
    g.step(42, 24);
    assertFalse(g.inChain());
    assertEquals(2, g.turn);
    assertEquals(-1, g.board[17]);
  }

  @Test
  public void backwardCaptureOnlyWhenEnabled() {
    Game g = empty();
    g.board[26] = 1;
    g.board[35] = -1;
    assertFalse(has(g, 26, 44));
    Game.Rules r = new Game.Rules();
    r.backward = true;
    Game h = empty(r);
    h.board = g.board.clone();
    assertTrue(has(h, 26, 44));
    assertFalse(has(h, 26, 33));
  }

  @Test
  public void simplePromotion() {
    Game g = empty();
    g.board[10] = 1;
    g.board[55] = -1;
    assertTrue(g.step(10, 1));
    assertEquals(2, g.board[1]);
    assertEquals(2, g.turn);
  }

  @Test
  public void playerTwoPromotion() {
    Game.Rules r = new Game.Rules();
    r.first = 2;
    Game g = empty(r);
    g.board[53] = -1;
    g.board[8] = 1;
    assertTrue(g.step(53, 62));
    assertEquals(-2, g.board[62]);
  }

  @Test
  public void crownStopsCapture() {
    Game g = empty();
    g.board[17] = 1;
    g.board[10] = -1;
    g.board[12] = -1;
    g.step(17, 3);
    assertEquals(2, g.board[3]);
    assertFalse(g.inChain());
    assertEquals(2, g.turn);
    assertEquals(-1, g.board[12]);
  }

  @Test
  public void crownCanContinueHouseRule() {
    Game.Rules r = new Game.Rules();
    r.crownEnds = false;
    Game g = empty(r);
    g.board[17] = 1;
    g.board[10] = -1;
    g.board[12] = -1;
    g.step(17, 3);
    assertTrue(g.inChain());
    assertTrue(g.step(3, 21));
    assertEquals(2, g.board[21]);
    assertEquals(1, g.result);
  }

  @Test
  public void shortKingsMoveBothWays() {
    Game g = empty();
    g.board[35] = 2;
    g.board[1] = -1;
    assertTrue(has(g, 35, 26));
    assertTrue(has(g, 35, 44));
    assertFalse(has(g, 35, 17));
  }

  @Test
  public void kingsJumpBackward() {
    Game g = empty();
    g.board[26] = 2;
    g.board[35] = -1;
    assertTrue(has(g, 26, 44));
  }

  @Test
  public void flyingKingSlides() {
    Game.Rules r = new Game.Rules();
    r.flying = true;
    Game g = empty(r);
    g.board[35] = 2;
    assertTrue(has(g, 35, 8));
    assertTrue(has(g, 35, 62));
  }

  @Test
  public void flyingKingMultipleLandings() {
    Game.Rules r = new Game.Rules();
    r.flying = true;
    Game g = empty(r);
    g.board[49] = 2;
    g.board[35] = -1;
    assertTrue(has(g, 49, 28));
    assertTrue(has(g, 49, 21));
    assertTrue(has(g, 49, 14));
    assertTrue(has(g, 49, 7));
    assertFalse(has(g, 49, 42));
  }

  @Test
  public void flyingCannotCrossTwoPieces() {
    Game.Rules r = new Game.Rules();
    r.flying = true;
    Game g = empty(r);
    g.board[49] = 2;
    g.board[35] = -1;
    g.board[28] = -1;
    assertFalse(has(g, 49, 21));
  }

  @Test
  public void maximumCaptureConsidersWholePath() {
    Game.Rules r = new Game.Rules();
    r.maximum = true;
    Game g = empty(r);
    g.board[42] = 1;
    g.board[33] = -1;
    g.board[17] = -1;
    g.board[35] = -1;
    assertTrue(has(g, 42, 24));
    assertFalse(has(g, 42, 28));
  }

  @Test
  public void standardAllowsShorterCapture() {
    Game g = empty();
    g.board[42] = 1;
    g.board[33] = -1;
    g.board[17] = -1;
    g.board[35] = -1;
    assertTrue(has(g, 42, 24));
    assertTrue(has(g, 42, 28));
  }

  @Test
  public void maximumFiltersBranchAfterFirstHop() {
    Game.Rules r = new Game.Rules();
    r.maximum = true;
    Game g = empty(r);
    g.board[60] = 1;
    g.board[51] = -1;
    g.board[33] = -1;
    g.board[35] = -1;
    g.board[17] = -1;
    assertTrue(g.step(60, 42));
    assertTrue(has(g, 42, 24));
    assertFalse(has(g, 42, 28));
  }

  @Test
  public void winByCapture() {
    Game g = empty();
    g.board[42] = 1;
    g.board[33] = -1;
    g.step(42, 24);
    assertEquals(1, g.result);
    assertTrue(g.legalTurns().isEmpty());
    assertFalse(g.step(24, 17));
  }

  @Test
  public void winByBlocking() {
    Game g = empty();
    g.board[10] = 1;
    g.board[56] = -1;
    g.step(10, 1);
    assertEquals(1, g.result);
  }

  @Test
  public void undoRegularTurn() {
    Game g = new Game(new Game.Rules());
    int[] b = g.board.clone();
    g.step(40, 33);
    g.undo();
    assertArrayEquals(b, g.board);
    assertEquals(1, g.turn);
  }

  @Test
  public void quietCounterResetsForMan() {
    Game g = empty();
    g.quiet = 79;
    g.board[42] = 1;
    g.board[7] = -2;
    g.step(42, 33);
    assertEquals(0, g.quiet);
    assertEquals(0, g.result);
  }

  @Test
  public void eightyQuietPliesDraw() {
    Game g = empty();
    g.quiet = 79;
    g.board[56] = 2;
    g.board[7] = -2;
    g.step(56, 49);
    assertEquals(3, g.result);
  }

  @Test
  public void captureResetsQuietCounter() {
    Game g = empty();
    g.quiet = 79;
    g.board[42] = 2;
    g.board[33] = -1;
    g.board[7] = -2;
    g.step(42, 24);
    assertEquals(0, g.quiet);
    assertEquals(0, g.result);
  }

  @Test
  public void disablingDraws() {
    Game.Rules r = new Game.Rules();
    r.draws = false;
    Game g = empty(r);
    g.quiet = 79;
    g.board[56] = 2;
    g.board[7] = -2;
    g.step(56, 49);
    assertEquals(0, g.result);
  }

  @Test
  public void repetitionWithSameTurn() {
    Game g = empty();
    g.board[56] = 2;
    g.board[7] = -2;
    for (int cycle = 0; cycle < 3 && g.result == 0; cycle++) {
      assertTrue(g.step(56, 49));
      if (g.result != 0) break;
      assertTrue(g.step(7, 14));
      assertTrue(g.step(49, 56));
      assertTrue(g.step(14, 7));
    }
    assertEquals(3, g.result);
  }

  @Test
  public void rulesCopiedNotAliased() {
    Game.Rules r = new Game.Rules();
    Game g = new Game(r);
    r.flying = true;
    assertFalse(g.rules.flying);
  }

  @Test
  public void agreeAndUndoDraw() {
    Game g = new Game(new Game.Rules());
    g.agreeDraw();
    assertEquals(3, g.result);
    g.undo();
    assertEquals(0, g.result);
  }

  @Test
  public void noDrawDuringChain() {
    Game g = empty();
    g.board[42] = 1;
    g.board[33] = -1;
    g.board[17] = -1;
    g.step(42, 24);
    g.agreeDraw();
    assertEquals(0, g.result);
    assertTrue(g.inChain());
  }

  @Test
  public void hintIsLegalAndDoesNotMutate() {
    Game g = new Game(new Game.Rules());
    int[] b = g.board.clone();
    Game.Move m = g.hint();
    assertTrue(has(g, m.from, m.landings.get(0)));
    assertArrayEquals(b, g.board);
    assertFalse(g.canUndo());
  }

  @Test
  public void serializePendingChainAndUndo() throws Exception {
    Game g = empty();
    g.board[42] = 1;
    g.board[33] = -1;
    g.board[17] = -1;
    g.step(42, 24);
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    new ObjectOutputStream(bytes).writeObject(g);
    Game h =
        (Game) new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray())).readObject();
    assertTrue(h.inChain());
    assertTrue(h.step(24, 10));
    assertEquals(1, h.result);
    assertTrue(h.undo());
    assertEquals(1, h.board[42]);
    assertEquals(-1, h.board[33]);
  }

  @Test
  public void randomizedAllRuleCombinations() {
    Random random = new Random(91);
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
      for (int ply = 0; ply < 100 && g.result == 0; ply++) {
        List<Game.Move> moves = g.legalTurns();
        assertFalse("mask " + mask, moves.isEmpty());
        Game.Move move = moves.get(random.nextInt(moves.size()));
        int before = pieces(g), from = move.from;
        for (int to : move.landings) {
          assertTrue(g.step(from, to));
          from = to;
        }
        assertFalse(g.inChain());
        assertEquals(before - move.count(), pieces(g));
        for (int i = 0; i < 64; i++) if (g.board[i] != 0) assertTrue(Game.dark(i));
      }
    }
  }

  @Test
  public void longHouseGameKeepsBoundedUndo() {
    Game.Rules r = new Game.Rules();
    r.draws = false;
    Game g = empty(r);
    g.board[56] = 2;
    g.board[7] = -2;
    for (int i = 0; i < 150; i++) {
      assertTrue(g.step(56, 49));
      assertTrue(g.step(7, 14));
      assertTrue(g.step(49, 56));
      assertTrue(g.step(14, 7));
    }
    int undos = 0;
    while (g.undo()) undos++;
    assertEquals(512, undos);
  }

  int pieces(Game g) {
    int n = 0;
    for (int p : g.board) if (p != 0) n++;
    return n;
  }
}
