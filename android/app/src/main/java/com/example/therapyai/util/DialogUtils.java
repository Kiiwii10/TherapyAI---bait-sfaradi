package com.example.therapyai.util;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import androidx.appcompat.app.AlertDialog;

import com.example.therapyai.R;

public class DialogUtils {

    /**
     * Shows a dialog explaining why notifications are needed and provides a button
     * to open the app's notification settings.
     * @param activity The Activity context needed to show the dialog and start settings.
     */
    public static void showEnableNotificationsDialog(final Activity activity) {
        if (activity == null || activity.isFinishing()) {
            return;
        }

        new AlertDialog.Builder(activity)
                .setTitle(R.string.dialog_enable_notifications_title)
                .setMessage(R.string.dialog_enable_notifications_message)
                .setPositiveButton(R.string.dialog_enable_notifications_button_settings, (dialog, which) -> {
                    openNotificationSettings(activity);
                })
                .setNegativeButton(R.string.dialog_enable_notifications_button_cancel, (dialog, which) -> {
                    dialog.dismiss();
                })
                .show();
    }

    /**
     * Opens the system settings screen for this app's notifications.
     * @param context Context to start the intent.
     */
    public static void openNotificationSettings(Context context) {
        Intent intent = new Intent();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // For Android O and above, opens the specific app notification settings
            intent.setAction(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
            intent.putExtra(Settings.EXTRA_APP_PACKAGE, context.getPackageName());
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP){
            // For Lollipop to Nougat, opens general app settings
            intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.addCategory(Intent.CATEGORY_DEFAULT);
            intent.setData(Uri.parse("package:" + context.getPackageName()));
        } else {
            // Before Lollipop, this was less direct - maybe just open general settings
            intent.setAction(Settings.ACTION_SETTINGS);
        }
        try {
            context.startActivity(intent);
        } catch (Exception e) {
            Log.e("DialogUtils", "Could not open notification settings", e);
        }
    }
}