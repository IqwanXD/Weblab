package ru.iqwanoino.weblab;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;

/**
 * InoSecurity — satu file untuk seluruh proteksi:
 *  - InoSecurity          : layar penolakan. Dibuka native (libinenc.so) sebagai
 *                           "<paket>.InoSecurity" saat ada modifikasi.
 *  - InoSecurity.Provider : ContentProvider; memuat libinenc.so sebelum Application.
 *
 * TIDAK ADA pengecekan di Java. Pengecekan paket & sertifikat dilakukan di native.
 */
public class InoSecurity extends Activity {

	static {
		try {
			System.loadLibrary("inenc");
		} catch (Throwable ignored) {}
	}

	/** Dipanggil dari native saat modifikasi (kompatibilitas). Sengaja kosong. */
	public static void markLocked() {}

	/** Memicu static{} di atas (loadLibrary). */
	public static void load() {}

	public static void setReportContact(String uri) {
		reportContact = uri;
	}


	// ---- Palet M3 Expressive (dark, fixed — tidak tergantung Material You device) ----
	private static final int BG            = 0xFF141218;
	private static final int SURFACE_HIGH  = 0xFF2B2930;
	private static final int SURFACE_HIGHEST = 0xFF36343B;
	private static final int PRIMARY       = 0xFFB8C4FF;
	private static final int ON_PRIMARY    = 0xFF00208A;
	private static final int PRIM_CONT     = 0xFF2947FF;
	private static final int ON_PRIM_CONT  = 0xFFDDE1FF;
	private static final int ON            = 0xFFE5E1E6;
	private static final int ON_VAR        = 0xFFC9C5D0;

	private static String reportContact = null;


	private ValueAnimator pulseAnimator;

	@Override
	protected void onCreate(Bundle b) {
		try { super.onCreate(b); } catch (Throwable t) {
			try { super.onCreate(null); } catch (Throwable ignored) {}
		}
		try { setupWindow(); } catch (Throwable ignored) {}

		ArrayList<String> reasons = null;
		try {
			if (getIntent() != null)
				reasons = getIntent().getStringArrayListExtra("reasons");
		} catch (Throwable ignored) {}
		if (reasons == null) reasons = new ArrayList<>();
		if (reasons.isEmpty()) reasons.add("Certificate integrity check failed");

		try {
			setContentView(buildUi(reasons));
		} catch (Throwable t) {
			try {
				TextView tv = new TextView(this);
				tv.setText("Access Denied\n\nProtected by InoSecurity");
				tv.setTextColor(ON);
				tv.setBackgroundColor(BG);
				tv.setGravity(Gravity.CENTER);
				tv.setTextSize(18);
				tv.setPadding(dp(24), dp(48), dp(24), dp(24));
				setContentView(tv);
			} catch (Throwable ignored) {}
		}
	}

	private View buildUi(ArrayList<String> reasons) {
		int pad = dp(24);
		FrameLayout root = new FrameLayout(this);
		root.setBackgroundColor(BG);

		LinearLayout col = new LinearLayout(this);
		col.setOrientation(LinearLayout.VERTICAL);
		col.setGravity(Gravity.CENTER_HORIZONTAL);
		col.setPadding(pad, dp(48), pad, pad);

		// ---- Badge besar (squircle) + ring pulsa di belakangnya (motion signature M3 Expressive) ----
		FrameLayout badgeWrap = new FrameLayout(this);
		final PulseRingView ring = new PulseRingView(this, PRIM_CONT);
		badgeWrap.addView(ring, new FrameLayout.LayoutParams(dp(132), dp(132), Gravity.CENTER));

		FrameLayout badge = new FrameLayout(this);
		badge.setBackground(squircle(PRIM_CONT, 0.42f));
		View lock = new LockIconView(this);
		int ip = dp(26);
		lock.setPadding(ip, ip, ip, ip);
		badge.addView(lock, new FrameLayout.LayoutParams(dp(96), dp(96)));
		badgeWrap.addView(badge, new FrameLayout.LayoutParams(dp(96), dp(96), Gravity.CENTER));

		LinearLayout.LayoutParams bwlp = lp(dp(132), dp(132));
		bwlp.topMargin = dp(8);
		bwlp.bottomMargin = dp(24);
		col.addView(badgeWrap, bwlp);
		badge.setScaleX(0.6f); badge.setScaleY(0.6f); badge.setAlpha(0f);

		TextView title = text("Access Denied", 28, ON, true);
		title.setGravity(Gravity.CENTER);
		title.setLetterSpacing(-0.01f);
		col.addView(title, lpWrap(0, 0, 0, dp(10)));

		TextView sub = text(
				"Sertifikat signing aplikasi tidak valid. Aplikasi tidak dapat berjalan dengan aman.",
				14, ON_VAR, false);
		sub.setGravity(Gravity.CENTER);
		col.addView(sub, lp(-1, -2, dp(12), 0, dp(12), dp(28)));

		// ---- Kartu alasan, tonal container besar, corner radius ekspresif ----
		LinearLayout card = new LinearLayout(this);
		card.setOrientation(LinearLayout.VERTICAL);
		card.setBackground(roundRect(SURFACE_HIGH, 32));
		card.setPadding(dp(22), dp(20), dp(22), dp(20));
		TextView cardTitle = text("VIOLATION DETAILS", 11, PRIMARY, true);
		if (Build.VERSION.SDK_INT >= 21) cardTitle.setLetterSpacing(0.1f);
		cardTitle.setPadding(0, 0, 0, dp(12));
		card.addView(cardTitle);
		for (String r : reasons) {
			if (r == null) continue;
			LinearLayout row = new LinearLayout(this);
			row.setOrientation(LinearLayout.HORIZONTAL);
			row.setPadding(0, dp(6), 0, dp(6));
			View dot = new View(this);
			dot.setBackground(roundRect(PRIMARY, 10));
			LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dp(6), dp(6));
			dlp.topMargin = dp(8);
			dlp.rightMargin = dp(10);
			row.addView(dot, dlp);
			TextView rt = text(r, 13.5f, ON, false);
			row.addView(rt, new LinearLayout.LayoutParams(0, -2, 1f));
			card.addView(row);
		}
		ScrollView scroll = new ScrollView(this);
		scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
		scroll.addView(card, new FrameLayout.LayoutParams(-1, -2));
		LinearLayout.LayoutParams slp = lp(-1, 0);
		slp.weight = 1f;
		slp.bottomMargin = dp(20);
		col.addView(scroll, slp);
		card.setAlpha(0f);
		card.setTranslationY(dp(28));

		// ---- Tombol pill penuh, tinggi besar (gaya expressive) ----
		LinearLayout btnRow = new LinearLayout(this);
		btnRow.setOrientation(LinearLayout.HORIZONTAL);
		final String contact = reportContact;
		TextView report = button("Report", SURFACE_HIGHEST, PRIMARY);
		report.setOnClickListener(v -> {
			if (contact != null && contact.length() > 0) {
				try {
					startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(contact)));
				} catch (Throwable ignored) {}
			}
		});
		TextView close = button("Close", PRIMARY, ON_PRIMARY);
		close.setOnClickListener(v -> exitApp());
		if (contact == null || contact.isEmpty()) report.setVisibility(View.GONE);
		LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(0, dp(56), 1f);
		bp.setMargins(dp(6), 0, dp(6), 0);
		btnRow.addView(report, bp);
		btnRow.addView(close, bp);
		col.addView(btnRow, lp(-1, dp(56)));
		btnRow.setAlpha(0f);
		btnRow.setTranslationY(dp(16));

		TextView footer = text("Protected by InoSecurity", 12, ON_VAR, false);
		footer.setGravity(Gravity.CENTER);
		footer.setPadding(0, dp(18), 0, 0);
		col.addView(footer, lp(-1, -2));

		if (Build.VERSION.SDK_INT >= 20) {
			try {
				col.setOnApplyWindowInsetsListener((v, insets) -> {
					v.setPadding(pad, insets.getSystemWindowInsetTop() + dp(24),
							pad, insets.getSystemWindowInsetBottom() + dp(16));
					return insets.consumeSystemWindowInsets();
				});
				col.requestApplyInsets();
			} catch (Throwable ignored) {}
		}

		root.addView(col, new FrameLayout.LayoutParams(-1, -1));
		animateEntrance(badge, card, btnRow);
		return root;
	}

	/** Entrance spring-like: badge overshoot, lalu kartu & tombol fade+slide susul. */
	private void animateEntrance(View badge, View card, View btnRow) {
		try {
			badge.animate().alpha(1f).scaleX(1f).scaleY(1f)
					.setStartDelay(60).setDuration(420)
					.setInterpolator(new OvershootInterpolator(2.2f)).start();
			card.animate().alpha(1f).translationY(0)
					.setStartDelay(160).setDuration(360)
					.setInterpolator(new DecelerateInterpolator()).start();
			btnRow.animate().alpha(1f).translationY(0)
					.setStartDelay(260).setDuration(320)
					.setInterpolator(new DecelerateInterpolator()).start();
		} catch (Throwable ignored) {}
	}

	@Override
	protected void onResume() {
		super.onResume();
		try {
			if (pulseAnimator == null) return;
			if (!pulseAnimator.isStarted()) pulseAnimator.start();
		} catch (Throwable ignored) {}
	}

	@Override
	protected void onPause() {
		try { if (pulseAnimator != null) pulseAnimator.pause(); } catch (Throwable ignored) {}
		super.onPause();
	}

	@Override
	protected void onDestroy() {
		try { if (pulseAnimator != null) pulseAnimator.cancel(); } catch (Throwable ignored) {}
		super.onDestroy();
	}

	private void exitApp() {
		try {
			if (Build.VERSION.SDK_INT >= 21) finishAndRemoveTask();
			else finish();
		} catch (Throwable ignored) {}
		try { Process.killProcess(Process.myPid()); } catch (Throwable ignored) {}
		try { System.exit(0); } catch (Throwable ignored) {}
	}

	@Override public void onBackPressed() { /* block */ }

	private void setupWindow() {
		if (Build.VERSION.SDK_INT >= 21) {
			Window w = getWindow();
			w.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
			w.setStatusBarColor(BG);
			w.setNavigationBarColor(BG);
		}
	}

	private int dp(int v) {
		return Math.round(TypedValue.applyDimension(
				TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
	}

	private GradientDrawable roundRect(int color, float radiusDp) {
		GradientDrawable d = new GradientDrawable();
		d.setColor(color);
		d.setCornerRadius(dp((int) radiusDp));
		return d;
	}

	/** Squircle: persegi dengan radius proporsional terhadap sisi (bentuk khas M3 Expressive). */
	private GradientDrawable squircle(int color, float radiusRatio) {
		GradientDrawable d = new GradientDrawable();
		d.setColor(color);
		d.setCornerRadius(dp(96) * radiusRatio);
		return d;
	}

	private TextView text(String s, float sp, int color, boolean bold) {
		TextView t = new TextView(this);
		t.setText(s);
		t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
		t.setTextColor(color);
		t.setTypeface(Typeface.create(Typeface.SANS_SERIF, bold ? Typeface.BOLD : Typeface.NORMAL));
		return t;
	}

	private TextView button(String label, int bg, int fg) {
		TextView t = text(label, 15, fg, true);
		t.setGravity(Gravity.CENTER);
		t.setBackground(roundRect(bg, 28)); // pill penuh pada tinggi 56dp
		t.setClickable(true);
		t.setFocusable(true);
		return t;
	}

	private LinearLayout.LayoutParams lp(int w, int h) {
		return new LinearLayout.LayoutParams(w, h);
	}

	private LinearLayout.LayoutParams lp(int w, int h, int l, int t, int r, int b) {
		LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
		p.setMargins(l, t, r, b);
		return p;
	}

	private LinearLayout.LayoutParams lpWrap(int l, int t, int r, int b) {
		return lp(-2, -2, l, t, r, b);
	}

	/** Ring pulsa lembut di belakang badge — ciri motion M3 Expressive. */
	private class PulseRingView extends View {
		private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
		private final int color;
		private float progress = 0f;

		PulseRingView(Activity a, int color) {
			super(a);
			this.color = color;
			paint.setStyle(Paint.Style.FILL);
			try {
				pulseAnimator = ValueAnimator.ofFloat(0f, 1f);
				pulseAnimator.setDuration(1600);
				pulseAnimator.setRepeatCount(ValueAnimator.INFINITE);
				pulseAnimator.setRepeatMode(ValueAnimator.RESTART);
				pulseAnimator.addUpdateListener(anim -> {
					progress = (float) anim.getAnimatedValue();
					invalidate();
				});
				pulseAnimator.start();
			} catch (Throwable ignored) {}
		}

		@Override
		protected void onDraw(Canvas c) {
			try {
				int w = getWidth(), h = getHeight();
				if (w <= 0 || h <= 0) return;
				float cx = w / 2f, cy = h / 2f;
				float base = Math.min(w, h) * 0.36f;
				float radius = base + (Math.min(w, h) * 0.14f) * progress;
				int alpha = (int) (70 * (1f - progress));
				paint.setColor(color);
				paint.setAlpha(Math.max(0, alpha));
				c.drawCircle(cx, cy, radius, paint);
			} catch (Throwable ignored) {}
		}
	}

	private static class LockIconView extends View {
		private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
		private final Path path = new Path();

		LockIconView(Activity a) { super(a); }

		@Override
		protected void onDraw(Canvas c) {
			try {
				int w = getWidth(), h = getHeight();
				if (w <= 0 || h <= 0) return;
				int pad = Math.min(w, h) / 6;
				float cx = w / 2f;
				float bodyTop = h * 0.42f, bodyBottom = h - pad;
				float bodyLeft = pad * 1.2f, bodyRight = w - pad * 1.2f;
				float radius = (bodyRight - bodyLeft) * 0.2f;
				paint.setColor(0xFFDDE1FF);
				paint.setStyle(Paint.Style.FILL);
				if (Build.VERSION.SDK_INT >= 21)
					c.drawRoundRect(bodyLeft, bodyTop, bodyRight, bodyBottom, radius, radius, paint);
				else
					c.drawRect(bodyLeft, bodyTop, bodyRight, bodyBottom, paint);
				paint.setStyle(Paint.Style.STROKE);
				paint.setStrokeWidth(Math.max(3f, w * 0.085f));
				paint.setStrokeCap(Paint.Cap.ROUND);
				float shackleR = (bodyRight - bodyLeft) * 0.3f;
				path.reset();
				path.addArc(cx - shackleR, bodyTop - shackleR * 1.6f,
						cx + shackleR, bodyTop + shackleR * 0.4f, 200, 140);
				c.drawPath(path, paint);
				paint.setStyle(Paint.Style.FILL);
				float khR = (bodyRight - bodyLeft) * 0.085f;
				float khCy = bodyTop + (bodyBottom - bodyTop) * 0.38f;
				c.drawCircle(cx, khCy, khR, paint);
				c.drawRect(cx - khR * 0.45f, khCy, cx + khR * 0.45f, khCy + khR * 2.2f, paint);
			} catch (Throwable ignored) {}
		}
	}

	/** ContentProvider: di-instantiate sistem sebelum Application.onCreate(). */
	public static final class Provider extends ContentProvider {

		@Override
		public boolean onCreate() {
			InoSecurity.load();
			return true;
		}

		@Override
		public Cursor query(Uri uri, String[] projection, String selection,
				String[] selectionArgs, String sortOrder) {
			return null;
		}

		@Override public String getType(Uri uri) { return null; }

		@Override public Uri insert(Uri uri, ContentValues values) { return null; }

		@Override public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }

		@Override
		public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
			return 0;
		}
	}
}
