package ru.iqwanoino.weblab;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/** Ikon digambar langsung di Canvas (viewbox 24x24). Tanpa res/drawable XML. */
final class Icons extends Drawable {
    static final int MENU = 0, UNDO = 1, REDO = 2, SAVE = 3, LOAD = 4, SHARE = 5,
            PLAY = 6, CODE = 7, SETTINGS = 8, INFO = 9, LINK = 10, CLOSE = 11, BACK = 12;

    private final int type;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();

    private Icons(int type, int color) {
        this.type = type;
        paint.setColor(color);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
    }

    static Drawable of(int type, int color) {
        return new Icons(type, color);
    }

    @Override
    public void draw(Canvas c) {
        Rect b = getBounds();
        float s = Math.min(b.width(), b.height()) / 24f;
        if (s <= 0) return;
        int save = c.save();
        c.translate(b.left + (b.width() - 24 * s) / 2f, b.top + (b.height() - 24 * s) / 2f);
        c.scale(s, s);
        draw24(c);
        c.restoreToCount(save);
    }

    private void draw24(Canvas c) {
        path.rewind();
        switch (type) {
            case MENU:
                c.drawLine(4, 6, 20, 6, paint);
                c.drawLine(4, 12, 20, 12, paint);
                c.drawLine(4, 18, 20, 18, paint);
                break;
            case BACK:
                path.moveTo(15, 5); path.lineTo(8, 12); path.lineTo(15, 19);
                c.drawPath(path, paint);
                break;
            case CLOSE:
                c.drawLine(6, 6, 18, 18, paint);
                c.drawLine(18, 6, 6, 18, paint);
                break;
            case UNDO:
                path.moveTo(9, 4); path.lineTo(4, 9); path.lineTo(9, 14);
                path.moveTo(4, 9); path.lineTo(13, 9);
                path.cubicTo(17.4f, 9, 20, 11.5f, 20, 15);
                path.cubicTo(20, 18.5f, 17.4f, 21, 13, 21);
                path.lineTo(11, 21);
                c.drawPath(path, paint);
                break;
            case REDO:
                path.moveTo(15, 4); path.lineTo(20, 9); path.lineTo(15, 14);
                path.moveTo(20, 9); path.lineTo(11, 9);
                path.cubicTo(6.6f, 9, 4, 11.5f, 4, 15);
                path.cubicTo(4, 18.5f, 6.6f, 21, 11, 21);
                path.lineTo(13, 21);
                c.drawPath(path, paint);
                break;
            case SAVE:
                path.moveTo(5, 3); path.lineTo(16, 3); path.lineTo(21, 8);
                path.lineTo(21, 19); path.lineTo(19, 21); path.lineTo(5, 21);
                path.lineTo(3, 19); path.lineTo(3, 5); path.close();
                c.drawPath(path, paint);
                rect.set(7, 3, 16, 9);
                c.drawRect(rect, paint);
                rect.set(7, 14, 17, 21);
                c.drawRect(rect, paint);
                break;
            case LOAD:
                c.drawLine(12, 3, 12, 15, paint);
                path.moveTo(7, 10); path.lineTo(12, 15); path.lineTo(17, 10);
                path.moveTo(4, 15); path.lineTo(4, 19); path.lineTo(6, 21);
                path.lineTo(18, 21); path.lineTo(20, 19); path.lineTo(20, 15);
                c.drawPath(path, paint);
                break;
            case SHARE:
                c.drawCircle(18, 5, 3, paint);
                c.drawCircle(6, 12, 3, paint);
                c.drawCircle(18, 19, 3, paint);
                c.drawLine(8.6f, 10.5f, 15.4f, 6.5f, paint);
                c.drawLine(8.6f, 13.5f, 15.4f, 17.5f, paint);
                break;
            case PLAY:
                paint.setStyle(Paint.Style.FILL);
                path.moveTo(7, 4); path.lineTo(20, 12); path.lineTo(7, 20); path.close();
                c.drawPath(path, paint);
                paint.setStyle(Paint.Style.STROKE);
                break;
            case CODE:
                path.moveTo(8, 7); path.lineTo(3, 12); path.lineTo(8, 17);
                path.moveTo(16, 7); path.lineTo(21, 12); path.lineTo(16, 17);
                path.moveTo(14, 4); path.lineTo(10, 20);
                c.drawPath(path, paint);
                break;
            case SETTINGS:
                c.drawCircle(12, 12, 3, paint);
                c.drawCircle(12, 12, 7.5f, paint);
                for (int i = 0; i < 8; i++) {
                    double a = i * Math.PI / 4;
                    float x1 = (float) (12 + Math.cos(a) * 7.5f), y1 = (float) (12 + Math.sin(a) * 7.5f);
                    float x2 = (float) (12 + Math.cos(a) * 10.5f), y2 = (float) (12 + Math.sin(a) * 10.5f);
                    c.drawLine(x1, y1, x2, y2, paint);
                }
                break;
            case INFO:
                c.drawCircle(12, 12, 9, paint);
                c.drawLine(12, 11, 12, 17, paint);
                paint.setStyle(Paint.Style.FILL);
                c.drawCircle(12, 7.5f, 1.2f, paint);
                paint.setStyle(Paint.Style.STROKE);
                break;
            case LINK:
                path.moveTo(10, 14); path.lineTo(14, 10);
                path.moveTo(8, 12); path.lineTo(6, 14);
                path.cubicTo(3.8f, 16.2f, 4.2f, 19.8f, 6.4f, 20.2f);
                path.cubicTo(8.6f, 20.6f, 10.6f, 19.5f, 12, 18);
                path.moveTo(16, 12); path.lineTo(18, 10);
                path.cubicTo(20.2f, 7.8f, 19.8f, 4.2f, 17.6f, 3.8f);
                path.cubicTo(15.4f, 3.4f, 13.4f, 4.5f, 12, 6);
                c.drawPath(path, paint);
                break;
            default:
                break;
        }
    }

    @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
    @Override public void setColorFilter(ColorFilter cf) { paint.setColorFilter(cf); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
