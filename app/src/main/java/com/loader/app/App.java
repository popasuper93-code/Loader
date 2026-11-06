package com.loader.app;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Bundle;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.app.configuration.AppLifecycleCallback;
import top.niunaijun.blackbox.app.configuration.ClientConfiguration;

public class App extends Application {

    static final String TAG = "Loader";

    /** Имя плагина без "lib" и ".so": app/src/main/jniLibs/arm64-v8a/libplugin.so */
    static final String PLUGIN_LIB = "plugin";

    /** Виртуальный пользователь внутри BlackBox */
    static final int USER_ID = 0;

    /** Строго допустимые пакеты (анти-кряк) */
    static final String GAME_PKG = "com.catsbit.oxidesurvivalisland";
    static final String DISCORD_PKG = "com.discord";

    /** Файлы-хранилища (лежат в filesDir хоста, доступны из всех процессов) */
    static final String F_GAME = "game.pkg";
    static final String F_DISCORD = "discord.pkg";
    static final String F_LOG = "loader.log";

    /** Ключи настроек */
    static final String K_ACCENT = "set.accent";          // purple | blue | red | green
    static final String K_THEME = "set.theme";            // dark | light
    static final String K_PLUGIN = "set.plugin";          // 1 | 0
    static final String K_PLUGIN_LATE = "set.pluginlate"; // 1 | 0

    static Context sContext;

    /** Состояние инициализации BlackBox (для кнопки «Проверить BlackBox») */
    static volatile boolean sBlackBoxReady = false;
    static volatile String sInitError = null;

    private static boolean sPluginLoaded = false;
    private static boolean sWatchdogStarted = false;
    private static volatile String sLastEvent = "событий ещё не было";
    private static Thread.UncaughtExceptionHandler sOurHandler;
    private static String sProcName;

    // =====================================================================
    // Жизненный цикл хоста
    // =====================================================================

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        sContext = base;
        try {
            BlackBoxCore.get().doAttachBaseContext(base, new ClientConfiguration() {
                @Override
                public String getHostPackageName() {
                    return base.getPackageName();
                }
            });
        } catch (Throwable e) {
            sInitError = "doAttachBaseContext: " + e;
            Log.e(TAG, "doAttachBaseContext failed", e);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        log("App.onCreate, процесс = " + processName());

        installCrashHandler("host");

        try {
            BlackBoxCore.get().doCreate();
            if (sInitError == null) sBlackBoxReady = true;
        } catch (Throwable e) {
            sInitError = "doCreate: " + e;
            log("ОШИБКА doCreate: " + e);
        }

        // Колбэк срабатывает только когда внутри BlackBox стартует гостевое приложение
        try {
            BlackBoxCore.get().addAppLifecycleCallback(new AppLifecycleCallback() {
                public void beforeCreateApplication(String packageName, String processName,
                                                    Context context, int userId) {
                    log("▶ beforeCreateApplication: " + packageName
                            + " (процесс " + processName + ", user " + userId + ")");
                    sLastEvent = "beforeCreateApplication";
                    installCrashHandler(packageName);
                    startWatchdog(packageName);
                    if (!isEnabled(K_PLUGIN_LATE, false)) {
                        loadPluginIfGame(packageName, "до создания Application");
                    }
                }

                public void beforeApplicationOnCreate(String packageName, String processName,
                                                      Application application, int userId) {
                    log("▶ beforeApplicationOnCreate: " + packageName
                            + " → " + application.getClass().getName());
                    sLastEvent = "beforeApplicationOnCreate";
                    watchActivities(application);
                }

                public void afterApplicationOnCreate(String packageName, String processName,
                                                     Application application, int userId) {
                    log("✔ afterApplicationOnCreate: " + packageName + " — Application создан");
                    sLastEvent = "afterApplicationOnCreate";
                    if (isEnabled(K_PLUGIN_LATE, false)) {
                        loadPluginIfGame(packageName, "после создания Application");
                    }
                }
            });
            log("Колбэк жизненного цикла гостя зарегистрирован");
        } catch (Throwable e) {
            log("ОШИБКА addAppLifecycleCallback: " + e);
        }
    }

    // =====================================================================
    // Плагин
    // =====================================================================

    /** Грузит libplugin.so один раз и только в процессе Oxide. */
    private static synchronized void loadPluginIfGame(String pkg, String when) {
        if (pkg == null) return;
        if (!GAME_PKG.equals(pkg)) {
            log("Плагин не нужен для " + pkg);
            return;
        }
        if (sPluginLoaded) return;
        if (!isEnabled(K_PLUGIN, true)) {
            log("⚠ Плагин отключён в настройках — игра стартует без него");
            return;
        }

        File so = new File(sContext.getApplicationInfo().nativeLibraryDir,
                "lib" + PLUGIN_LIB + ".so");
        log("Загружаю плагин (" + when + "): " + so.getAbsolutePath()
                + ", найден=" + so.exists() + ", размер=" + so.length());

        long t0 = SystemClock.uptimeMillis();
        try {
            System.loadLibrary(PLUGIN_LIB);
            sPluginLoaded = true;
            log("✔ Плагин загружен за " + (SystemClock.uptimeMillis() - t0) + " мс");
        } catch (Throwable t) {
            log("ОШИБКА загрузки плагина: " + t);
        }
    }

    // =====================================================================
    // Диагностика гостя: активити, watchdog, краши
    // =====================================================================

    private static void watchActivities(Application app) {
        try {
            app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
                @Override public void onActivityCreated(Activity a, Bundle b) {
                    event("Activity created: " + a.getClass().getName());
                }
                @Override public void onActivityStarted(Activity a) {
                    event("Activity started: " + a.getClass().getSimpleName());
                }
                @Override public void onActivityResumed(Activity a) {
                    event("Activity resumed: " + a.getClass().getSimpleName());
                }
                @Override public void onActivityPaused(Activity a) {
                    event("Activity paused: " + a.getClass().getSimpleName());
                }
                @Override public void onActivityStopped(Activity a) {
                    event("Activity stopped: " + a.getClass().getSimpleName());
                }
                @Override public void onActivitySaveInstanceState(Activity a, Bundle b) { }
                @Override public void onActivityDestroyed(Activity a) {
                    event("Activity destroyed: " + a.getClass().getSimpleName());
                }
            });
            log("Слежение за Activity гостя включено");
        } catch (Throwable t) {
            log("ОШИБКА watchActivities: " + t);
        }
    }

    private static void event(String s) {
        sLastEvent = s;
        log("• " + s);
    }

    /**
     * Если игра «висит на заставке», по этим дампам видно, где застрял главный поток.
     * Работает в отдельном потоке — отработает, даже если главный поток заблокирован.
     */
    private static synchronized void startWatchdog(String pkg) {
        if (sWatchdogStarted) return;
        sWatchdogStarted = true;
        Thread t = new Thread(() -> {
            try {
                Thread.sleep(12000);
                dumpMainThread(pkg + ": 12 с после старта");
                Thread.sleep(18000);
                dumpMainThread(pkg + ": 30 с после старта");
            } catch (InterruptedException ignored) {
            }
        }, "loader-watchdog");
        t.setDaemon(true);
        t.start();
    }

    private static void dumpMainThread(String why) {
        try {
            StackTraceElement[] st = Looper.getMainLooper().getThread().getStackTrace();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < Math.min(st.length, 14); i++) {
                sb.append("\n      at ").append(st[i]);
            }
            log("⏱ " + why + "\n    последнее событие: " + sLastEvent
                    + "\n    главный поток:" + sb);
        } catch (Throwable t) {
            log("⏱ " + why + ": не удалось получить стек: " + t);
        }
    }

    /** Ловит Java-краши в любом процессе и пишет причину в лог. */
    private static synchronized void installCrashHandler(String who) {
        Thread.UncaughtExceptionHandler cur = Thread.getDefaultUncaughtExceptionHandler();
        if (cur != null && cur == sOurHandler) return; // уже наш

        final Thread.UncaughtExceptionHandler prev = cur;
        sOurHandler = (thread, e) -> {
            try {
                String st = stackOf(e);
                log("CRASH [" + who + "] поток=" + thread.getName() + "\n" + rootCauseLine(st)
                        + "\n" + limit(st, 3500));
                log("ПРИЧИНА: " + explainCrash(st));
            } catch (Throwable ignored) {
            }
            if (prev != null) prev.uncaughtException(thread, e);
        };
        Thread.setDefaultUncaughtExceptionHandler(sOurHandler);
    }

    static String stackOf(Throwable e) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    static String rootCauseLine(String stack) {
        String root = null;
        for (String line : stack.split("\n")) {
            if (line.startsWith("Caused by:")) root = line;
        }
        if (root != null) return root;
        int nl = stack.indexOf('\n');
        return nl > 0 ? stack.substring(0, nl) : stack;
    }

    /** Грубая расшифровка причины краша по тексту стека. */
    static String explainCrash(String st) {
        String s = st.toLowerCase(Locale.ROOT);
        if (s.contains("com.google.android.gms") || s.contains("googleplayservices")
                || s.contains("googleapiavailability") || s.contains("firebaseapp")
                || s.contains("play-services") || s.contains("com.google.firebase")) {
            return "нет Google Play Services (GMS) внутри BlackBox — приложение требует их при старте";
        }
        if (s.contains("unsatisfiedlinkerror")) {
            return "не загрузилась нативная библиотека (.so): не та архитектура или нет файла (split-APK?)";
        }
        if (s.contains("classnotfoundexception") || s.contains("noclassdeffounderror")) {
            return "не найден класс: dex не загружен — часто бывает у split-APK";
        }
        if (s.contains("resources$notfoundexception") || s.contains("resourcesnotfound")) {
            return "не найдены ресурсы — вероятно, не хватает частей split-APK";
        }
        if (s.contains("securityexception")) {
            return "SecurityException: проверка прав или подписи внутри песочницы";
        }
        if (s.contains("outofmemoryerror")) {
            return "не хватает памяти";
        }
        if (s.contains("contentprovider") || s.contains("providerinfo")) {
            return "не удалось поднять ContentProvider приложения";
        }
        if (s.contains("nullpointerexception")) {
            return "NullPointerException — смотрите стек выше (первая строка после Caused by)";
        }
        return "причина не опознана — смотрите стек выше";
    }

    // =====================================================================
    // Хранилище настроек и лог (файлы в filesDir — видны всем процессам хоста)
    // =====================================================================

    static String readPref(String name) {
        try {
            if (sContext == null) return null;
            File f = new File(sContext.getFilesDir(), name);
            if (!f.exists()) return null;
            try (BufferedReader r = new BufferedReader(new FileReader(f))) {
                String s = r.readLine();
                if (s == null) return null;
                s = s.trim();
                return s.isEmpty() ? null : s;
            }
        } catch (Throwable t) {
            return null;
        }
    }

    static void writePref(String name, String value) {
        try (FileWriter w = new FileWriter(new File(sContext.getFilesDir(), name))) {
            w.write(value);
        } catch (Throwable t) {
            Log.e(TAG, "writePref failed", t);
        }
    }

    static boolean isEnabled(String key, boolean def) {
        String v = readPref(key);
        return v == null ? def : "1".equals(v);
    }

    static String processName() {
        if (sProcName != null) return sProcName;
        try (FileInputStream in = new FileInputStream("/proc/self/cmdline")) {
            byte[] b = new byte[256];
            int n = in.read(b);
            if (n <= 0) return "?";
            int end = 0;
            while (end < n && b[end] != 0) end++;
            sProcName = new String(b, 0, end, StandardCharsets.UTF_8);
        } catch (Throwable t) {
            sProcName = "?";
        }
        return sProcName;
    }

    private static String shortProc() {
        String p = processName();
        String host = sContext != null ? sContext.getPackageName() : "";
        if (p.equals(host)) return "main";
        if (p.startsWith(host + ":")) return p.substring(host.length());
        return p;
    }

    /** Пишет в logcat и в файл loader.log (его показывает вкладка «Логи»). */
    static synchronized void log(String msg) {
        Log.i(TAG, msg);
        if (sContext == null) return;
        try {
            File f = new File(sContext.getFilesDir(), F_LOG);
            if (f.length() > 256 * 1024) trimLog(f);
            String ts = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(new Date());
            try (FileWriter w = new FileWriter(f, true)) {
                w.write(ts + " [" + shortProc() + "] " + msg + "\n");
            }
        } catch (Throwable ignored) {
        }
    }

    private static void trimLog(File f) {
        try {
            byte[] keep = new byte[64 * 1024];
            try (RandomAccessFile raf = new RandomAccessFile(f, "r")) {
                long len = raf.length();
                if (len <= keep.length) return;
                raf.seek(len - keep.length);
                raf.readFully(keep);
            }
            try (java.io.FileOutputStream os = new java.io.FileOutputStream(f, false)) {
                os.write(keep);
            }
        } catch (Throwable ignored) {
        }
    }

    static String readLogTail(int maxBytes) {
        try {
            if (sContext == null) return "";
            File f = new File(sContext.getFilesDir(), F_LOG);
            if (!f.exists() || f.length() == 0) return "";
            try (RandomAccessFile raf = new RandomAccessFile(f, "r")) {
                long len = raf.length();
                int n = (int) Math.min(len, maxBytes);
                byte[] b = new byte[n];
                raf.seek(len - n);
                raf.readFully(b);
                String s = new String(b, StandardCharsets.UTF_8);
                if (len > n) {
                    int nl = s.indexOf('\n');
                    if (nl >= 0) s = s.substring(nl + 1);
                }
                return s;
            }
        } catch (Throwable t) {
            return "";
        }
    }

    static void clearLog() {
        try {
            File f = new File(sContext.getFilesDir(), F_LOG);
            if (f.exists()) f.delete();
        } catch (Throwable ignored) {
        }
    }

    private static String limit(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "\n…(обрезано)";
    }
                }
        
