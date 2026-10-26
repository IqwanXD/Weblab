package ru.iqwanoino.weblab;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

/**
 * Handle kursor / seleksi berbentuk tetesan (drop), gaya Sora Editor (tanpa AndroidX).
 *
 * Titik ujung (hotspot) mengikuti aturan TextView:
 *  - INSERT : ujung di tengah-atas  (hotspot x = 1/2 lebar)
 *  - LEFT   : sudut runcing kanan-atas (hotspot x = 3/4 lebar)
 *  - RIGHT  : sudut runcing kiri-atas  (hotspot x = 1/4 lebar)
 * Sisi atas drawable menempel di bawah garis teks.
 */
public class HandleDrawable extends Drawable {
    public static final int INSERT = 0, LEFT = 1, RIGHT = 2;

    private final int type;
    private final float r;          // jari-jari lingkaran (px)
    private final int w, h;
    private final float padSide;    // ruang sentuh ekstra kiri/kanan (hanya INSERT)
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public HandleDrawable(int type, int color, float radiusPx, float touchPadPx) {
        this.type = type;
        this.r = radiusPx;
        paint.setColor(color);
        paint.setStyle(Paint.Style.FILL);
        float d = (float) Math.sqrt(2) * r;     // jarak ujung ke pusat lingkaran
        if (type == INSERT) {
            padSide = touchPadPx;
            w = Math.round(2 * r + 2 * padSide);
            h = Math.round(d + r + touchPadPx);
        } else {
            padSide = 0;
            w = Math.round(8f * r / 3f);        // 3/4 w = 2r  -> ujung pas di tepi lingkaran
            h = Math.round(2 * r + touchPadPx);
        }
    }

    @Override public void draw(Canvas c) {
        Rect b = getBounds();
        float d = (float) Math.sqrt(2) * r;
        float k = (float) Math.sqrt(0.5) * r;
        if (type == INSERT) {
            float cx = b.left + b.width() / 2f;
            float cy = b.top + d;
            c.drawCircle(cx, cy, r, paint);
            android.graphics.Path p = new android.graphics.Path();
            p.moveTo(cx, b.top);
            p.lineTo(cx + k, cy - k);
            p.lineTo(cx - k, cy - k);
            p.close();
            c.drawPath(p, paint);
        } else if (type == LEFT) {
            float tipX = b.left + b.width() * 0.75f;
            float cx = tipX - r, cy = b.top + r;
            c.drawCircle(cx, cy, r, paint);
            c.drawRect(cx, b.top, cx + r, cy, paint);      // sudut kanan-atas runcing
        } else {
            float tipX = b.left + b.width() * 0.25f;
            float cx = tipX + r, cy = b.top + r;
            c.drawCircle(cx, cy, r, paint);
            c.drawRect(cx - r, b.top, cx, cy, paint);      // sudut kiri-atas runcing
        }
    }

    @Override public int getIntrinsicWidth()  { return w; }
    @Override public int getIntrinsicHeight() { return h; }
    @Override public void setAlpha(int a) { paint.setAlpha(a); invalidateSelf(); }
    @Override public void setColorFilter(ColorFilter cf) { paint.setColorFilter(cf); invalidateSelf(); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
