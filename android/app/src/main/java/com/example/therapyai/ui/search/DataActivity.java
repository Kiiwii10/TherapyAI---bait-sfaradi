package com.example.therapyai.ui.search;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.example.therapyai.R;

import com.example.therapyai.ui.viewmodels.DataViewModel;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

public class DataActivity extends AppCompatActivity {

    public static final String EXTRA_SESSION_ID = "com.example.therapyai.ui.session.SESSION_ID";
    // Optional extras if needed early
    public static final String EXTRA_PATIENT_ID = "com.example.therapyai.ui.session.PATIENT_ID";
    public static final String EXTRA_THERAPIST_ID = "com.example.therapyai.ui.session.THERAPIST_ID";

    private static final String TAG = "SessionActivity";

    private DataViewModel dataViewModel;
    private ViewPager2 viewPager;
    private TabLayout tabLayout;
    private MaterialToolbar toolbar;
    private ProgressBar progressBar;

    private String sessionId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_data);

        sessionId = getIntent().getStringExtra(EXTRA_SESSION_ID);
        if (sessionId == null || sessionId.isEmpty()) {
            Log.e(TAG, "Session ID is missing in Intent extras.");
            Toast.makeText(this, R.string.error_missing_session_id, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        dataViewModel = new ViewModelProvider(this).get(DataViewModel.class);

        bindViews();
        setupToolbar();
        setupViewPagerAndTabs();
        observeViewModel();

        if (dataViewModel.getSessionDetail().getValue() == null) {
            dataViewModel.fetchSessionDetails(sessionId);
        }
    }

    private void bindViews() {
        toolbar = findViewById(R.id.toolbar);
        viewPager = findViewById(R.id.sessionViewPager);
        tabLayout = findViewById(R.id.sessionTabLayout);
        progressBar = findViewById(R.id.progressBarSession);
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
            getSupportActionBar().setTitle(R.string.session_detail_title);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void setupViewPagerAndTabs() {
        SessionFragmentAdapter adapter = new SessionFragmentAdapter(this);
        viewPager.setAdapter(adapter);

        // Link Tabs with ViewPager
        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            switch (position) {
                case 0:
                    tab.setText(R.string.tab_session_overview);
                    // tab.setIcon(R.drawable.ic_overview);
                    break;
                case 1:
                    tab.setText(R.string.tab_session_transcript);
                    // tab.setIcon(R.drawable.ic_transcript);
                    break;
            }
        }).attach();
    }

    private void observeViewModel() {
        dataViewModel.isLoading.observe(this, isLoading -> {
            progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        });

        dataViewModel.getSessionDetail().observe(this, sessionDetail -> {
            if (sessionDetail != null) {
                Log.d(TAG, "SessionDetail loaded for ID: " + sessionId);
                if (getSupportActionBar() != null) {
                    String title = getString(R.string.session_title_format,
                            sessionDetail.getPatientName() != null ? sessionDetail.getPatientName() : "Unknown",
                            sessionDetail.getTreatmentDate() != null ? sessionDetail.getTreatmentDate() : "N/A");
                    getSupportActionBar().setTitle(title);
                }
            }
        });

        dataViewModel.getError().observe(this, error -> {
            if (error != null && !error.isEmpty()) {
                Log.e(TAG, "Error loading session details: " + error);
                String fullErrorMessage = getString(R.string.error_loading_session_prefix) + " " + error;
                Toast.makeText(this, fullErrorMessage, Toast.LENGTH_LONG).show();
            }
        });

        dataViewModel.getNavigateToTranscriptEvent().observe(this, event -> {
            if (event != null && event.getContentIfNotHandled() != null) {
                viewPager.setCurrentItem(1, true);
            }
        });
    }

    private static class SessionFragmentAdapter extends FragmentStateAdapter {
        public SessionFragmentAdapter(@NonNull AppCompatActivity activity) {
            super(activity);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            switch (position) {
                case 0:
                    return new SessionOverviewFragment();
                case 1:
                    return new SessionTranscriptFragment();
                default:
                    throw new IllegalStateException("Invalid position: " + position);
            }
        }

        @Override
        public int getItemCount() {
            return 2;
        }
    }
}