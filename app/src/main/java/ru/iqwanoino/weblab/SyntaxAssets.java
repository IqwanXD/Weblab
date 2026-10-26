package ru.iqwanoino.weblab;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Pemuat tema dan bahasa dari assets/syntax. Hasilnya di-cache.
 *  - assets/syntax/languages/*.json  : semua bahasa (dikenali dari extensions / filenames)
 *  - assets/syntax/themes/NAMA.json  : tema warna
 * Menambah bahasa atau tema cukup dengan menaruh file JSON baru di folder tersebut.
 */
public final class SyntaxAssets {
    private static final String TAG = "SyntaxAssets";

    /** Nama tema default (tanpa .json). */
    public static final String DEFAULT_THEME = "github-dark";
    /** Tema terang bawaan saat app memakai tema terang. */
    public static final String LIGHT_THEME = "github-light";

    /** Tema syntax otomatis: gelap memakai Oren Dracula, terang memakai GitHub Light. */
    public static String autoTheme(boolean darkApp) {
        return darkApp ? DEFAULT_THEME : LIGHT_THEME;
    }

    private static final String LANG_DIR  = "syntax/languages";
    private static final String THEME_DIR = "syntax/themes";

    private static final Map<String, SyntaxTheme>    themes = new HashMap<>();
    private static final Map<String, Language> byExt  = new HashMap<>();
    private static final Map<String, Language> byFile = new HashMap<>();
    private static final Map<String, Language> byName = new HashMap<>();
    private static boolean languagesLoaded;

    private SyntaxAssets() {}

    public static synchronized SyntaxTheme theme(Context c, String name) {
        SyntaxTheme t = themes.get(name);
        if (t != null) return t;

        String json = readAsset(c, THEME_DIR + "/" + name + ".json");
        if (json != null) t = SyntaxTheme.parse(name, json);
        if (t == null) {
            Log.e(TAG, "Tema '" + name + "' gagal dimuat");
            if (!DEFAULT_THEME.equals(name)) return theme(c, DEFAULT_THEME);
            t = SyntaxTheme.empty(name);
        }
        themes.put(name, t);
        return t;
    }

    /**
     * Daftar nama tema yang tersedia di assets/syntax/themes (tanpa .json), sudah diurutkan.
     * Dipakai untuk membangun daftar pilihan tema di UI.
     */
    public static synchronized List<String> themeNames(Context c) {
        List<String> out = new ArrayList<>();
        try {
            String[] files = assets(c).list(THEME_DIR);
            if (files != null) {
                for (String f : files) {
                    if (f.endsWith(".json")) out.add(f.substring(0, f.length() - 5));
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Folder tema tidak bisa dibaca", e);
        }
        Collections.sort(out);
        return out;
    }

    /** Nama tampilan sebuah tema (field "name" di JSON), atau nama file jika tidak ada. */
    public static String themeTitle(Context c, String name) {
        return theme(c, name).name;
    }

    /** Bahasa berdasarkan nama bahasanya (mis. "javascript"), dipakai untuk blok embed. */
    public static synchronized Language languageByName(Context c, String name) {
        loadLanguages(c);
        return byName.get(name);
    }

    /** Bahasa untuk sebuah nama file, atau null jika tidak dikenali (teks polos). */
    public static synchronized Language languageFor(Context c, String fileName) {
        if (fileName == null || fileName.isEmpty()) return null;
        loadLanguages(c);

        Language l = byFile.get(fileName);
        if (l != null) return l;

        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot + 1 >= fileName.length()) return null;
        return byExt.get(fileName.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    private static void loadLanguages(Context c) {
        if (languagesLoaded) return;
        languagesLoaded = true;

        String[] files;
        try {
            files = assets(c).list(LANG_DIR);
        } catch (IOException e) {
            Log.e(TAG, "Folder bahasa tidak bisa dibaca", e);
            return;
        }
        if (files == null) return;

        for (String f : files) {
            if (!f.endsWith(".json")) continue;
            Language lang = Language.parse(readAsset(c, LANG_DIR + "/" + f));
            if (lang == null) continue;
            byName.put(lang.name, lang);
            for (String e : lang.extensions) byExt.put(e.toLowerCase(Locale.ROOT), lang);
            for (String n : lang.fileNames)  byFile.put(n, lang);
        }
    }

    private static AssetManager assets(Context c) {
        return c.getApplicationContext().getAssets();
    }

    private static String readAsset(Context c, String path) {
        try (InputStream in = assets(c).open(path)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            Log.w(TAG, "Aset tidak bisa dibaca: " + path, e);
            return null;
        }
    }
}
