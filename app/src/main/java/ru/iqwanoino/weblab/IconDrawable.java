package ru.iqwanoino.weblab;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;

/**
 * Pembungkus icon: vector drawable (res/drawable/ic_*.xml) + warna + ukuran.
 * Pakai: IconDrawable.of(context, R.drawable.ic_nama, warna, dp(ukuran))
 */
public class IconDrawable extends Drawable {
	private final Drawable d;
	private final int size;
	
	public IconDrawable(Drawable d, int color, int size) {
		this.d = d.mutate();
		this.size = size;
		this.d.setColorFilter(color, PorterDuff.Mode.SRC_IN);
	}
	
	public static IconDrawable of(Context c, int res, int color, int size) {
		return new IconDrawable(c.getResources().getDrawable(res), color, size);
	}
	
	@Override public void draw(Canvas c) {
		d.setBounds(getBounds());
		d.draw(c);
	}
	@Override public int getIntrinsicWidth() { return size; }
	@Override public int getIntrinsicHeight() { return size; }
	@Override public void setAlpha(int alpha) { d.setAlpha(alpha); }
	@Override public void setColorFilter(ColorFilter cf) { d.setColorFilter(cf); }
	@Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
