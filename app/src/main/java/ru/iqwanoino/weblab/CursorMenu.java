package ru.iqwanoino.weblab;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;


/**
 * Menu kursor/seleksi (Select all, Cut, Copy, Paste) gaya Sora Editor, tanpa AndroidX.
 * Berupa bar ikon membulat yang melayang di atas kursor / seleksi (atau di bawahnya
 * bila tidak muat). Tidak mengambil fokus, jadi keyboard tidak tertutup.
 */
public class CursorMenu {

    /** Pasang font aplikasi (Fonts) ke semua TextView di dalam view. */
    private void applyFont(View v) {
        if (v instanceof TextView) ((TextView) v).setTypeface(Fonts.regular(ctx));
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) applyFont(g.getChildAt(i));
        }
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    public static final int SELECT_ALL = 1, CUT = 2, COPY = 3, PASTE = 4, LONG_SELECT = 5;

    public interface Listener { void onAction(int id); }

    private final Context ctx;
    private final float density;
    private final Listener listener;
    private int cBg = Theme.SURFACE_HIGH, cIcon = Theme.ON;
    private PopupWindow window;
    private int shownSig = -1;
    private int lastX, lastY;

    public CursorMenu(Context c, Listener l) {
        ctx = c;
        density = c.getResources().getDisplayMetrics().density;
        listener = l;
    }

    public void setColors(int background, int icon) {
        if (background != cBg || icon != cIcon) { hide(); }
        cBg = background;
        cIcon = icon;
    }

    public boolean isShowing() { return window != null && window.isShowing(); }

    public void hide() {
        if (window != null) {
            try { window.dismiss(); } catch (Exception ignored) {}
            window = null;
        }
        shownSig = -1;
    }

    /**
     * @param anchor     view editor (koordinat di bawah relatif terhadap view ini)
     * @param centerX    titik tengah horizontal target menu
     * @param topY       sisi atas area kursor/seleksi
     * @param bottomY    sisi bawah area kursor/seleksi (tempat handle menggantung)
     */
    public void show(View anchor, boolean hasSel, boolean canPaste, boolean hasText, boolean allSelected,
                     int centerX, int topY, int bottomY) {
        int sig = (hasSel ? 1 : 0) | (canPaste ? 2 : 0) | (hasText ? 4 : 0) | (allSelected ? 8 : 0);
        final int shadow = dp(16);   // ruang bayangan agar tidak terpotong

        boolean fresh = !isShowing() || sig != shownSig;
        int mw, mh;
        View content;
        if (fresh) {
            hide();
            content = build(hasSel, canPaste, hasText, allSelected, shadow);
            if (content == null) return;
            content.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            mw = content.getMeasuredWidth();
            mh = content.getMeasuredHeight();
        } else {
            content = window.getContentView();
            mw = content.getMeasuredWidth();
            mh = content.getMeasuredHeight();
        }

        int cardW = mw - 2 * shadow, cardH = mh - 2 * shadow;
        int vw = anchor.getWidth(), vh = anchor.getHeight();
        int margin = dp(8);
        int x = centerX - cardW / 2;
        x = Math.max(margin, Math.min(x, vw - cardW - margin));

        int y = topY - cardH - dp(10);                      // di atas
        if (y < margin) y = bottomY + dp(34);               // di bawah handle
        if (y + cardH > vh - margin) y = Math.max(margin, Math.min(topY - cardH - dp(10), vh - cardH - margin));
        if (y < margin) y = margin;

        int px = x - shadow, py = y - shadow;               // koordinat jendela (termasuk bayangan)

        if (!fresh) {
            if (px != lastX || py != lastY) {
                window.update(anchor, px, py, -1, -1);
                lastX = px; lastY = py;
            }
            return;
        }

        applyFont(content);   // popup punya window sendiri: pasang font aplikasi
        window = new PopupWindow(content, ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, false);   // false = tidak ambil fokus
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.setOutsideTouchable(false);
        window.setClippingEnabled(false);
        if (Build.VERSION.SDK_INT >= 23) window.setOverlapAnchor(true);
        window.setElevation(0f);

        content.setPivotX(mw / 2f);
        content.setPivotY(y < topY ? mh - shadow : shadow);
        content.setScaleX(0.9f);
        content.setScaleY(0.9f);
        content.setAlpha(0f);
        try {
            window.showAsDropDown(anchor, px, py, Gravity.TOP | Gravity.START);
        } catch (Exception e) {      // anchor belum attach
            window = null;
            return;
        }
        lastX = px; lastY = py; shownSig = sig;
        content.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(130)
                .setInterpolator(new DecelerateInterpolator(1.6f)).start();
    }

    private View build(boolean hasSel, boolean canPaste, boolean hasText, boolean allSelected, int shadow) {
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(4), dp(4), dp(4), dp(4));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(cBg);
        bg.setCornerRadius(dp(14));
        bg.setStroke(dp(1), withAlpha(Theme.ON, 0x1A));
        card.setBackground(bg);

        if (hasSel) {
            // seleksi: Select all (bila belum semua), Cut, Copy, Paste
            if (!allSelected) card.addView(button(SELECT_ALL, R.drawable.ic_select_all, "Pilih semua", true));
            card.addView(button(CUT, R.drawable.ic_cut, "Potong", true));
            card.addView(button(COPY, R.drawable.ic_copy, "Salin", true));
            card.addView(button(PASTE, R.drawable.ic_paste, "Tempel", canPaste));
        } else {
            // kursor saja: selalu 3 pilihan -> Select all, Paste, Long select
            card.addView(button(SELECT_ALL, R.drawable.ic_select_all, "Pilih semua", hasText));
            card.addView(button(PASTE, R.drawable.ic_paste, "Tempel", canPaste));
            card.addView(button(LONG_SELECT, R.drawable.ic_long_select, "Pilih panjang", hasText));
        }

        FrameLayout wrap = new FrameLayout(ctx);
        wrap.setPadding(shadow, shadow, shadow, shadow);
        wrap.addView(card, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return wrap;
    }

    private View button(final int id, int iconRes, String desc, boolean enabled) {
        ImageView v = new ImageView(ctx);
        v.setScaleType(ImageView.ScaleType.CENTER);
        v.setImageDrawable(IconDrawable.of(ctx, iconRes, cIcon, dp(22)));
        v.setContentDescription(desc);
        v.setClickable(enabled);
        v.setAlpha(enabled ? 1f : 0.38f);
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(Color.BLACK);
        mask.setCornerRadius(dp(10));
        if (enabled) {
            v.setBackground(new RippleDrawable(ColorStateList.valueOf(withAlpha(Theme.ON, 0x26)), null, mask));
            v.setOnClickListener(new View.OnClickListener() {
                public void onClick(View view) { if (listener != null) listener.onAction(id); }
            });
        }
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(46), dp(40)));
        return v;
    }

    private int dp(float v) { return (int) (v * density + 0.5f); }
}
