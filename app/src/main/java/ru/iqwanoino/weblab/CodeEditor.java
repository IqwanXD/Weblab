package ru.iqwanoino.weblab;

import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.VectorDrawable;
import android.os.Build;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.Layout;
import android.text.Selection;
import android.text.Spannable;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.VelocityTracker;
import android.view.ViewConfiguration;
import android.view.ViewTreeObserver;
import android.graphics.drawable.GradientDrawable;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputConnectionWrapper;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.OverScroller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Code editor ringan ala Sora, tanpa AndroidX.
 *
 * v6:
 *  - Viewport DIAM saat kursor dipindah lewat tap / drag handle. Scroll hanya terjadi bila
 *    kursor benar-benar keluar dari area terlihat, dan hanya karena mengetik, tombol panah,
 *    edit programatik, atau keyboard muncul.
 *  - TextView tidak lagi boleh menggeser viewport sendiri (bringPointIntoView / requestRectangleOnScreen
 *    di-nonaktifkan); semua auto-scroll lewat ensureCursorVisible().
 *  - Animasi scroll lebih panjang (200 ms) agar terasa halus.
 *  - getThemeName() untuk menandai tema yang sedang aktif di UI.
 *
 * v5:
 *  - Tap / drag tidak lagi menarik viewport ke kursor. Auto-scroll ke kursor hanya saat
 *    mengetik atau edit programatik (insertText, undo, redo, gotoLine, dll).
 *  - Overscroll: baris terakhir bisa digeser sampai ke tengah layar (vertikal) dan area
 *    kanan lebih lega (horizontal).
 *  - Margin bawah saat mengikuti kursor dikurangi, jadi kursor di dekat bawah tidak
 *    langsung memicu scroll.
 *
 * v4:
 *  - Tema & bahasa dimuat dari assets/syntax (JSON). Tidak ada warna/regex hardcode.
 *    Tema: assets/syntax/themes/*.json, bahasa: assets/syntax/languages/*.json.
 *
 * v3:
 *  - FIX baris bawah tidak ter-render sampai scroll mentok: TextView meng-clip teks di
 *    area padding (atas/kanan/bawah) kecuali saat scroll persis di ujung. Padding bawah
 *    96dp = ~4 baris terakhir hilang. Sekarang padding atas/kanan/bawah = 0.
 *  - FIX koordinat gambar: canvas onDraw() sudah di-translate (-scrollX,-scrollY), jadi
 *    gutter & scrollbar digambar lewat canvas.translate(scrollX, scrollY).
 *  - Tema baru mengikuti editor referensi (latar hijau-gelap, keyword salmon, tipe ungu,
 *    angka/literal biru, konstanta oranye, komentar abu-abu).
 *  - Gutter menampilkan swatch warna (0xFF..., #RRGGBB, Color.rgb) dan preview
 *    R.drawable.xxx, seperti Android Studio.
 *  - Tap saat editor belum fokus -> fokus + pindah kursor + tampilkan keyboard.
 *  - Lebar baris terpanjang di-cache (sebelumnya dihitung ulang tiap frame).
 *  - Highlight saat scroll file besar di-throttle (tidak lagi di-debounce terus).
 */
public class CodeEditor extends EditText {

    // ===== Warna: diisi dari tema JSON (assets/syntax/themes/*.json) =====
    private SyntaxTheme theme;
    private String   themeName = SyntaxAssets.DEFAULT_THEME;
    private Language lang;
    private int C_BG, C_TEXT, C_SELECTION;
    private int C_GUTTER, C_GUTTER_LINE, C_NUMBER, C_NUMBER_ACTIVE;
    private int C_CURRENT_LINE, C_GUIDE, C_BRACKET_FILL, C_BRACKET_STR;
    private int C_SCROLLBAR, C_SCROLLBAR_TRACK, C_ICON_TINT, C_SWATCH_STROKE;
    private int C_HANDLE = 0xFF6FDBB8, C_MENU_BG = 0xFF222B28, C_MENU_ICON = 0xFFDDE0DC;

    private static final int LARGE_TEXT    = 60000;
    private static final int WINDOW_LINES  = 80;
    private static final int MAX_UNDO      = 400;
    private static final int MATCH_SCAN    = 20000;

    /** Dimensi scrollbar: thumb & track */
    private static final float SB_THUMB_W  = 6f;   // dp, lebar thumb aktif
    private static final float SB_TRACK_W  = 8f;   // dp, lebar area sentuh + track samar
    private static final float SB_MARGIN   = 2f;   // dp, jarak dari tepi
    private static final float SB_MIN_LEN  = 40f;  // dp, panjang thumb minimum

    /** Swatch warna / preview drawable di gutter. */
    private static final Pattern MARK = Pattern.compile(
            "0[xX]([0-9A-Fa-f]{8}|[0-9A-Fa-f]{6})\\b"
          + "|#([0-9A-Fa-f]{8}|[0-9A-Fa-f]{6})\\b"
          + "|Color\\.rgb\\(\\s*(\\d{1,3})\\s*,\\s*(\\d{1,3})\\s*,\\s*(\\d{1,3})\\s*\\)"
          + "|R\\.drawable\\.(\\w+)"
          + "|@drawable/(\\w+)");

    private static float sTextSizePx = -1f;

    private static class SyntaxSpan extends ForegroundColorSpan {
        SyntaxSpan(int c) { super(c); }
    }

    private static class Edit {
        int start; String removed, added; long time;
    }

    // ===== State =====
    private boolean ready;
    private boolean loading;
    private boolean applying;
    private boolean highlighting;
    private boolean wordWrap;
    private boolean smartEdit  = true;
    private boolean autoIndent = true;
    private boolean showLineNumbers = true;
    private boolean highlightCurrentLine = true;
    /** Tag void HTML: tidak menambah indentasi setelahnya. */
    private static final String VOID_TAGS =
            " area base br col embed hr img input link meta param source track wbr ";
    private String  fileName   = "";

    private final Paint numPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF tmp       = new RectF();

    private int  gutterWidth;
    private int  padTop, padRight, padBottom;   // padding non-gutter (gutter = kiri)
    private int  logicalLines = 1;
    private int[] lineNoCache;
    private boolean lineNoDirty = true;

    private Pattern   syntax;

    private final ArrayList<Edit> undoStack = new ArrayList<>();
    private final ArrayList<Edit> redoStack = new ArrayList<>();
    private String pendingRemoved = "";

    private int matchA = -1, matchB = -1;
    private int hlFrom = -1, hlTo = -1;

    // ---- scroll 2-D bebas ----
    private OverScroller    flingScroller;
    private GestureDetector gestureDetector;
    private boolean         freeScrolling;   // true = tidak fokus, mode scroll 2D
    private float           lastFreeX, lastFreeY;
    private boolean         freeDragging;

    // ---- scroll 2-D bebas saat FOKUS (menggantikan scroll bawaan TextView) ----
    private VelocityTracker focusVt;
    private boolean         focusTaking;      // true = kita yang menggeser, bukan TextView
    private float           focusDownX, focusDownY, focusLastX, focusLastY;
    private int             touchSlop, minFlingV, maxFlingV;

    // ---- scrollbar ----
    private boolean  sbVDragging, sbHDragging;
    private float    sbVGrabOff, sbHGrabOff;
    private float    sbAlpha;
    private long     sbShownAt;
    private boolean  sbVisible;

    // ---- zoom ----
    private ScaleGestureDetector scaler;
    private boolean scaling;
    /** Posisi scroll yang harus dipasang setelah layout ulang akibat zoom (titik fokus jari tetap). */
    private boolean zoomAnchorPending;
    private int zoomAnchorX, zoomAnchorY;

    private Runnable saveListener;

    // ---- cache / gutter ----
    private boolean hlPending;
    private boolean widthDirty = true;
    private float   maxLineW;
    private int     digitsW;
    private final Matcher markMatcher = MARK.matcher("");
    private final HashMap<String, Drawable> iconCache = new HashMap<String, Drawable>();

    private final Runnable hlTask = new Runnable() {
        public void run() { hlPending = false; highlightNow(); }
    };
    private final Runnable fadeTask = new Runnable() {
        public void run() {
            long age = System.currentTimeMillis() - sbShownAt;
            if (age < 1200) { postDelayed(fadeTask, 50); return; }
            float next = sbAlpha - 0.08f;
            if (next <= 0f) { sbAlpha = 0f; sbVisible = false; }
            else { sbAlpha = next; sbVisible = true; postDelayed(fadeTask, 16); }
            invalidate();
        }
    };

    public CodeEditor(Context c)                  { super(c);       init(); }
    public CodeEditor(Context c, AttributeSet a)  { super(c, a);    init(); }

    // =====================================================================
    // Init
    // =====================================================================

    private void init() {
        theme = SyntaxAssets.theme(getContext(), SyntaxAssets.DEFAULT_THEME);
        applyTheme();
        setTypeface(codeFont(getContext()));

        float defSp = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 14,
                getResources().getDisplayMetrics());
        setTextSize(TypedValue.COMPLEX_UNIT_PX, sTextSizePx > 0 ? sTextSizePx : defSp);

        setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        setSingleLine(false);
        setHorizontallyScrolling(true);
        setSaveEnabled(false);
        setOverScrollMode(OVER_SCROLL_NEVER);
        setVerticalScrollBarEnabled(false);
        setHorizontalScrollBarEnabled(false);

        setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);

        if (Build.VERSION.SDK_INT >= 23) {
            setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE);
            setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE);
        }

        numPaint.setTypeface(getTypeface());
        strPaint.setStyle(Paint.Style.STROKE);
        strPaint.setStrokeWidth(dpf(1));

        // PENTING: padding atas/kanan/bawah harus 0. TextView.onDraw() meng-clip teks di
        // area padding (kecuali saat scroll persis di ujung), jadi padding bawah besar
        // membuat baris-baris terakhir di viewport tidak digambar sampai scroll mentok.
        padTop    = 0;
        padRight  = 0;
        padBottom = 0;

        setFilters(new InputFilter[]{ smartFilter });
        initCursorUi();

        addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {
                if (loading || applying) return;
                pendingRemoved = c > 0 ? s.subSequence(st, st + c).toString() : "";
            }
            public void onTextChanged(CharSequence s, int st, int before, int c) {
                lineNoDirty = true;
                longAnchor = -1;
                if (loading) return;
                requestFollow();
                String added = c > 0 ? s.subSequence(st, st + c).toString() : "";
                if (!applying) {
                    logicalLines += countNl(added) - countNl(pendingRemoved);
                    recordEdit(st, pendingRemoved, added);
                }
            }
            public void afterTextChanged(Editable e) {
                if (loading) return;
                updateGutter();
                scheduleHighlight();
            }
        });

        flingScroller  = new OverScroller(getContext());
        ViewConfiguration vc = ViewConfiguration.get(getContext());
        touchSlop = vc.getScaledTouchSlop();
        minFlingV = vc.getScaledMinimumFlingVelocity();
        maxFlingV = vc.getScaledMaximumFlingVelocity();
        gestureDetector = new GestureDetector(getContext(), new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onScroll(MotionEvent e1, MotionEvent e2, float dx, float dy) {
                if (!freeScrolling) return false;
                flingScroller.forceFinished(true);
                scrollByFree((int) dx, (int) dy);
                showScrollbars();
                return true;
            }
            @Override public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
                if (!freeScrolling) return false;
                flingScroller.forceFinished(true);
                flingScroller.fling(getScrollX(), getScrollY(),
                        (int) -vx, (int) -vy,
                        0, maxScrollX(), 0, maxScrollY(),
                        dp(32), dp(32));
                postInvalidateOnAnimation();
                return true;
            }
            @Override public boolean onDown(MotionEvent e) { return true; }
            @Override public boolean onSingleTapUp(MotionEvent e) {
                if (!freeScrolling) return false;
                requestFocus();
                int off = getOffsetForPosition(e.getX(), e.getY());
                if (off >= 0 && off <= getText().length()) setSelection(off);
                showIme();
                return true;
            }
        });

        scaler = new ScaleGestureDetector(getContext(),
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override public boolean onScale(ScaleGestureDetector d) {
                        float minPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 8,
                                getResources().getDisplayMetrics());
                        float maxPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 32,
                                getResources().getDisplayMetrics());
                        float oldPx = getTextSize();
                        float px = Math.max(minPx, Math.min(maxPx, oldPx * d.getScaleFactor()));
                        if (Math.abs(px - oldPx) <= 0.3f) return true;

                        // Titik di bawah jari (dalam koordinat konten, sebelum zoom) harus tetap di bawah jari.
                        float r = px / oldPx;
                        float fx = d.getFocusX(), fy = d.getFocusY();
                        float docX = getScrollX() + fx - gutterWidth;   // relatif ke awal teks
                        float docY = getScrollY() + fy;                 // padding atas = 0

                        flingScroller.forceFinished(true);
                        setTextSize(TypedValue.COMPLEX_UNIT_PX, px);
                        sTextSizePx = px;
                        updateGutter();                                  // lebar gutter bisa ikut berubah

                        zoomAnchorX = Math.round(gutterWidth + docX * r - fx);
                        zoomAnchorY = Math.round(docY * r - fy);
                        zoomAnchorPending = true;
                        requestLayout();
                        return true;
                    }
                });

        ready = true;
        rebuildSyntax();
        updateGutter();
    }

    /** Font kode editor: monospace bawaan sistem (Typeface.MONOSPACE). */
    private static Typeface codeFont(Context c) {
        return Typeface.MONOSPACE;
    }

    // =====================================================================
    // API publik
    // =====================================================================

    public void load(String name, String text) {
        setFileName(name);
        loading = true;
        try {
            super.setText(text == null ? "" : text, BufferType.EDITABLE);
        } finally { loading = false; }
        logicalLines = countNl(getText().toString()) + 1;
        clearHistory();
        lineNoDirty = true;
        hlFrom = hlTo = -1;
        setSelection(0);
        scrollTo(0, 0);
        updateGutter();
        highlightNow();
    }

    @Override
    public void setText(CharSequence text, BufferType type) {
        if (!ready) { super.setText(text, type); return; }
        loading = true;
        try { super.setText(text, type); }
        finally { loading = false; }
        logicalLines = countNl(getText() == null ? "" : getText().toString()) + 1;
        clearHistory();
        lineNoDirty = true;
        hlFrom = hlTo = -1;
        updateGutter();
        highlightNow();
    }

    public void setFileName(String name) {
        fileName = name == null ? "" : name;
        lang = SyntaxAssets.languageFor(getContext(), fileName);
        rebuildSyntax();
        hlFrom = hlTo = -1;
        if (ready) highlightNow();
    }

    public boolean isHighlighting() { return highlighting; }
    public void    setOnSaveRequested(Runnable r) { saveListener = r; }
    public boolean isWordWrap()     { return wordWrap; }

    public void setWordWrap(boolean on) {
        if (wordWrap == on) return;
        wordWrap = on;
        setHorizontallyScrolling(!on);
        lineNoDirty = true;
        scrollTo(0, 0);
        requestLayout();
        invalidate();
    }


    /** Auto-complete simbol: ( [ { " ' berpasangan otomatis dan hapus ikut menghapus pasangannya. */
    public void setSmartEdit(boolean on) { smartEdit = on; }
    public boolean isSmartEdit() { return smartEdit; }

    /** Indentasi otomatis saat Enter. */
    public void setAutoIndent(boolean on) { autoIndent = on; }

    public void setShowLineNumbers(boolean on) {
        if (showLineNumbers == on) return;
        showLineNumbers = on;
        updateGutter();
        invalidate();
    }

    public void setHighlightCurrentLine(boolean on) {
        highlightCurrentLine = on;
        invalidate();
    }

    /** Ukuran teks dalam sp (dipakai Pengaturan). */
    public void setFontSizeSp(int sp) {
        float px = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, getResources().getDisplayMetrics());
        sTextSizePx = px;
        setTextSize(TypedValue.COMPLEX_UNIT_PX, px);
        updateGutter();
    }

    /** Terapkan ulang warna kursor, handle, dan menu kursor setelah tema aplikasi berubah. */
    public void refreshColors() { setTheme(themeName); }

    public void gotoLine(int line) {
        String t = getText().toString();
        int target = Math.max(1, Math.min(line, logicalLines));
        int pos = 0, cur = 1;
        while (cur < target) {
            int nl = t.indexOf('\n', pos);
            if (nl < 0) break;
            pos = nl + 1; cur++;
        }
        setSelection(pos);
        requestFocus();
        requestFollow();
    }

    public void insertText(String s) {
        int a = Math.max(0, Math.min(getSelectionStart(), getSelectionEnd()));
        int b = Math.max(0, Math.max(getSelectionStart(), getSelectionEnd()));
        getText().replace(a, b, s);
    }

    // ---- undo/redo ----

    public boolean canUndo() { return !undoStack.isEmpty(); }
    public boolean canRedo() { return !redoStack.isEmpty(); }
    public void clearHistory() { undoStack.clear(); redoStack.clear(); }

    public void undo() {
        if (undoStack.isEmpty()) return;
        Edit e = undoStack.remove(undoStack.size() - 1);
        applyEdit(e.start, e.added.length(), e.removed);
        redoStack.add(e);
        setSelection(Math.min(getText().length(), e.start + e.removed.length()));
    }

    public void redo() {
        if (redoStack.isEmpty()) return;
        Edit e = redoStack.remove(redoStack.size() - 1);
        applyEdit(e.start, e.removed.length(), e.added);
        undoStack.add(e);
        setSelection(Math.min(getText().length(), e.start + e.added.length()));
    }

    private void applyEdit(int start, int removeLen, String insert) {
        applying = true;
        try {
            Editable ed = getText();
            int end = Math.min(ed.length(), start + removeLen);
            String old = ed.subSequence(start, end).toString();
            logicalLines += countNl(insert) - countNl(old);
            ed.replace(start, end, insert);
        } finally { applying = false; }
        lineNoDirty = true;
        updateGutter();
        scheduleHighlight();
    }

    private void recordEdit(int start, String removed, String added) {
        if (removed.isEmpty() && added.isEmpty()) return;
        redoStack.clear();
        long now = System.currentTimeMillis();
        if (!undoStack.isEmpty()) {
            Edit last = undoStack.get(undoStack.size() - 1);
            if (now - last.time < 1000) {
                if (removed.isEmpty() && last.removed.isEmpty()
                        && added.length() == 1 && added.charAt(0) != '\n'
                        && start == last.start + last.added.length()
                        && last.added.indexOf('\n') < 0) {
                    last.added += added; last.time = now; return;
                }
                if (added.isEmpty() && last.added.isEmpty()
                        && removed.length() == 1 && start + 1 == last.start) {
                    last.start = start;
                    last.removed = removed + last.removed;
                    last.time = now; return;
                }
            }
        }
        Edit e = new Edit();
        e.start = start; e.removed = removed; e.added = added; e.time = now;
        undoStack.add(e);
        if (undoStack.size() > MAX_UNDO) undoStack.remove(0);
    }

    // =====================================================================
    // Auto-indent / auto-close
    // =====================================================================

    private int pendingSel = -1;

    private void moveCaretLater(int pos) {
        pendingSel = pos;
        post(new Runnable() { public void run() {
            int p = pendingSel; pendingSel = -1;
            if (p >= 0 && p <= getText().length()) setSelection(p);
        }});
    }

    private static boolean isOpen(char c)  { return c=='('||c=='['||c=='{'; }
    private static boolean isClose(char c) { return c==')'||c==']'||c=='}'; }
    private static char    closeOf(char c) { return c=='('?')':c=='['?']':'}'; }
    private static char    openOf(char c)  { return c==')'?'(':c==']'?'[':'{'; }
    private static boolean isQuote(char c) { return c=='"'||c=='\''; }
    private static boolean isWord(char c)  { return Character.isLetterOrDigit(c)||c=='_'; }
    private static boolean isBracket(char c){ return isOpen(c)||isClose(c); }

    private final InputFilter smartFilter = new InputFilter() {
        public CharSequence filter(CharSequence src, int start, int end,
                                   Spanned dest, int dstart, int dend) {
            if (!ready || loading || applying || !smartEdit) return null;
            if (end - start != 1) return null;
            char c = src.charAt(start);
            char prev = dstart > 0 ? dest.charAt(dstart - 1) : '\0';
            char next = dend < dest.length() ? dest.charAt(dend) : '\0';
            boolean hasSel = dend > dstart;

            if (c == '\n') {
                if (!autoIndent) return null;
                int ls = dstart;
                while (ls > 0 && dest.charAt(ls - 1) != '\n') ls--;
                int ie = ls;
                while (ie < dstart && (dest.charAt(ie)==' '||dest.charAt(ie)=='\t')) ie++;
                String indent = dest.subSequence(ls, ie).toString();
                boolean opener = isOpen(prev) || (prev==':' && lang != null && lang.colonIndent)
                        || (lang != null && lang.tagIndent && opensHtmlTag(dest.subSequence(ls, dstart).toString()));
                String extra = opener ? "    " : "";
                if (isOpen(prev) && next == closeOf(prev)) {
                    moveCaretLater(dstart + 1 + indent.length() + extra.length());
                    return "\n" + indent + extra + "\n" + indent;
                }
                if (indent.isEmpty() && extra.isEmpty()) return null;
                return "\n" + indent + extra;
            }
            if (isOpen(c)) {
                if (hasSel) return "" + c + dest.subSequence(dstart, dend) + closeOf(c);
                if (next=='\0'||Character.isWhitespace(next)||isClose(next)||next==';'||next==',') {
                    moveCaretLater(dstart + 1);
                    return "" + c + closeOf(c);
                }
                return null;
            }
            if (isClose(c) && !hasSel && next == c) {
                moveCaretLater(dstart + 1); return "";
            }
            if (isQuote(c)) {
                if (hasSel) return "" + c + dest.subSequence(dstart, dend) + c;
                if (next == c) { moveCaretLater(dstart + 1); return ""; }
                if (isWord(prev)||prev==c||prev=='\\') return null;
                if (next=='\0'||Character.isWhitespace(next)||isClose(next)||next==';'||next==',') {
                    moveCaretLater(dstart + 1);
                    return "" + c + c;
                }
            }
            return null;
        }
    };

    private boolean deletePairAtCursor() {
        if (!ready || !smartEdit) return false;
        int s = getSelectionStart();
        if (s != getSelectionEnd() || s <= 0 || s >= getText().length()) return false;
        char p = getText().charAt(s - 1), n = getText().charAt(s);
        if ((isOpen(p)&&closeOf(p)==n)||(isQuote(p)&&p==n)) {
            getText().delete(s - 1, s + 1); return true;
        }
        return false;
    }

    @Override
    public InputConnection onCreateInputConnection(EditorInfo out) {
        InputConnection ic = super.onCreateInputConnection(out);
        if (ic == null) return null;
        out.imeOptions |= EditorInfo.IME_FLAG_NO_EXTRACT_UI | EditorInfo.IME_FLAG_NO_FULLSCREEN;
        return new InputConnectionWrapper(ic, true) {
            @Override public boolean deleteSurroundingText(int b, int a) {
                if (b == 1 && a == 0 && deletePairAtCursor()) return true;
                return super.deleteSurroundingText(b, a);
            }
            @Override public boolean sendKeyEvent(KeyEvent e) {
                if (e.getAction()==KeyEvent.ACTION_DOWN
                        && e.getKeyCode()==KeyEvent.KEYCODE_DEL
                        && deletePairAtCursor()) return true;
                return super.sendKeyEvent(e);
            }
        };
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent e) {
        boolean ctrl = e.isCtrlPressed();
        if (keyCode == KeyEvent.KEYCODE_TAB && !ctrl) { insertText("    "); return true; }
        if (ctrl && keyCode == KeyEvent.KEYCODE_Z) {
            if (e.isShiftPressed()) redo(); else undo(); return true;
        }
        if (ctrl && keyCode == KeyEvent.KEYCODE_Y) { redo(); return true; }
        if (ctrl && keyCode == KeyEvent.KEYCODE_S) {
            if (saveListener != null) saveListener.run(); return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DEL && !ctrl && deletePairAtCursor()) return true;
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN
                || keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                || keyCode == KeyEvent.KEYCODE_ENTER) requestFollow();
        return super.onKeyDown(keyCode, e);
    }

    // =====================================================================
    // Syntax: aturan dari assets/syntax/languages, warna dari tema
    // =====================================================================

    /** Ambil semua warna editor dari tema. Dipanggil saat init dan saat tema diganti. */
    private void applyTheme() {
        C_BG            = theme.editor("background");
        C_TEXT          = theme.editor("text");
        C_SELECTION     = theme.editor("selection");
        C_GUTTER        = theme.editor("gutter");
        C_GUTTER_LINE   = theme.editor("gutterLine");
        C_NUMBER        = theme.editor("lineNumber");
        C_NUMBER_ACTIVE = theme.editor("lineNumberActive");
        C_CURRENT_LINE  = theme.editor("currentLine");
        C_GUIDE         = theme.editor("indentGuide");
        C_BRACKET_FILL  = theme.editor("bracketFill");
        C_BRACKET_STR   = theme.editor("bracketStroke");
        C_SCROLLBAR     = theme.editor("scrollbar");
        C_SCROLLBAR_TRACK = theme.editor("scrollbarTrack");
        C_ICON_TINT     = theme.editor("iconTint");
        // Kursor, handle, dan menu kursor mengikuti palet app (bukan warna tema syntax).
        C_HANDLE        = appAccent();
        C_MENU_BG       = Theme.SURFACE_HIGH != 0 ? Theme.SURFACE_HIGH : 0xFF222B28;
        C_MENU_ICON     = Theme.ON != 0 ? Theme.ON : 0xFFDDE0DC;
        C_SWATCH_STROKE = theme.editor("swatchStroke");

        iconCache.clear();          // ikon di-tint, harus dibuat ulang
        setBackground(new ColorDrawable(C_BG));
        setTextColor(C_TEXT);
        setHighlightColor(C_SELECTION);
        applyCursorDrawable();
    }

    /** Warna aksen app (primary M3). Dipakai untuk kursor dan handle. */
    private static int appAccent() {
        return Theme.PRIMARY != 0 ? Theme.PRIMARY : 0xFF6750A4;
    }

    /** Kursor lebih tebal (2dp) & kontras terhadap latar, supaya jelas terlihat. */
    private void applyCursorDrawable() {
        if (Build.VERSION.SDK_INT < 29) return;
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setColor(appAccent());
        d.setSize(Math.max(2, dp(2)), (int) Math.ceil(getTextSize()));
        setTextCursorDrawable(d);
        applyHandleDrawables();
    }

    /** Handle bentuk tetesan (gaya Sora Editor). Hanya bisa diganti di API 29+. */
    private void applyHandleDrawables() {
        if (Build.VERSION.SDK_INT < 29) return;
        float r = dpf(10);
        float pad = dpf(8);
        setTextSelectHandle(new HandleDrawable(HandleDrawable.INSERT, C_HANDLE, r, pad));
        setTextSelectHandleLeft(new HandleDrawable(HandleDrawable.LEFT, C_HANDLE, r, pad));
        setTextSelectHandleRight(new HandleDrawable(HandleDrawable.RIGHT, C_HANDLE, r, pad));
    }

    // =====================================================================
    // Menu kursor / seleksi (gaya Sora Editor)
    // =====================================================================

    private CursorMenu cursorMenu;
    private boolean cursorModeActive;
    private ActionMode activeMode;
    private int longAnchor = -1;            // posisi awal "Long select"; -1 = tidak aktif
    private int lastLocX = Integer.MIN_VALUE, lastLocY;
    private final int[] locBuf = new int[2];
    private final Runnable showCursorMenuTask = new Runnable() {
        public void run() { if (cursorModeActive) showCursorMenu(); }
    };

    /** Long select: setelah menekan "Long select", ketuk/geser kursor ke posisi lain -> teks di antaranya terseleksi. */
    private final Runnable longSelectTask = new Runnable() {
        public void run() {
            if (longAnchor < 0) return;
            int p = getSelectionEnd();
            if (getSelectionStart() != p || p == longAnchor) return;
            int a = Math.min(longAnchor, getText().length());
            longAnchor = -1;
            Selection.setSelection(getText(), a, p);
            // Seleksi dibuat lewat kode, jadi framework tidak membuat action mode baru. Nyalakan lagi
            // mode kursor di sini, supaya menu Potong / Salin / Tempel muncul untuk seleksi ini.
            cursorModeActive = true;
            revealSelectionHandles();
            scheduleCursorMenu();
        }
    };

    /**
     * Pantau posisi editor di layar (mis. saat drawer PUSH digeser). Menu kursor adalah window
     * terpisah, jadi tidak ikut bergeser: begitu editor berpindah / kehilangan fokus, tutup.
     */
    private final ViewTreeObserver.OnPreDrawListener posWatcher = new ViewTreeObserver.OnPreDrawListener() {
        @Override public boolean onPreDraw() {
            boolean menuUp = cursorMenu != null && cursorMenu.isShowing();
            if (!cursorModeActive && !menuUp) { lastLocX = Integer.MIN_VALUE; return true; }
            getLocationOnScreen(locBuf);
            boolean moved = lastLocX != Integer.MIN_VALUE
                    && (Math.abs(locBuf[0] - lastLocX) > 1 || Math.abs(locBuf[1] - lastLocY) > 1);
            lastLocX = locBuf[0]; lastLocY = locBuf[1];
            if (moved || (menuUp && (!isFocused() || !cursorModeActive || !isShown()))) {
                finishCursorMode();
            }
            return true;
        }
    };

    /** Tutup menu kursor/seleksi dari luar editor (mis. saat sheet atau panel lain dibuka). */
    public void dismissCursorUi() {
        longAnchor = -1;
        finishCursorMode();
    }

    /** Tutup menu + action mode (handle ikut hilang). */
    private void finishCursorMode() {
        removeCallbacks(showCursorMenuTask);
        if (cursorMenu != null) cursorMenu.hide();
        if (activeMode != null) {
            try { activeMode.finish(); } catch (Exception ignored) {}
            activeMode = null;
        }
        cursorModeActive = false;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        getViewTreeObserver().addOnPreDrawListener(posWatcher);
    }

    @Override
    protected void onFocusChanged(boolean focused, int direction, android.graphics.Rect prev) {
        super.onFocusChanged(focused, direction, prev);
        if (!focused) { longAnchor = -1; finishCursorMode(); }
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        if (!hasWindowFocus && cursorMenu != null) cursorMenu.hide();
        else if (hasWindowFocus && cursorModeActive) scheduleCursorMenu();
    }

    /**
     * Ganti toolbar mengambang bawaan dengan CursorMenu. Callback mengembalikan true dengan menu
     * kosong supaya handle tetap muncul (mengembalikan false akan mematikan seleksi sama sekali).
     */
    private void initCursorUi() {
        ActionMode.Callback cb = new ActionMode.Callback() {
            @Override public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                menu.clear();
                activeMode = mode;
                lastLocX = Integer.MIN_VALUE;
                cursorModeActive = true;
                removeCallbacks(showCursorMenuTask);
                postDelayed(showCursorMenuTask, 120);
                return true;
            }
            @Override public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                menu.clear();
                return true;
            }
            @Override public boolean onActionItemClicked(ActionMode mode, MenuItem item) { return false; }
            @Override public void onDestroyActionMode(ActionMode mode) {
                // Saat kursor berubah jadi seleksi (mis. Long select), mode lama bisa ditutup SETELAH
                // mode baru dibuat. Hanya mode aktif yang boleh mematikan menu, supaya menu tidak hilang.
                if (activeMode != mode) return;
                activeMode = null;
                cursorModeActive = false;
                removeCallbacks(showCursorMenuTask);
                if (cursorMenu != null) cursorMenu.hide();
            }
        };
        setCustomSelectionActionModeCallback(cb);
        if (Build.VERSION.SDK_INT >= 23) setCustomInsertionActionModeCallback(cb);
    }

    private void showCursorMenu() {
        Layout l = getLayout();
        if (!ready || l == null || loading || getWindowToken() == null) return;
        if (longAnchor >= 0) return;                // sedang menunggu ketukan Long select
        int len = getText().length();
        int a = Math.max(0, Math.min(getSelectionStart(), getSelectionEnd()));
        int b = Math.max(0, Math.max(getSelectionStart(), getSelectionEnd()));
        if (b > len) return;
        boolean hasSel = a != b;

        if (cursorMenu == null) {
            cursorMenu = new CursorMenu(getContext(), new CursorMenu.Listener() {
                public void onAction(int id) { onCursorMenuAction(id); }
            });
        }
        cursorMenu.setColors(C_MENU_BG, C_MENU_ICON);

        int padT = getExtendedPaddingTop();
        int la = l.getLineForOffset(a), lb = l.getLineForOffset(b);
        int topY = padT + l.getLineTop(la) - getScrollY();
        int bottomY = padT + l.getLineBottom(lb) - getScrollY();
        int cx;
        if (la == lb) {
            float xa = getPaddingLeft() + l.getPrimaryHorizontal(a) - getScrollX();
            float xb = getPaddingLeft() + l.getPrimaryHorizontal(b) - getScrollX();
            cx = (int) ((xa + xb) / 2f);
        } else {
            cx = gutterWidth + (getWidth() - gutterWidth) / 2;
        }
        // seleksi/kursor di luar layar -> tempel ke tepi viewport
        if (bottomY < 0 || topY > getHeight()) { cursorMenu.hide(); return; }
        topY = Math.max(topY, 0);
        bottomY = Math.min(bottomY, getHeight());

        boolean all = a == 0 && b == len;
        ClipboardManager cm = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
        boolean canPaste = cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip() != null
                && cm.getPrimaryClip().getItemCount() > 0;
        cursorMenu.show(this, hasSel, canPaste, len > 0, all, cx, topY, bottomY);
    }

    private void onCursorMenuAction(int id) {
        switch (id) {
            case CursorMenu.SELECT_ALL:
                longAnchor = -1;
                onTextContextMenuItem(android.R.id.selectAll);
                break;
            case CursorMenu.LONG_SELECT:
                longAnchor = getSelectionEnd();
                if (cursorMenu != null) cursorMenu.hide();
                return;
            case CursorMenu.CUT:        longAnchor = -1; onTextContextMenuItem(android.R.id.cut); break;
            case CursorMenu.COPY:       longAnchor = -1; onTextContextMenuItem(android.R.id.copy); break;
            case CursorMenu.PASTE:
                longAnchor = -1;
                onTextContextMenuItem(Build.VERSION.SDK_INT >= 23
                        ? android.R.id.pasteAsPlainText : android.R.id.paste);
                break;
        }
        if (cursorMenu != null) cursorMenu.hide();
        if (id == CursorMenu.SELECT_ALL) scheduleCursorMenu();   // seleksi baru -> munculkan lagi
    }

    /** Sembunyikan menu selagi handle digeser / editor di-scroll, tampilkan lagi setelah tenang. */
    private void scheduleCursorMenu() {
        if (!cursorModeActive) return;
        if (cursorMenu != null) cursorMenu.hide();
        removeCallbacks(showCursorMenuTask);
        postDelayed(showCursorMenuTask, 300);
    }

    /** Ganti tema saat runtime. Nama = nama file JSON di assets/syntax/themes tanpa .json. */
    /** Nama file tema yang sedang aktif (tanpa .json). */
    public String getThemeName() { return themeName; }

    public void setTheme(String themeName) {
        this.themeName = themeName;
        theme = SyntaxAssets.theme(getContext(), themeName);
        applyTheme();
        rebuildSyntax();
        if (ready && getText() != null) {
            Editable ed = getText();
            for (SyntaxSpan sp : ed.getSpans(0, ed.length(), SyntaxSpan.class)) ed.removeSpan(sp);
            highlightNow();
        }
        invalidate();
    }

    private void rebuildSyntax() {
        syntax = (lang != null && lang.pattern != null && theme != null) ? lang.pattern : null;
    }

    private static int withAlpha(int color, float a) {
        return (((int) (Color.alpha(color) * a)) << 24) | (color & 0xFFFFFF);
    }

    /** Baris yang berakhir dengan tag pembuka HTML (bukan tag void, penutup, atau mandiri). */
    private static boolean opensHtmlTag(String before) {
        String t = before.trim();
        if (!t.startsWith("<") || !t.endsWith(">")) return false;
        if (t.startsWith("</") || t.startsWith("<!") || t.startsWith("<?") || t.endsWith("/>")) return false;
        if (t.contains("</")) return false;
        String name = t.substring(1).split("[\\s>/]", 2)[0].toLowerCase(Locale.ROOT);
        return !name.isEmpty() && !VOID_TAGS.contains(" " + name + " ");
    }

    /** Warna token tiap grup regex sebuah bahasa, sesuai tema yang aktif. */
    private int[] colorsOf(Language lg) {
        int[] cols = new int[lg.scopes.length];
        for (int i = 0; i < cols.length; i++) cols[i] = theme.token(lg.scopes[i]);
        return cols;
    }

    private void scheduleHighlight() {
        if (syntax == null) return;
        removeCallbacks(hlTask);
        hlPending = false;
        if (getText().length() <= 12000) highlightNow();
        else { hlPending = true; postDelayed(hlTask, 120); }
    }

    private void highlightNow() {
        Editable ed = getText();
        if (ed == null || highlighting) return;
        highlighting = true;
        try {
            int len = ed.length();
            int from = 0, to = len;
            if (len > LARGE_TEXT) {
                Layout l = getLayout();
                if (l == null) { postDelayed(hlTask, 50); return; }
                int top = Math.max(0, getScrollY() - getExtendedPaddingTop());
                int fl  = Math.max(0, l.getLineForVertical(top) - WINDOW_LINES);
                int ll  = Math.min(l.getLineCount()-1, l.getLineForVertical(top+getHeight()) + WINDOW_LINES);
                from = l.getLineStart(fl); to = l.getLineEnd(ll);
                hlFrom = from; hlTo = to;
            }
            SyntaxSpan[] old = ed.getSpans(from, to, SyntaxSpan.class);
            for (SyntaxSpan sp : old) ed.removeSpan(sp);
            if (lang == null || lang.pattern == null || theme == null || to <= from) return;
            String seg = ed.subSequence(from, to).toString();
            paintRange(ed, from, seg, 0, seg.length(), lang, 0);
        } finally { highlighting = false; }
    }

    /**
     * Warnai seg[a..b) dengan bahasa lg. Blok embed (mis. isi <script> dan <style> di dalam HTML)
     * diwarnai dengan bahasanya sendiri, sedangkan sisanya memakai aturan lg.
     */
    private void paintRange(Editable ed, int base, String seg, int a, int b, Language lg, int depth) {
        int pos = a;
        while (pos < b) {
            Language.Embed best = null;
            int bestOpenEnd = -1, bestCloseStart = -1, bestCloseEnd = -1, bestStart = -1;
            if (depth < 4) {
                for (Language.Embed em : lg.embeds) {
                    Matcher sm = em.start.matcher(seg);
                    sm.region(pos, b);
                    sm.useTransparentBounds(true);
                    sm.useAnchoringBounds(false);
                    if (!sm.find()) continue;
                    if (best != null && sm.start() >= bestStart) continue;
                    best = em;
                    bestStart = sm.start();
                    bestOpenEnd = sm.end();
                    Matcher cm = em.end.matcher(seg);
                    cm.region(bestOpenEnd, b);
                    cm.useAnchoringBounds(false);
                    if (cm.find()) {
                        bestCloseStart = cm.start();
                        bestCloseEnd = cm.end();
                    } else {
                        bestCloseStart = b;
                        bestCloseEnd = b;
                    }
                }
            }
            if (best == null) {
                paintPattern(ed, base, seg, pos, b, lg);
                return;
            }
            paintPattern(ed, base, seg, pos, bestOpenEnd, lg);           // termasuk tag pembuka
            Language sub = SyntaxAssets.languageByName(getContext(), best.language);
            if (sub != null && sub.pattern != null) {
                paintRange(ed, base, seg, bestOpenEnd, bestCloseStart, sub, depth + 1);
            }
            paintPattern(ed, base, seg, bestCloseStart, bestCloseEnd, lg); // tag penutup
            pos = Math.max(bestCloseEnd, pos + 1);
        }
    }

    private void paintPattern(Editable ed, int base, String seg, int a, int b, Language lg) {
        if (a >= b || lg.pattern == null) return;
        int[] cols = colorsOf(lg);
        Matcher m = lg.pattern.matcher(seg);
        m.region(a, b);
        m.useTransparentBounds(true);
        m.useAnchoringBounds(false);
        while (m.find()) {
            int s = m.start(), e = m.end();
            if (s == e) continue;
            for (int g = 1; g <= cols.length; g++) {
                if (m.start(g) >= 0) {
                    ed.setSpan(new SyntaxSpan(cols[g - 1]), base + s, base + e,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    break;
                }
            }
        }
    }

    @Override
    protected void onScrollChanged(int l, int t, int oldl, int oldt) {
        super.onScrollChanged(l, t, oldl, oldt);
        if (!ready) return;
        showScrollbars();
        if (cursorModeActive) scheduleCursorMenu();
        if (getText().length() > LARGE_TEXT && syntax != null) {
            Layout lay = getLayout();
            if (lay != null) {
                int top = Math.max(0, t - getExtendedPaddingTop());
                int vs = lay.getLineStart(lay.getLineForVertical(top));
                int ve = lay.getLineEnd(lay.getLineForVertical(top + getHeight()));
                // throttle (bukan debounce): tetap ter-highlight selagi fling
                if ((vs < hlFrom || ve > hlTo || hlFrom < 0) && !hlPending) {
                    hlPending = true;
                    postDelayed(hlTask, 40);
                }
            }
        }
    }

    // =====================================================================
    // Gutter + padding
    // =====================================================================

    /**
     * Padding kiri = lebar gutter (teks mulai tepat di kanan gutter).
     * Padding atas/kanan/bawah = 0 (lihat catatan di init()).
     * Layout gutter: [8dp][nomor][6dp][kotak ikon/swatch 14dp][6dp]
     */
    private void updateGutter() {
        widthDirty = true;
        if (!ready) return;
        numPaint.setTextSize(getTextSize() * 0.86f);
        numPaint.setTypeface(getTypeface());
        int digits = Math.max(2, String.valueOf(Math.max(1, logicalLines)).length());
        digitsW = (int) Math.ceil(numPaint.measureText("9") * digits);
        int gw = dp(8) + (showLineNumbers ? digitsW + dp(6) : 0) + dp(14) + dp(6);
        if (gw == gutterWidth) return;
        gutterWidth = gw;
        setPadding(gutterWidth, padTop, padRight, padBottom);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        lineNoDirty = true;
        if (h != oh) requestFollow();
        if (cursorModeActive) scheduleCursorMenu();
    }

    // =====================================================================
    // Line number helpers (word-wrap aware)
    // =====================================================================

    private int logicalNumber(Layout l, int line) {
        if (!wordWrap) return line + 1;
        int n = l.getLineCount();
        if (lineNoDirty || lineNoCache == null || lineNoCache.length != n) {
            lineNoCache = new int[n];
            CharSequence t = getText();
            int no = 0;
            for (int i = 0; i < n; i++) {
                int st = l.getLineStart(i);
                if (i == 0 || st == 0 || t.charAt(st - 1) == '\n') no++;
                lineNoCache[i] = no;
            }
            lineNoDirty = false;
        }
        return lineNoCache[Math.min(line, n - 1)];
    }

    private boolean isLineStart(Layout l, int line) {
        if (!wordWrap || line == 0) return true;
        int st = l.getLineStart(line);
        return st == 0 || getText().charAt(st - 1) == '\n';
    }

    // =====================================================================
    // Bracket matching
    // =====================================================================

    /**
     * TextView hanya menampilkan handle kiri-kanan saat seleksi dibuat lewat sentuhan. Seleksi dari
     * kode (Long select) tidak memunculkannya, jadi pengendali handle TextView dinyalakan lewat refleksi.
     * Bila gagal (perangkat/versi lain), diam-diam dilewati; seleksinya tetap berfungsi.
     */
    private void revealSelectionHandles() {
        if (getSelectionStart() == getSelectionEnd()) return;
        try {
            java.lang.reflect.Field fe = TextView.class.getDeclaredField("mEditor");
            fe.setAccessible(true);
            Object editor = fe.get(this);
            if (editor == null) return;
            java.lang.reflect.Method gc = editor.getClass().getDeclaredMethod("getSelectionController");
            gc.setAccessible(true);
            Object controller = gc.invoke(editor);
            if (controller == null) return;
            java.lang.reflect.Method show = controller.getClass().getDeclaredMethod("show");
            show.setAccessible(true);
            show.invoke(controller);
        } catch (Throwable ignored) {
            // API tidak tersedia: handle tidak tampil, seleksi tetap jalan
        }
    }

    @Override
    protected void onSelectionChanged(int selStart, int selEnd) {
        super.onSelectionChanged(selStart, selEnd);
        if (!ready) return;
        if (selStart != selEnd) longAnchor = -1;            // seleksi lain muncul -> batalkan Long select
        if (longAnchor >= 0 && selStart == selEnd && selStart != longAnchor) {
            removeCallbacks(longSelectTask);
            postDelayed(longSelectTask, 180);               // tunggu tap/drag handle selesai
        }
        if (cursorModeActive) scheduleCursorMenu();
        matchA = matchB = -1;
        if (selStart == selEnd) findBracketMatch(selStart);
        invalidate();
    }

    private void findBracketMatch(int pos) {
        CharSequence t = getText();
        int len = t.length(), p = -1;
        if (pos > 0   && isBracket(t.charAt(pos - 1))) p = pos - 1;
        else if (pos < len && isBracket(t.charAt(pos)))     p = pos;
        if (p < 0) return;
        char c = t.charAt(p);
        boolean fwd = isOpen(c);
        char open  = fwd ? c : openOf(c);
        char close = fwd ? closeOf(c) : c;
        int depth = 0, i = p, limit = 0;
        while (i >= 0 && i < len && limit++ < MATCH_SCAN) {
            char x = t.charAt(i);
            if (x == open)  depth += fwd ?  1 : -1;
            if (x == close) depth += fwd ? -1 :  1;
            if (depth == 0) { matchA = p; matchB = i; return; }
            i += fwd ? 1 : -1;
        }
    }

    // =====================================================================
    // Drawing
    // =====================================================================

    /**
     * Saat onDraw() dipanggil, canvas SUDAH di-translate (-scrollX, -scrollY) oleh View.
     *  - Elemen yang ikut menggulir bersama teks (baris aktif, indent guide, bracket)
     *    digambar di koordinat KONTEN: x = padL + kolom, y = padT + lineTop.
     *  - Gutter & scrollbar menempel di viewport: digambar setelah
     *    canvas.translate(scrollX, scrollY), jadi koordinatnya koordinat VIEW (0,0)-(w,h).
     */
    @Override
    protected void onDraw(Canvas canvas) {
        Layout l = getLayout();
        final int sx = getScrollX(), sy = getScrollY();
        final int padL = getPaddingLeft();
        final int padT = getExtendedPaddingTop();
        final int w = getWidth(), h = getHeight();
        int first = 0, last = -1;

        if (l != null) {
            first = l.getLineForVertical(Math.max(0, sy - padT));
            last  = Math.min(l.getLineCount() - 1,
                    l.getLineForVertical(Math.max(0, sy + h - padT)));

            // ---- highlight baris aktif ----
            int selS = getSelectionStart(), selE = getSelectionEnd();
            int curLine = (selS >= 0) ? l.getLineForOffset(selS) : -1;
            if (highlightCurrentLine && selS == selE && curLine >= 0) {
                int a = curLine, b = curLine;
                while (a > 0 && !isLineStart(l, a)) a--;
                while (b < l.getLineCount() - 1 && !isLineStart(l, b + 1)) b++;
                fillPaint.setColor(C_CURRENT_LINE);
                canvas.drawRect(sx + gutterWidth, padT + l.getLineTop(a),
                        sx + w, padT + l.getLineBottom(b), fillPaint);
            }

            // ---- indent guide ----
            float space = getPaint().measureText(" ");
            fillPaint.setColor(C_GUIDE);
            CharSequence txt = getText();
            for (int line = first; line <= last; line++) {
                if (!isLineStart(l, line)) continue;
                int st = l.getLineStart(line), en = l.getLineEnd(line);
                int col = 0, k = st;
                while (k < en && k - st < 96) {
                    char ch = txt.charAt(k);
                    if (ch == ' ')  col++;
                    else if (ch == '\t') col += 4;
                    else break;
                    k++;
                }
                if (col < 4) continue;
                float top    = padT + l.getLineTop(line);
                float bottom = padT + l.getLineBottom(line);
                for (int g = 4; g <= col; g += 4) {
                    float x = padL + (g - 4) * space;
                    if (x < sx + gutterWidth || x > sx + w) continue;
                    canvas.drawRect(x, top, x + dpf(1), bottom, fillPaint);
                }
            }

            // ---- bracket highlight ----
            if (matchA >= 0 && matchB >= 0) {
                drawBracket(canvas, l, matchA, padL, padT);
                drawBracket(canvas, l, matchB, padL, padT);
            }
        }

        // ---- teks + kursor + seleksi ----
        super.onDraw(canvas);

        // ---- gutter + scrollbar: menempel di viewport ----
        canvas.save();
        canvas.translate(sx, sy);
        if (l != null) drawGutter(canvas, l, sy, padT, first, last);
        if (sbVisible) drawScrollbars(canvas, w, h);
        canvas.restore();
    }

    private void drawBracket(Canvas c, Layout l, int pos, int padL, int padT) {
        if (pos < 0 || pos >= getText().length()) return;
        int line = l.getLineForOffset(pos);
        float x0 = padL + l.getPrimaryHorizontal(pos);
        float x1 = padL + l.getPrimaryHorizontal(pos + 1);
        if (x1 <= x0) x1 = x0 + getPaint().measureText("(");
        float top    = padT + l.getLineTop(line)    + dpf(1);
        float bottom = padT + l.getLineBottom(line) - dpf(1);
        tmp.set(x0, top, x1, bottom);
        fillPaint.setColor(C_BRACKET_FILL);
        c.drawRoundRect(tmp, dpf(3), dpf(3), fillPaint);
        strPaint.setColor(C_BRACKET_STR);
        c.drawRoundRect(tmp, dpf(3), dpf(3), strPaint);
    }

    /** Koordinat VIEW. */
    private void drawGutter(Canvas c, Layout l, int sy, int padT, int first, int last) {
        int h = getHeight();

        fillPaint.setColor(C_GUTTER);
        c.drawRect(0, 0, gutterWidth, h, fillPaint);
        fillPaint.setColor(C_GUTTER_LINE);
        c.drawRect(gutterWidth - dpf(1), 0, gutterWidth, h, fillPaint);

        int selS   = getSelectionStart();
        int active = selS >= 0 ? logicalNumber(l, l.getLineForOffset(selS)) : -1;

        numPaint.setTextAlign(Paint.Align.RIGHT);
        float numRight = dpf(8) + digitsW;
        float iconLeft = showLineNumbers ? numRight + dpf(6) : dpf(8);
        float box      = dpf(14);
        CharSequence txt = getText();

        for (int line = first; line <= last; line++) {
            if (!isLineStart(l, line)) continue;
            int no = logicalNumber(l, line);
            float top    = padT + l.getLineTop(line)    - sy;
            float bottom = padT + l.getLineBottom(line) - sy;
            if (no == active && highlightCurrentLine) {
                fillPaint.setColor(C_CURRENT_LINE);
                c.drawRect(0, top, gutterWidth - dpf(1), bottom, fillPaint);
            }
            numPaint.setColor(no == active ? C_NUMBER_ACTIVE : C_NUMBER);
            numPaint.setFakeBoldText(no == active);
            if (showLineNumbers) c.drawText(String.valueOf(no), numRight, padT + l.getLineBaseline(line) - sy, numPaint);
            drawMark(c, txt, l.getLineStart(line), iconLeft, (top + bottom) / 2f, box);
        }
    }

    /** Swatch warna atau preview R.drawable.xxx di sisi kanan nomor baris. */
    private void drawMark(Canvas c, CharSequence t, int st, float left, float cy, float box) {
        int len = t.length(), en = st;
        while (en < len && en - st < 240 && t.charAt(en) != '\n') en++;
        if (en - st < 4) return;
        markMatcher.reset(t.subSequence(st, en).toString());
        if (!markMatcher.find()) return;

        String icon = markMatcher.group(6);
        if (icon == null) icon = markMatcher.group(7);
        if (icon != null) {
            Drawable d = markDrawable(icon);
            if (d != null) {
                d.setBounds((int) left, (int) (cy - box / 2), (int) (left + box), (int) (cy + box / 2));
                d.draw(c);
            }
            return;
        }

        int color;
        try {
            if (markMatcher.group(1) != null)      color = parseHex(markMatcher.group(1), false);
            else if (markMatcher.group(2) != null) color = parseHex(markMatcher.group(2),
                    lang != null && lang.hashColorAlphaFirst);
            else color = Color.rgb(
                    Math.min(255, Integer.parseInt(markMatcher.group(3))),
                    Math.min(255, Integer.parseInt(markMatcher.group(4))),
                    Math.min(255, Integer.parseInt(markMatcher.group(5))));
        } catch (RuntimeException e) { return; }

        float s = box - dpf(2);
        tmp.set(left + dpf(1), cy - s / 2, left + dpf(1) + s, cy + s / 2);
        fillPaint.setColor(color);
        c.drawRoundRect(tmp, dpf(3), dpf(3), fillPaint);
        strPaint.setColor(C_SWATCH_STROKE);
        c.drawRoundRect(tmp, dpf(3), dpf(3), strPaint);
    }

    private static int parseHex(String hex, boolean rgbaOrder) {
        long v = Long.parseLong(hex, 16);
        if (hex.length() == 6) return 0xFF000000 | (int) v;
        if (rgbaOrder) return (int) (((v & 0xFF) << 24) | ((v >>> 8) & 0xFFFFFF));
        return (int) v;
    }

    private Drawable markDrawable(String name) {
        if (iconCache.containsKey(name)) return iconCache.get(name);
        Drawable d = null;
        try {
            Context ctx = getContext();
            int id = ctx.getResources().getIdentifier(name, "drawable", ctx.getPackageName());
            if (id != 0) {
                d = ctx.getResources().getDrawable(id).mutate();
                if (d instanceof VectorDrawable) d.setColorFilter(C_ICON_TINT, PorterDuff.Mode.SRC_IN);
            }
        } catch (Exception e) { d = null; }
        if (iconCache.size() > 200) iconCache.clear();
        iconCache.put(name, d);
        return d;
    }

    private void showIme() {
        InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(this, 0);
    }

    // =====================================================================
    // Scroll range + scrollbar
    // =====================================================================

    /** Lebar baris terpanjang; di-cache, dihitung ulang hanya saat teks/ukuran berubah. */
    private float maxLineWidth() {
        Layout l = getLayout();
        if (l == null || wordWrap) return 0;
        if (!widthDirty) return maxLineW;
        widthDirty = false;

        Paint p = getPaint();
        float adv = p.measureText("W");
        if (Math.abs(adv - p.measureText("i")) < 0.01f) {
            // monospace: cukup hitung jumlah karakter terpanjang (O(n) murah, tanpa measuring)
            CharSequence t = getText();
            int best = 0, cur = 0;
            for (int i = 0, n = t.length(); i < n; i++) {
                char ch = t.charAt(i);
                if (ch == '\n') { if (cur > best) best = cur; cur = 0; }
                else cur += (ch == '\t') ? 4 : 1;
            }
            if (cur > best) best = cur;
            maxLineW = best * adv;
        } else {
            float m = 0;
            for (int i = 0, n = l.getLineCount(); i < n; i++) m = Math.max(m, l.getLineWidth(i));
            maxLineW = m;
        }
        return maxLineW;
    }

    private int maxScrollX() {
        if (getLayout() == null || wordWrap) return 0;
        int total = (int) Math.ceil(maxLineWidth()) + getPaddingLeft() + getPaddingRight()
                + dp(48) + getWidth() / 4;
        return Math.max(0, total - getWidth());
    }

    private int maxScrollY() {
        Layout l = getLayout();
        if (l == null) return 0;
        // overscroll: baris terakhir bisa digeser sampai ke tengah layar
        int overscroll = Math.max(dp(48), getHeight() / 2);
        int total = l.getHeight() + getExtendedPaddingTop() + getExtendedPaddingBottom() + overscroll;
        return Math.max(0, total - getHeight());
    }

    private void showScrollbars() {
        sbAlpha    = 1f;
        sbShownAt  = System.currentTimeMillis();
        sbVisible  = true;
        removeCallbacks(fadeTask);
        postDelayed(fadeTask, 1200);
        invalidate();
    }

    private float vThumbLen(int h, int vRange) {
        return Math.max(dpf(SB_MIN_LEN), (float) h * h / (vRange + h));
    }

    private int hTrackLen() { return Math.max(1, getWidth() - gutterWidth); }

    private float hThumbLen(int hRange) {
        int tl = hTrackLen();
        return Math.max(dpf(SB_MIN_LEN), (float) tl * tl / (hRange + tl));
    }

    /** Koordinat VIEW. */
    private void drawScrollbars(Canvas c, int w, int h) {
        float a = sbAlpha;
        int trackColor = withAlpha(C_SCROLLBAR_TRACK, a);
        int thumbColor = withAlpha(C_SCROLLBAR, a);
        float tw  = dpf(SB_THUMB_W);
        float trw = dpf(SB_TRACK_W);
        float mg  = dpf(SB_MARGIN);

        int vRange = maxScrollY();
        if (vRange > 0) {
            float vLen = vThumbLen(h, vRange);
            float vY   = (float) getScrollY() / vRange * (h - vLen);
            float trX  = w - trw - mg;
            tmp.set(trX, 0, trX + trw, h);
            fillPaint.setColor(trackColor);
            c.drawRoundRect(tmp, trw / 2, trw / 2, fillPaint);
            float thX = trX + (trw - tw) / 2;
            tmp.set(thX, vY, thX + tw, vY + vLen);
            fillPaint.setColor(thumbColor);
            c.drawRoundRect(tmp, tw / 2, tw / 2, fillPaint);
        }

        int hRange = maxScrollX();
        if (hRange > 0 && !wordWrap) {
            float hLen = hThumbLen(hRange);
            float hX   = gutterWidth + (float) getScrollX() / hRange * (hTrackLen() - hLen);
            float trY  = h - trw - mg;
            tmp.set(gutterWidth, trY, w, trY + trw);
            fillPaint.setColor(trackColor);
            c.drawRoundRect(tmp, trw / 2, trw / 2, fillPaint);
            float thY = trY + (trw - tw) / 2;
            tmp.set(hX, thY, hX + hLen, thY + tw);
            fillPaint.setColor(thumbColor);
            c.drawRoundRect(tmp, tw / 2, tw / 2, fillPaint);
        }
    }

    // =====================================================================
    // Touch: scroll 2-D bebas + drag scrollbar + pinch-zoom
    // =====================================================================

    /**
     * Setelah zoom, layout baru dihitung ulang. Posisi scroll dipasang di sini dengan batas
     * ukuran konten yang baru, supaya teks dan nomor baris tidak terdorong keluar layar.
     */
    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (zoomAnchorPending) {
            zoomAnchorPending = false;
            int sx = Math.max(0, Math.min(zoomAnchorX, maxScrollX()));
            int sy = Math.max(0, Math.min(zoomAnchorY, maxScrollY()));
            scrollTo(sx, sy);
        }
    }

    /**
     * Saat TIDAK FOKUS: jari menyapu bebas ke segala arah (2D pan + fling); tap = fokus.
     * Saat FOKUS: EditText menangani sendiri (kursor, seleksi, dll).
     * Scrollbar bisa di-drag kapan saja.
     */
    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        int action = ev.getActionMasked();

        // ---- pinch zoom ----
        scaler.onTouchEvent(ev);
        if (scaler.isInProgress()) {
            if (!scaling) {
                scaling = true;
                MotionEvent cancel = MotionEvent.obtain(ev);
                cancel.setAction(MotionEvent.ACTION_CANCEL);
                super.onTouchEvent(cancel);
                cancel.recycle();
            }
            return true;
        }
        if (scaling) {
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL)
                scaling = false;
            return true;
        }

        // ---- drag scrollbar ----
        if (action == MotionEvent.ACTION_DOWN) {
            float fx = ev.getX(), fy = ev.getY();
            flingScroller.forceFinished(true);
            if (hitVScrollbar(fx)) {
                sbVDragging = true;
                sbVGrabOff = fy - scrollbarVThumbTop();
                showScrollbars(); return true;
            }
            if (hitHScrollbar(fy)) {
                sbHDragging = true;
                sbHGrabOff = fx - scrollbarHThumbLeft();
                showScrollbars(); return true;
            }
        }
        if (sbVDragging) {
            if (action == MotionEvent.ACTION_MOVE) {
                int vRange = maxScrollY();
                int h = getHeight();
                float room = h - vThumbLen(h, vRange);
                if (room > 0) {
                    int newSY = (int) ((ev.getY() - sbVGrabOff) * vRange / room);
                    scrollTo(getScrollX(), Math.max(0, Math.min(newSY, vRange)));
                }
                showScrollbars();
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                sbVDragging = false;
            }
            return true;
        }
        if (sbHDragging) {
            if (action == MotionEvent.ACTION_MOVE) {
                int hRange = maxScrollX();
                float room = hTrackLen() - hThumbLen(hRange);
                if (room > 0) {
                    int newSX = (int) ((ev.getX() - sbHGrabOff - gutterWidth) * hRange / room);
                    scrollTo(Math.max(0, Math.min(newSX, hRange)), getScrollY());
                }
                showScrollbars();
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                sbHDragging = false;
            }
            return true;
        }

        // ---- mode tidak fokus: scroll 2-D bebas ----
        if (!isFocused()) {
            freeScrolling = true;
            gestureDetector.onTouchEvent(ev);
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                freeScrolling = false;
            }
            return true;
        }

        return handleFocusedTouch(ev, action);
    }

    /**
     * Mode FOKUS: geser 2D benar-benar bebas (diagonal, tanpa kunci sumbu) seperti editor kode
     * pada umumnya. Tap & long-press tetap diserahkan ke TextView (kursor/seleksi); begitu jari
     * melewati touch-slop sebelum long-press, kita ambil alih dan batalkan gesture TextView
     * sehingga kursor tidak menarik viewport kembali.
     */
    private boolean handleFocusedTouch(MotionEvent ev, int action) {
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                flingScroller.forceFinished(true);
                focusTaking = false;
                focusDownX = focusLastX = ev.getX();
                focusDownY = focusLastY = ev.getY();
                if (focusVt != null) focusVt.recycle();
                focusVt = VelocityTracker.obtain();
                focusVt.addMovement(ev);
                return super.onTouchEvent(ev);

            case MotionEvent.ACTION_MOVE:
                if (focusVt != null) focusVt.addMovement(ev);
                if (!focusTaking) {
                    float tx = ev.getX() - focusDownX, ty = ev.getY() - focusDownY;
                    boolean beyondSlop = Math.abs(tx) > touchSlop || Math.abs(ty) > touchSlop;
                    boolean beforeLongPress = ev.getEventTime() - ev.getDownTime()
                            < ViewConfiguration.getLongPressTimeout();
                    if (beyondSlop && beforeLongPress) {
                        focusTaking = true;
                        MotionEvent cancel = MotionEvent.obtain(ev);
                        cancel.setAction(MotionEvent.ACTION_CANCEL);
                        super.onTouchEvent(cancel);
                        cancel.recycle();
                        focusLastX = ev.getX();
                        focusLastY = ev.getY();
                        if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                    } else {
                        return super.onTouchEvent(ev);
                    }
                }
                scrollByFree((int) (focusLastX - ev.getX()), (int) (focusLastY - ev.getY()));
                focusLastX = ev.getX();
                focusLastY = ev.getY();
                showScrollbars();
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                boolean taking = focusTaking;
                focusTaking = false;
                if (taking) {
                    if (action == MotionEvent.ACTION_UP && focusVt != null) {
                        focusVt.addMovement(ev);
                        focusVt.computeCurrentVelocity(1000, maxFlingV);
                        float vx = focusVt.getXVelocity(), vy = focusVt.getYVelocity();
                        if (Math.abs(vx) > minFlingV || Math.abs(vy) > minFlingV) {
                            flingScroller.forceFinished(true);
                            flingScroller.fling(getScrollX(), getScrollY(),
                                    (int) -vx, (int) -vy,
                                    0, maxScrollX(), 0, maxScrollY(), 0, 0);
                            postInvalidateOnAnimation();
                        }
                    }
                    if (focusVt != null) { focusVt.recycle(); focusVt = null; }
                    return true;
                }
                if (focusVt != null) { focusVt.recycle(); focusVt = null; }
                return super.onTouchEvent(ev);
        }
        return super.onTouchEvent(ev);
    }

    // =====================================================================
    // Jaga kursor tetap terlihat
    // =====================================================================

    private final Runnable ensureCursorTask = new Runnable() {
        @Override public void run() {
            if (pendingCenter) { pendingCenter = false; followPending = false; centerCursor(); }
            else if (followPending) { followPending = false; ensureCursorVisible(); }
        }
    };

    private boolean pendingCenter;
    private boolean followPending;   // true = ada edit/keyboard yang minta viewport mengikuti kursor

    /** Minta viewport mengikuti kursor, tapi HANYA jika kursor memang keluar dari area terlihat. */
    private void requestFollow() {
        followPending = true;
        scheduleCursor(false);
    }

    /** TextView sendiri sering menggeser viewport saat tap / drag handle. Matikan, kita yang atur. */
    @Override
    public boolean bringPointIntoView(int offset) { return false; }

    @Override
    public boolean requestRectangleOnScreen(android.graphics.Rect rect) { return false; }

    @Override
    public boolean requestRectangleOnScreen(android.graphics.Rect rect, boolean immediate) { return false; }

    /** center=true: taruh kursor di TENGAH layar; false: cukup pastikan terlihat. */
    private void scheduleCursor(boolean center) {
        if (center) pendingCenter = true;
        removeCallbacks(ensureCursorTask);
        post(ensureCursorTask);
    }

    /** Geser viewport agar kursor berada di tengah area teks (di luar gutter), horizontal & vertikal. */
    public void centerCursor() {
        Layout l = getLayout();
        if (!ready || l == null || loading) return;
        int off = getSelectionEnd();
        if (off < 0 || off > getText().length()) return;
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;

        int line = l.getLineForOffset(off);
        int padT = getExtendedPaddingTop();
        float cx = getPaddingLeft() + l.getPrimaryHorizontal(off);
        float cy = padT + (l.getLineTop(line) + l.getLineBottom(line)) / 2f;

        int nsx = (int) (cx - gutterWidth - (w - gutterWidth) / 2f);
        int nsy = (int) (cy - h / 2f);
        nsx = Math.max(0, Math.min(nsx, maxScrollX()));
        nsy = Math.max(0, Math.min(nsy, maxScrollY()));
        if (nsx != getScrollX() || nsy != getScrollY()) {
            flingScroller.forceFinished(true);
            scrollTo(nsx, nsy);
            showScrollbars();
        }
    }

    /**
     * Geser viewport HANYA jika kursor berada di luar area terlihat (tertutup gutter, keluar
     * tepi, atau tertutup keyboard). Kursor yang sudah terlihat tidak pernah menggeser layar,
     * sehingga tap dan drag handle terasa diam.
     */
    public void ensureCursorVisible() {
        Layout l = getLayout();
        if (!ready || l == null || loading) return;
        int off = getSelectionEnd();
        if (off < 0 || off > getText().length()) return;

        int line = l.getLineForOffset(off);
        int padT = getExtendedPaddingTop();
        float cx = getPaddingLeft() + l.getPrimaryHorizontal(off);
        int top = padT + l.getLineTop(line);
        int bottom = padT + l.getLineBottom(line);
        int lineH = Math.max(1, bottom - top);

        int sx = getScrollX(), sy = getScrollY();
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;

        int visW  = Math.max(1, w - gutterWidth);
        int inset = dp(16);

        // ---- horizontal ----
        int nsx = sx;
        float leftLimit  = sx + gutterWidth + inset;
        float rightLimit = sx + w - inset;
        if (cx < leftLimit)        nsx = (int) (cx - gutterWidth - visW * 0.25f);
        else if (cx > rightLimit)  nsx = (int) (cx - w + visW * 0.25f);

        // ---- vertikal: baris keluar viewport -> geser secukupnya (sisakan ruang satu-dua baris) ----
        int nsy = sy;
        if (top < sy)                 nsy = top - lineH;
        else if (bottom > sy + h)     nsy = bottom + lineH * 2 - h;

        nsx = Math.max(0, Math.min(nsx, maxScrollX()));
        nsy = Math.max(0, Math.min(nsy, maxScrollY()));
        if (nsx == sx && nsy == sy) return;
        animateScrollTo(nsx, nsy);
    }

    /** Geser viewport dengan animasi singkat (bukan lompat), agar fokus kursor terasa halus. */
    private void animateScrollTo(int nx, int ny) {
        int sx = getScrollX(), sy = getScrollY();
        if (nx == sx && ny == sy) return;
        flingScroller.forceFinished(true);
        flingScroller.startScroll(sx, sy, nx - sx, ny - sy, 200);
        postInvalidateOnAnimation();
        showScrollbars();
    }

    /** Scroll horizontal+vertikal dengan batas, dipanggil saat free-scroll. */
    private void scrollByFree(int dx, int dy) {
        int nx = Math.max(0, Math.min(getScrollX() + dx, maxScrollX()));
        int ny = Math.max(0, Math.min(getScrollY() + dy, maxScrollY()));
        scrollTo(nx, ny);
    }

    @Override
    public void computeScroll() {
        super.computeScroll();
        if (flingScroller.computeScrollOffset()) {
            scrollTo(Math.max(0, Math.min(flingScroller.getCurrX(), maxScrollX())),
                     Math.max(0, Math.min(flingScroller.getCurrY(), maxScrollY())));
            postInvalidateOnAnimation();
        }
    }

    // ---- hit-test scrollbar ----
    private boolean hitVScrollbar(float fx) {
        if (maxScrollY() <= 0) return false;
        float mg  = dpf(SB_MARGIN);
        float trw = dpf(SB_TRACK_W);
        return fx >= getWidth() - trw - mg * 2;
    }

    private boolean hitHScrollbar(float fy) {
        if (maxScrollX() <= 0 || wordWrap) return false;
        float mg  = dpf(SB_MARGIN);
        float trw = dpf(SB_TRACK_W);
        return fy >= getHeight() - trw - mg * 2;
    }

    private float scrollbarVThumbTop() {
        int vRange = maxScrollY(), h = getHeight();
        return (float) getScrollY() / vRange * (h - vThumbLen(h, vRange));
    }

    private float scrollbarHThumbLeft() {
        int hRange = maxScrollX();
        return gutterWidth + (float) getScrollX() / hRange * (hTrackLen() - hThumbLen(hRange));
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        getViewTreeObserver().removeOnPreDrawListener(posWatcher);
        removeCallbacks(longSelectTask);
        finishCursorMode();
        removeCallbacks(hlTask);
        removeCallbacks(fadeTask);
    }

    // =====================================================================
    // Util
    // =====================================================================

    private static int countNl(String s) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) == '\n') n++;
        return n;
    }

    private int  dp(float v)  { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }
    private float dpf(float v) { return v * getResources().getDisplayMetrics().density; }
}
