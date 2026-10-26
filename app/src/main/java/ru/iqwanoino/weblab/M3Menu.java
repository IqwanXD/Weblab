package ru.iqwanoino.weblab;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import java.util.ArrayList;

/**
 * Popup menu bergaya Material 3, diambil dari JetEditor dan disesuaikan dengan palet Weblab:
 * kartu tonal membulat, ikon di depan, item berbentuk pill saat ditekan, pemisah garis,
 * dan item bercentang. Pakai:
 *
 *   new M3Menu(ctx)
 *       .add(ID_SAVE, R.drawable.ic_save, "Simpan")
 *       .addDivider()
 *       .addCheck(ID_AUTO, R.drawable.ic_tune, "Lengkapi simbol", on)
 *       .setOnItemClickListener(id -> { ... })
 *       .show(anchorView);
 */
final class M3Menu {

    interface Listener { void onItem(int id); }

    private static final class Item {
        int id, icon;
        String title;
        boolean checkable, checked, enabled = true, divider;
    }

    private final Context ctx;
    private final float density;
    private final ArrayList<Item> items = new ArrayList<>();
    private Listener listener;
    private PopupWindow window;

    M3Menu(Context c) {
        ctx = c;
        density = c.getResources().getDisplayMetrics().density;
    }

    M3Menu add(int id, int icon, String title) { return add(id, icon, title, true); }

    M3Menu add(int id, int icon, String title, boolean enabled) {
        Item i = new Item();
        i.id = id;
        i.icon = icon;
        i.title = title;
        i.enabled = enabled;
        items.add(i);
        return this;
    }

    M3Menu addCheck(int id, int icon, String title, boolean checked) {
        Item i = new Item();
        i.id = id;
        i.icon = icon;
        i.title = title;
        i.checkable = true;
        i.checked = checked;
        items.add(i);
        return this;
    }

    M3Menu addDivider() {
        Item i = new Item();
        i.divider = true;
        items.add(i);
        return this;
    }

    M3Menu setOnItemClickListener(Listener l) {
        listener = l;
        return this;
    }

    void dismiss() {
        if (window != null && window.isShowing()) window.dismiss();
    }

    /** Menu rata kanan di bawah anchor. Naik ke atas anchor kalau di bawahnya tidak muat. */
    void show(View anchor) {
        if (items.isEmpty()) return;

        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(8), dp(8), dp(8), dp(8));
        card.setMinimumWidth(dp(200));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Theme.SURFACE_HIGH);
        bg.setCornerRadius(dp(24));
        card.setBackground(bg);
        for (Item it : items) card.addView(buildRow(it));

        FrameLayout wrap = new FrameLayout(ctx);
        wrap.addView(card, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        wrap.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int mw = wrap.getMeasuredWidth();
        int mh = wrap.getMeasuredHeight();

        int[] loc = new int[2];
        anchor.getLocationOnScreen(loc);
        DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
        int screenW = dm.widthPixels;
        int screenH = dm.heightPixels;
        int insetTop = 0, insetBottom = 0;
        if (Build.VERSION.SDK_INT >= 23) {
            WindowInsets wi = anchor.getRootWindowInsets();
            if (wi != null) {
                if (Build.VERSION.SDK_INT >= 30) {
                    insetTop = wi.getInsets(WindowInsets.Type.statusBars()).top;
                    insetBottom = wi.getInsets(WindowInsets.Type.navigationBars()).bottom;
                } else {
                    insetTop = wi.getStableInsetTop();
                    insetBottom = wi.getStableInsetBottom();
                }
            }
        }
        int margin = dp(8);
        int minY = insetTop + margin;
        int maxY = screenH - insetBottom - margin;
        int anchorTop = loc[1];
        int anchorBottom = loc[1] + anchor.getHeight();
        int spaceBelow = maxY - anchorBottom;
        int spaceAbove = anchorTop - minY;
        boolean above = spaceBelow < mh && spaceAbove > spaceBelow;

        int y = above ? anchorTop - mh - dp(2) : anchorBottom + dp(2);
        y = Math.max(minY, Math.min(y, maxY - mh));
        int x = loc[0] + anchor.getWidth() - mw;
        x = Math.max(margin, Math.min(x, screenW - mw - margin));

        window = new PopupWindow(wrap, ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, true);
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.setOutsideTouchable(true);

        // animasi masuk: scale + fade dari sudut yang paling dekat anchor
        wrap.setPivotX(mw);
        wrap.setPivotY(above ? mh : 0);
        wrap.setScaleX(0.88f);
        wrap.setScaleY(0.88f);
        wrap.setAlpha(0f);

        window.showAtLocation(anchor, Gravity.NO_GRAVITY, x, y);

        wrap.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(170)
                .setInterpolator(new DecelerateInterpolator(1.8f)).start();
    }

    private View buildRow(final Item it) {
        if (it.divider) {
            View d = new View(ctx);
            d.setBackgroundColor(withAlpha(Theme.ON, 0x24));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
            lp.setMargins(dp(8), dp(6), dp(8), dp(6));
            d.setLayoutParams(lp);
            return d;
        }

        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(48));
        row.setPadding(dp(12), 0, dp(16), 0);
        row.setClickable(it.enabled);
        row.setAlpha(it.enabled ? 1f : 0.38f);
        if (it.enabled) row.setBackground(pillRipple());

        if (it.icon != 0) {
            ImageView icon = new ImageView(ctx);
            icon.setImageDrawable(Ui.tinted(ctx, it.icon, Theme.ON_VAR));
            row.addView(icon, new LinearLayout.LayoutParams(dp(24), dp(24)));
        }

        TextView t = new TextView(ctx);
        t.setText(it.title);
        t.setTextSize(15f);
        t.setTextColor(Theme.ON);
        t.setTypeface(Fonts.regular(ctx));
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tl.leftMargin = it.icon != 0 ? dp(14) : 0;
        row.addView(t, tl);

        if (it.checkable) {
            ImageView check = new ImageView(ctx);
            check.setImageDrawable(Ui.tinted(ctx, R.drawable.ic_check, Theme.PRIMARY));
            check.setVisibility(it.checked ? View.VISIBLE : View.INVISIBLE);
            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(dp(20), dp(20));
            cl.leftMargin = dp(16);
            row.addView(check, cl);
            if (it.checked) t.setTextColor(Theme.PRIMARY);
        }

        if (it.enabled) {
            row.setOnClickListener(v -> {
                dismiss();
                if (listener != null) listener.onItem(it.id);
            });
        }
        return row;
    }

    private RippleDrawable pillRipple() {
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(Color.BLACK);
        mask.setCornerRadius(dp(20));
        return new RippleDrawable(ColorStateList.valueOf(Theme.RIPPLE), null, mask);
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    private int dp(float v) {
        return (int) (v * density + 0.5f);
    }
}
