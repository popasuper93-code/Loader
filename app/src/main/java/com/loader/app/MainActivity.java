package com.loader.app;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.entity.pm.InstallResult;

public class MainActivity extends Activity {

    private static final int REQ_GAME = 101;
    private static final int REQ_DISCORD = 102;

    // Палитра
    private static final int C_BG1 = 0xFF080B16;
    private static final int C_BG2 = 0xFF1A0F33;
    private static final int C_CARD = 0xFF121A2E;
    private static final int C_STROKE = 0xFF263150;
    private static final int C_TEXT = 0xFFF2F4FF;
    private static final int C_MUTED = 0xFF8A93B2;
    private static final int C_OK = 0xFF3DDC97;

    private static final int GAME_A = 0xFF7C5CFF;
    private static final int GAME_B = 0xFF00D4FF;
    private static final int DISC_A = 0xFF5865F2;
    private static final int DISC_B = 0xFFB06BFF;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    private TextView gameStatus, discordStatus, busyText;
    private View btnRunGame, btnRunDiscord;
    private View busyBox;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setStatusBarColor(C_BG1);
        getWindow().setNavigationBarColor(C_BG2);

        FrameLayout root = new FrameLayout(this);
        root.setBackground(new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{C_BG1, 0xFF12122B, C_BG2}));

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setFillViewport(true);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(20), dp(28), dp(20), dp(28));

        // ---------- Шапка ----------
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.VERTICAL);

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);

        View dot = new View(this);
        GradientDrawable dotBg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR, new int[]{GAME_A, GAME_B});
        dotBg.setShape(GradientDrawable.OVAL);
        dot.setBackground(dotBg);
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dp(14), dp(14));
        dotLp.rightMargin = dp(12);
        titleRow.addView(dot, dotLp);

        TextView title = text("LOADER", 30, true, C_TEXT);
        title.setLetterSpacing(0.12f);
        titleRow.addView(title);
        head.addView(titleRow);

        TextView sub = text("Виртуальная среда BlackBox · без root", 13, false, C_MUTED);
        LinearLayout.LayoutParams subLp = lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        subLp.topMargin = dp(6);
        head.addView(sub, subLp);
        col.addView(head);

        // Пульсация индикатора
        ObjectAnimator pulse = ObjectAnimator.ofPropertyValuesHolder(dot,
                PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.6f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.6f),
                PropertyValuesHolder.ofFloat(View.ALPHA, 1f, 0.5f));
        pulse.setDuration(900);
        pulse.setRepeatCount(ValueAnimator.INFINITE);
        pulse.setRepeatMode(ValueAnimator.REVERSE);
        pulse.start();

        // ---------- Индикатор занятости ----------
        busyBox = new LinearLayout(this);
        LinearLayout busy = (LinearLayout) busyBox;
        busy.setOrientation(LinearLayout.VERTICAL);
        ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setIndeterminate(true);
        bar.setIndeterminateTintList(ColorStateList.valueOf(GAME_B));
        busy.addView(bar, lp(ViewGroup.LayoutParams.MATCH_PARENT, dp(6)));
        busyText = text("", 12, false, C_MUTED);
        busy.addView(busyText);
        LinearLayout.LayoutParams busyLp = lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        busyLp.topMargin = dp(18);
        busy.setVisibility(View.GONE);
        col.addView(busy, busyLp);

        // ---------- Карточка: игра ----------
        LinearLayout gameCard = newCard("Игра", "APK Oxide из памяти телефона", GAME_A, GAME_B);
        gameStatus = text("", 13, true, C_MUTED);
        addWithTop(gameCard, gameStatus, 14);
        TextView btnAddGame = button("Добавить игру", GAME_A, GAME_B, false);
        btnAddGame.setOnClickListener(v -> pick(REQ_GAME));
        addWithTop(gameCard, btnAddGame, 14);
        TextView runGame = button("Запустить игру", GAME_A, GAME_B, true);
        runGame.setOnClickListener(v -> launch(true));
        btnRunGame = runGame;
        addWithTop(gameCard, runGame, 10);
        col.addView(gameCard);

        // ---------- Карточка: Discord ----------
        LinearLayout discCard = newCard("Discord", "Обычный APK Discord (не XAPK)", DISC_A, DISC_B);
        discordStatus = text("", 13, true, C_MUTED);
        addWithTop(discCard, discordStatus, 14);
        TextView btnAddDiscord = button("Добавить Discord", DISC_A, DISC_B, false);
        btnAddDiscord.setOnClickListener(v -> pick(REQ_DISCORD));
        addWithTop(discCard, btnAddDiscord, 14);
        TextView runDiscord = button("Запустить Discord", DISC_A, DISC_B, true);
        runDiscord.setOnClickListener(v -> launch(false));
        btnRunDiscord = runDiscord;
        addWithTop(discCard, runDiscord, 10);
        col.addView(discCard);

        // ---------- Подвал ----------
        TextView foot = text("Плагин подгружается только в процесс игры и закрывается вместе с ней.",
                12, false, C_MUTED);
        foot.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams footLp = lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        footLp.topMargin = dp(22);
        col.addView(foot, footLp);

        scroll.addView(col, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);

        refreshState();

        // Анимация появления
        View[] items = {head, gameCard, discCard, foot};
        for (int i = 0; i < items.length; i++) {
            View v = items[i];
            v.setAlpha(0f);
            v.setTranslationY(dp(40));
            v.animate().alpha(1f).translationY(0f)
                    .setStartDelay(i * 110L).setDuration(450)
                    .setInterpolator(new DecelerateInterpolator()).start();
        }
    }

    // =====================================================================
    // Выбор файла
    // =====================================================================

    private void pick(int req) {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("*/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "application/vnd.android.package-archive",
                "application/octet-stream"});
        try {
            startActivityForResult(Intent.createChooser(i, "Выберите APK"), req);
        } catch (Throwable t) {
            toast("Файловый менеджер не найден");
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null || data.getData() == null) return;
        if (req == REQ_GAME) install(data.getData(), true);
        else if (req == REQ_DISCORD) install(data.getData(), false);
    }

    // =====================================================================
    // Установка в BlackBox
    // =====================================================================

    private void install(Uri uri, boolean game) {
        setBusy(true, "Копирую APK…");
        io.execute(() -> {
            String err = installBlocking(uri, game);
            ui.post(() -> {
                setBusy(false, "");
                refreshState();
                toast(err == null ? "Готово: добавлено в BlackBox" : err);
            });
        });
    }

    /** Возвращает null при успехе или текст ошибки. */
    private String installBlocking(Uri uri, boolean game) {
        try {
            File dir = new File(getFilesDir(), "apks");
            if (!dir.exists() && !dir.mkdirs()) return "Не удалось создать папку";
            File out = new File(dir, game ? "game.apk" : "discord.apk");

            InputStream in = getContentResolver().openInputStream(uri);
            if (in == null) return "Не удалось открыть файл";
            try (InputStream is = in; OutputStream os = new FileOutputStream(out)) {
                byte[] buf = new byte[256 * 1024];
                int n;
                while ((n = is.read(buf)) > 0) os.write(buf, 0, n);
            }

            PackageInfo pi = getPackageManager().getPackageArchiveInfo(out.getAbsolutePath(), 0);
            if (pi == null || pi.packageName == null) {
                return "Это не APK (XAPK/APKS/split не поддерживаются)";
            }

            ui.post(() -> setBusy(true, "Устанавливаю в BlackBox…"));
            InstallResult r = BlackBoxCore.get().installPackageAsUser(out, App.USER_ID);
            if (r == null || !r.success) {
                return "Ошибка установки: " + (r == null ? "нет ответа" : r.msg);
            }

            App.writePref(game ? App.F_GAME : App.F_DISCORD, pi.packageName);
            return null;
        } catch (Throwable t) {
            return "Ошибка: " + t;
        }
    }

    // =====================================================================
    // Запуск
    // =====================================================================

    private void launch(boolean game) {
        String pkg = App.readPref(game ? App.F_GAME : App.F_DISCORD);
        if (pkg == null) {
            toast(game ? "Сначала добавьте игру" : "Сначала добавьте Discord");
            return;
        }
        if (!game && Build.VERSION.SDK_INT >= 23) {
            requestPermissions(new String[]{
                    Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA}, 1);
        }
        try {
            BlackBoxCore.get().launchApk(pkg, App.USER_ID);
        } catch (Throwable t) {
            toast("Ошибка запуска: " + t.getMessage());
        }
    }

    // =====================================================================
    // Состояние UI
    // =====================================================================

    private void refreshState() {
        bindStatus(gameStatus, btnRunGame, App.readPref(App.F_GAME));
        bindStatus(discordStatus, btnRunDiscord, App.readPref(App.F_DISCORD));
    }

    private void bindStatus(TextView st, View run, String pkg) {
        if (pkg == null) {
            st.setText("● Не добавлено");
            st.setTextColor(C_MUTED);
            run.setAlpha(0.35f);
        } else {
            st.setText("● Готово · " + pkg);
            st.setTextColor(C_OK);
            run.setAlpha(1f);
        }
    }

    private void setBusy(boolean busy, String msg) {
        busyBox.setVisibility(busy ? View.VISIBLE : View.GONE);
        busyText.setText(msg);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }

    // =====================================================================
    // Хелперы UI
    // =====================================================================

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    private TextView text(String s, float sp, boolean bold, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private void addWithTop(LinearLayout parent, View child, int topDp) {
        LinearLayout.LayoutParams p = lp(ViewGroup.LayoutParams.MATCH_PARENT,
                child instanceof TextView && child.isClickable() ? dp(54)
                        : ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(topDp);
        parent.addView(child, p);
    }

    private LinearLayout newCard(String title, String hint, int a1, int a2) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(20), dp(20), dp(20), dp(20));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(C_CARD);
        bg.setCornerRadius(dp(24));
        bg.setStroke(dp(1), C_STROKE);
        c.setBackground(bg);

        LinearLayout.LayoutParams cp = lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        cp.topMargin = dp(20);
        c.setLayoutParams(cp);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        View accent = new View(this);
        GradientDrawable ab = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM, new int[]{a1, a2});
        ab.setCornerRadius(dp(3));
        accent.setBackground(ab);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(dp(4), dp(24));
        alp.rightMargin = dp(12);
        row.addView(accent, alp);
        row.addView(text(title, 20, true, C_TEXT));
        c.addView(row);

        LinearLayout.LayoutParams hp = lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        hp.topMargin = dp(6);
        c.addView(text(hint, 13, false, C_MUTED), hp);
        return c;
    }

    private TextView button(String label, int a1, int a2, boolean filled) {
        TextView b = new TextView(this);
        b.setText(label);
        b.setGravity(Gravity.CENTER);
        b.setTextSize(16);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(filled ? Color.WHITE : a2);
        b.setClickable(true);
        b.setFocusable(true);

        GradientDrawable base;
        if (filled) {
            base = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                    new int[]{a1, a2});
        } else {
            base = new GradientDrawable();
            base.setColor(0x00000000);
            base.setStroke(dp(1.5f), a1);
        }
        base.setCornerRadius(dp(16));

        GradientDrawable mask = new GradientDrawable();
        mask.setColor(Color.WHITE);
        mask.setCornerRadius(dp(16));
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x44FFFFFF), base, mask));

        b.setOnTouchListener((v, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    v.animate().scaleX(0.97f).scaleY(0.97f).setDuration(90).start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.animate().scaleX(1f).scaleY(1f).setDuration(140).start();
                    break;
                default:
                    break;
            }
            return false;
        });
        return b;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        io.shutdown();
    }
}
