package ru.iqwanoino.weblab;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.Layout;
import android.text.Spannable;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputConnectionWrapper;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Editor HTML gaya Sora Editor Lite: gutter nomor baris yang ikut ter-scroll, sorot baris aktif,
 * auto-indent, word wrap opsional, melengkapi simbol otomatis, dan bar simbol yang meluas
 * sampai navigation bar. Palet otomatis memakai GitHub Dark atau GitHub Light sesuai tema aplikasi.
 */
public class HtmlCodeEditor extends LinearLayout {

    private static final String VOID_TAGS =
            " area base br col embed hr img input link meta param source track wbr ";
    private static final String[] SYMBOLS = {
            "Tab", "<", ">", "/", "=", "\"", "'", "{", "}", "(", ")", "[", "]", ";", ":", "&", "!", "-", "_"
    };

    // Palet warna editor. Diisi ulang oleh applyPalette() sesuai tema.
    private int BG, GUTTER, NUMBER, NUMBER_ACTIVE, DIVIDER, LINE_HI, TEXT, TAG, ATTR, VALUE,
            COMMENT, DOCTYPE, PUNCT, ENTITY, KEY_BG, SELECTION;

    private final float density;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable highlightRunnable = new Runnable() {
        @Override public void run() { highlightSyntax(); }
    };

    private final CodeEditText editor;
    private final Gutter gutter;
    private final HorizontalScrollView hScroll;
    private LinearLayout symbolBar;

    private boolean lineNumbers, highlightLine, autoIndent, wrap;
    private int pendingNewlineAt = -1;

    /** true saat teks diisi dari kode (buka file, undo, rapikan, simbol bar): pasangan simbol dilewati. */
    private boolean programmatic;
    private int pendingSel = -1;

    /** Melengkapi simbol: ( [ { " ' otomatis berpasangan, dan tombol hapus ikut menghapus pasangannya. */
    private final InputFilter pairFilter = new InputFilter() {
        @Override
        public CharSequence filter(CharSequence src, int start, int end,
                                   Spanned dest, int dstart, int dend) {
            if (programmatic || end - start != 1 || !Prefs.autoPair()) return null;
            char c = src.charAt(start);
            char prev = dstart > 0 ? dest.charAt(dstart - 1) : '\0';
            char next = dend < dest.length() ? dest.charAt(dend) : '\0';
            boolean hasSel = dend > dstart;

            if (c == '\n') {
                // Enter di antara ( [ { dan penutupnya: buat blok kosong di tengah
                if (!isOpen(prev) || next != closeOf(prev)) return null;
                int ls = dstart;
                while (ls > 0 && dest.charAt(ls - 1) != '\n') ls--;
                int ie = ls;
                while (ie < dstart && (dest.charAt(ie) == ' ' || dest.charAt(ie) == '\t')) ie++;
                String indent = dest.subSequence(ls, ie).toString();
                moveCaretLater(dstart + 1 + indent.length() + 4);
                return "\n" + indent + "    \n" + indent;
            }
            if (isOpen(c)) {
                if (hasSel) return c + dest.subSequence(dstart, dend).toString() + closeOf(c);
                if (next == '\0' || Character.isWhitespace(next) || isClose(next) || next == ';' || next == ',') {
                    moveCaretLater(dstart + 1);
                    return String.valueOf(c) + closeOf(c);
                }
                return null;
            }
            if (isClose(c) && !hasSel && next == c) {
                moveCaretLater(dstart + 1);
                return "";
            }
            if (isQuote(c)) {
                if (hasSel) return c + dest.subSequence(dstart, dend).toString() + c;
                if (next == c) {
                    moveCaretLater(dstart + 1);
                    return "";
                }
                if (isWord(prev) || prev == c || prev == '\\') return null;
                if (next == '\0' || Character.isWhitespace(next) || isClose(next) || next == ';' || next == ',') {
                    moveCaretLater(dstart + 1);
                    return String.valueOf(c) + c;
                }
            }
            return null;
        }
    };

    public HtmlCodeEditor(Context context) {
        super(context);
        density = context.getResources().getDisplayMetrics().density;
        applyPalette(Theme.dark);
        lineNumbers = Prefs.lineNumbers();
        highlightLine = Prefs.highlightLine();
        autoIndent = Prefs.autoIndent();
        wrap = Prefs.wordWrap();

        setOrientation(VERTICAL);
        setBackgroundColor(BG);

        editor = new CodeEditText(context);
        editor.setBackground(null);
        editor.setTypeface(Typeface.MONOSPACE);
        editor.setTextSize(TypedValue.COMPLEX_UNIT_SP, Prefs.fontSize());
        editor.setTextColor(TEXT);
        editor.setHighlightColor(SELECTION);
        editor.setPadding(dp(8), dp(10), dp(16), dp(10));
        editor.setLineSpacing(0f, 1.2f);
        editor.setGravity(Gravity.TOP | Gravity.START);
        editor.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        editor.setFocusableInTouchMode(true);
        editor.setFilters(new InputFilter[]{pairFilter});
        InoInputLayout.tintCursor(editor);
        editor.setHorizontallyScrolling(!wrap);

        hScroll = new HorizontalScrollView(context);
        hScroll.setFillViewport(true);
        hScroll.setHorizontalScrollBarEnabled(false);
        hScroll.addView(editor, new FrameLayout.LayoutParams(
                wrap ? ViewGroupParams.MATCH : ViewGroupParams.WRAP, ViewGroupParams.WRAP));

        gutter = new Gutter(context);
        gutter.syncTextSize(editor.getTextSize());

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(HORIZONTAL);
        row.addView(gutter, new LinearLayout.LayoutParams(dp(40), ViewGroupParams.MATCH));
        row.addView(hScroll, new LinearLayout.LayoutParams(0, ViewGroupParams.WRAP, 1f));
        gutter.setVisibility(lineNumbers ? VISIBLE : GONE);

        ScrollView vScroll = new ScrollView(context);
        vScroll.setFillViewport(true);
        vScroll.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
        vScroll.addView(row, new FrameLayout.LayoutParams(ViewGroupParams.MATCH, ViewGroupParams.WRAP));
        addView(vScroll, new LinearLayout.LayoutParams(ViewGroupParams.MATCH, 0, 1f));

        addView(buildSymbolBar(context), new LinearLayout.LayoutParams(ViewGroupParams.MATCH, ViewGroupParams.WRAP));

        editor.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (autoIndent && count == 1 && before == 0 && start < s.length() && s.charAt(start) == '\n') {
                    pendingNewlineAt = start;
                }
            }

            @Override public void afterTextChanged(Editable s) {
                if (pendingNewlineAt >= 0) {
                    int nl = pendingNewlineAt;
                    pendingNewlineAt = -1;
                    applyAutoIndent(s, nl);
                }
                onContentChanged();
            }
        });
        onContentChanged();
    }

    // ----- API publik -----

    public Editable getText() { return editor.getText(); }

    /** Mengisi teks dari kode: pasangan simbol tidak ikut ditambahkan. */
    public void setText(CharSequence text) {
        programmatic = true;
        try {
            editor.setText(text);
        } finally {
            programmatic = false;
        }
    }

    public EditText getEditText() { return editor; }

    public void setSelection(int index) { editor.setSelection(index); }

    public void setSelection(int start, int end) { editor.setSelection(start, end); }

    /** Tinggi navigation bar (px). Bar simbol naik di atasnya, latar tetap meluas ke bawah. */
    public void setBottomInset(int px) {
        symbolBar.setPadding(dp(6), dp(6), dp(6), dp(6) + px);
    }

    // ----- palet -----

    /** Palet GitHub Dark (gelap) atau GitHub Light (terang), dengan warna sintaks yang sama dengan GitHub. */
    private void applyPalette(boolean dark) {
        if (dark) {
            BG = 0xFF0D1117;
            GUTTER = 0xFF0D1117;
            NUMBER = 0xFF6E7681;
            NUMBER_ACTIVE = 0xFFC9D1D9;
            DIVIDER = 0xFF30363D;
            LINE_HI = 0xFF161B22;
            TEXT = 0xFFC9D1D9;
            TAG = 0xFF7EE787;
            ATTR = 0xFF79C0FF;
            VALUE = 0xFFA5D6FF;
            COMMENT = 0xFF8B949E;
            DOCTYPE = 0xFFFF7B72;
            PUNCT = 0xFFC9D1D9;
            ENTITY = 0xFF79C0FF;
            KEY_BG = 0xFF21262D;
            SELECTION = 0x661F6FEB;
        } else {
            BG = 0xFFFFFFFF;
            GUTTER = 0xFFF6F8FA;
            NUMBER = 0xFF8C959F;
            NUMBER_ACTIVE = 0xFF24292F;
            DIVIDER = 0xFFD0D7DE;
            LINE_HI = 0x0F000000;
            TEXT = 0xFF24292F;
            TAG = 0xFF116329;
            ATTR = 0xFF0550AE;
            VALUE = 0xFF0A3069;
            COMMENT = 0xFF6E7781;
            DOCTYPE = 0xFFCF222E;
            PUNCT = 0xFF24292F;
            ENTITY = 0xFF0550AE;
            KEY_BG = 0xFFEAEEF2;
            SELECTION = 0x66B6D7FF;
        }
    }

    // ----- internal -----

    private int dp(float v) { return Math.round(v * density); }

    private static final class ViewGroupParams {
        static final int MATCH = -1, WRAP = -2;
    }

    private void onContentChanged() {
        updateGutterWidth();
        gutter.invalidate();
        handler.removeCallbacks(highlightRunnable);
        handler.postDelayed(highlightRunnable, 150);
    }

    private void updateGutterWidth() {
        if (!lineNumbers) return;
        int lines = 1;
        CharSequence t = editor.getText();
        for (int i = 0; i < t.length(); i++) {
            if (t.charAt(i) == '\n') lines++;
        }
        int digits = Math.max(3, String.valueOf(lines).length());
        int w = Math.round(digits * gutter.charWidth()) + dp(20);
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) gutter.getLayoutParams();
        if (lp.width != w) {
            lp.width = w;
            gutter.setLayoutParams(lp);
        }
    }

    /** Salin indentasi baris sebelumnya, dan tambah satu level setelah tag pembuka. */
    private void applyAutoIndent(Editable s, int nl) {
        int lineStart = 0;
        for (int i = nl - 1; i >= 0; i--) {
            if (s.charAt(i) == '\n') {
                lineStart = i + 1;
                break;
            }
        }
        String prev = s.subSequence(lineStart, nl).toString();
        StringBuilder indent = new StringBuilder();
        for (int i = 0; i < prev.length(); i++) {
            char ch = prev.charAt(i);
            if (ch == ' ' || ch == '\t') indent.append(ch);
            else break;
        }
        if (opensTag(prev.trim())) indent.append("    ");
        if (indent.length() == 0) return;
        s.insert(nl + 1, indent);
        editor.setSelection(nl + 1 + indent.length());
    }

    private static boolean opensTag(String t) {
        if (!t.startsWith("<") || !t.endsWith(">")) return false;
        if (t.startsWith("</") || t.startsWith("<!") || t.startsWith("<?") || t.endsWith("/>")) return false;
        if (t.contains("</")) return false;
        String name = t.substring(1).split("[\\s>/]", 2)[0].toLowerCase(Locale.ROOT);
        return !name.isEmpty() && !VOID_TAGS.contains(" " + name + " ");
    }

    // ----- pasangan simbol -----

    private static boolean isOpen(char c) { return c == '(' || c == '[' || c == '{'; }

    private static boolean isClose(char c) { return c == ')' || c == ']' || c == '}'; }

    private static char closeOf(char c) { return c == '(' ? ')' : c == '[' ? ']' : '}'; }

    private static boolean isQuote(char c) { return c == '"' || c == '\''; }

    private static boolean isWord(char c) { return Character.isLetterOrDigit(c) || c == '_'; }

    private void moveCaretLater(final int pos) {
        pendingSel = pos;
        editor.post(new Runnable() {
            @Override public void run() {
                int p = pendingSel;
                pendingSel = -1;
                if (p >= 0 && p <= editor.getText().length()) editor.setSelection(p);
            }
        });
    }

    /** Hapus sepasang simbol yang mengapit kursor, misalnya "()" atau "\"\"". */
    private boolean deletePairAtCursor() {
        if (programmatic || !Prefs.autoPair()) return false;
        Editable t = editor.getText();
        int s = editor.getSelectionStart();
        if (s != editor.getSelectionEnd() || s <= 0 || s >= t.length()) return false;
        char p = t.charAt(s - 1), n = t.charAt(s);
        if ((isOpen(p) && closeOf(p) == n) || (isQuote(p) && p == n)) {
            t.delete(s - 1, s + 1);
            return true;
        }
        return false;
    }

    // ----- bar simbol -----

    private View buildSymbolBar(Context c) {
        HorizontalScrollView bar = new HorizontalScrollView(c);
        bar.setHorizontalScrollBarEnabled(false);
        bar.setBackgroundColor(GUTTER);

        symbolBar = new LinearLayout(c);
        symbolBar.setOrientation(HORIZONTAL);
        symbolBar.setGravity(Gravity.CENTER_VERTICAL);
        symbolBar.setPadding(dp(6), dp(6), dp(6), dp(6));

        for (final String sym : SYMBOLS) {
            TextView key = new TextView(c);
            key.setText(sym);
            key.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            key.setTextSize(14);
            key.setTextColor(TEXT);
            key.setGravity(Gravity.CENTER);
            key.setMinWidth(dp(42));
            key.setMinHeight(dp(36));
            key.setPadding(dp(10), dp(8), dp(10), dp(8));
            key.setBackground(Ui.ripple(KEY_BG, 8));
            key.setClickable(true);
            key.setFocusable(false);
            key.setOnClickListener(v -> insertAtCursor(sym.equals("Tab") ? "    " : sym));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroupParams.WRAP, ViewGroupParams.WRAP);
            lp.setMargins(dp(3), 0, dp(3), 0);
            symbolBar.addView(key, lp);
        }
        bar.addView(symbolBar);
        return bar;
    }

    /** Sisipkan simbol dari bar tanpa menambah pasangan otomatis. */
    private void insertAtCursor(String text) {
        Editable e = editor.getText();
        int a = editor.getSelectionStart(), b = editor.getSelectionEnd();
        if (a < 0) {
            a = e.length();
            b = a;
        }
        int s = Math.min(a, b), end = Math.max(a, b);
        programmatic = true;
        try {
            e.replace(s, end, text);
        } finally {
            programmatic = false;
        }
        editor.setSelection(s + text.length());
        editor.requestFocus();
    }

    // ----- sorot sintaks -----

    /** Sintaks HTML dengan warna sesuai palet aktif. Berjalan setelah jeda singkat. */
    private void highlightSyntax() {
        Editable text = editor.getText();
        if (text == null) return;

        ForegroundColorSpan[] spans = text.getSpans(0, text.length(), ForegroundColorSpan.class);
        for (ForegroundColorSpan old : spans) text.removeSpan(old);

        String src = text.toString();
        if (src.isEmpty()) return;

        text.setSpan(new ForegroundColorSpan(TEXT), 0, text.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        applyPattern(text, src, "<!DOCTYPE.*?>", DOCTYPE, Pattern.CASE_INSENSITIVE);
        applyPattern(text, src, "<!--(.*?)-->", COMMENT, Pattern.DOTALL);
        applyPattern(text, src, "</?[A-Za-z][A-Za-z0-9_-]*", TAG, 0);
        applyPattern(text, src, "\\s+[A-Za-z_-][A-Za-z0-9_-]*(?=\\s*=)", ATTR, 0);
        applyPattern(text, src, "=(\"[^\"]*\"|'[^']*')", VALUE, 0);
        applyPattern(text, src, "[<>/=]", PUNCT, 0);
        applyPattern(text, src, "[\"']", VALUE, 0);
        applyPattern(text, src, "&[A-Za-z]+;", ENTITY, 0);
        applyPattern(text, src, "<\\?.*?\\?>", TAG, Pattern.DOTALL);
    }

    private static void applyPattern(Editable text, String src, String regex, int color, int flags) {
        try {
            Matcher m = Pattern.compile(regex, flags).matcher(src);
            while (m.find()) {
                text.setSpan(new ForegroundColorSpan(color), m.start(), m.end(),
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        } catch (Exception ignored) { }
    }

    // ----- gutter nomor baris -----

    private static boolean isLogicalStart(CharSequence text, int lineStart) {
        return lineStart == 0 || text.charAt(lineStart - 1) == '\n';
    }

    private final class Gutter extends View {
        private final Paint numPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint linePaint = new Paint();

        Gutter(Context c) {
            super(c);
            setBackgroundColor(GUTTER);
            numPaint.setTypeface(Typeface.MONOSPACE);
            numPaint.setTextAlign(Paint.Align.RIGHT);
            linePaint.setColor(DIVIDER);
            linePaint.setStrokeWidth(dp(1));
        }

        void syncTextSize(float px) { numPaint.setTextSize(px); }

        float charWidth() { return numPaint.measureText("0"); }

        @Override
        protected void onDraw(Canvas c) {
            super.onDraw(c);
            Layout l = editor.getLayout();
            if (l == null) return;
            CharSequence text = editor.getText();
            int lines = l.getLineCount();

            int cursorVisual = l.getLineForOffset(Math.max(0, editor.getSelectionStart()));
            int activeNo = -1, no = 0;
            for (int v = 0; v < lines; v++) {
                if (isLogicalStart(text, l.getLineStart(v))) no++;
                if (v == cursorVisual) {
                    activeNo = no;
                    break;
                }
            }

            Rect clip = new Rect();
            c.getClipBounds(clip);
            int width = getWidth();
            float top = editor.getTotalPaddingTop();
            no = 0;
            for (int v = 0; v < lines; v++) {
                if (!isLogicalStart(text, l.getLineStart(v))) continue;
                no++;
                float base = top + l.getLineBaseline(v);
                if (base < clip.top - dp(40)) continue;
                if (base > clip.bottom + dp(40)) break;
                numPaint.setColor(no == activeNo ? NUMBER_ACTIVE : NUMBER);
                c.drawText(String.valueOf(no), width - dp(10), base, numPaint);
            }
            c.drawLine(width - 0.5f, clip.top, width - 0.5f, clip.bottom, linePaint);
        }
    }

    // ----- editor -----

    private final class CodeEditText extends EditText {
        private final Paint hiPaint = new Paint();

        CodeEditText(Context c) {
            super(c);
            hiPaint.setStyle(Paint.Style.FILL);
        }

        @Override
        public InputConnection onCreateInputConnection(EditorInfo out) {
            InputConnection ic = super.onCreateInputConnection(out);
            if (ic == null) return null;
            return new InputConnectionWrapper(ic, true) {
                @Override public boolean deleteSurroundingText(int before, int after) {
                    if (before == 1 && after == 0 && deletePairAtCursor()) return true;
                    return super.deleteSurroundingText(before, after);
                }

                @Override public boolean sendKeyEvent(KeyEvent e) {
                    if (e.getAction() == KeyEvent.ACTION_DOWN && e.getKeyCode() == KeyEvent.KEYCODE_DEL
                            && deletePairAtCursor()) return true;
                    return super.sendKeyEvent(e);
                }
            };
        }

        @Override
        public boolean onKeyDown(int keyCode, KeyEvent e) {
            if (keyCode == KeyEvent.KEYCODE_DEL && !e.isCtrlPressed() && deletePairAtCursor()) return true;
            return super.onKeyDown(keyCode, e);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            Layout l = getLayout();
            int sel = getSelectionStart();
            if (highlightLine && isFocused() && l != null && sel >= 0 && sel == getSelectionEnd()) {
                int line = l.getLineForOffset(sel);
                float top = getTotalPaddingTop() + l.getLineTop(line);
                float bottom = getTotalPaddingTop() + l.getLineBottom(line);
                hiPaint.setColor(LINE_HI);
                canvas.drawRect(0, top, getWidth(), bottom, hiPaint);
            }
            super.onDraw(canvas);
        }

        @Override
        protected void onSelectionChanged(int selStart, int selEnd) {
            super.onSelectionChanged(selStart, selEnd);
            invalidate();
            if (isFocused()) {
                post(new Runnable() {
                    @Override public void run() { scrollCursorIntoView(); }
                });
            }
        }

        void scrollCursorIntoView() {
            Layout l = getLayout();
            int off = getSelectionStart();
            if (l == null || off < 0 || off > length()) return;
            int line = l.getLineForOffset(off);
            int top = getTotalPaddingTop() + l.getLineTop(line);
            int bottom = getTotalPaddingTop() + l.getLineBottom(line);
            int x = (int) (getTotalPaddingLeft() + l.getPrimaryHorizontal(off));
            requestRectangleOnScreen(new Rect(x - dp(24), top - dp(24), x + dp(24), bottom + dp(24)), false);
        }
    }
}
