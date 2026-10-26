package ru.iqwanoino.weblab;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Rapikan HTML (diambil dari JetEditor, disederhanakan untuk HTML saja): satu tag per baris,
 * indentasi 4 spasi, dan teks pendek tetap sebaris dengan tag pembukanya.
 * Isi script, style, pre, dan textarea dibiarkan apa adanya supaya kode JavaScript
 * atau teks yang sudah diformat tidak rusak.
 */
final class HtmlPrettyPrinter {

    private static final String IND = "    ";
    private static final int T_TEXT = 0, T_OPEN = 1, T_CLOSE = 2, T_SELF = 3, T_RAW = 4, T_BLOCK = 5;
    /** Elemen yang isinya tidak boleh diubah. */
    private static final String KEEP_TAGS = " script style pre textarea ";
    /** Elemen yang penutupnya harus menempel pada isinya, karena spasi di dalamnya bermakna. */
    private static final String TIGHT_TAGS = " pre textarea ";
    /** Elemen void: tidak punya penutup, jadi tidak menambah indentasi. */
    private static final String VOID_TAGS =
            " area base br col embed hr img input link meta param source track wbr ";

    private HtmlPrettyPrinter() { }

    static String format(String s) {
        List<String> toks = new ArrayList<>();
        List<Integer> kinds = new ArrayList<>();
        int n = s.length();
        int i = 0;
        while (i < n) {
            if (isTagStart(s, i)) {
                int end;
                if (s.startsWith("<!--", i)) {
                    end = s.indexOf("-->", i);
                    end = end < 0 ? n : end + 3;
                    add(toks, kinds, s.substring(i, end), T_RAW);
                } else if (s.startsWith("<![CDATA[", i)) {
                    end = s.indexOf("]]>", i);
                    end = end < 0 ? n : end + 3;
                    add(toks, kinds, s.substring(i, end), T_RAW);
                } else {
                    end = tagEnd(s, i);
                    String tag = collapse(s.substring(i, end));
                    int kind;
                    if (tag.startsWith("</")) kind = T_CLOSE;
                    else if (tag.startsWith("<?") || tag.startsWith("<!")) kind = T_RAW;
                    else if (tag.endsWith("/>")) kind = T_SELF;
                    else kind = T_OPEN;
                    if (kind == T_OPEN && VOID_TAGS.contains(" " + tagName(tag) + " ")) kind = T_SELF;
                    add(toks, kinds, tag, kind);

                    if (kind == T_OPEN) {
                        String name = tagName(tag);
                        if (!name.isEmpty() && KEEP_TAGS.contains(" " + name + " ")) {
                            int close = indexOf(s, "</" + name, end);
                            if (close < 0) close = n;
                            if (close > end) {
                                toks.add(s.substring(end, close));
                                kinds.add(T_BLOCK);
                            }
                            end = close;
                        }
                    }
                }
                i = end;
            } else {
                int end = i + 1;
                while (end < n && !isTagStart(s, end)) end++;
                add(toks, kinds, s.substring(i, end), T_TEXT);
                i = end;
            }
        }

        StringBuilder out = new StringBuilder(s.length() + 64);
        int indent = 0;
        boolean first = true;
        boolean tight = false;
        for (int k = 0; k < toks.size(); k++) {
            int kind = kinds.get(k);
            String t = toks.get(k);

            // <p>teks</p> tetap sebaris
            if (kind == T_OPEN && k + 2 < toks.size()
                    && kinds.get(k + 1) == T_TEXT && kinds.get(k + 2) == T_CLOSE) {
                line(out, first, indent, t + toks.get(k + 1) + toks.get(k + 2));
                first = false;
                k += 2;
                continue;
            }
            // <div></div> tetap sebaris
            if (kind == T_OPEN && k + 1 < toks.size() && kinds.get(k + 1) == T_CLOSE) {
                line(out, first, indent, t + toks.get(k + 1));
                first = false;
                k += 1;
                continue;
            }
            // <script>...isi...</script>: isi dibiarkan apa adanya
            if (kind == T_OPEN && k + 1 < toks.size() && kinds.get(k + 1) == T_BLOCK) {
                line(out, first, indent, t);
                first = false;
                indent++;
                String body = toks.get(k + 1);
                if (k + 2 < toks.size() && kinds.get(k + 2) == T_CLOSE
                        && TIGHT_TAGS.contains(" " + tagName(t) + " ")) {
                    out.append(body);
                    tight = true;
                } else {
                    out.append(rtrim(body));
                }
                k += 1;
                continue;
            }
            if (kind == T_BLOCK) continue;

            if (kind == T_CLOSE) {
                indent = Math.max(0, indent - 1);
                if (tight) {
                    out.append(t);
                    tight = false;
                    continue;
                }
            }
            line(out, first, indent, t);
            first = false;
            if (kind == T_OPEN) indent++;
        }
        return out.length() == 0 ? "" : out.append('\n').toString();
    }

    private static boolean isTagStart(String s, int i) {
        if (s.charAt(i) != '<' || i + 1 >= s.length()) return false;
        char c = s.charAt(i + 1);
        return Character.isLetter(c) || c == '/' || c == '!' || c == '?';
    }

    /** Akhir tag: '>' pertama yang berada di luar tanda kutip atribut. */
    private static int tagEnd(String s, int i) {
        int n = s.length();
        char q = 0;
        for (int j = i + 1; j < n; j++) {
            char d = s.charAt(j);
            if (q != 0) {
                if (d == q) q = 0;
            } else if (d == '"' || d == '\'') {
                q = d;
            } else if (d == '>') {
                return j + 1;
            }
        }
        return n;
    }

    private static String tagName(String tag) {
        int j = 1;
        while (j < tag.length() && !Character.isWhitespace(tag.charAt(j))
                && tag.charAt(j) != '>' && tag.charAt(j) != '/') {
            j++;
        }
        return tag.substring(1, j).toLowerCase(Locale.ROOT);
    }

    private static int indexOf(String s, String needle, int from) {
        for (int j = from; j + needle.length() <= s.length(); j++) {
            if (s.regionMatches(true, j, needle, 0, needle.length())) return j;
        }
        return -1;
    }

    private static void add(List<String> toks, List<Integer> kinds, String t, int kind) {
        if (kind == T_TEXT) {
            t = t.trim().replaceAll("\\s*\\r?\\n\\s*", " ");
            if (t.isEmpty()) return;
        }
        toks.add(t);
        kinds.add(kind);
    }

    private static void line(StringBuilder out, boolean first, int indent, String text) {
        if (!first) out.append('\n');
        for (int i = 0; i < indent; i++) out.append(IND);
        out.append(text);
    }

    /** Spasi dan baris baru di dalam satu tag dirapikan jadi satu spasi. Nilai atribut tetap. */
    private static String collapse(String tag) {
        return tag.replaceAll("\\s*\\r?\\n\\s*", " ").replaceAll("[ \\t]{2,}", " ");
    }

    private static String rtrim(String s) {
        int e = s.length();
        while (e > 0 && Character.isWhitespace(s.charAt(e - 1))) e--;
        return s.substring(0, e);
    }
}
