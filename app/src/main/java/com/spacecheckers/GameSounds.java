package com.spacecheckers;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.Handler;
import android.os.Looper;
import java.util.ArrayList;
import java.util.List;

/** Short bundled sounds; follows media volume and never changes system audio settings. */
final class GameSounds {
  private final SoundPool pool;
  private final Handler handler = new Handler(Looper.getMainLooper());
  private final int[] sounds = new int[4];
  private final boolean[] ready = new boolean[4];
  final List<Integer> streams = new ArrayList<>();
  private boolean enabled;
  private boolean released;

  GameSounds(Context context, boolean enabled) {
    this.enabled = enabled;
    pool =
        new SoundPool.Builder()
            .setMaxStreams(4)
            .setAudioAttributes(
                new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build())
            .build();
    pool.setOnLoadCompleteListener(
        (p, id, status) -> {
          for (int i = 0; i < sounds.length; i++) if (sounds[i] == id) ready[i] = status == 0;
        });
    int[] resources = {R.raw.sfx_move, R.raw.sfx_capture, R.raw.sfx_king, R.raw.sfx_win};
    for (int i = 0; i < resources.length; i++) sounds[i] = pool.load(context, resources[i], 1);
  }

  void setEnabled(boolean value) {
    enabled = value;
    if (!value) stop();
  }

  void play(List<Integer> cues) {
    if (!enabled || released) return;
    int delay = 0;
    int[] spacing = {220, 350, 650, 950};
    for (int cue : cues) {
      handler.postDelayed(
          () -> {
            if (enabled && !released && ready[cue]) {
              int stream = pool.play(sounds[cue], .65f, .65f, 1, 0, 1);
              if (stream != 0) {
                streams.add(stream);
                handler.postDelayed(
                    () -> streams.remove(Integer.valueOf(stream)), spacing[cue] + 50);
              }
            }
          },
          delay);
      delay += spacing[cue];
    }
  }

  boolean allLoaded() {
    for (boolean loaded : ready) if (!loaded) return false;
    return true;
  }

  void stop() {
    handler.removeCallbacksAndMessages(null);
    for (int stream : streams) pool.stop(stream);
    streams.clear();
  }

  void release() {
    stop();
    released = true;
    pool.release();
  }
}
