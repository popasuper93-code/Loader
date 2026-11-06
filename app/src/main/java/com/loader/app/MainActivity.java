package com.loader.app;

import android.Manifest;
import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.ScaleAnimation;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.entity.pm.InstallResult;

public class MainActivity extends Activity {

    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    // Цвета статусов
    private static final int C_OK = 0xFF3DDC97;
    private static final int C_ERR = 0xFFFF5C6C;
    private static final int C_WARN = 0xFFFFB84D;

    // Discord всегда в своём фирменном цвете
    private static final int DISC_A = 0xFF5865F2;
    private static final int DISC_B = 0xFFB06BFF;

    // Варианты акцента
    private static final String[] ACC_KEYS = {"purple", "blue", "red", "green"};
    private static final String[] ACC_NAMES = {"Фиолетовый", "Синий", "Красный", "Зелёный"};
    private static final int[] ACC_A = {0xFF7C5CFF, 0xFF2F80FF, 0xFFFF3B4D, 0xFF18C76B};
    private static final int[] ACC_B = {0xFF00D4FF, 0xFF00C2FF, 0xFFFF9A5B, 0xFF9BE15D};

    // Палитра (зависит от настроек)
    private int cBg1, cBg2, cCard, cStroke, cText, cMuted, cNav, accA, accB;
    private boolean dark = true;
    private String accentKey = "purple";

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    private String gamePackage;
    private String discordPackage;

    private int curTab = 0;
    private boolean firstBuild = true;
    private boolean resumed = false;
    private String lastLogText = null;

    // Ссылки на текущие view
    private final View[] pages = new View[3];
    private final TextView[] tabIcons = new TextView[3];
    private final TextView[] tabLabels = new TextView[3];
    private TextView gameStatus, discordStatus, busyText, logView;
    private View btnRunGame, btnRunDiscord, busyBox;
    private ScrollView logScroll;
    private Animator pulse;

    /** Элемент списка установленных приложений */
    private static class AppItem {
        final String label, pkg, apkPath;
        final Drawable icon;
        final int splitCount;
        final boolean match;

        AppItem(String label, String pkg, String apkPath, Drawable icon, int splitCount, boolean match) {
            this.label = label;
            this.pkg = pkg;
            this.apkPath = apkPath;
            this.icon = icon;
            this.splitCount = splitCount;
            this.match = match;
        }
    }

    private interface BoolCb {
        void onChange(boolean value);
    }

    // =====================================================================
    // Жизненный цикл
    // =====================================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        App.log("MainActivity.onCreate");
        loadSettings();
        buildUi();
        refreshState();
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        ui.removeCallbacks(ticker);
        ui.post(ticker);
    }

    @Override
    protected void onPause() {
        super.onPause();
        resumed = false;
        ui.removeCallbacks(ticker);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ui.removeCallbacks(ticker);
        io.shutdown();
    }

    /** Пока открыта вкладка «Логи» — подтягиваем новые строки (их пишут и гостевые процессы). */
    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            if (resumed && curTab == 2) refreshLogs();
            ui.postDelayed(this, 1200);
        }
    };

    // =====================================================================
    // Настройки и тема
    // =====================================================================

    private void loadSettings() {
        dark = !"light".equals(App.readPref(App.K_THEME));
        String k = App.readPref(App.K_ACCENT);
        accentKey = "purple";
        for (String key : ACC_KEYS) if (key.equals(k)) accentKey = key;
        int idx = Arrays.asList(ACC_KEYS).indexOf(accentKey);
        accA = ACC_A[idx];
        accB = ACC_B[idx];

        if (dark) {
            cBg1 = 0xFF080B16; cBg2 = 0xFF1A0F33; cCard = 0xFF121A2E; cStroke = 0xFF263150;
            cText = 0xFFF2F4FF; cMuted = 0xFF8A93B2; cNav = 0xFF0C1224;
        } else {
            cBg1 = 0xFFF4F6FF; cBg2 = 0xFFE4E8FA; cCard = 0xFFFFFFFF; cStroke = 0xFFD5DAF0;
            cText = 0xFF151A2E; cMuted = 0xFF5E6684; cNav = 0xFFFFFFFF;
        }
    }

    private void applySystemBars() {
        getWindow().setStatusBarColor(cBg1);
        getWindow().setNavigationBarColor(cNav);
        int flags = 0;
        if (!dark) {
            if (Build.VERSION.SDK_INT >= 23) flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= 26) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        getWindow().getDecorView().setSystemUiVisibility(flags);
    }

    /** Пересобирает интерфейс (после смены темы/акцента). */
    private void rebuild() {
        if (pulse != null) pulse.cancel();
        lastLogText = null;
        buildUi();
        refreshState();
    }

    // =====================================================================
    // Каркас: страницы + нижняя панель вкладок
    // =====================================================================

    private void buildUi() {
        applySystemBars();

        FrameLayout root = new FrameLayout(this);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{cBg1, cBg2}));

        LinearLayout main = new LinearLayout(this);
        main.setOrientation(LinearLayout.VERTICAL);

        FrameLayout host = new FrameLayout(this);
        pages[0] = buildGamesPage();
        pages[1] = buildSettingsPage();
        pages[2] = buildLogsPage();
        for (View p : pages) host.addView(p, new FrameLayout.LayoutParams(MATCH, MATCH));
        main.addView(host, new LinearLayout.LayoutParams(MATCH, 0, 1f));
        main.addView(buildNavBar(), new LinearLayout.LayoutParams(MATCH, dp(62)));

        root.addView(main, new FrameLayout.LayoutParams(MATCH, MATCH));
        setContentView(root);

        selectTab(curTab, false);
        firstBuild = false;
    }

    private View buildNavBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(cNav);
        bg.setStroke(dp(1), cStroke);
        bar.setBackground(bg);

        String[] icons = {"🎮", "⚙", "☰"};
        String[] labels = {"Игры", "Настройки", "Логи"};
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            LinearLayout tab = new LinearLayout(this);
            tab.setOrientation(LinearLayout.VERTICAL);
            tab.setGravity(Gravity.CENTER);
            tab.setClickable(true);
            tab.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22FFFFFF),
                    new ColorDrawable(0), new ColorDrawable(Color.WHITE)));

            tabIcons[i] = text(icons[i], 20, false, cMuted);
            tabLabels[i] = text(labels[i], 11, true, cMuted);
            tab.addView(tabIcons[i]);
            tab.addView(tabLabels[i]);
            tab.setOnClickListener(v -> selectTab(idx, true));
            bar.addView(tab, new LinearLayout.LayoutParams(0, MATCH, 1f));
        }
        return bar;
    }

    private void selectTab(int idx, boolean animate) {
        curTab = idx;
        for (int i = 0; i < 3; i++) {
            boolean sel = (i == idx);
            pages[i].setVisibility(sel ? View.VISIBLE : View.GONE);
            tabIcons[i].setTextColor(sel ? accA : cMuted);
            tabLabels[i].setTextColor(sel ? accA : cMuted);
        }
        if (animate) fadeIn(pages[idx], 0, 250);
        if (idx == 2) refreshLogs();
    }

    // =====================================================================
    // Страница «Игры»
    // =====================================================================

    private View buildGamesPage() {
        LinearLayout col = column();

        LinearLayout head = pageHeader("ChadLouder", "Виртуальная среда BlackBox · без root", true);
        col.addView(head);

        // Индикатор занятости
        LinearLayout busy = new LinearLayout(this);
        busyBox = busy;
        busy.setOrientation(LinearLayout.VERTICAL);
        ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setIndeterminate(true);
        bar.setIndeterminateTintList(ColorStateList.valueOf(accB));
        busy.addView(bar, new LinearLayout.LayoutParams(MATCH, dp(6)));
        busyText = text("", 12, false, cMuted);
        busy.addView(busyText);
        busy.setVisibility(View.GONE);
        addRow(col, busy, 18, false);

        // Oxide
        LinearLayout gameCard = newCard("Oxide", "Выберите установленную Oxide: Survival Island", accA, accB);
        gameStatus = text("", 13, true, cMuted);
        addRow(gameCard, gameStatus, 14, false);
        TextView addGame = button("Добавить игру", accA, accB, false, 16);
        addGame.setOnClickListener(v -> showAppPicker(true));
        addRow(gameCard, addGame, 14, true);
        TextView runGame = button("Запустить игру", accA, accB, true, 16);
        runGame.setOnClickListener(v -> launch(true));
        btnRunGame = runGame;
        addRow(gameCard, runGame, 10, true);
        col.addView(gameCard);

        // Discord
        LinearLayout discCard = newCard("Discord", "Выберите установленный Discord", DISC_A, DISC_B);
        discordStatus = text("", 13, true, cMuted);
        addRow(discCard, discordStatus, 14, false);
        TextView addDisc = button("Добавить Discord", DISC_A, DISC_B, false, 16);
        addDisc.setOnClickListener(v -> showAppPicker(false));
        addRow(discCard, addDisc, 14, true);
        TextView runDisc = button("Запустить Discord", DISC_A, DISC_B, true, 16);
        runDisc.setOnClickListener(v -> launch(false));
        btnRunDiscord = runDisc;
        addRow(discCard, runDisc, 10, true);
        col.addView(discCard);

        // Диагностика
        LinearLayout diagCard = newCard("Диагностика", "Проверка виртуальной среды, результат — во вкладке «Логи»",
                accA, accB);
        TextView check = button("Проверить BlackBox", accA, accB, false, 16);
        check.setOnClickListener(v -> checkBlackBox());
        addRow(diagCard, check, 14, true);
        col.addView(diagCard);

        if (firstBuild) {
            fadeIn(head, 0, 450);
            fadeIn(gameCard, 120, 450);
            fadeIn(discCard, 240, 450);
            fadeIn(diagCard, 360, 450);
        }
        return scrollPage(col);
    }

    // =====================================================================
    // Страница «Настройки»
    // =====================================================================

    private View buildSettingsPage() {
        LinearLayout col = column();
        col.addView(pageHeader("Настройки", "Внешний вид и плагин", false));

        // --- Акцент
        LinearLayout accCard = newCard("Цвет акцента", "Применяется ко всему интерфейсу", accA, accB);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < ACC_KEYS.length; i++) {
            final String key = ACC_KEYS[i];
            boolean sel = key.equals(accentKey);

            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER_HORIZONTAL);

            View circle = new View(this);
            GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    new int[]{ACC_A[i], ACC_B[i]});
            g.setShape(GradientDrawable.OVAL);
            if (sel) g.setStroke(dp(3), cText);
            circle.setBackground(g);
            item.addView(circle, new LinearLayout.LayoutParams(dp(46), dp(46)));

            TextView lbl = text(ACC_NAMES[i], 10, sel, sel ? cText : cMuted);
            LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(WRAP, WRAP);
            ll.topMargin = dp(6);
            item.addView(lbl, ll);

            item.setOnClickListener(v -> {
                App.writePref(App.K_ACCENT, key);
                App.log("Акцент: " + key);
                loadSettings();
                rebuild();
            });
            LinearLayout.LayoutParams il = new LinearLayout.LayoutParams(0, WRAP, 1f);
            row.addView(item, il);
        }
        addRow(accCard, row, 16, false);
        col.addView(accCard);

        // --- Тема
        LinearLayout themeCard = newCard("Тема", "Тёмная — по умолчанию", accA, accB);
        addRow(themeCard, toggleRow("Светлая тема", "Переключить оформление", !dark, v -> {
            App.writePref(App.K_THEME, v ? "light" : "dark");
            App.log("Тема: " + (v ? "светлая" : "тёмная"));
            loadSettings();
            rebuild();
        }), 14, false);
        col.addView(themeCard);

        // --- Плагин
        LinearLayout plCard = newCard("Плагин (.so)",
                "Если игра висит на заставке — выключите плагин и проверьте, стартует ли чистая игра",
                accA, accB);
        addRow(plCard, toggleRow("Загружать плагин", "Только в процесс Oxide",
                App.isEnabled(App.K_PLUGIN, true), v -> {
                    App.writePref(App.K_PLUGIN, v ? "1" : "0");
                    App.log("Плагин: " + (v ? "включён" : "выключен"));
                }), 14, false);
        addRow(plCard, toggleRow("Грузить после старта игры",
                "Иначе — раньше всего, до создания Application",
                App.isEnabled(App.K_PLUGIN_LATE, false), v -> {
                    App.writePref(App.K_PLUGIN_LATE, v ? "1" : "0");
                    App.log("Загрузка плагина: " + (v ? "после старта игры" : "до старта игры"));
                }), 14, false);
        col.addView(plCard);

        return scrollPage(col);
    }

    private LinearLayout toggleRow(String title, String hint, boolean on, BoolCb cb) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(text(title, 15, true, cText));
        TextView h = text(hint, 12, false, cMuted);
        texts.addView(h);
        row.addView(texts, new LinearLayout.LayoutParams(0, WRAP, 1f));

        final Toggle t = new Toggle(this, on);
        row.addView(t, new LinearLayout.LayoutParams(dp(52), dp(30)));
        row.setClickable(true);
        row.setOnClickListener(v -> {
            t.set(!t.on);
            cb.onChange(t.on);
        });
        return row;
    }

    /** Самодельный переключатель в цвете акцента. */
    private class Toggle extends FrameLayout {
        final View knob;
        boolean on;

        Toggle(Context c, boolean on) {
            super(c);
            knob = new View(c);
            GradientDrawable k = new GradientDrawable();
            k.setShape(GradientDrawable.OVAL);
            k.setColor(Color.WHITE);
            knob.setBackground(k);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(24), dp(24));
            lp.leftMargin = dp(3);
            lp.rightMargin = dp(3);
            addView(knob, lp);
            set(on);
        }

        void set(boolean v) {
            on = v;
            GradientDrawable track = new GradientDrawable();
            track.setCornerRadius(dp(15));
            track.setColor(on ? accA : cStroke);
            setBackground(track);
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) knob.getLayoutParams();
            lp.gravity = (on ? Gravity.END : Gravity.START) | Gravity.CENTER_VERTICAL;
            knob.setLayoutParams(lp);
        }
    }

    // =====================================================================
    // Страница «Логи»
    // =====================================================================

    private View buildLogsPage() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(20), dp(24), dp(20), dp(16));

        col.addView(pageHeader("Логи", "Все действия лоудера и гостевых приложений", false));

        LinearLayout btns = new LinearLayout(this);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        String[] names = {"Обновить", "Копия", "Logcat", "Очистить"};
        for (int i = 0; i < names.length; i++) {
            final int idx = i;
            TextView b = button(names[i], accA, accB, i == 0, 12);
            b.setOnClickListener(v -> {
                if (idx == 0) refreshLogs();
                else if (idx == 1) copyLogs();
                else if (idx == 2) collectLogcat();
                else clearLogs();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(42), 1f);
            if (i > 0) lp.leftMargin = dp(8);
            btns.addView(b, lp);
        }
        addRow(col, btns, 14, false);

        logView = text("", 11, false, cText);
        logView.setTypeface(Typeface.MONOSPACE);
        logView.setTextIsSelectable(true);
        logView.setPadding(dp(12), dp(12), dp(12), dp(12));

        logScroll = new ScrollView(this);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(cCard);
        bg.setCornerRadius(dp(18));
        bg.setStroke(dp(1), cStroke);
        logScroll.setBackground(bg);
        logScroll.addView(logView, new FrameLayout.LayoutParams(MATCH, WRAP));

        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(MATCH, 0, 1f);
        sp.topMargin = dp(12);
        col.addView(logScroll, sp);
        return col;
    }

    private void refreshLogs() {
        if (logView == null) return;
        String t = App.readLogTail(40 * 1024);
        if (t.isEmpty()) t = "(лог пуст)";
        if (t.equals(lastLogText)) return;
        lastLogText = t;
        logView.setText(colorize(t));
        logScroll.post(() -> logScroll.fullScroll(View.FOCUS_DOWN));
    }

    private CharSequence colorize(String s) {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        for (String line : s.split("\n")) {
            int start = sb.length();
            sb.append(line).append('\n');
            int col = 0;
            if (line.contains("CRASH") || line.contains("ОШИБКА") || line.contains("FATAL")
                    || line.contains("НЕ инициализирован") || line.contains("ОТСУТСТВУЕТ")) {
                col = C_ERR;
            } else if (line.contains("ПРИЧИНА") || line.contains("⚠") || line.contains("⏱")) {
                col = C_WARN;
            } else if (line.contains("✔") || line.contains("инициализирован")) {
                col = C_OK;
            }
            if (col != 0) sb.setSpan(new ForegroundColorSpan(col), start, sb.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return sb;
    }

    private void copyLogs() {
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("ChadLouder log", App.readLogTail(60 * 1024)));
            toast("Лог скопирован");
        } catch (Throwable t) {
            toast("Не удалось скопировать: " + t.getMessage());
        }
    }

    private void clearLogs() {
        App.clearLog();
        lastLogText = null;
        refreshLogs();
    }

    /** Вытаскивает из системного logcat строки про краши/нативные ошибки (наш UID, включая гостей). */
    private void collectLogcat() {
        App.log("— Сбор logcat —");
        io.execute(() -> {
            List<String> found = new ArrayList<>();
            try {
                java.lang.Process p = Runtime.getRuntime().exec(
                        new String[]{"logcat", "-d", "-v", "time", "-t", "3000"});
                BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
                String line;
                while ((line = r.readLine()) != null) {
                    if (interesting(line)) found.add(line);
                }
                p.destroy();
            } catch (Throwable t) {
                found.add("logcat недоступен: " + t);
            }
            int from = Math.max(0, found.size() - 60);
            if (found.isEmpty()) App.log("[logcat] подходящих строк нет");
            for (int i = from; i < found.size(); i++) {
                String l = found.get(i);
                App.log("[logcat] " + (l.length() > 300 ? l.substring(0, 300) + "…" : l));
            }
            ui.post(this::refreshLogs);
        });
    }

    private boolean interesting(String line) {
        String s = line.toLowerCase(Locale.ROOT);
        return s.contains("fatal") || s.contains("androidruntime") || s.contains("unsatisfiedlink")
                || s.contains("classnotfound") || s.contains("noclassdeffound")
                || s.contains("securityexception") || s.contains("googleplay")
                || s.contains("com.google.android.gms") || s.contains("backtrace")
                || s.contains("signal 11") || s.contains("sigsegv") || s.contains("sigabrt")
                || s.contains("catsbit") || s.contains("oxide") || s.contains("discord")
                || s.contains("niunaijun") || s.contains("blackbox") || s.contains(" loader");
    }

    // =====================================================================
    // Проверка BlackBox
    // =====================================================================

    private void checkBlackBox() {
        App.log("— Проверка BlackBox —");
        try {
            if (App.sBlackBoxReady && BlackBoxCore.get() != null) {
                App.log("✔ BlackBox инициализирован");
            } else {
                App.log("НЕ инициализирован: " + (App.sInitError == null ? "неизвестно" : App.sInitError));
            }
        } catch (Throwable t) {
            App.log("НЕ инициализирован: " + t);
        }

        String abi = Build.SUPPORTED_ABIS.length > 0 ? Build.SUPPORTED_ABIS[0] : "?";
        boolean is64 = Build.VERSION.SDK_INT >= 23 ? android.os.Process.is64Bit() : abi.contains("64");
        String arch = abi.contains("arm64") ? "arm64" : abi;
        App.log("Архитектура: " + arch + (is64 ? " (64-bit процесс)" : " (32-bit процесс)")
                + ", ABI: " + Arrays.toString(Build.SUPPORTED_ABIS));
        if (!abi.contains("arm64")) App.log("⚠ Устройство не arm64 — плагин arm64-v8a работать не будет");

        try {
            ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            App.log("Память: " + (mi.totalMem >> 20) + " MB всего, "
                    + (mi.availMem >> 20) + " MB свободно, heap-лимит "
                    + (Runtime.getRuntime().maxMemory() >> 20) + " MB"
                    + (mi.lowMemory ? " ⚠ мало памяти" : ""));
        } catch (Throwable t) {
            App.log("Память: не удалось определить: " + t);
        }

        File so = new File(getApplicationInfo().nativeLibraryDir, "lib" + App.PLUGIN_LIB + ".so");
        App.log(so.exists()
                ? "✔ Плагин lib" + App.PLUGIN_LIB + ".so найден (" + so.length() + " байт)"
                : "ОШИБКА: lib" + App.PLUGIN_LIB + ".so не найден в " + getApplicationInfo().nativeLibraryDir);

        App.log("Плагин: " + (App.isEnabled(App.K_PLUGIN, true) ? "включён" : "выключен")
                + ", загрузка " + (App.isEnabled(App.K_PLUGIN_LATE, false) ? "после" : "до")
                + " старта игры");
        App.log("Игра: " + (gamePackage == null ? "не добавлена" : gamePackage)
                + " | Discord: " + (discordPackage == null ? "не добавлен" : discordPackage));
        logGms();

        selectTab(2, true);
    }

    private void logGms() {
        try {
            PackageInfo gms = getPackageManager().getPackageInfo("com.google.android.gms", 0);
            App.log("Google Play Services на телефоне: есть (v" + gms.versionName + "). "
                    + "Внутри BlackBox они могут быть недоступны гостю");
        } catch (Throwable t) {
            App.log("Google Play Services на телефоне: ОТСУТСТВУЕТ — Discord может падать при старте");
        }
    }

    // =====================================================================
    // Выбор из установленных приложений
    // =====================================================================

    private void showAppPicker(boolean game) {
        final String need = game ? App.GAME_PKG : App.DISCORD_PKG;
        setBusy(true, "Загружаю список приложений…");
        io.execute(() -> {
            List<AppItem> apps = loadInstalledApps(need);
            ui.post(() -> {
                setBusy(false, "");
                if (isFinishing()) return;
                boolean any = false;
                for (AppItem a : apps) if (a.match) any = true;
                if (!any) {
                    App.log("ОШИБКА: на телефоне не найден " + need);
                    toast("На телефоне не установлен " + need);
                    return;
                }
                openPickerDialog(apps, game);
            });
        });
    }

    /** getInstalledPackages(0) → только приложения с иконкой запуска, нужный пакет — сверху. */
    private List<AppItem> loadInstalledApps(String need) {
        List<AppItem> out = new ArrayList<>();
        try {
            PackageManager pm = getPackageManager();
            for (PackageInfo pi : pm.getInstalledPackages(0)) {
                if (pi == null || pi.applicationInfo == null) continue;
                if (pi.packageName.equals(getPackageName())) continue;
                if (pm.getLaunchIntentForPackage(pi.packageName) == null) continue;
                ApplicationInfo ai = pi.applicationInfo;
                if (ai.sourceDir == null) continue;
                int splits = ai.splitSourceDirs == null ? 0 : ai.splitSourceDirs.length;
                out.add(new AppItem(String.valueOf(ai.loadLabel(pm)), pi.packageName, ai.sourceDir,
                        ai.loadIcon(pm), splits, pi.packageName.equals(need)));
            }
        } catch (Throwable t) {
            App.log("ОШИБКА getInstalledPackages: " + t);
        }
        Collections.sort(out, (a, b) -> {
            if (a.match != b.match) return a.match ? -1 : 1;
            return a.label.compareToIgnoreCase(b.label);
        });
        return out;
    }

    private void openPickerDialog(List<AppItem> all, boolean game) {
        final Dialog dlg = new Dialog(this);
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(18), dp(16), dp(12));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(cCard);
        bg.setCornerRadius(dp(24));
        bg.setStroke(dp(1), cStroke);
        box.setBackground(bg);

        box.addView(text(game ? "Выберите Oxide" : "Выберите Discord", 20, true, cText));

        EditText search = new EditText(this);
        search.setHint("Поиск");
        search.setHintTextColor(cMuted);
        search.setTextColor(cText);
        search.setTextSize(15);
        search.setSingleLine(true);
        search.setPadding(dp(14), dp(10), dp(14), dp(10));
        GradientDrawable sb = new GradientDrawable();
        sb.setColor(dark ? 0xFF0C1224 : 0xFFF0F2FC);
        sb.setCornerRadius(dp(14));
        sb.setStroke(dp(1), cStroke);
        search.setBackground(sb);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(MATCH, WRAP);
        sp.topMargin = dp(12);
        sp.bottomMargin = dp(8);
        box.addView(search, sp);

        final List<AppItem> shown = new ArrayList<>(all);
        final AppAdapter adapter = new AppAdapter(shown);

        ListView list = new ListView(this);
        list.setAdapter(adapter);
        list.setDivider(new ColorDrawable(0));
        list.setDividerHeight(dp(2));
        list.setVerticalScrollBarEnabled(false);
        box.addView(list, new LinearLayout.LayoutParams(MATCH, 0, 1f));

        list.setOnItemClickListener((parent, view, pos, id) -> {
            AppItem it = shown.get(pos);
            dlg.dismiss();
            installFromInstalled(it, game);
        });

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }

            @Override
            public void afterTextChanged(Editable e) {
                String q = e.toString().trim().toLowerCase(Locale.ROOT);
                shown.clear();
                for (AppItem it : all) {
                    if (q.isEmpty() || it.label.toLowerCase(Locale.ROOT).contains(q)
                            || it.pkg.toLowerCase(Locale.ROOT).contains(q)) {
                        shown.add(it);
                    }
                }
                adapter.notifyDataSetChanged();
            }
        });

        dlg.setContentView(box);
        dlg.show();

        Window w = dlg.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            int h = (int) (getResources().getDisplayMetrics().heightPixels * 0.8f);
            int wd = (int) (getResources().getDisplayMetrics().widthPixels * 0.92f);
            w.setLayout(wd, h);
        }
    }

    private class AppAdapter extends BaseAdapter {
        private final List<AppItem> data;

        AppAdapter(List<AppItem> data) {
            this.data = data;
        }

        @Override public int getCount() { return data.size(); }
        @Override public Object getItem(int i) { return data.get(i); }
        @Override public long getItemId(int i) { return i; }

        @Override
        public View getView(int i, View convert, ViewGroup parent) {
            LinearLayout row;
            ImageView icon;
            TextView name, pkg;

            if (convert == null) {
                row = new LinearLayout(MainActivity.this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(10), dp(10), dp(10), dp(10));

                GradientDrawable base = new GradientDrawable();
                base.setColor(0);
                base.setCornerRadius(dp(14));
                GradientDrawable mask = new GradientDrawable();
                mask.setColor(Color.WHITE);
                mask.setCornerRadius(dp(14));
                row.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33808080), base, mask));

                icon = new ImageView(MainActivity.this);
                icon.setId(1);
                row.addView(icon, new LinearLayout.LayoutParams(dp(44), dp(44)));

                LinearLayout texts = new LinearLayout(MainActivity.this);
                texts.setOrientation(LinearLayout.VERTICAL);
                LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, WRAP, 1f);
                tl.leftMargin = dp(12);

                name = text("", 16, true, cText);
                name.setId(2);
                name.setSingleLine(true);
                pkg = text("", 12, false, cMuted);
                pkg.setId(3);
                pkg.setSingleLine(true);
                texts.addView(name);
                texts.addView(pkg);
                row.addView(texts, tl);
            } else {
                row = (LinearLayout) convert;
                icon = row.findViewById(1);
                name = row.findViewById(2);
                pkg = row.findViewById(3);
            }

            AppItem it = data.get(i);
            icon.setImageDrawable(it.icon);
            name.setText(it.match ? "✓ " + it.label : it.label);
            String extra = it.splitCount > 0 ? " · split ×" + it.splitCount : "";
            pkg.setText(it.pkg + extra);
            pkg.setTextColor(it.match ? C_OK : cMuted);
            return row;
        }
    }

    // =====================================================================
    // Копирование APK, проверка пакета и установка в BlackBox
    // =====================================================================

    private void installFromInstalled(AppItem it, boolean game) {
        setBusy(true, "Копирую APK…");
        io.execute(() -> {
            String err = installBlocking(it, game);
            ui.post(() -> {
                setBusy(false, "");
                refreshState();
                if (err != null) {
                    toast(err);
                } else if (it.splitCount > 0) {
                    toast("Добавлено, но у приложения split-APK (×" + it.splitCount
                            + "): может не запуститься");
                } else {
                    toast("Готово: добавлено в BlackBox");
                }
            });
        });
    }

    /** Возвращает null при успехе или текст ошибки. */
    private String installBlocking(AppItem it, boolean game) {
        final String need = game ? App.GAME_PKG : App.DISCORD_PKG;
        final String name = game ? "game" : "discord";
        try {
            App.log("Добавление " + it.pkg + " из " + it.apkPath
                    + (it.splitCount > 0 ? " (split-частей: " + it.splitCount + ")" : ""));

            File dir = new File(getFilesDir(), "apks");
            if (!dir.exists() && !dir.mkdirs()) return "Не удалось создать папку";
            File tmp = new File(dir, name + ".tmp");

            try (InputStream is = new FileInputStream(it.apkPath);
                 OutputStream os = new FileOutputStream(tmp)) {
                byte[] buf = new byte[256 * 1024];
                int n;
                while ((n = is.read(buf)) > 0) os.write(buf, 0, n);
            }

            // Анти-кряк: пакет читаем из самого APK
            PackageInfo pi = getPackageManager().getPackageArchiveInfo(tmp.getAbsolutePath(), 0);
            if (pi == null || pi.packageName == null) {
                tmp.delete();
                App.log("ОШИБКА: не удалось прочитать APK " + it.apkPath);
                return "Не удалось прочитать APK";
            }
            if (!need.equals(pi.packageName)) {
                tmp.delete();
                App.log("ОШИБКА: неверный пакет " + pi.packageName + ", нужен " + need);
                return "Неверный пакет: " + pi.packageName + "\nНужен: " + need;
            }

            File out = new File(dir, name + ".apk");
            if (out.exists() && !out.delete()) return "Не удалось заменить старый APK";
            if (!tmp.renameTo(out)) return "Не удалось сохранить APK";
            App.log("APK скопирован: " + out.getAbsolutePath() + " (" + (out.length() >> 10) + " КБ)");

            ui.post(() -> setBusy(true, "Устанавливаю в BlackBox…"));
            InstallResult r = BlackBoxCore.get().installPackageAsUser(out, App.USER_ID);

            if (r == null || !r.success) {
                App.log("Установка из файла не удалась: " + (r == null ? "нет ответа" : r.msg)
                        + " — пробую по имени пакета");
                try {
                    r = BlackBoxCore.get().installPackageAsUser(it.pkg, App.USER_ID);
                } catch (Throwable ignored) {
                    // оставляем результат первой попытки
                }
            }
            if (r == null || !r.success) {
                String m = r == null ? "нет ответа" : String.valueOf(r.msg);
                App.log("ОШИБКА установки: " + m);
                return "Ошибка установки: " + m;
            }

            if (game) {
                gamePackage = it.pkg;
                App.writePref(App.F_GAME, it.pkg);
            } else {
                discordPackage = it.pkg;
                App.writePref(App.F_DISCORD, it.pkg);
            }
            App.log("✔ " + it.pkg + " добавлен в BlackBox");
            return null;
        } catch (Throwable t) {
            App.log("ОШИБКА добавления: " + App.rootCauseLine(App.stackOf(t)));
            return "Ошибка: " + t;
        }
    }

    // =====================================================================
    // Запуск с перехватом ошибок
    // =====================================================================

    private void launch(boolean game) {
        String pkg = game ? gamePackage : discordPackage;
        if (pkg == null) {
            toast(game ? "Сначала добавьте игру" : "Сначала добавьте Discord");
            return;
        }

        App.log("▶ Запуск " + pkg + " (" + (game ? "игра" : "Discord") + ")");
        if (!game) {
            logGms();
            if (Build.VERSION.SDK_INT >= 23) {
                requestPermissions(new String[]{
                        Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA}, 1);
            }
        }

        try {
            BlackBoxCore.get().launchApk(pkg, App.USER_ID);
            App.log("launchApk вызван без исключений — дальше смотрите события гостя ниже");
        } catch (Throwable t) {
            String st = App.stackOf(t);
            App.log("ОШИБКА запуска " + pkg + ": " + App.rootCauseLine(st));
            App.log("ПРИЧИНА: " + App.explainCrash(st));
            toast("Ошибка запуска: " + t.getMessage());
            return;
        }

        // Жив ли гостевой процесс? Если его нет — приложение упало сразу после старта.
        ui.postDelayed(() -> checkProcesses("через 4 с"), 4000);
        ui.postDelayed(() -> checkProcesses("через 15 с"), 15000);
    }

    private void checkProcesses(String label) {
        try {
            ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
            List<ActivityManager.RunningAppProcessInfo> list = am.getRunningAppProcesses();
            StringBuilder sb = new StringBuilder();
            String prefix = getPackageName() + ":";
            if (list != null) {
                for (ActivityManager.RunningAppProcessInfo p : list) {
                    if (p.processName.startsWith(prefix)) {
                        sb.append(p.processName.substring(getPackageName().length())).append(' ');
                    }
                }
            }
            if (sb.length() == 0) {
                App.log("⚠ " + label + ": гостевых процессов BlackBox нет — приложение упало или не стартовало");
            } else {
                App.log(label + ": процессы BlackBox: " + sb);
            }
        } catch (Throwable t) {
            App.log("checkProcesses: " + t);
        }
    }

    // =====================================================================
    // Состояние UI
    // =====================================================================

    private void refreshState() {
        gamePackage = App.readPref(App.F_GAME);
        discordPackage = App.readPref(App.F_DISCORD);
        bindStatus(gameStatus, btnRunGame, gamePackage);
        bindStatus(discordStatus, btnRunDiscord, discordPackage);
    }

    private void bindStatus(TextView st, View run, String pkg) {
        if (st == null || run == null) return;
        if (pkg == null) {
            st.setText("● Не добавлено");
            st.setTextColor(cMuted);
            run.setAlpha(0.35f);
        } else {
            st.setText("● Готово · " + pkg);
            st.setTextColor(C_OK);
            run.setAlpha(1f);
        }
    }

    private void setBusy(boolean busy, String msg) {
        if (busyBox == null) return;
        busyBox.setVisibility(busy ? View.VISIBLE : View.GONE);
        busyText.setText(msg);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }

    // =====================================================================
    // Хелперы UI и анимации
    // =====================================================================

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private TextView text(String s, float sp, boolean bold, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private LinearLayout column() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(20), dp(24), dp(20), dp(24));
        return c;
    }

    private ScrollView scrollPage(LinearLayout col) {
        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.setFillViewport(true);
        sv.addView(col, new FrameLayout.LayoutParams(MATCH, WRAP));
        return sv;
    }

    private LinearLayout pageHeader(String title, String sub, boolean withDot) {
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.VERTICAL);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        if (withDot) {
            View dot = new View(this);
            GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    new int[]{accA, accB});
            d.setShape(GradientDrawable.OVAL);
            dot.setBackground(d);
            LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(dp(14), dp(14));
            dl.rightMargin = dp(12);
            row.addView(dot, dl);

            ObjectAnimator a = ObjectAnimator.ofPropertyValuesHolder(dot,
                    PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.6f),
                    PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.6f),
                    PropertyValuesHolder.ofFloat(View.ALPHA, 1f, 0.5f));
            a.setDuration(900);
            a.setRepeatCount(ValueAnimator.INFINITE);
            a.setRepeatMode(ValueAnimator.REVERSE);
            a.start();
            pulse = a;
        }

        TextView t = text(title, 28, true, cText);
        t.setLetterSpacing(0.04f);
        row.addView(t);
        head.addView(row);

        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(MATCH, WRAP);
        sl.topMargin = dp(6);
        head.addView(text(sub, 13, false, cMuted), sl);
        return head;
    }

    private void addRow(LinearLayout parent, View child, int topDp, boolean isButton) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(MATCH, isButton ? dp(54) : WRAP);
        p.topMargin = dp(topDp);
        parent.addView(child, p);
    }

    private LinearLayout newCard(String title, String hint, int a1, int a2) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(20), dp(20), dp(20), dp(20));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(cCard);
        bg.setCornerRadius(dp(24));
        bg.setStroke(dp(1), cStroke);
        c.setBackground(bg);

        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(MATCH, WRAP);
        cp.topMargin = dp(20);
        c.setLayoutParams(cp);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        View accent = new View(this);
        GradientDrawable ab = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{a1, a2});
        ab.setCornerRadius(dp(3));
        accent.setBackground(ab);
        LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(dp(4), dp(24));
        al.rightMargin = dp(12);
        row.addView(accent, al);
        row.addView(text(title, 20, true, cText));
        c.addView(row);

        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(MATCH, WRAP);
        hp.topMargin = dp(6);
        c.addView(text(hint, 13, false, cMuted), hp);
        return c;
    }

    private TextView button(String label, int a1, int a2, boolean filled, float sp) {
        TextView b = new TextView(this);
        b.setText(label);
        b.setGravity(Gravity.CENTER);
        b.setTextSize(sp);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(filled ? Color.WHITE : a1);
        b.setClickable(true);
        b.setFocusable(true);

        GradientDrawable base;
        if (filled) {
            base = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{a1, a2});
        } else {
            base = new GradientDrawable();
            base.setColor(0);
            base.setStroke(dp(1.5f), a1);
        }
        base.setCornerRadius(dp(16));

        GradientDrawable mask = new GradientDrawable();
        mask.setColor(Color.WHITE);
        mask.setCornerRadius(dp(16));
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x44FFFFFF), base, mask));

        // Плавное нажатие через ScaleAnimation
        b.setOnTouchListener((v, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    scale(v, 1f, 0.95f, 100);
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    scale(v, 0.95f, 1f, 140);
                    break;
                default:
                    break;
            }
            return false;
        });
        return b;
    }

    private void scale(View v, float from, float to, long ms) {
        ScaleAnimation a = new ScaleAnimation(from, to, from, to,
                Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        a.setDuration(ms);
        a.setFillAfter(true);
        v.startAnimation(a);
    }

    /** Плавное появление (Fade In). */
    private void fadeIn(View v, long delayMs, long durationMs) {
        AlphaAnimation a = new AlphaAnimation(0f, 1f);
        a.setDuration(durationMs);
        a.setStartOffset(delayMs);
        v.startAnimation(a);
    }
}
