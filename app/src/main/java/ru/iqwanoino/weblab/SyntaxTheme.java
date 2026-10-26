package ru.iqwanoino.weblab;

import android.graphics.Color;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Tema editor, dibaca dari assets/syntax/themes/*.json.
 *
 * Format:
 *  {
 *    "name": "Oren Dracula",
 *    "editor": { "background": "#FF0E1312", "text": "#FFE1E1DC", ... },
 *    "tokens": { "keyword": "#FFF4796E", "string": "#FF9CCB7C", ... }
 *  }
 * Nilai warna memakai format Color.parseColor: #RRGGBB atau #AARRGGBB.
 */
public final class SyntaxTheme {
    private static final String TAG = "SyntaxTheme";

    public final String name;
    private final Map<String, Integer> editor;
    private final Map<String, Integer> tokens;

    SyntaxTheme(String name, Map<String, Integer> editor, Map<String, Integer> tokens) {
        this.name = name;
        this.editor = editor;
        this.tokens = tokens;
    }

    static SyntaxTheme empty(String name) {
        return new SyntaxTheme(name, new HashMap<String, Integer>(), new HashMap<String, Integer>());
    }

    static SyntaxTheme parse(String fallbackName, String json) {
        try {
            JSONObject o = new JSONObject(json);
            return new SyntaxTheme(o.optString("name", fallbackName),
                    colors(o.optJSONObject("editor")),
                    colors(o.optJSONObject("tokens")));
        } catch (JSONException e) {
            Log.e(TAG, "JSON tema '" + fallbackName + "' tidak valid", e);
            return null;
        }
    }

    private static Map<String, Integer> colors(JSONObject o) {
        Map<String, Integer> m = new HashMap<>();
        if (o == null) return m;
        Iterator<String> keys = o.keys();
        while (keys.hasNext()) {
            String k = keys.next();
            try {
                m.put(k, Color.parseColor(o.getString(k)));
            } catch (IllegalArgumentException | JSONException e) {
                Log.w(TAG, "Warna tidak valid untuk '" + k + "'");
            }
        }
        return m;
    }

    /** Warna elemen editor (background, gutter, ...). Kunci yang tidak ada akan tampil magenta. */
    public int editor(String key) {
        Integer c = editor.get(key);
        if (c != null) return c;
        Log.w(TAG, "Kunci editor '" + key + "' tidak ada di tema '" + name + "'");
        return Color.MAGENTA;
    }

    /** Seperti editor(), tetapi memakai nilai cadangan (tanpa log/magenta) bila kunci tidak ada. */
    public int editorOr(String key, int fallback) {
        Integer c = editor.get(key);
        return c != null ? c : fallback;
    }

    /** Warna token untuk sebuah scope. Scope yang tidak diatur memakai warna teks. */
    public int token(String scope) {
        Integer c = tokens.get(scope);
        return c != null ? c : editor("text");
    }
}
