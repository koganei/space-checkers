package com.spacecheckers;

import static org.junit.Assert.*;

import java.util.Arrays;
import org.junit.Test;

public class SoundCueTest {
  @Test
  public void normalMove() {
    int[] before = new int[64], after = new int[64];
    before[40] = 1;
    after[33] = 1;
    assertEquals(Arrays.asList(SoundCue.MOVE), SoundCue.landing(before, 40, after, 33, 0));
  }

  @Test
  public void captureCrownAndWinAreOrdered() {
    int[] before = new int[64], after = new int[64];
    before[17] = 1;
    before[10] = -1;
    after[3] = 2;
    assertEquals(
        Arrays.asList(SoundCue.CAPTURE, SoundCue.KING, SoundCue.WIN),
        SoundCue.landing(before, 17, after, 3, 1));
  }

  @Test
  public void otherPlayerPromotion() {
    int[] before = new int[64], after = new int[64];
    before[49] = -1;
    after[56] = -2;
    assertEquals(
        Arrays.asList(SoundCue.MOVE, SoundCue.KING), SoundCue.landing(before, 49, after, 56, 0));
  }

  @Test
  public void existingKingAndDrawDoNotCelebrate() {
    int[] before = new int[64], after = new int[64];
    before[49] = -2;
    after[56] = -2;
    assertEquals(Arrays.asList(SoundCue.MOVE), SoundCue.landing(before, 49, after, 56, 3));
  }

  @Test
  public void blockedOpponentWinWithoutCapture() {
    int[] before = new int[64], after = new int[64];
    before[40] = 1;
    after[33] = 1;
    assertEquals(
        Arrays.asList(SoundCue.MOVE, SoundCue.WIN), SoundCue.landing(before, 40, after, 33, 2));
  }
}
