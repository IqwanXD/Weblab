package ru.iqwanoino.weblab;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Definisi bahasa, dibaca dari assets/syntax/languages/*.json.
 *
 * Format:
 *  {
 *    "name": "html",
 *    "extensions": ["html"],
 *    "filenames": [],                      // opsional: nama file persis
 *    "colonIndent": false,                 // opsional: indent otomatis setelah ':' (python)
 *    "tagIndent": false,                   // opsional: indent otomatis setelah tag pembuka HTML
 *    "hashColorAlphaFirst": false,         // opsional: #RRGGBBAA untuk preview warna
 *    "rules": [ { "scope": "comment", "pattern": "//[^\\n]*" } ],
 *    "embed": [                            // opsional: blok bahasa lain di dalam bahasa ini
 *      { "start": "<script\\b[^>]*>", "end": "</script\\s*>", "language": "javascript" }
 *    ]
 *  }
 * Urutan rules = prioritas. Pola regex tidak boleh memakai grup capture biasa "( )",
 * gunakan "(?: )". Isi blok embed diwarnai dengan aturan bahasanya sendiri.
 */
public final class Language {
    private static final String TAG = "Language";

    /** Blok bahasa lain: isi di antara start dan end diwarnai dengan bahasa {@link #language}. */
    public static final class Embed {
        public final Pattern start;
        public final Pattern end;
        public final String language;

        Embed(Pattern start, Pattern end, String language) {
            this.start = start;
            this.end = end;
            this.language = language;
        }
    }

    public final String name;
    public final List<String> extensions;
    public final List<String> fileNames;
    public final boolean colonIndent;
    public final boolean tagIndent;
    public final boolean hashColorAlphaFirst;
    /** Scope tiap rule, indeks sama dengan grup regex (grup ke-i = scopes[i-1]). */
    public final String[] scopes;
    /** Null jika tidak ada rule atau regex gagal dikompilasi. */
    public final Pattern pattern;
    public final List<Embed> embeds;

    private Language(String name, List<String> extensions, List<String> fileNames,
                     boolean colonIndent, boolean tagIndent, boolean hashColorAlphaFirst,
                     String[] scopes, Pattern pattern, List<Embed> embeds) {
        this.name = name;
        this.extensions = extensions;
        this.fileNames = fileNames;
        this.colonIndent = colonIndent;
        this.tagIndent = tagIndent;
        this.hashColorAlphaFirst = hashColorAlphaFirst;
        this.scopes = scopes;
        this.pattern = pattern;
        this.embeds = embeds;
    }

    static Language parse(String json) {
        try {
            JSONObject o = new JSONObject(json);
            String name = o.optString("name", "?");
            JSONArray rules = o.optJSONArray("rules");

            List<String> scopeList = new ArrayList<>();
            StringBuilder re = new StringBuilder();
            if (rules != null) {
                for (int i = 0; i < rules.length(); i++) {
                    JSONObject r = rules.getJSONObject(i);
                    String regex = r.has("words")
                            ? wordsRegex(strings(r.getJSONArray("words")))
                            : r.getString("pattern");
                    if (re.length() > 0) re.append('|');
                    re.append('(').append(regex).append(')');
                    scopeList.add(r.getString("scope"));
                }
            }

            Pattern p = null;
            if (re.length() > 0) {
                try {
                    p = Pattern.compile(re.toString(), Pattern.MULTILINE);
                } catch (PatternSyntaxException e) {
                    Log.e(TAG, "Regex salah di bahasa '" + name + "'", e);
                }
            }

            List<Embed> embeds = new ArrayList<>();
            JSONArray em = o.optJSONArray("embed");
            if (em != null) {
                for (int i = 0; i < em.length(); i++) {
                    JSONObject x = em.getJSONObject(i);
                    try {
                        embeds.add(new Embed(
                                Pattern.compile(x.getString("start"), Pattern.CASE_INSENSITIVE),
                                Pattern.compile(x.getString("end"), Pattern.CASE_INSENSITIVE),
                                x.getString("language")));
                    } catch (PatternSyntaxException e) {
                        Log.e(TAG, "Regex embed salah di bahasa '" + name + "'", e);
                    }
                }
            }

            return new Language(name,
                    strings(o.optJSONArray("extensions")),
                    strings(o.optJSONArray("filenames")),
                    o.optBoolean("colonIndent", false),
                    o.optBoolean("tagIndent", false),
                    o.optBoolean("hashColorAlphaFirst", false),
                    scopeList.toArray(new String[0]),
                    p,
                    embeds);
        } catch (JSONException e) {
            Log.e(TAG, "JSON bahasa tidak valid", e);
            return null;
        }
    }

    private static String wordsRegex(List<String> words) {
        StringBuilder sb = new StringBuilder("\\b(?:");
        for (int i = 0; i < words.size(); i++) {
            if (i > 0) sb.append('|');
            sb.append(Pattern.quote(words.get(i)));
        }
        return sb.append(")\\b").toString();
    }

    private static List<String> strings(JSONArray a) {
        List<String> out = new ArrayList<>();
        if (a == null) return out;
        for (int i = 0; i < a.length(); i++) {
            String s = a.optString(i, null);
            if (s != null) out.add(s);
        }
        return out;
    }
}
