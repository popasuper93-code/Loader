package com.loader.app;

import android.app.Application;
import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.app.configuration.AppLifecycleCallback;
import top.niunaijun.blackbox.app.configuration.ClientConfiguration;

public class App extends Application {

    static final String TAG = "Loader";

    /** Имя плагина без "lib" и ".so": файл app/src/main/jniLibs/arm64-v8a/libplugin.so */
    static final String PLUGIN_LIB = "plugin";

    /** Виртуальный пользователь внутри BlackBox */
    static final int USER_ID = 0;

    /** Файлы с именами пакетов (лежат в filesDir хоста) */
    static final String F_GAME = "game.pkg";
    static final String F_DISCORD = "discord.pkg";

    private static final String DISCORD_PKG = "com.discord";

    static Context sContext;
    private static boolean sPluginLoaded = false;

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
            Log.e(TAG, "doAttachBaseContext failed", e);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            BlackBoxCore.get().doCreate();
        } catch (Throwable e) {
            Log.e(TAG, "doCreate failed", e);
        }

        // Выполняется в каждом процессе хоста, но колбэк срабатывает
        // только когда внутри BlackBox стартует гостевое приложение.
        try {
            BlackBoxCore.get().addAppLifecycleCallback(new AppLifecycleCallback() {
                public void beforeCreateApplication(String packageName, String processName,
                                                    Context context, int userId) {
                    loadPluginIfGame(packageName);
                }

                public void beforeApplicationOnCreate(String packageName, String processName,
                                                      Application application, int userId) {
                    loadPluginIfGame(packageName);
                }

                public void afterApplicationOnCreate(String packageName, String processName,
                                                     Application application, int userId) {
                }
            });
        } catch (Throwable e) {
            Log.e(TAG, "addAppLifecycleCallback failed", e);
        }
    }

    /** Грузит libplugin.so один раз, и только в процессе игры (не Discord). */
    private static synchronized void loadPluginIfGame(String pkg) {
        if (sPluginLoaded || pkg == null) return;

        String game = readPref(F_GAME);
        boolean isGame = (game != null) ? game.equals(pkg) : !DISCORD_PKG.equals(pkg);
        if (!isGame) return;

        try {
            System.loadLibrary(PLUGIN_LIB);
            sPluginLoaded = true;
            Log.i(TAG, "Plugin loaded into " + pkg);
        } catch (Throwable t) {
            Log.e(TAG, "Plugin load failed for " + pkg, t);
        }
    }

    static String readPref(String name) {
        try {
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
}
