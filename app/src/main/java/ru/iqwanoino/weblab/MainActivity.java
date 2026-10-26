package ru.iqwanoino.weblab;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/** Layar utama Weblab: drawer dorong, editor kode (CodeEditor), menu titik tiga, dan pratinjau. */
public class MainActivity extends Activity {
	
	private static final int REQ_LOAD = 123;
	private static final int REQ_PERM = 2167;
	private static final String DEFAULT_BASE = "file:///storage/emulated/0/Download/Weblab/";
	private static final String SAVE_DIR = "Download/Weblab";
	private static final String NAMA_BERKAS_BARU = "Tanpa judul.html";
	private static final String URL_DISCORD = "https://discord.gg/u7QPpQD7QC";
	private static final String URL_GITHUB = "https://github.com/IqwanXD/";
	private static final String URL_TELEGRAM = "https://t.me/IqwanoINO/";
	
	private static final int M_MUAT = 1, M_SIMPAN = 2, M_BAGIKAN = 3, M_PRATINJAU = 4,
	M_RAPIKAN = 5, M_SIMBOL_OTOMATIS = 6;
	
	private static final String[] SYMBOLS = {
		"Tab", "<", ">", "/", "=", "\"", "'", "{", "}", "(", ")", "[", "]", ";", ":", "&", "!", "-", "_"
	};
	
	private PushDrawerLayout drawerLayout;
	private LinearLayout root, topBar, drawer;
	private FrameLayout body;
	private CodeEditor editor;
	private WebView preview;
	private HorizontalScrollView symbolBar;
	private LinearLayout symbolRow;
	private TextView fileNameView;
	private ImageView undoBtn, redoBtn, moreBtn;
	private Sheet openSheet;
	private String shownKey = "";
	private String currentName = NAMA_BERKAS_BARU;
	private String currentBaseUrl = DEFAULT_BASE;
	private boolean previewing;
	private int bottomInset;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Ui.init(this);
		Prefs.init(this);
		Theme.load(this);
		Ui.setupEdge(this);
		
		editor = new CodeEditor(this);
		preview = buildPreview();
		preview.setVisibility(View.GONE);
		
		String code = "";
		if (savedInstanceState != null) {
			currentName = savedInstanceState.getString("name", NAMA_BERKAS_BARU);
			currentBaseUrl = savedInstanceState.getString("base", DEFAULT_BASE);
			code = savedInstanceState.getString("code", "");
		}
		editor.load(currentName, code);
		applyEditorPrefs();
		shownKey = stateKey();
		
		buildUi();
		checkAndRequestPermissions();
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		Theme.load(this);
		// Pengaturan atau tema berubah: perbarui tampilan langsung, tanpa memuat ulang activity.
		String key = stateKey();
		if (!shownKey.equals(key)) {
			shownKey = key;
			liveRefresh();
		}
	}
	
	@Override
	protected void onSaveInstanceState(Bundle out) {
		super.onSaveInstanceState(out);
		out.putString("code", editor.getText().toString());
		out.putString("name", currentName);
		out.putString("base", currentBaseUrl);
	}
	
	@Override
	protected void onDestroy() {
		super.onDestroy();
		if (preview != null) {
			detach(preview);
			preview.destroy();
			preview = null;
		}
	}
	
	/** Kunci yang berubah bila tema atau pengaturan editor berubah. */
	private String stateKey() {
		return M3.paletteKey + "|" + Prefs.lineNumbers() + Prefs.highlightLine() + Prefs.autoIndent()
		+ Prefs.wordWrap() + Prefs.fontSize() + Prefs.autoPair();
	}
	
	/** Terapkan tema dan pengaturan ke editor. Teks dan riwayat undo tetap. */
	private void applyEditorPrefs() {
		editor.setTheme(SyntaxAssets.autoTheme(Theme.dark));
		editor.setWordWrap(Prefs.wordWrap());
		editor.setShowLineNumbers(Prefs.lineNumbers());
		editor.setHighlightCurrentLine(Prefs.highlightLine());
		editor.setAutoIndent(Prefs.autoIndent());
		editor.setSmartEdit(Prefs.autoPair());
		editor.setFontSizeSp(Prefs.fontSize());
	}
	
	/** Bangun ulang seluruh tampilan dengan tema terbaru, dengan transisi memudar. */
	private void liveRefresh() {
		Ui.crossfade(this, () -> {
			Ui.setupEdge(this);
			if (openSheet != null) openSheet.dismiss();
			buildUi();
			applyEditorPrefs();
		}, !previewing);
	}
	
	// ------------------------------------------------------------ UI
	
	private void buildUi() {
		root = new LinearLayout(this);
		root.setOrientation(LinearLayout.VERTICAL);
		root.setBackgroundColor(Theme.BG);
		
		topBar = buildTopBar();
		root.addView(topBar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
		ViewGroup.LayoutParams.WRAP_CONTENT));
		
		body = new FrameLayout(this);
		detach(editor);
		body.addView(editor, new FrameLayout.LayoutParams(-1, -1));
		detach(preview);
		body.addView(preview, new FrameLayout.LayoutParams(-1, -1));
		editor.setVisibility(previewing ? View.GONE : View.VISIBLE);
		preview.setVisibility(previewing ? View.VISIBLE : View.GONE);
		root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1f));
		
		symbolBar = buildSymbolBar();
		symbolBar.setVisibility(previewing ? View.GONE : View.VISIBLE);
		root.addView(symbolBar, new LinearLayout.LayoutParams(-1, -2));
		
		drawer = new LinearLayout(this);
		drawer.setOrientation(LinearLayout.VERTICAL);
		buildDrawer(drawer);
		ScrollView drawerScroll = new ScrollView(this);
		drawerScroll.setBackgroundColor(Theme.BG);
		drawerScroll.setVerticalScrollBarEnabled(false);
		drawerScroll.addView(drawer, new FrameLayout.LayoutParams(-1, -2));
		
		drawerLayout = new PushDrawerLayout(this, (int) (getResources().getDisplayMetrics().widthPixels * 0.80f));
		drawerLayout.setViews(root, drawerScroll);
		drawerLayout.setOnApplyWindowInsetsListener((v, ins) -> {
			onInsets(ins);
			return ins;
		});
		setContentView(drawerLayout);
		applyInsets();
	}
	
	/** Lepas view dari induknya sebelum dipasang ulang (dipakai saat tampilan dibangun ulang). */
	private static void detach(View v) {
		if (v != null && v.getParent() instanceof ViewGroup) ((ViewGroup) v.getParent()).removeView(v);
	}
	
	/** Padding yang bergantung pada inset sistem (status bar dan navigation bar). */
	private void applyInsets() {
		int t = Ui.insetTop;
		topBar.setPadding(Ui.dp(8), t + Ui.dp(8), Ui.dp(8), Ui.dp(8));
		// Padding bawah root = tinggi keyboard atau navigation bar. Editor dan bar simbol ikut naik.
		root.setPadding(0, 0, 0, bottomInset);
		if (symbolRow != null) symbolRow.setPadding(Ui.dp(6), Ui.dp(6), Ui.dp(6), Ui.dp(6));
		drawer.setPadding(Ui.dp(24), t + Ui.dp(24), Ui.dp(24), Ui.insetBottom + Ui.dp(24));
	}
	
	/** Inset sistem: bar simbol naik di atas keyboard saat IME terbuka. */
	private void onInsets(WindowInsets ins) {
		Ui.readInsets(ins);
		if (Build.VERSION.SDK_INT >= 30) {
			int ime = ins.getInsets(WindowInsets.Type.ime()).bottom;
			int nav = ins.getInsets(WindowInsets.Type.navigationBars()).bottom;
			bottomInset = Math.max(ime, nav);
		} else {
			bottomInset = Ui.insetBottom;   // di bawah API 30, keyboard sudah mengecilkan window (adjustResize)
		}
		applyInsets();
	}
	
	private LinearLayout buildTopBar() {
		LinearLayout bar = new LinearLayout(this);
		bar.setOrientation(LinearLayout.HORIZONTAL);
		bar.setGravity(Gravity.CENTER_VERTICAL);
		bar.setBackgroundColor(Theme.BG);
		
		int size = Ui.dp(44);
		bar.addView(iconButton(R.drawable.ic_menu, Theme.ON, () -> drawerLayout.toggle()), Ui.lp(size, size));
		
		LinearLayout titles = new LinearLayout(this);
		titles.setOrientation(LinearLayout.VERTICAL);
		titles.setPadding(Ui.dp(4), 0, Ui.dp(8), 0);
		titles.addView(Ui.text(this, "Weblab", 18, Theme.ON, true), Ui.lp(-2, -2));
		fileNameView = Ui.text(this, currentName, 12, Theme.ON_VAR, false);
		fileNameView.setTypeface(Typeface.MONOSPACE);
		fileNameView.setSingleLine(true);
		fileNameView.setEllipsize(TextUtils.TruncateAt.END);
		titles.addView(fileNameView, Ui.lp(-2, -2));
		bar.addView(titles, new LinearLayout.LayoutParams(0, -2, 1f));
		
		undoBtn = iconButton(R.drawable.ic_undo, Theme.ON, this::undo);
		redoBtn = iconButton(R.drawable.ic_redo, Theme.ON, this::redo);
		moreBtn = iconButton(R.drawable.ic_more, Theme.ON, () -> showEditorMenu(moreBtn));
		bar.addView(undoBtn, Ui.lp(size, size));
		bar.addView(redoBtn, Ui.lp(size, size));
		bar.addView(moreBtn, Ui.lp(size, size));
		undoBtn.setVisibility(previewing ? View.GONE : View.VISIBLE);
		redoBtn.setVisibility(previewing ? View.GONE : View.VISIBLE);
		return bar;
	}
	
	private ImageView iconButton(int type, int color, Runnable action) {
		ImageView iv = new ImageView(this);
		int pad = Ui.dp(11);
		iv.setPadding(pad, pad, pad, pad);
		iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
		iv.setImageDrawable(Ui.tinted(this, type, color));
		iv.setBackground(Ui.ripple(0, 22));
		iv.setClickable(true);
		iv.setOnClickListener(v -> action.run());
		return iv;
	}
	
	/** Bar simbol di bawah editor, untuk mengetik simbol HTML/JS/CSS dengan cepat. */
	private HorizontalScrollView buildSymbolBar() {
		HorizontalScrollView bar = new HorizontalScrollView(this);
		bar.setHorizontalScrollBarEnabled(false);
		bar.setBackgroundColor(Theme.SURFACE);
		
		symbolRow = new LinearLayout(this);
		symbolRow.setOrientation(LinearLayout.HORIZONTAL);
		symbolRow.setGravity(Gravity.CENTER_VERTICAL);
		for (final String sym : SYMBOLS) {
			TextView key = new TextView(this);
			key.setText(sym);
			key.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
			key.setTextSize(14);
			key.setTextColor(Theme.ON);
			key.setGravity(Gravity.CENTER);
			key.setMinWidth(Ui.dp(42));
			key.setMinHeight(Ui.dp(36));
			key.setPadding(Ui.dp(10), Ui.dp(8), Ui.dp(10), Ui.dp(8));
			key.setBackground(Ui.ripple(Theme.SURFACE_HIGH, 8));
			key.setClickable(true);
			key.setFocusable(false);
			key.setOnClickListener(v -> editor.insertText(sym.equals("Tab") ? "    " : sym));
			LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
			lp.setMargins(Ui.dp(3), 0, Ui.dp(3), 0);
			symbolRow.addView(key, lp);
		}
		bar.addView(symbolRow);
		return bar;
	}
	
	private void buildDrawer(LinearLayout d) {
		d.setBackgroundColor(Theme.BG);
		d.setClickable(true);
		
		d.addView(Ui.text(this, "Weblab", 34, Theme.PRIMARY, true), Ui.lp(-2, -2));
		d.addView(Ui.text(this, "Editor HTML ringan untuk Android", 14, Theme.ON_VAR, false),
		Ui.lp(-2, -2, 0, 0, 0, Ui.dp(20)));
		
		d.addView(drawerItem(R.drawable.ic_info, "Informasi", this::showInfo));
		d.addView(drawerItem(R.drawable.ic_settings, "Pengaturan",
		() -> startActivity(new Intent(this, SettingsActivity.class))));
		d.addView(divider(), Ui.lp(-1, Ui.dp(1), 0, Ui.dp(12), 0, Ui.dp(12)));
		
		TextView related = Ui.text(this, "Tautan terkait", 14, Theme.ON_VAR, true);
		related.setPadding(0, 0, 0, Ui.dp(8));
		d.addView(related, Ui.lp(-2, -2));
		d.addView(drawerItem(R.drawable.ic_discord, "Discord", () -> openUrl(URL_DISCORD)));
		d.addView(drawerItem(R.drawable.ic_github, "GitHub", () -> openUrl(URL_GITHUB)));
		d.addView(drawerItem(R.drawable.ic_telegram, "Telegram", () -> openUrl(URL_TELEGRAM)));
	}
	
	private View drawerItem(int icon, String label, final Runnable action) {
		LinearLayout row = new LinearLayout(this);
		row.setGravity(Gravity.CENTER_VERTICAL);
		row.setPadding(0, Ui.dp(14), 0, Ui.dp(14));
		row.setBackground(Ui.ripple(0, 12));
		row.setClickable(true);
		
		ImageView iv = new ImageView(this);
		iv.setImageDrawable(Ui.tinted(this, icon, Theme.ON_VAR));
		row.addView(iv, Ui.lp(Ui.dp(26), Ui.dp(26)));
		
		LinearLayout.LayoutParams tl = Ui.lp(-2, -2);
		tl.leftMargin = Ui.dp(20);
		row.addView(Ui.text(this, label, 16, Theme.ON, false), tl);
		
		row.setOnClickListener(v -> {
			drawerLayout.close();
			action.run();
		});
		return row;
	}
	
	private View divider() {
		View v = new View(this);
		v.setBackgroundColor(M3.alpha(Theme.ON, 0.2f));
		return v;
	}
	
	private void openUrl(String url) {
		try {
			startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
		} catch (Exception e) {
			M3.toast(this, "Tidak bisa membuka tautan");
		}
	}
	
	// ------------------------------------------------------------ pratinjau
	
	private WebView buildPreview() {
		WebView w = new WebView(this);
		WebSettings s = w.getSettings();
		s.setJavaScriptEnabled(true);
		s.setDomStorageEnabled(true);
		s.setAllowFileAccess(true);
		s.setLoadsImagesAutomatically(true);
		s.setSupportZoom(true);
		if (Build.VERSION.SDK_INT >= 21) s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
		return w;
	}
	
	private void togglePreview() {
		if (previewing) showEditor();
		else showPreview();
	}
	
	private void showPreview() {
		previewing = true;
		editor.setVisibility(View.GONE);
		preview.setVisibility(View.VISIBLE);
		undoBtn.setVisibility(View.GONE);
		redoBtn.setVisibility(View.GONE);
		symbolBar.setVisibility(View.GONE);
		preview.loadDataWithBaseURL(currentBaseUrl, editor.getText().toString(), "text/html", "UTF-8", null);
	}
	
	private void showEditor() {
		previewing = false;
		preview.loadDataWithBaseURL(null, "", "text/html", "UTF-8", null);
		preview.setVisibility(View.GONE);
		editor.setVisibility(View.VISIBLE);
		undoBtn.setVisibility(View.VISIBLE);
		redoBtn.setVisibility(View.VISIBLE);
		symbolBar.setVisibility(View.VISIBLE);
		editor.requestFocus();
	}
	
	// ------------------------------------------------------------ menu editor (popup)
	
	/** Menu titik tiga: muat, simpan, bagikan, pratinjau, lalu rapikan kode dan simbol otomatis. */
	private void showEditorMenu(View anchor) {
		new M3Menu(this)
		.add(M_MUAT, R.drawable.ic_upload, "Muat")
		.add(M_SIMPAN, R.drawable.ic_save, "Simpan")
		.add(M_BAGIKAN, R.drawable.ic_share, "Bagikan")
		.add(M_PRATINJAU, previewing ? R.drawable.ic_code : R.drawable.ic_play,
		previewing ? "Tutup pratinjau" : "Pratinjau")
		.addDivider()
		.add(M_RAPIKAN, R.drawable.ic_code, "Rapikan kode")
		.addCheck(M_SIMBOL_OTOMATIS, R.drawable.ic_tune, "Lengkapi simbol otomatis", Prefs.autoPair())
		.setOnItemClickListener(id -> {
			switch (id) {
				case M_MUAT: pickFile(); break;
				case M_SIMPAN: showSaveSheet(); break;
				case M_BAGIKAN: shareText(); break;
				case M_PRATINJAU: togglePreview(); break;
				case M_RAPIKAN:
				if (previewing) showEditor();
				prettyPrint();
				break;
				case M_SIMBOL_OTOMATIS:
				Prefs.setAutoPair(!Prefs.autoPair());
				editor.setSmartEdit(Prefs.autoPair());
				M3.toast(this, Prefs.autoPair()
				? "Lengkapi simbol otomatis aktif"
				: "Lengkapi simbol otomatis nonaktif");
				break;
				default: break;
			}
		})
		.show(anchor);
	}
	
	/** Rapikan HTML. Perubahan masuk ke riwayat undo. */
	private void prettyPrint() {
		String src = editor.getText().toString();
		String out = HtmlPrettyPrinter.format(src);
		if (out.equals(src)) {
			M3.toast(this, "Kode sudah rapi");
			return;
		}
		editor.getText().replace(0, src.length(), out);
		M3.toast(this, "Kode dirapikan");
	}
	
	// ------------------------------------------------------------ undo / redo
	
	public void undo() {
		if (editor.canUndo()) editor.undo();
		else M3.toast(this, "Tidak ada yang bisa dibatalkan");
	}
	
	public void redo() {
		if (editor.canRedo()) editor.redo();
		else M3.toast(this, "Tidak ada yang bisa diulang");
	}
	
	// ------------------------------------------------------------ simpan / muat / bagikan
	
	private void setName(String name) {
		currentName = name;
		if (fileNameView != null) fileNameView.setText(name);
	}
	
	private void showSaveSheet() {
		final Sheet s = new Sheet(this, true);
		s.panel.addView(Ui.text(this, "Simpan file", 22, Theme.ON, true),
		Ui.lp(-2, -2, 0, Ui.dp(8), 0, Ui.dp(12)));
		s.panel.addView(Ui.text(this, "Disimpan di Download/Weblab. Tanpa ekstensi, memakai .html.",
		14, Theme.ON_VAR, false), Ui.lp(-1, -2, 0, 0, 0, Ui.dp(16)));
		
		final InoInputLayout input = new InoInputLayout(this);
		input.setLabelText("Nama file");
		input.setText(currentName);
		s.panel.addView(input, Ui.lp(-1, -2, 0, 0, 0, Ui.dp(16)));
		
		LinearLayout buttons = buttonRow();
		buttons.addView(Ui.button(this, "Batal", 0, Theme.PRIMARY, s::dismiss), weightLp());
		buttons.addView(Ui.button(this, "Simpan", Theme.PRIMARY, Theme.ON_PRIMARY, () -> {
			String name = input.getText().trim();
			if (name.isEmpty()) {
				M3.toast(this, "Nama file tidak boleh kosong");
				return;
			}
			if (!name.contains(".")) name += ".html";
			File file = new File(saveDir(), name);
			String text = editor.getText().toString();
			s.dismiss();
			if (file.exists()) showOverwriteSheet(file, text);
			else writeFile(file, text);
		}), weightLp());
		s.panel.addView(buttons, Ui.lp(-1, -2));
		present(s);
	}
	
	private void showOverwriteSheet(final File file, final String text) {
		final Sheet s = new Sheet(this, true);
		s.panel.addView(Ui.text(this, "File sudah ada", 22, Theme.ON, true),
		Ui.lp(-2, -2, 0, Ui.dp(8), 0, Ui.dp(12)));
		s.panel.addView(Ui.text(this, file.getName() + " sudah ada. Ganti nama, atau timpa file lama?",
		14, Theme.ON_VAR, false), Ui.lp(-1, -2, 0, 0, 0, Ui.dp(16)));
		
		LinearLayout buttons = buttonRow();
		buttons.addView(Ui.button(this, "Ganti nama", 0, Theme.PRIMARY, () -> {
			s.dismiss();
			writeFile(uniqueFile(file), text);
		}), weightLp());
		buttons.addView(Ui.button(this, "Timpa", Theme.PRIMARY, Theme.ON_PRIMARY, () -> {
			s.dismiss();
			writeFile(file, text);
		}), weightLp());
		s.panel.addView(buttons, Ui.lp(-1, -2));
		present(s);
	}
	
	private File saveDir() {
		File dir = new File(Environment.getExternalStorageDirectory(), SAVE_DIR);
		if (!dir.exists()) dir.mkdirs();
		return dir;
	}
	
	private File uniqueFile(File file) {
		String name = file.getName();
		String base = name, ext = "";
		int dot = name.lastIndexOf('.');
		if (dot > 0) {
			base = name.substring(0, dot);
			ext = name.substring(dot);
		}
		File f;
		int n = 1;
		do {
			f = new File(file.getParentFile(), base + "(" + n + ")" + ext);
			n++;
		} while (f.exists());
		return f;
	}
	
	private boolean writeFile(File file, String text) {
		try (FileOutputStream fos = new FileOutputStream(file)) {
			fos.write(text.getBytes("UTF-8"));
			setName(file.getName());
			editor.setFileName(file.getName());
			M3.toast(this, "Tersimpan: " + file.getName());
			return true;
		} catch (IOException e) {
			M3.toast(this, "Gagal menyimpan: " + e.getMessage());
			return false;
		}
	}
	
	private void pickFile() {
		Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
		intent.setType("*/*");
		intent.addCategory(Intent.CATEGORY_OPENABLE);
		startActivityForResult(Intent.createChooser(intent, "Pilih file"), REQ_LOAD);
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (requestCode != REQ_LOAD || resultCode != RESULT_OK || data == null || data.getData() == null) return;
		Uri uri = data.getData();
		try {
			String fileName = queryName(uri);
			
			StringBuilder sb = new StringBuilder();
			InputStream in = getContentResolver().openInputStream(uri);
			if (in == null) throw new IOException("tidak bisa dibuka");
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, "UTF-8"))) {
				String line;
				while ((line = reader.readLine()) != null) sb.append(line).append('\n');
			}
			editor.load(fileName, sb.toString());
			setName(fileName);
			
			String path = getPathFromUri(uri);
			if (path != null && new File(path).getParent() != null) {
				currentBaseUrl = "file://" + new File(path).getParent() + "/";
			}
			M3.toast(this, "Dimuat: " + fileName);
		} catch (Exception e) {
			M3.toast(this, "Gagal memuat file");
		}
	}
	
	private String queryName(Uri uri) {
		String name = "unknown";
		try (Cursor c = getContentResolver().query(uri, null, null, null, null)) {
			if (c != null && c.moveToFirst()) {
				int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
				if (idx >= 0) name = c.getString(idx);
			}
		} catch (Exception ignored) { }
		return name;
	}
	
	private String getPathFromUri(Uri uri) {
		if ("content".equalsIgnoreCase(uri.getScheme())) {
			String[] projection = { android.provider.MediaStore.Files.FileColumns.DATA };
			try (Cursor cursor = getContentResolver().query(uri, projection, null, null, null)) {
				if (cursor != null && cursor.moveToFirst()) {
					return cursor.getString(cursor.getColumnIndexOrThrow(
					android.provider.MediaStore.Files.FileColumns.DATA));
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		} else if ("file".equalsIgnoreCase(uri.getScheme())) {
			return uri.getPath();
		}
		return null;
	}
	
	private void shareText() {
		String text = editor.getText().toString();
		if (text.trim().isEmpty()) {
			M3.toast(this, "Tidak ada teks untuk dibagikan");
			return;
		}
		Intent share = new Intent(Intent.ACTION_SEND);
		share.setType("text/plain");
		share.putExtra(Intent.EXTRA_TEXT, text);
		startActivity(Intent.createChooser(share, "Bagikan lewat"));
	}
	
	private LinearLayout buttonRow() {
		LinearLayout r = new LinearLayout(this);
		r.setOrientation(LinearLayout.HORIZONTAL);
		return r;
	}
	
	private LinearLayout.LayoutParams weightLp() {
		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(52), 1f);
		lp.setMargins(Ui.dp(4), 0, Ui.dp(4), 0);
		return lp;
	}
	
	// ------------------------------------------------------------ sheet, info, keluar
	
	private void present(final Sheet s) {
		openSheet = s;
		s.setOnDismiss(() -> {
			if (openSheet == s) openSheet = null;
		});
		s.show();
	}
	
	private void showInfo() {
		Sheet s = new Sheet(this, true);
		String ver = "-";
		try {
			PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
			ver = pi.versionName;
		} catch (PackageManager.NameNotFoundException ignored) { }
		
		s.panel.addView(Ui.text(this, "Weblab", 22, Theme.ON, true), Ui.lp(-2, -2, 0, Ui.dp(8), 0, Ui.dp(12)));
		s.panel.addView(Ui.text(this, "Versi " + ver, 15, Theme.ON_VAR, false), Ui.lp(-2, -2, 0, 0, 0, Ui.dp(6)));
		s.panel.addView(Ui.text(this, "Editor HTML dengan nomor baris, sorot baris aktif, indentasi otomatis, "
		+ "sorotan HTML, CSS, dan JavaScript, serta pratinjau langsung. "
		+ "Berkas disimpan di Download/Weblab.",
		14, Theme.ON_VAR, false), Ui.lp(-1, -2, 0, 0, 0, Ui.dp(16)));
		s.panel.addView(Ui.button(this, "Tutup", Theme.PRIMARY, Theme.ON_PRIMARY, s::dismiss),
		Ui.lp(-1, Ui.dp(52)));
		present(s);
	}
	
	@Override
	public void onBackPressed() {
		if (drawerLayout.isOpen()) {
			drawerLayout.close();
		} else if (openSheet != null) {
			openSheet.dismiss();
			openSheet = null;
		} else if (previewing) {
			showEditor();
		} else {
			showExitSheet();
		}
	}
	
	private void showExitSheet() {
		final Sheet s = new Sheet(this, true);
		s.panel.addView(Ui.text(this, "Keluar", 22, Theme.ON, true), Ui.lp(-2, -2, 0, Ui.dp(8), 0, Ui.dp(12)));
		s.panel.addView(Ui.text(this, "Yakin ingin menutup Weblab sekarang?", 14, Theme.ON_VAR, false),
		Ui.lp(-1, -2, 0, 0, 0, Ui.dp(16)));
		
		LinearLayout buttons = buttonRow();
		buttons.addView(Ui.button(this, "Batal", 0, Theme.PRIMARY, s::dismiss), weightLp());
		buttons.addView(Ui.button(this, "Keluar", Theme.PRIMARY, Theme.ON_PRIMARY, () -> {
			s.dismiss();
			cleanupWebData();
			if (Build.VERSION.SDK_INT >= 21) finishAndRemoveTask();
			else finish();
		}), weightLp());
		s.panel.addView(buttons, Ui.lp(-1, -2));
		present(s);
	}
	
	private void cleanupWebData() {
		try {
			if (preview != null) {
				preview.clearHistory();
				preview.clearCache(true);
				preview.loadUrl("about:blank");
			}
			CookieManager cm = CookieManager.getInstance();
			if (Build.VERSION.SDK_INT >= 21) {
				cm.removeAllCookies(null);
				cm.flush();
			}
			deleteDir(getCacheDir());
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	private boolean deleteDir(File dir) {
		if (dir != null && dir.isDirectory()) {
			String[] children = dir.list();
			if (children != null) {
				for (String child : children) deleteDir(new File(dir, child));
			}
		}
		return dir != null && dir.delete();
	}
	
	// ------------------------------------------------------------ izin
	
	private void checkAndRequestPermissions() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
			if (!Environment.isExternalStorageManager()) {
				try {
					Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
					intent.setData(Uri.parse("package:" + getPackageName()));
					startActivity(intent);
				} catch (Exception e) {
					startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
				}
			}
		} else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
			|| checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
				requestPermissions(new String[]{
					Manifest.permission.READ_EXTERNAL_STORAGE,
					Manifest.permission.WRITE_EXTERNAL_STORAGE
				}, REQ_PERM);
			}
		}
	}
	
	@Override
	public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);
		if (requestCode != REQ_PERM) return;
		boolean granted = grantResults.length > 0;
		for (int res : grantResults) {
			if (res == PackageManager.PERMISSION_DENIED) {
				granted = false;
				break;
			}
		}
		M3.toast(this, granted ? "Izin diberikan, siap menyimpan file" : "Izin ditolak. File tidak bisa disimpan");
	}
}
