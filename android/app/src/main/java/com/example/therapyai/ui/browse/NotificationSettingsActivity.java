package com.example.therapyai.ui.browse;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.NotificationManagerCompat;

import android.content.res.Resources;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.therapyai.R;
import com.example.therapyai.data.local.LocalStorageManager;
import com.example.therapyai.util.DialogUtils;
import com.example.therapyai.util.InAppNotificationManager;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;

public class NotificationSettingsActivity extends AppCompatActivity {

    // System notification status views
    private ImageView iconSystemStatus;
    private TextView textSystemStatus;
    private TextView textSystemStatusDescription;
    private Button buttonOpenSystemSettings;

    // System notification settings
    private LinearLayout layoutSystemNotificationSettings;
    private SwitchMaterial switchVibration;
    private SwitchMaterial switchSound;
    private SwitchMaterial switchPopup;

    // In-app notification settings
    private SwitchMaterial switchInAppNotifications;
    private SwitchMaterial switchInAppSound;
    private TextInputEditText editAutoDismissTime;

    private LocalStorageManager localStorageManager;
    private InAppNotificationManager inAppNotificationManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notification_settings);

        localStorageManager = LocalStorageManager.getInstance();
        inAppNotificationManager = InAppNotificationManager.getInstance();

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Notification Settings");
        }

        initializeViews();
        updateSystemNotificationStatus();
        loadSettings();
        setupListeners();
    }

    private void initializeViews() {
        // System notification status views
        iconSystemStatus = findViewById(R.id.icon_system_status);
        textSystemStatus = findViewById(R.id.text_system_status);
        textSystemStatusDescription = findViewById(R.id.text_system_status_description);
        buttonOpenSystemSettings = findViewById(R.id.button_open_system_settings);

        // System notification settings
        layoutSystemNotificationSettings = findViewById(R.id.layout_system_notification_settings);
        switchVibration = findViewById(R.id.switch_vibration);
        switchSound = findViewById(R.id.switch_sound);
        switchPopup = findViewById(R.id.switch_popup);

        // In-app notification settings
        switchInAppNotifications = findViewById(R.id.switch_in_app_notifications);
        switchInAppSound = findViewById(R.id.switch_in_app_sound);
        editAutoDismissTime = findViewById(R.id.edit_auto_dismiss_time);
    }

    private void updateSystemNotificationStatus() {
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        boolean areNotificationsEnabled = notificationManager.areNotificationsEnabled();
        TypedValue typedValue = new TypedValue();
        TypedValue errorTypedValue = new TypedValue();
        Resources.Theme theme = this.getTheme();

        theme.resolveAttribute(R.attr.colorPrimary, typedValue, true);
        theme.resolveAttribute(R.attr.colorError, errorTypedValue, true);

        if (areNotificationsEnabled) {
            // System notifications are enabled
            iconSystemStatus.setImageResource(R.drawable.baseline_notifications_24);
            iconSystemStatus.setColorFilter(typedValue.data);
            textSystemStatus.setText("Enabled");
            textSystemStatus.setTextColor(typedValue.data);
            textSystemStatusDescription.setText(R.string.system_notifications_enabled);

            // Show system notification settings
            layoutSystemNotificationSettings.setVisibility(LinearLayout.VISIBLE);
        } else {
            // System notifications are disabled
            iconSystemStatus.setImageResource(R.drawable.baseline_notifications_off_24);
            iconSystemStatus.setColorFilter(errorTypedValue.data);
            textSystemStatus.setText("Disabled");
            textSystemStatus.setTextColor(errorTypedValue.data);
            textSystemStatusDescription.setText(R.string.system_notifications_disabled_desc);

            // Hide system notification settings since they won't work
            layoutSystemNotificationSettings.setVisibility(LinearLayout.GONE);
        }
    }

    private void loadSettings() {
        // System notification settings
        switchVibration.setChecked(localStorageManager.isNotificationVibrationEnabled());
        switchSound.setChecked(localStorageManager.isNotificationSoundEnabled());
        switchPopup.setChecked(localStorageManager.isNotificationPopupEnabled());

        // In-app notification settings
        switchInAppNotifications.setChecked(localStorageManager.getBoolean("in_app_notifications_enabled", true));
        switchInAppSound.setChecked(localStorageManager.getBoolean("in_app_notification_sound_enabled", true));
        
        // Auto-dismiss time (default 5 seconds)
        int autoDismissTime = localStorageManager.getInt("in_app_notification_auto_dismiss_time", 5);
        editAutoDismissTime.setText(String.valueOf(autoDismissTime));
    }

    private void setupListeners() {
        // System notification settings listeners
        switchVibration.setOnCheckedChangeListener((buttonView, isChecked) -> {
            localStorageManager.setNotificationVibrationEnabled(isChecked);
        });

        switchSound.setOnCheckedChangeListener((buttonView, isChecked) -> {
            localStorageManager.setNotificationSoundEnabled(isChecked);
        });

        switchPopup.setOnCheckedChangeListener((buttonView, isChecked) -> {
            localStorageManager.setNotificationPopupEnabled(isChecked);
        });

        // In-app notification settings listeners
        switchInAppNotifications.setOnCheckedChangeListener((buttonView, isChecked) -> {
            localStorageManager.setBoolean("in_app_notifications_enabled", isChecked);
            inAppNotificationManager.setInAppNotificationsEnabled(isChecked);
        });

        switchInAppSound.setOnCheckedChangeListener((buttonView, isChecked) -> {
            localStorageManager.setBoolean("in_app_notification_sound_enabled", isChecked);
            inAppNotificationManager.setInAppSoundEnabled(isChecked);
        });

        // Auto-dismiss time change listener
        editAutoDismissTime.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                try {
                    String timeText = editAutoDismissTime.getText().toString();
                    if (!timeText.isEmpty()) {
                        int time = Integer.parseInt(timeText);
                        // Ensure reasonable bounds (1-30 seconds)
                        time = Math.max(1, Math.min(30, time));
                        editAutoDismissTime.setText(String.valueOf(time));
                        localStorageManager.setInt("in_app_notification_auto_dismiss_time", time);
                        inAppNotificationManager.setAutoDismissTime(time * 1000); // Convert to milliseconds
                    }
                } catch (NumberFormatException e) {
                    // Reset to default if invalid input
                    editAutoDismissTime.setText("5");
                    localStorageManager.setInt("in_app_notification_auto_dismiss_time", 5);
                    inAppNotificationManager.setAutoDismissTime(5000);
                }
            }
        });

        // Open system settings button
        buttonOpenSystemSettings.setOnClickListener(v -> {
            DialogUtils.openNotificationSettings(this);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Update system notification status when returning from settings
        updateSystemNotificationStatus();
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}