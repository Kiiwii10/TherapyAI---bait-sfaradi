package com.example.therapyai.ui.browse;

import static android.app.UiModeManager.MODE_NIGHT_NO;
import static android.app.UiModeManager.MODE_NIGHT_YES;

import static androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.example.therapyai.R;
import com.example.therapyai.data.local.LocalStorageManager;
import com.example.therapyai.data.local.SessionManager;
import com.example.therapyai.data.repository.ProcessedDataRepository;

import com.example.therapyai.ui.welcome.WelcomeActivity;

public class BrowseFragment extends Fragment {
    private LocalStorageManager localStorageManager;
    private SessionManager sessionManager;

    private LinearLayout inboxOption, accountOption, themeOption, aboutOption, notificationOption;
    private LinearLayout logOutButton, supportOption, swipeActionOption;
    private TextView themeSubtitleView, swipeActionSubtitleView;
    private TextView inboxBadgeView;
    private SessionManager.UserType currentUserType;

    private static final String PREF_SWIPE_RIGHT_ACTION = "pref_swipe_right_action";
    private static final String SWIPE_ACTION_DELETE = "delete";
    private static final String SWIPE_ACTION_EDIT = "edit";

    public BrowseFragment() {
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sessionManager = SessionManager.getInstance();
        currentUserType = sessionManager.getUserType();
        localStorageManager = LocalStorageManager.getInstance();
    }


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_browse, container, false);

        initializeViews(view);
        setupVisibilities();
        setupClickListeners();
        setupSwipeActionListener();
        updateSwipeActionSubtitle();
        localStorageManager = LocalStorageManager.getInstance();
        setThemeText(localStorageManager.getThemeMode());

        return view;
    }

    private void initializeViews(View view) {
        accountOption = view.findViewById(R.id.account_option);
        inboxOption = view.findViewById(R.id.inboxOption);
        inboxBadgeView = view.findViewById(R.id.inboxBadgeView);

        themeOption = view.findViewById(R.id.theme_option);
        themeSubtitleView = view.findViewById(R.id.themeSubtitleView);
        notificationOption = view.findViewById(R.id.notification_option);

        aboutOption = view.findViewById(R.id.aboutOption);
        supportOption = view.findViewById(R.id.supportOption);
        logOutButton = view.findViewById(R.id.logOutButton);

        swipeActionOption = view.findViewById(R.id.swipeActionOption);
        swipeActionSubtitleView = view.findViewById(R.id.swipeActionSubtitleView);
    }

    private void setupVisibilities() {
        if (currentUserType == SessionManager.UserType.THERAPIST) {
            inboxOption.setVisibility(View.VISIBLE);
            swipeActionOption.setVisibility(View.VISIBLE);
        } else {
            inboxOption.setVisibility(View.GONE);
            inboxBadgeView.setVisibility(View.GONE);
            swipeActionOption.setVisibility(View.GONE);
        }
    }

    private void setupClickListeners() {
        // Only set inbox listener if the user is a therapist
        if (currentUserType == SessionManager.UserType.THERAPIST) {
            inboxOption.setOnClickListener(v -> navigateToActivity(InboxActivity.class));
        }
        // Other listeners remain the same
        accountOption.setOnClickListener(v -> navigateToActivity(AccountActivity.class));
        notificationOption.setOnClickListener(v -> navigateToActivity(NotificationSettingsActivity.class));
        aboutOption.setOnClickListener(v -> navigateToActivity(AboutActivity.class));
        supportOption.setOnClickListener(v -> navigateToActivity(SupportActivity.class));

        themeOption.setOnClickListener(this::showThemeMenu);
        logOutButton.setOnClickListener(v -> {
            Toast.makeText(getContext(), "Logging out...", Toast.LENGTH_SHORT).show();
            sessionManager.logout(); // Use the instance variable

            Intent welcomeIntent = new Intent(requireContext(), WelcomeActivity.class);
            // Clear task and start new prevents user from going back to MainActivity after logout
            welcomeIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(welcomeIntent);
        });
    }

    private void setupSwipeActionListener() {
        if (currentUserType == SessionManager.UserType.THERAPIST) {
            swipeActionOption.setOnClickListener(v -> showSwipeActionDialog());
        }
    }

    // NEW Method
    private void updateSwipeActionSubtitle() {
        if (currentUserType == SessionManager.UserType.THERAPIST) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
            String currentAction = prefs.getString(PREF_SWIPE_RIGHT_ACTION, SWIPE_ACTION_DELETE);
            swipeActionSubtitleView.setText(SWIPE_ACTION_EDIT.equals(currentAction) ? "Edit" : "Delete");
        }
    }

    // NEW Method
    private void showSwipeActionDialog() {
        final String[] actions = {"Delete", "Edit"};
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
        String currentActionPref = prefs.getString(PREF_SWIPE_RIGHT_ACTION, SWIPE_ACTION_DELETE);
        int checkedItem = SWIPE_ACTION_EDIT.equals(currentActionPref) ? 1 : 0; // 0 for Delete, 1 for Edit

        new AlertDialog.Builder(requireContext())
                .setTitle("Set Swipe Right Action")
                .setSingleChoiceItems(actions, checkedItem, (dialog, which) -> {
                    SharedPreferences.Editor editor = prefs.edit();
                    if (which == 1) {
                        editor.putString(PREF_SWIPE_RIGHT_ACTION, SWIPE_ACTION_EDIT);
                    } else {
                        editor.putString(PREF_SWIPE_RIGHT_ACTION, SWIPE_ACTION_DELETE);
                    }
                    editor.apply();
                    updateSwipeActionSubtitle();
                    dialog.dismiss();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }


    private void showThemeMenu(View view) {
        ThemeSelectionDialog dialog = new ThemeSelectionDialog(requireContext(),
                new ThemeSelectionDialog.ThemeSelectedListener() {
                    @Override
                    public void onThemeSelected(String theme) {
                        applyTheme(theme);
                    }
                });
        dialog.show();
    }




    private void applyTheme(String themeMode) {
        switch (themeMode) {
            case "Light":
                localStorageManager.setApplyTheme(MODE_NIGHT_NO);
                setThemeText(1);
                break;
            case "Dark":
                localStorageManager.setApplyTheme(MODE_NIGHT_YES);
                setThemeText(2);
                break;
            case "System":
            default:
                localStorageManager.setApplyTheme(MODE_NIGHT_FOLLOW_SYSTEM);
                setThemeText(3);
                break;
        }
    }

    private void setThemeText(int themeMode) {
        switch (themeMode) {
            case 1:
                themeSubtitleView.setText("Light");
                break;
            case 2:
                themeSubtitleView.setText("Dark");
                break;
            case 3:
            default:
                themeSubtitleView.setText("System Theme");
                break;
        }
    }

    private void navigateToActivity(Class<?> activityClass) {
        Intent intent = new Intent(getActivity(), activityClass);
        startActivity(intent);
    }

    @Override
    public void onResume() {
        super.onResume();
        AppCompatActivity activity = (AppCompatActivity) getActivity();
        if (activity != null && activity.getSupportActionBar() != null) {
            activity.getSupportActionBar().setTitle("Browse");
        }
        updateSwipeActionSubtitle();
        if (currentUserType == SessionManager.UserType.THERAPIST) {
            updateInboxBadgeCount();
        } else {
            inboxBadgeView.setVisibility(View.GONE);
        }
    }

    private void updateInboxBadgeCount() {
        ProcessedDataRepository.getInstance().getPendingCount().observe(getViewLifecycleOwner(), count -> {
            if (count != null && count > 0) {
                inboxBadgeView.setVisibility(View.VISIBLE);
                inboxBadgeView.setText(String.valueOf(count));
            } else {
                inboxBadgeView.setVisibility(View.GONE);
            }
        });
    }
}