package com.spacecheckers;

import java.util.ArrayList;
import java.util.List;

/** Sounds describe a successful landing; invalid gestures and Undo never call this. */
final class SoundCue {
  static final int MOVE = 0, CAPTURE = 1, KING = 2, WIN = 3;

  static List<Integer> landing(int[] before, int from, int[] after, int to, int result) {
    List<Integer> cues = new ArrayList<>();
    int oldCount = 0, newCount = 0;
    for (int p : before) if (p != 0) oldCount++;
    for (int p : after) if (p != 0) newCount++;
    cues.add(newCount < oldCount ? CAPTURE : MOVE);
    if (Math.abs(before[from]) == 1 && Math.abs(after[to]) == 2) cues.add(KING);
    if (result == 1 || result == 2) cues.add(WIN);
    return cues;
  }
}
