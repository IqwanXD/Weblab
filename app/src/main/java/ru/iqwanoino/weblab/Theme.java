package ru.iqwanoino.weblab;

import android.content.Context;

/** Fasad warna di atas M3. Panggil load() sebelum membangun UI. */
final class Theme {
    static boolean dark = true;
    static int BG, SURFACE, SURFACE_HIGH, ON, ON_VAR, PRIMARY, ON_PRIMARY,
            PRIM_CONT, OUTLINE, RIPPLE, ROW_SEL;

    private Theme() { }

    static void setDark(Context c, boolean on) { Prefs.init(c); Prefs.setDarkTheme(on); }

    /** Hitung ulang palet dari Prefs terbaru, lalu isi field di atas. */
    static void load(Context c) {
        M3.init(c);
        dark = M3.dark;
        BG = M3.background;
        SURFACE_HIGH = M3.surfaceContainerHigh;
        SURFACE = M3.mix(M3.background, M3.surfaceContainerHigh, 0.6f);
        ON = M3.onSurface;
        ON_VAR = M3.onSurfaceVariant;
        PRIMARY = M3.primary;
        ON_PRIMARY = M3.onPrimary;
        PRIM_CONT = M3.primaryContainer;
        OUTLINE = M3.outline;
        RIPPLE = M3.rippleColor();
        ROW_SEL = M3.alpha(M3.primary, 0.2f);
    }
}
