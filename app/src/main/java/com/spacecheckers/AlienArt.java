package com.spacecheckers;

import android.graphics.*;

/** Four friendly, scalable toy-like portraits sharing Space Play's rounded visual language. */
final class AlienArt {
  private final SpaceArt a;
  static final int[] COLORS = {0xff8be0a4, 0xff68c8f3, 0xffffa28a, 0xffc6a5f0};

  AlienArt(SpaceArt art) {
    a = art;
  }

  void draw(Canvas c, int alien, float x, float y, float radius) {
    c.save();
    c.translate(x, y);
    c.scale(radius / 50, radius / 50);
    int color = COLORS[alien];
    a.fill(0x33000000);
    c.drawOval(-39, 34, 39, 49, a.p);
    // Antennae / fins distinguish silhouettes, even at badge size.
    if (alien == 0) {
      a.fill(color);
      a.p.setStrokeWidth(6);
      a.p.setStrokeCap(Paint.Cap.ROUND);
      c.drawLine(0, -27, -3, -57, a.p);
      c.save();
      c.rotate(-28, -5, -51);
      c.drawOval(-21, -63, -1, -48, a.p);
      c.restore();
    } else if (alien == 1) {
      a.fill(a.blend(color, Color.WHITE, .22f));
      c.drawOval(-56, -10, -27, 25, a.p);
      c.drawOval(27, -10, 56, 25, a.p);
    } else if (alien == 2) {
      a.fill(color);
      a.p.setStrokeWidth(6);
      a.p.setStrokeCap(Paint.Cap.ROUND);
      c.drawLine(-23, -24, -32, -48, a.p);
      c.drawLine(23, -24, 32, -48, a.p);
      c.drawCircle(-32, -49, 7, a.p);
      c.drawCircle(32, -49, 7, a.p);
    } else {
      a.fill(color);
      a.p.setStrokeWidth(5);
      c.drawLine(-18, -26, -24, -51, a.p);
      c.drawLine(18, -26, 24, -51, a.p);
      a.fill(0xffffdf85);
      c.drawPath(a.star(-24, -54, 9, 5, .48f), a.p);
      c.drawPath(a.star(24, -54, 9, 5, .48f), a.p);
    }
    a.fill(color);
    a.p.setShader(
        new RadialGradient(
            -20,
            -24,
            84,
            new int[] {a.blend(color, Color.WHITE, .35f), color, a.blend(color, Color.BLACK, .22f)},
            null,
            Shader.TileMode.CLAMP));
    c.drawRoundRect(-42, -36, 42, 39, alien == 1 ? 32 : 37, alien == 1 ? 32 : 37, a.p);
    a.p.setShader(null);
    // Tiny feet and soft cheeks.
    a.round(c, -31, 29, -9, 44, 10, color);
    a.round(c, 9, 29, 31, 44, 10, color);
    a.fill(0x55ff789e);
    c.drawOval(-33, 9, -17, 19, a.p);
    c.drawOval(17, 9, 33, 19, a.p);
    if (alien == 0) eye(c, 0, -7, 17);
    else if (alien == 2) {
      eye(c, -23, -5, 10);
      eye(c, 0, -11, 12);
      eye(c, 23, -5, 10);
    } else {
      eye(c, -17, -6, 14);
      eye(c, 17, -6, 14);
    }
    a.fill(0xff18354b);
    a.p.setStyle(Paint.Style.STROKE);
    a.p.setStrokeWidth(3);
    a.p.setStrokeCap(Paint.Cap.ROUND);
    c.drawArc(-9, 11, 9, 25, 10, 160, false, a.p);
    if (alien == 3) {
      a.fill(0xff5e568f);
      a.p.setStyle(Paint.Style.STROKE);
      a.p.setStrokeWidth(2.8f);
      c.drawCircle(-17, -6, 16, a.p);
      c.drawCircle(17, -6, 16, a.p);
      c.drawLine(-1, -6, 1, -6, a.p);
    }
    c.restore();
  }

  private void eye(Canvas c, float x, float y, float r) {
    a.fill(0xfff8fcff);
    c.drawOval(x - r, y - r * 1.1f, x + r, y + r * 1.1f, a.p);
    a.fill(0xff18354b);
    c.drawCircle(x + 2, y + 1, r * .53f, a.p);
    a.fill(Color.WHITE);
    c.drawCircle(x - r * .12f, y - r * .22f, r * .2f, a.p);
  }
}
