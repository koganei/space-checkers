package com.spacecheckers;

import android.graphics.*;

/** Resolution-independent artwork shared by every screen. */
final class SpaceArt {
  static final int BG = Color.rgb(6, 26, 51),
      WHITE = Color.rgb(245, 248, 255),
      MUTED = Color.rgb(169, 193, 229),
      CYAN = Color.rgb(33, 215, 233);
  final Paint p = new Paint(3);
  final Typeface regular, bold;
  final Bitmap starSprite, ringSprite;
  float textScale = 1;

  SpaceArt(android.content.res.AssetManager assets) {
    try (java.io.InputStream star = assets.open("piece-star.png");
        java.io.InputStream ring = assets.open("piece-ring.png")) {
      starSprite = BitmapFactory.decodeStream(star);
      ringSprite = BitmapFactory.decodeStream(ring);
    } catch (java.io.IOException e) {
      throw new IllegalStateException("Missing piece artwork", e);
    }
    regular =
        new Typeface.Builder(assets, "Fredoka.ttf")
            .setFontVariationSettings("'wght' 400, 'wdth' 100")
            .build();
    bold =
        new Typeface.Builder(assets, "Fredoka.ttf")
            .setFontVariationSettings("'wght' 600, 'wdth' 100")
            .build();
  }

  int blend(int a, int b, float t) {
    return Color.rgb(
        (int) (Color.red(a) * (1 - t) + Color.red(b) * t),
        (int) (Color.green(a) * (1 - t) + Color.green(b) * t),
        (int) (Color.blue(a) * (1 - t) + Color.blue(b) * t));
  }

  void fill(int color) {
    p.reset();
    p.setAntiAlias(true);
    p.setColor(color);
  }

  void text(
      Canvas c,
      String s,
      float x,
      float y,
      float size,
      int color,
      boolean strong,
      boolean centered) {
    fill(color);
    p.setTypeface(strong ? bold : regular);
    p.setTextSize(size * textScale);
    float available = centered ? Math.min(x, 390 - x) * 2 - 24 : 378 - x;
    if (p.measureText(s) > available) p.setTextSize(p.getTextSize() * available / p.measureText(s));
    p.setTextAlign(centered ? Paint.Align.CENTER : Paint.Align.LEFT);
    c.drawText(s, x, y, p);
  }

  void round(Canvas c, float l, float t, float r, float b, float radius, int color) {
    fill(color);
    c.drawRoundRect(l, t, r, b, radius, radius, p);
  }

  void stroke(Canvas c, float l, float t, float r, float b, float radius, int color, float width) {
    fill(color);
    p.setStyle(Paint.Style.STROKE);
    p.setStrokeWidth(width);
    c.drawRoundRect(l, t, r, b, radius, radius, p);
  }

  Path star(float x, float y, float radius, int points, float inner) {
    Path s = new Path();
    for (int i = 0; i < points * 2; i++) {
      double a = -Math.PI / 2 + i * Math.PI / points;
      float r = (i % 2 == 0 ? radius : radius * inner);
      float xx = x + (float) Math.cos(a) * r, yy = y + (float) Math.sin(a) * r;
      if (i == 0) s.moveTo(xx, yy);
      else s.lineTo(xx, yy);
    }
    s.close();
    return s;
  }

  void background(Canvas c, float h, boolean detail) {
    fill(BG);
    c.drawRect(0, 0, 390, h, p);
    p.setShader(
        new RadialGradient(
            200,
            h * .47f,
            390,
            new int[] {0xff0d2c4c, BG, 0xff031329},
            null,
            Shader.TileMode.CLAMP));
    c.drawRect(0, 0, 390, h, p);
    p.setShader(null);
    java.util.Random rng = new java.util.Random(42);
    for (int i = 0; i < 65; i++) {
      float x = rng.nextFloat() * 390, y = rng.nextFloat() * h;
      if (y < (detail ? 220 : 90) && x > 80 && x < 340) continue;
      fill(i % 5 == 0 ? 0xfff7d765 : 0xff437ca9);
      c.drawCircle(x, y, i % 5 == 0 ? 1.5f : 0.8f, p);
    }
    if (detail) {
      planet(c, 381, 84, 25, true);
      planet(c, 43, 122, 17, false);
    } else {
      planet(c, 33, 80, 22, true);
      planet(c, 348, 129, 17, false);
    }
    planet(c, 3, h + 24, 44, true);
    planet(c, 403, h + 8, 58, true);
    for (float[] a :
        new float[][] {{76, 24, 4}, {365, 85, 3}, {372, h - 210, 4}, {45, h - 140, 4}}) {
      fill(0xffffdc65);
      c.drawPath(star(a[0], a[1], a[2], 5, .48f), p);
    }
  }

  void planet(Canvas c, float x, float y, float r, boolean ring) {
    c.save();
    c.rotate(-22, x, y);
    fill(0xff234c7e);
    p.setShader(
        new LinearGradient(
            x - r,
            y - r,
            x + r,
            y + r,
            new int[] {0xff5791c4, 0xff315c94, 0xff12335f},
            null,
            Shader.TileMode.CLAMP));
    c.drawCircle(x, y, r, p);
    p.setShader(null);
    c.save();
    Path clip = new Path();
    clip.addCircle(x, y, r, Path.Direction.CW);
    c.clipPath(clip);
    if (ring) {
      fill(0x244f9bdb);
      p.setStyle(Paint.Style.STROKE);
      p.setStrokeWidth(r * .16f);
      for (int i = -1; i < 3; i++)
        c.drawOval(x - r * 1.2f, y - r + i * r * .5f, x + r * 1.1f, y - r * .3f + i * r * .5f, p);
    } else {
      fill(0x88416c9e);
      c.drawCircle(x - r * .35f, y - r * .3f, r * .28f, p);
      c.drawCircle(x + r * .35f, y + r * .2f, r * .21f, p);
      fill(0x66213e68);
      c.drawCircle(x - r * .3f, y + r * .5f, r * .2f, p);
    }
    c.restore();
    if (ring) {
      fill(0xff5d9ed7);
      p.setStyle(Paint.Style.STROKE);
      p.setStrokeWidth(r * .13f);
      c.drawArc(x - r * 1.7f, y - r * .28f, x + r * 1.7f, y + r * .38f, 0, 180, false, p);
    }
    c.restore();
  }

  void piece(Canvas c, float x, float y, float r, int color, int player, boolean king) {
    fill(Color.WHITE);
    p.setFilterBitmap(true);
    // Preserve grayscale lighting while changing the base pigment at runtime.
    float shade = .95f;
    p.setColorFilter(
        new ColorMatrixColorFilter(
            new float[] {
              shade,
              0,
              0,
              0,
              Color.red(color) - 171,
              0,
              shade,
              0,
              0,
              Color.green(color) - 171,
              0,
              0,
              shade,
              0,
              Color.blue(color) - 171,
              0,
              0,
              0,
              1,
              0
            }));
    Bitmap sprite = player == 1 && !king ? starSprite : ringSprite;
    float extent = r * 1.12f;
    c.drawBitmap(sprite, null, new RectF(x - extent, y - extent, x + extent, y + extent), p);
    p.setColorFilter(null);
    if (king) {
      Path crown = new Path();
      crown.moveTo(x - r * .45f, y - r * .18f);
      crown.lineTo(x - r * .28f, y + r * .30f);
      crown.lineTo(x + r * .28f, y + r * .30f);
      crown.lineTo(x + r * .45f, y - r * .18f);
      crown.lineTo(x + r * .16f, y - r * .02f);
      crown.lineTo(x, y - r * .40f);
      crown.lineTo(x - r * .16f, y - r * .02f);
      crown.close();
      fill(blend(color, Color.BLACK, .45f));
      p.setStrokeJoin(Paint.Join.ROUND);
      p.setStrokeWidth(r * .08f);
      p.setStyle(Paint.Style.FILL_AND_STROKE);
      c.drawPath(crown, p);
      fill(0xfffff4cf);
      c.drawPath(crown, p);
      // A second gold rim makes kings distinguishable at board scale.
      fill(0xffffe28a);
      p.setStyle(Paint.Style.STROKE);
      p.setStrokeWidth(r * .045f);
      c.drawCircle(x, y - r * .03f, r * .80f, p);
    }
  }

  void panel(Canvas c, float top, float bottom) {
    fill(0xff142e50);
    p.setShader(
        new LinearGradient(16, top, 374, bottom, 0xff193553, 0xff0e2340, Shader.TileMode.CLAMP));
    c.drawRoundRect(16, top, 374, bottom, 20, 20, p);
    p.setShader(null);
    stroke(c, 16, top, 374, bottom, 20, 0xff36567e, 1);
  }

  void primary(Canvas c, String label, float top) {
    fill(CYAN);
    p.setShader(
        new LinearGradient(0, top, 0, top + 62, 0xff5ee9f2, 0xff15c6de, Shader.TileMode.CLAMP));
    c.drawRoundRect(24, top, 366, top + 62, 31, 31, p);
    p.setShader(null);
    stroke(c, 24, top, 366, top + 62, 31, 0xff7bedf4, 1);
    text(c, label, 195, top + 41, 27, BG, true, true);
  }

  void icon(Canvas c, String type, float x, float y, float size, int color) {
    c.save();
    c.translate(x, y);
    c.scale(size / 24, size / 24);
    fill(color);
    p.setStrokeCap(Paint.Cap.ROUND);
    p.setStrokeJoin(Paint.Join.ROUND);
    if (type.equals("gear")) {
      Path gear = star(0, 0, 12, 8, .8f);
      c.drawPath(gear, p);
      fill(BG);
      c.drawCircle(0, 0, 4, p);
    } else if (type.equals("back") || type.equals("undo")) {
      p.setStyle(Paint.Style.STROKE);
      p.setStrokeWidth(3.5f);
      Path q = new Path();
      q.moveTo(-2, -7);
      q.lineTo(-9, 0);
      q.lineTo(-2, 7);
      q.moveTo(-9, 0);
      q.lineTo(7, 0);
      if (type.equals("undo")) {
        q.cubicTo(15, 0, 15, 12, 5, 12);
        q.lineTo(-4, 12);
      }
      c.drawPath(q, p);
    } else if (type.equals("hint")) {
      c.drawCircle(0, -3, 7, p);
      round(c, -3, 4, 3, 10, 1, color);
      p.setStyle(Paint.Style.STROKE);
      p.setStrokeWidth(2);
      for (int i = 0; i < 5; i++) {
        double a = Math.PI + i * Math.PI / 4;
        c.drawLine(
            (float) Math.cos(a) * 10,
            -3 + (float) Math.sin(a) * 10,
            (float) Math.cos(a) * 13,
            -3 + (float) Math.sin(a) * 13,
            p);
      }
    } else if (type.equals("colors")) {
      c.drawCircle(0, 0, 11, p);
      fill(BG);
      for (float[] q : new float[][] {{-5, -4}, {2, -6}, {6, 0}, {-5, 4}})
        c.drawCircle(q[0], q[1], 2, p);
      c.drawCircle(5, 8, 4, p);
    } else if (type.equals("check")) {
      p.setStyle(Paint.Style.STROKE);
      p.setStrokeWidth(3.5f);
      Path q = new Path();
      q.moveTo(-7, 0);
      q.lineTo(-2, 5);
      q.lineTo(8, -6);
      c.drawPath(q, p);
    }
    c.restore();
  }
}
