package com.spacecheckers;

/** A fixed view of the board from the chosen player's side; never changes with the turn. */
final class BoardPerspective {
  static int cell(int visibleCell, int bottomPlayer) {
    if (visibleCell < 0 || visibleCell >= 64 || (bottomPlayer != 1 && bottomPlayer != 2))
      throw new IllegalArgumentException("Invalid board perspective");
    return bottomPlayer == 1 ? visibleCell : 63 - visibleCell;
  }
}
