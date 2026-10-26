package ru.iqwanoino.weblab;

import android.content.Context;
import android.graphics.Typeface;

/** Font aplikasi dari assets/fonts/me.ttf. Tebal dibuat oleh sistem. */
final class Fonts {
    private static Typeface regular, bold;

    private Fonts() { }

    static Typeface regular(Context c) {
        load(c);
        return regular;
    }

    static Typeface bold(Context c) {
        load(c);
        return bold;
    }

    private static void load(Context c) {
        if (regular != null) return;
        Typeface t;
        try {
            t = Typeface.createFromAsset(c.getApplicationContext().getAssets(), "fonts/me.ttf");
        } catch (Exception e) {
            t = Typeface.DEFAULT;
        }
        regular = t;
        bold = Typeface.create(t, Typeface.BOLD);
    }
}
