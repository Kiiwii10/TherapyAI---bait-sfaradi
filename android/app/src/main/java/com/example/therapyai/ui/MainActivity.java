package com.example.therapyai.ui;

import androidx.appcompat.widget.Toolbar;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;


import com.example.therapyai.BaseSignedActivity;
import com.example.therapyai.R;

import androidx.viewpager2.widget.ViewPager2;
import com.example.therapyai.ui.adapters.MainViewPagerAdapter;


import com.example.therapyai.data.local.SessionManager;
import com.example.therapyai.data.repository.ProcessedDataRepository;
import com.example.therapyai.ui.browse.InboxActivity;

import com.example.therapyai.ui.browse.ProcessedDataDetailActivity;
import com.example.therapyai.ui.welcome.WelcomeActivity;
import com.google.android.material.badge.BadgeDrawable;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends BaseSignedActivity {
    private static final String TAG = "MainActivity";

    private SessionManager.UserType currentType;
    private BottomNavigationView bottomNavigation;
    private BadgeDrawable inboxBadge;
    private ViewPager2 viewPager;
    private MainViewPagerAdapter pagerAdapter;

    @SuppressLint("NonConstantResourceId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // DIAGNOSTIC: Log MainActivity creation context
        Log.d(TAG, "DIAGNOSTIC: MainActivity.onCreate() started");
        Log.d(TAG, "DIAGNOSTIC: Intent extras: " + (getIntent().getExtras() != null ? getIntent().getExtras().keySet() : "none"));
        Log.d(TAG, "DIAGNOSTIC: Task ID: " + getTaskId());
        Log.d(TAG, "DIAGNOSTIC: Process PID: " + android.os.Process.myPid());
        
        setContentView(R.layout.activity_main);

        Log.d(TAG, "DIAGNOSTIC: About to call SessionManager.getInstance().getUserType()");
        currentType = SessionManager.getInstance().getUserType();
        
        if (currentType == null) {
            Log.w(TAG, "DIAGNOSTIC: currentType is null, calling logoutAndGoToWelcome()");
            logoutAndGoToWelcome();
            return;
        }
        
        Log.d(TAG, "DIAGNOSTIC: MainActivity.onCreate() completed successfully with userType: " + currentType);

        Log.d("UserType", "Use Type: " + currentType);

        viewPager = findViewById(R.id.viewPager);
        bottomNavigation = findViewById(R.id.bottomNavigation);
        Toolbar toolbar = findViewById(R.id.mainToolbar);
        setSupportActionBar(toolbar);

        pagerAdapter = new MainViewPagerAdapter(this, currentType);
        viewPager.setAdapter(pagerAdapter);
        viewPager.setOffscreenPageLimit(2);

        setupInboxBadge();
        setupNavigationSync();

//        if (currentType == SessionManager.UserType.THERAPIST) {
//            inboxBadge = bottomNavigation.getOrCreateBadge(R.id.nav_browse);
//            inboxBadge.setBackgroundColor(getResources().getColor(R.color.m3_error_light));
//            inboxBadge.setVisible(false);
//
//            ProcessedDataRepository.getInstance().getPendingCount().observe(this, count -> {
//                if (inboxBadge != null) {
//                    if (count != null && count > 0) {
//                        inboxBadge.setVisible(true);
//                        inboxBadge.setNumber(count);
//                    } else {
//                        inboxBadge.setVisible(false);
//                    }
//                }
//            });
//        }
//        bottomNavigation.setOnItemSelectedListener(item -> { // cant switch case because R.id.nav_session is not a constant
//            if (item.getItemId() == R.id.nav_session){
//                getSupportActionBar().setTitle("Session");
//                loadSessionFragment();
//                return true;
//            } else if (item.getItemId() == R.id.nav_browse) {
//                getSupportActionBar().setTitle("Browse");
//                loadBrowseFragment();
//                return true;
//            } else if (item.getItemId() == R.id.nav_data) {
//                getSupportActionBar().setTitle("Data");
//                loadDataFragment();
//                return true;
//            }
//            return false;
//        });
//
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
//            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
//                DialogUtils.showEnableNotificationsDialog(this);
//            }
//        } else {
//            NotificationHelper notificationHelper = new NotificationHelper(this);
//            if (!notificationHelper.areNotificationsEnabledForChannel()) {
//                DialogUtils.showEnableNotificationsDialog(this);
//            }
//        }

        if (savedInstanceState == null) {
//            bottomNavigation.setSelectedItemId(R.id.nav_session);
//            getSupportActionBar().setTitle("Session");
//            loadSessionFragment();

            int initialPosition = MainViewPagerAdapter.getPositionForMenuItem(R.id.nav_session);
            viewPager.setCurrentItem(initialPosition, false); // Go to initial page without animation
            updateTitle(MainViewPagerAdapter.getMenuItemIdForPosition(initialPosition));
        }

        handleNotificationIntent();
    }


    private void setupInboxBadge() {
        if (currentType == SessionManager.UserType.THERAPIST) {
            inboxBadge = bottomNavigation.getOrCreateBadge(R.id.nav_browse);
            inboxBadge.setBackgroundColor(getResources().getColor(R.color.m3_error_light));
            inboxBadge.setVisible(false);

            ProcessedDataRepository.getInstance().getPendingCount().observe(this, count -> {
                if (inboxBadge != null) {
                    if (count != null && count > 0) {
                        inboxBadge.setVisible(true);
                        inboxBadge.setNumber(count);
                    } else {
                        inboxBadge.setVisible(false);
                    }
                }
            });
        }
    }

    private void setupNavigationSync() {
        // Listener for BottomNavigationView item selection
        bottomNavigation.setOnItemSelectedListener(item -> {
            int position = MainViewPagerAdapter.getPositionForMenuItem(item.getItemId());
            if (position != -1 && viewPager.getCurrentItem() != position) {
                viewPager.setCurrentItem(position); // Change ViewPager page
                // Title is updated by the ViewPager callback below
            }
            return true; // Return true to display the item as selected
        });

        // Listener for ViewPager2 page changes
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                int menuItemId = MainViewPagerAdapter.getMenuItemIdForPosition(position);
                if (bottomNavigation.getSelectedItemId() != menuItemId) {
                    bottomNavigation.setSelectedItemId(menuItemId); // Update BottomNav selection
                }
                updateTitle(menuItemId); // Update Toolbar title
            }
        });
    }


    // Helper method to update the Toolbar title (remains the same)
    private void updateTitle(int itemId) {
        if (itemId == R.id.nav_session) {
            getSupportActionBar().setTitle("Session");
        } else if (itemId == R.id.nav_data) {
            getSupportActionBar().setTitle("Data");
        } else if (itemId == R.id.nav_browse) {
            getSupportActionBar().setTitle("Browse");
        }
    }

//    private void loadSessionFragment() {
//        currentType = SessionManager.getInstance().getUserType();
//        Log.d("UserType", "loadSessionFragment - Use Type: " + currentType);
//        switch (currentType) {
//            case PATIENT:
//                replaceFragment(new PatientSessionFragment(), "SessionFragment");
//                break;
//            case THERAPIST:
//                replaceFragment(new TherapistSessionFragment(), "SessionFragment");
//                break;
//            default:
//                Intent intent = new Intent(this, WelcomeActivity.class);
//                startActivity(intent);
//                finish();
//                break;
//        }
//    }

//    private void loadDataFragment() {
//        currentType = SessionManager.getInstance().getUserType();
//        switch (currentType) {
//            case PATIENT:
//                replaceFragment(new PatientSearchFragment(), "SessionFragment");
//                break;
//            case THERAPIST:
//                replaceFragment(new TherapistSearchFragment(), "SessionFragment");
//                break;
//            default:
//                Intent intent = new Intent(this, WelcomeActivity.class);
//                startActivity(intent);
//                finish();
//                break;
//        }
//    }

//    private void loadBrowseFragment() {
//        Fragment fragment = new BrowseFragment();
//        replaceFragment(fragment, "BrowseFragment");
//    }
//    private void replaceFragment(Fragment fragment, String tag) {
//        getSupportFragmentManager()
//            .beginTransaction()
//            .replace(R.id.fragmentContainer, fragment, tag)
//            .commit();
//    }

    private void logoutAndGoToWelcome() {
        Log.d("UserType", "Use Type is null, logging out");
        SessionManager.getInstance().forceLogout();
        Intent intent = new Intent(this, WelcomeActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }


    @Override
    public void onReAuthRequested() {
    }

    @Override
    public void onPointerCaptureChanged(boolean hasCapture) {
        super.onPointerCaptureChanged(hasCapture);
    }

    private void handleNotificationIntent() {
        String dataId = getIntent().getStringExtra("data_id");
        String source = getIntent().getStringExtra("notification_source");

        // Assuming "PROCESSED_DATA" and "SESSION_READY" with a dataId should go to detail
        if (dataId != null && ("PROCESSED_DATA".equals(source) || "SESSION_READY".equals(source))) {
            Log.d(TAG, "Handling notification intent for " + source + " with dataId: " + dataId + ", navigating to detail.");
            Intent intent = new Intent(this, ProcessedDataDetailActivity.class);
            intent.putExtra(ProcessedDataDetailActivity.EXTRA_DATA_ID, dataId);
            // intent.putExtra("notification_source", source); // Optional if detail activity needs it
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP); // Good for navigation
            startActivity(intent);

            // Clear extras to prevent re-processing on e.g. orientation change
            getIntent().removeExtra("data_id");
            getIntent().removeExtra("notification_source");
        } else if (source != null && "SESSION_READY".equals(source) && dataId == null) {
            // SESSION_READY without a specific dataId, go to Inbox
            Log.d(TAG, "Handling notification intent for generic " + source + ", navigating to inbox.");
            Intent intent = new Intent(this, InboxActivity.class);
            intent.putExtra("notification_source", source);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            getIntent().removeExtra("notification_source"); // Clear this too
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (SessionManager.getInstance().getUserType() == SessionManager.UserType.THERAPIST) {
            ProcessedDataRepository.getInstance().refreshPendingData();
        }
    }


}
