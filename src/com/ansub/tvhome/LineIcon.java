package com.ansub.tvhome;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

// Tiny white outline icons on a 24x24 grid, so the app needs no image resources.
// Each icon is a list of ops: 0=moveTo x y, 1=lineTo x y, 2=close, 3=circle cx cy r,
// 4=arc cx cy r startDegrees sweepDegrees (0 degrees is 3 o'clock, going clockwise).
final class LineIcon extends Drawable {
    static final int[] PLAY = {0, 60, 45, 1, 60, 195, 1, 190, 120, 2};
    static final int[] HDMI = {0, 30, 90, 1, 210, 90, 1, 210, 130, 1, 185, 160, 1, 55, 160, 1, 30, 130, 2,
            0, 80, 120, 1, 160, 120};
    // A gear: eight teeth round a ring, and the hole in the middle.
    static final int[] SETTINGS = {0, 100, 49, 1, 105, 23, 1, 135, 23, 1, 140, 49, 1, 156, 55, 1, 178, 41,
            1, 199, 62, 1, 185, 84, 1, 191, 100, 1, 217, 105, 1, 217, 135, 1, 191, 140, 1, 185, 156, 1, 199, 178,
            1, 178, 199, 1, 156, 185, 1, 140, 191, 1, 135, 217, 1, 105, 217, 1, 100, 191, 1, 84, 185, 1, 62, 199,
            1, 41, 178, 1, 55, 156, 1, 49, 140, 1, 23, 135, 1, 23, 105, 1, 49, 100, 1, 55, 84, 1, 41, 62,
            1, 62, 41, 1, 84, 55, 2, 3, 120, 120, 30};
    static final int[] PLUS = {0, 120, 50, 1, 120, 190, 0, 50, 120, 1, 190, 120};
    static final int[] MORE = {3, 50, 120, 12, 3, 120, 120, 12, 3, 190, 120, 12};
    // Weather, by the clock.
    static final int[] SUN = {3, 120, 120, 40, 0, 186, 120, 1, 212, 120, 0, 167, 167, 1, 185, 185, 0, 120, 186,
            1, 120, 212, 0, 73, 167, 1, 55, 185, 0, 54, 120, 1, 28, 120, 0, 73, 73, 1, 55, 55, 0, 120, 54, 1, 120, 28,
            0, 167, 73, 1, 185, 55};
    static final int[] MOON = {4, 114, 126, 84, 14, 250, 4, 160, 86, 70, 60, 159};
    static final int[] CLOUD = {0, 80, 170, 1, 166, 170, 4, 80, 146, 24, 90, 190, 4, 122, 120, 38, 176, 176,
            4, 166, 142, 28, 257, 193};
    static final int[] RAIN = {0, 80, 148, 1, 166, 148, 4, 80, 124, 24, 90, 190, 4, 122, 98, 38, 176, 176,
            4, 166, 120, 28, 257, 193, 0, 92, 176, 1, 82, 206, 0, 127, 176, 1, 117, 206, 0, 162, 176, 1, 152, 206};
    static final int[] SNOW = {0, 80, 148, 1, 166, 148, 4, 80, 124, 24, 90, 190, 4, 122, 98, 38, 176, 176,
            4, 166, 120, 28, 257, 193, 3, 90, 192, 7, 3, 124, 210, 7, 3, 158, 192, 7};
    static final int[] STORM = {0, 80, 148, 1, 166, 148, 4, 80, 124, 24, 90, 190, 4, 122, 98, 38, 176, 176,
            4, 166, 120, 28, 257, 193, 0, 132, 168, 1, 110, 198, 1, 134, 198, 1, 112, 228};
    static final int[] FOG = {0, 50, 92, 1, 190, 92, 0, 36, 128, 1, 204, 128, 0, 56, 164, 1, 184, 164};
    static final int[] WIFI = {4, 120, 195, 135, 225, 90, 4, 120, 195, 92, 225, 90, 4, 120, 195, 50, 225, 90,
            3, 120, 195, 4};
    static final int[] WIFI_2 = {4, 120, 195, 92, 225, 90, 4, 120, 195, 50, 225, 90, 3, 120, 195, 4};
    static final int[] WIFI_1 = {4, 120, 195, 50, 225, 90, 3, 120, 195, 4};
    static final int[] SLASH = {0, 50, 45, 1, 190, 205};
    static final int[] BLUETOOTH = {0, 70, 80, 1, 170, 160, 1, 120, 205, 1, 120, 35, 1, 170, 80, 1, 70, 160};
    static final int[] SOUND = {0, 35, 95, 1, 75, 95, 1, 120, 55, 1, 120, 185, 1, 75, 145, 1, 35, 145, 2,
            4, 120, 120, 40, -45, 90, 4, 120, 120, 78, -45, 90};
    static final int[] ETHERNET = {0, 40, 60, 1, 200, 60, 1, 200, 165, 1, 160, 165, 1, 160, 195, 1, 80, 195,
            1, 80, 165, 1, 40, 165, 2, 0, 85, 60, 1, 85, 100, 0, 120, 60, 1, 120, 100, 0, 155, 60, 1, 155, 100};
    static final int[] PICTURE ={0, 30, 50, 1, 210, 50, 1, 210, 165, 1, 30, 165, 2, 0, 120, 165, 1, 120, 200,
            0, 80, 200, 1, 160, 200};

    private final int[] ops;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF arc = new RectF();

    LineIcon(int[] ops) {
        this.ops = ops;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setColor(0xEBFFFFFF);
    }

    @Override
    public void draw(Canvas c) {
        if (ops == null) return;
        float s = getBounds().width() / 240f; // ops are in tenths of the 24-unit grid
        paint.setStrokeWidth(16 * s);
        path.reset();
        for (int i = 0; i < ops.length; ) {
            switch (ops[i]) {
                case 0: path.moveTo(ops[i + 1] * s, ops[i + 2] * s); i += 3; break;
                case 1: path.lineTo(ops[i + 1] * s, ops[i + 2] * s); i += 3; break;
                case 2: path.close(); i += 1; break;
                case 3: path.addCircle(ops[i + 1] * s, ops[i + 2] * s, ops[i + 3] * s, Path.Direction.CW); i += 4; break;
                default:
                    float cx = ops[i + 1] * s, cy = ops[i + 2] * s, r = ops[i + 3] * s;
                    arc.set(cx - r, cy - r, cx + r, cy + r);
                    path.addArc(arc, ops[i + 4], ops[i + 5]);
                    i += 6;
            }
        }
        c.save();
        c.translate(getBounds().left, getBounds().top);
        c.drawPath(path, paint);
        c.restore();
    }

    @Override public void setAlpha(int a) { paint.setAlpha(a); }
    @Override public void setColorFilter(ColorFilter cf) { paint.setColorFilter(cf); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    @Override public int getIntrinsicWidth() { return 44; }
    @Override public int getIntrinsicHeight() { return 44; }
}
