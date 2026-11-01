package com.spacecheckers;

import static org.junit.Assert.*;

import org.junit.Test;

public class BoardPerspectiveTest {
  @Test
  public void mappingIsReversibleAndPreservesPlayableSquares() {
    for (int player = 1; player <= 2; player++)
      for (int i = 0; i < 64; i++) {
        int model = BoardPerspective.cell(i, player);
        assertEquals(i, BoardPerspective.cell(model, player));
        assertEquals(Game.dark(i), Game.dark(model));
      }
  }

  @Test
  public void chosenTeamOccupiesBottomRows() {
    Game g = new Game(new Game.Rules());
    for (int player = 1; player <= 2; player++)
      for (int i = 40; i < 64; i++)
        if (Game.dark(i))
          assertEquals(player, Game.owner(g.board[BoardPerspective.cell(i, player)]));
  }

  @Test
  public void bothTeamsMoveUpFromTheirOwnView() {
    assertTrue(BoardPerspective.cell(33, 1) / 8 < BoardPerspective.cell(40, 1) / 8);
    assertTrue(BoardPerspective.cell(24, 2) / 8 < BoardPerspective.cell(17, 2) / 8);
  }

  @Test(expected = IllegalArgumentException.class)
  public void invalidSideRejected() {
    BoardPerspective.cell(0, 0);
  }
}
