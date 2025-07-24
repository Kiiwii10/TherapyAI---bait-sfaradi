package com.example.therapyai.data.local;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.media.RingtoneManager;
import android.net.Uri;
import android.util.Log;

import androidx.appcompat.app.AppCompatDelegate;


import android.app.Activity;
import android.os.Build;
import android.view.View;
import android.view.Window;
import androidx.core.view.WindowInsetsControllerCompat;

import com.example.therapyai.R;

public class LocalStorageManager {
    private static final String PREF_NAME = "LocalStorage";
    private static final String KEY_THEME_MODE = "themeMode";
    private static final String KEY_NOTIF_VIBRATE_ENABLED = "notificationVibrationEnabled";
    private static final String KEY_NOTIF_SOUND_ENABLED = "notificationSoundEnabled";
    private static final String KEY_NOTIF_POPUP_ENABLED = "notificationPopupEnabled"; // Heads-up
    private static LocalStorageManager instance;
    private final SharedPreferences prefs;

    public LocalStorageManager(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized void init(Context context) {
        if (instance == null) {
            instance = new LocalStorageManager(context);
        }
    }

    public static synchronized LocalStorageManager getInstance() {
        if (instance == null) {
            throw new IllegalStateException("LocalStorageManager not initialized!");
        }
        return instance;
    }

    /**
     * Save theme mode (true for dark, false for light)
     * */
    public void setApplyTheme(int mode) {
        prefs.edit().putInt(KEY_THEME_MODE, mode).apply();
        switch (mode) {
            case 2: // Dark Mode
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
            case 1: // Light Mode
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
            case -1: // System Default
            default:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                break;
        }
    }

    /**
     * Retrieve theme mode
     * */
    public int getThemeMode() {
        int mode = prefs.getInt(KEY_THEME_MODE, -400);
        if (mode == -400) {
            // Set default theme mode
            prefs.edit().putInt(KEY_THEME_MODE, -1).apply();
            return -1;
        }
        return mode; // -1 is default value, system default
    }

    public void applyThemeFromPreferences() {
        int themeMode = getThemeMode();

        switch (themeMode) {
            case 2: // Dark Mode
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
            case 1: // Light Mode
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
            case -1: // System Default
            default:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                break;
        }
    }

    public static void setNavigationBarStyle(Activity activity, boolean isLightTheme) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { // API 26+
            Window window = activity.getWindow();

            // Set navigation bar color
            if (isLightTheme) {
                window.setNavigationBarColor(activity.getResources().getColor(R.color.m3_background_light));
            } else {
                window.setNavigationBarColor(activity.getResources().getColor(R.color.m3_background_dark));
            }

            // Use WindowInsetsControllerCompat for consistent behavior
            View decorView = window.getDecorView();
            WindowInsetsControllerCompat insetsController = new WindowInsetsControllerCompat(window, decorView);
            if (isLightTheme) {
                insetsController.setAppearanceLightNavigationBars(true); // Dark icons for light theme
            } else {
                insetsController.setAppearanceLightNavigationBars(false); // Light icons for dark theme
            }
        } else {
            // Fallback for older APIs
            Window window = activity.getWindow();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) { // API 21+
                if (isLightTheme) {
                    window.setNavigationBarColor(activity.getResources().getColor(R.color.m3_background_light));
                } else {
                    window.setNavigationBarColor(activity.getResources().getColor(R.color.m3_background_dark));
                }
            }
        }
    }

    public static boolean isSystemInDarkTheme(Context context) {
        int nightModeFlags = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return nightModeFlags == Configuration.UI_MODE_NIGHT_YES;
    }

    public void setNotificationVibrationEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_NOTIF_VIBRATE_ENABLED, enabled).apply();
    }

    public boolean isNotificationVibrationEnabled() {
        // Default to true
        return prefs.getBoolean(KEY_NOTIF_VIBRATE_ENABLED, true);
    }

    public void setNotificationSoundEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_NOTIF_SOUND_ENABLED, enabled).apply();
    }

    public boolean isNotificationSoundEnabled() {
        // Default to true
        return prefs.getBoolean(KEY_NOTIF_SOUND_ENABLED, true);
    }

    public void setNotificationPopupEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_NOTIF_POPUP_ENABLED, enabled).apply();
    }

    public boolean isNotificationPopupEnabled() {
        // Default to true (allow high priority/heads-up)
        return prefs.getBoolean(KEY_NOTIF_POPUP_ENABLED, true);
    }

    // Helper to get default notification sound URI
    public static Uri getDefaultNotificationSoundUri() {
        return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
    }

    public Boolean getBoolean(String key, boolean defaultValue) {
        return prefs.getBoolean(key, defaultValue);
    }

    public int getInt(String key, int defaultValue) {
        return prefs.getInt(key, defaultValue);
    }

    public void setInt(String key, int value) {
        prefs.edit().putInt(key, value).apply();
    }

    public void setBoolean(String key, boolean value) {
        prefs.edit().putBoolean(key, value).apply();
    }
}
