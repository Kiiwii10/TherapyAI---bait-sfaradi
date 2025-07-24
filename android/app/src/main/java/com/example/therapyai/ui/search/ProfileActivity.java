package com.example.therapyai.ui.search;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSmoothScroller;
import androidx.recyclerview.widget.RecyclerView;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.example.therapyai.R;
import com.example.therapyai.data.local.SessionManager;
import com.example.therapyai.util.ProfilePictureUtil;
import com.google.android.material.imageview.ShapeableImageView;
import com.example.therapyai.data.local.models.Profile;
import com.example.therapyai.data.local.models.SessionSummary;
import com.example.therapyai.data.repository.SearchRepository;
import com.example.therapyai.ui.adapters.ProfileSessionAdapter;
import com.example.therapyai.util.SentimentChartHelper;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.listener.ChartTouchListener;
import com.github.mikephil.charting.listener.OnChartGestureListener;
import com.github.mikephil.charting.listener.OnChartValueSelectedListener;
import com.google.android.material.appbar.MaterialToolbar;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ProfileActivity extends AppCompatActivity {

    public static final String EXTRA_PROFILE_ID = "com.example.therapyai.ui.profile.PROFILE_ID";
    private static final String TAG = "ProfileActivity";
    private static final float PROFILE_CHART_VISIBLE_GROUPS = 6f;    private static final SimpleDateFormat DATE_COMPARATOR_FORMAT = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

    private com.google.android.material.imageview.ShapeableImageView ivProfilePic;
    private TextView tvFullName, tvId, tvEmail, tvDateOfBirth;
    private TextView tvSessionSentimentTitle;
    private RecyclerView rvProfileSessions;
    private ProgressBar progressBarProfile, progressBarSessions, progressBarSessionChart;
    private MaterialToolbar toolbar;
    private View groupProfileContent;
    private BarChart barChartProfileSessions;
    private ProfileSessionAdapter profileSessionAdapter;
    private LinearLayoutManager sessionLayoutManager;
    private LinearSmoothScroller smoothScroller;

    private SearchRepository searchRepository;
    private String profileId;
    private Profile currentProfile;
    private List<SessionSummary> sortedSessionSummaries = new ArrayList<>();

    // --- Synchronization Logic Variables (like SessionTranscriptFragment) ---
    private final Handler scrollSyncHandler = new Handler(Looper.getMainLooper());
    private boolean isProgrammaticScroll = false;
    private boolean isChartGestureActive = false;
    private static final int SCROLL_SYNC_DEBOUNCE_MS = 15;
    private static final int PROGRAMMATIC_SCROLL_RESET_DELAY_MS = 150;
    private static final int SMOOTH_SCROLL_DURATION_APPROX_MS = 400;
    private Runnable syncChartRunnable = null;
    private Runnable syncListRunnable = null;
    private int highlightedSessionIndex = RecyclerView.NO_POSITION;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        searchRepository = SearchRepository.getInstance();
        SessionManager.UserType currentType = SessionManager.getInstance().getUserType();
        profileId = getIntent().getStringExtra(EXTRA_PROFILE_ID);
        if (profileId == null || profileId.isEmpty()) {
            Log.e(TAG, "Profile ID is missing in Intent extras.");
            Toast.makeText(this, R.string.error_missing_profile_id, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        setupSmoothScroller();
        bindViews();
        setupToolbar();
        setupRecyclerView();
        setupSessionChart();
        if (currentType == SessionManager.UserType.THERAPIST) {
            fetchProfile(profileId);
        }
        else{
            fetchProfilePatient(); // doesn't follow DRY, merge with fetchProfile or refactor later
        }
    }

    private void setupSmoothScroller() {
        smoothScroller = new LinearSmoothScroller(this) {
            @Override protected int getVerticalSnapPreference() {
                return LinearSmoothScroller.SNAP_TO_START;
            }
            @Override protected float calculateSpeedPerPixel(DisplayMetrics displayMetrics) {
                return 60f / displayMetrics.densityDpi;
            }
        };
    }

    private void bindViews() {
        toolbar = findViewById(R.id.toolbar);
        ivProfilePic = findViewById(R.id.ivProfilePic);
        tvFullName = findViewById(R.id.tvFullName);
        tvId = findViewById(R.id.tvId);
        tvEmail = findViewById(R.id.tvEmail);
        tvDateOfBirth = findViewById(R.id.tvDateOfBirth);
        rvProfileSessions = findViewById(R.id.rvProfileSessions);
        progressBarProfile = findViewById(R.id.progressBarProfile);
        progressBarSessions = findViewById(R.id.progressBarSessions);
        groupProfileContent = findViewById(R.id.groupProfileContent);
        barChartProfileSessions = findViewById(R.id.barChartProfileSessions);
        progressBarSessionChart = findViewById(R.id.progressBarSessionChart);
        tvSessionSentimentTitle = findViewById(R.id.tvSessionSentimentTitle);
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
            getSupportActionBar().setTitle(R.string.profile_title);
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

    private void setupRecyclerView() {
        profileSessionAdapter = new ProfileSessionAdapter(this, (sessionSummary, position) -> {
            Log.d(TAG, "Session clicked: ID " + sessionSummary.getId() + " at position " + position);


            cancelScrollSync();
            isProgrammaticScroll = false;
            setHighlight(position);

            scrollSyncHandler.postDelayed(() -> {
                if (currentProfile != null) {
                    Intent intent = new Intent(ProfileActivity.this, DataActivity.class);
                    intent.putExtra(DataActivity.EXTRA_SESSION_ID, sessionSummary.getId());
                    intent.putExtra(DataActivity.EXTRA_PATIENT_ID, currentProfile.getId());
                    startActivity(intent);
                } else {
                    Log.e(TAG, "Cannot navigate, currentProfile is null.");
                    Toast.makeText(this, "Error: Profile data missing.", Toast.LENGTH_SHORT).show();
                }
            }, 50);

        });

        sessionLayoutManager = new LinearLayoutManager(this);
        rvProfileSessions.setLayoutManager(sessionLayoutManager);
        rvProfileSessions.setAdapter(profileSessionAdapter);
        rvProfileSessions.setNestedScrollingEnabled(false);
        rvProfileSessions.setItemAnimator(null);

        // --- RecyclerView Scroll Listener for Sync ---
        rvProfileSessions.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    Log.v(TAG,"RV ScrollState: DRAGGING");
                    if (!isProgrammaticScroll) {
                        cancelScrollSync();
                    }
                } else if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    Log.v(TAG,"RV ScrollState: IDLE");
                    if (!isChartGestureActive) {
                        if (isProgrammaticScroll) {
                            scrollSyncHandler.postDelayed(() -> {
                                if (rvProfileSessions.getScrollState() == RecyclerView.SCROLL_STATE_IDLE && !isChartGestureActive) {
                                    isProgrammaticScroll = false;
                                    Log.v(TAG, "RV Scroll IDLE, Resetting isProgrammaticScroll = false");
                                }
                            }, PROGRAMMATIC_SCROLL_RESET_DELAY_MS);
                        } else {
                        }
                    } else {
                        Log.v(TAG,"RV ScrollState: IDLE, but chart gesture active, deferring flag reset.");
                    }
                }
            }

            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                if (!isProgrammaticScroll && !isChartGestureActive && profileSessionAdapter.getItemCount() > 0 && dy != 0) {
                    if (syncChartRunnable != null) {
                        scrollSyncHandler.removeCallbacks(syncChartRunnable);
                    }
                    syncChartRunnable = () -> {
                        if (!isProgrammaticScroll && !isChartGestureActive) {
                            Log.v(TAG, "RV Scrolled Syncing Chart (Debounced)");
                            syncChartToRecyclerViewScroll();
                        } else {
                            Log.v(TAG, "RV Scrolled Sync Chart Aborted (Debounced) - Flags changed: isProg=" + isProgrammaticScroll + " isGesture=" + isChartGestureActive);
                        }
                        syncChartRunnable = null;
                    };
                    scrollSyncHandler.postDelayed(syncChartRunnable, SCROLL_SYNC_DEBOUNCE_MS);
                } else if (dy != 0) {
                     Log.v(TAG, "RV Scrolled Skipped Sync Chart: isProg=" + isProgrammaticScroll + " isGesture=" + isChartGestureActive + " dy=" + dy);
                }
            }
        });
    }

    private void setupSessionChart() {
        SentimentChartHelper.setupSentimentBarChart(barChartProfileSessions, this, true, true, true);

        barChartProfileSessions.setDragEnabled(true);
        barChartProfileSessions.setScaleEnabled(false);
        barChartProfileSessions.setPinchZoom(false);
        barChartProfileSessions.setDoubleTapToZoomEnabled(false);
        barChartProfileSessions.setHighlightPerDragEnabled(false);

        // --- Chart Value Selected Listener (for direct bar taps) ---
        barChartProfileSessions.setOnChartValueSelectedListener(new OnChartValueSelectedListener() {
            @Override
            public void onValueSelected(Entry e, Highlight h) {
                if (e == null) return;
                int index = (int) e.getX();
                Log.d(TAG, "Chart value selected (direct tap) at index: " + index);
                if (index >= 0 && index < sortedSessionSummaries.size()) {
                    cancelScrollSync();
                    isProgrammaticScroll = true;
                    setHighlight(index);
                } else {
                    Log.w(TAG, "Chart value selected index out of bounds: " + index + " (Data size: " + sortedSessionSummaries.size() + ")");
                }
            }
            @Override public void onNothingSelected() { /* clear highlight? */ }
        });

        barChartProfileSessions.setOnChartGestureListener(new OnChartGestureListener() {
            private boolean wasDragged = false;

            @Override public void onChartGestureStart(MotionEvent me, ChartTouchListener.ChartGesture lastPerformedGesture) {
                if (lastPerformedGesture == ChartTouchListener.ChartGesture.DRAG ||
                        lastPerformedGesture == ChartTouchListener.ChartGesture.FLING ) {
                    Log.v(TAG,"ChartGesture: START " + lastPerformedGesture);
                    wasDragged = true;
                    isChartGestureActive = true;
                    cancelScrollSync();
                } else {
                    wasDragged = false;
                    isChartGestureActive = false;
                }
            }

            @Override public void onChartGestureEnd(MotionEvent me, ChartTouchListener.ChartGesture lastPerformedGesture) {
                Log.v(TAG, "ChartGesture: END " + lastPerformedGesture + " (wasDragged=" + wasDragged + ")");
                wasDragged = false;
                isChartGestureActive = false;
            }

            @Override public void onChartTranslate(MotionEvent me, float dX, float dY) {
                if (wasDragged && barChartProfileSessions.getData() != null) {
                    if (!isProgrammaticScroll) {
                        isProgrammaticScroll = true;
                        Log.v(TAG,"ChartTranslate: Set isProgrammaticScroll=true");
                    } else {
                        Log.v(TAG,"ChartTranslate: isProgrammaticScroll was already true.");
                    }
                    syncRecyclerViewToChartScroll(false);
                }
            }

            @Override public void onChartFling(MotionEvent me1, MotionEvent me2, float velocityX, float velocityY) {
                if (wasDragged && barChartProfileSessions.getData() != null) {
                    if (!isProgrammaticScroll) {
                        isProgrammaticScroll = true;
                        Log.v(TAG,"ChartFling: Set isProgrammaticScroll=true");
                    } else {
                        Log.v(TAG,"ChartFling: isProgrammaticScroll was already true.");
                    }
                    if (syncListRunnable != null) {
                        scrollSyncHandler.removeCallbacks(syncListRunnable);
                    }
                    syncListRunnable = () -> {
                        if (isProgrammaticScroll) {
                            Log.v(TAG,"ChartFling Runnable: Calling syncRecyclerViewToChartScroll");
                            syncRecyclerViewToChartScroll(false);
                        } else {
                            Log.w(TAG, "ChartFling Runnable: isProgrammaticScroll became false unexpectedly before sync.");
                        }
                        syncListRunnable = null;
                    };
                    scrollSyncHandler.postDelayed(syncListRunnable, SCROLL_SYNC_DEBOUNCE_MS);
                }
            }

            @Override public void onChartLongPressed(MotionEvent me) { wasDragged = false; isChartGestureActive = false;}
            @Override public void onChartDoubleTapped(MotionEvent me) { wasDragged = false; isChartGestureActive = false;}
            @Override public void onChartScale(MotionEvent me, float scaleX, float scaleY) { wasDragged = false; isChartGestureActive = false;}
            @Override public void onChartSingleTapped(MotionEvent me) {
                wasDragged = false;
                isChartGestureActive = false;
                Highlight h = barChartProfileSessions.getHighlightByTouchPoint(me.getX(), me.getY());
                if (h != null) {
                    int index = (int) h.getX();
                    Log.d(TAG, "Chart single tapped at index: " + index);
                    cancelScrollSync();
                    isProgrammaticScroll = true;
                    setHighlight(index);
                }
            }
        });

    }

    private void fetchProfilePatient() {
        Log.d(TAG, "Fetching profile data for patient");
        showProfileLoading(true);
        showSessionChartLoading(true);
        showSessionListLoading(true);
        clearChartAndList();

        searchRepository.getPatientProfile(new SearchRepository.ProfileCallback() {
            @Override
            public void onProfileFound(Profile profile) {
                runOnUiThread(() -> {
                    if (profile != null) {
                        currentProfile = profile;
                        Log.d(TAG, "Profile found: " + currentProfile.getFullName());
                        updateUIWithProfile(currentProfile);
                        showProfileLoading(false);

                        searchRepository.getPatientSessions(new SearchRepository.SessionSearchCallback() {
                            @Override
                            public void onSessionsFound(List<SessionSummary> sessionSummaries) {
                                runOnUiThread(() -> {
                                    if (sessionSummaries != null && !sessionSummaries.isEmpty()) {
                                        sortedSessionSummaries = sortSessionSummuries(sessionSummaries);
                                        Log.d(TAG, "Sorted " + sortedSessionSummaries.size() + " session scores.");
                                        highlightedSessionIndex = RecyclerView.NO_POSITION;
                                        updateSessionChart(sortedSessionSummaries);
                                        updateSessionList(sortedSessionSummaries);
                                        barChartProfileSessions.post(() ->
                                                SentimentChartHelper.applyInitialZoom(barChartProfileSessions, PROFILE_CHART_VISIBLE_GROUPS));
                                    } else {
                                        Log.d(TAG, "No session scores found for profile.");
                                        clearChartAndList();
                                    }
                                    showSessionChartLoading(false);
                                    showSessionListLoading(false);
                                });
                            }

                            @Override
                            public void onError(String error) {
                                runOnUiThread(() -> {
                                    Log.e(TAG, "Error fetching session summaries: " + error);
                                    Toast.makeText(ProfileActivity.this, getString(R.string.error_fetching_sessions, error), Toast.LENGTH_LONG).show();
                                    handleFetchError();
                                });
                            }
                        });

                    } else {
                        Log.w(TAG, "Profile not found for ID: " + profileId);
                        Toast.makeText(ProfileActivity.this, R.string.error_profile_not_found, Toast.LENGTH_LONG).show();
                        handleFetchError();
                    }
                });
            }



            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    Log.e(TAG, "Error fetching profile: " + error);
                    Toast.makeText(ProfileActivity.this, getString(R.string.error_fetching_profile, error), Toast.LENGTH_LONG).show();
                    handleFetchError();
                });
            }
        });
    }

    private void fetchProfile(String id) {
        Log.d(TAG, "Fetching profile data for ID: " + id);
        showProfileLoading(true);
        showSessionChartLoading(true);
        showSessionListLoading(true);
        clearChartAndList();

        searchRepository.performProfileSearch(id, new SearchRepository.ProfileSearchCallback() {
            @Override
            public void onProfilesFound(List<Profile> profiles) {
                runOnUiThread(() -> {
                    if (profiles != null && !profiles.isEmpty()) {
                        currentProfile = profiles.get(0);
                        Log.d(TAG, "Profile found: " + currentProfile.getFullName());
                        updateUIWithProfile(currentProfile);
                        showProfileLoading(false);
                        
                        searchRepository.performSessionSearch(currentProfile.getId(), new SearchRepository.SessionSearchCallback() {
                            @Override
                            public void onSessionsFound(List<SessionSummary> sessionSummaries) {
                                runOnUiThread(() -> {
                                    if (sessionSummaries != null && !sessionSummaries.isEmpty()) {
                                        sortedSessionSummaries = sortSessionSummuries(sessionSummaries);
                                        Log.d(TAG, "Sorted " + sortedSessionSummaries.size() + " session scores.");
                                        highlightedSessionIndex = RecyclerView.NO_POSITION;
                                        updateSessionChart(sortedSessionSummaries);
                                        updateSessionList(sortedSessionSummaries);
                                        barChartProfileSessions.post(() ->
                                                SentimentChartHelper.applyInitialZoom(barChartProfileSessions, PROFILE_CHART_VISIBLE_GROUPS));
                                    } else {
                                        Log.d(TAG, "No session scores found for profile.");
                                        clearChartAndList();
                                    }
                                    showSessionChartLoading(false);
                                    showSessionListLoading(false);
                                });
                            }

                            @Override
                            public void onError(String error) {
                                runOnUiThread(() -> {
                                    Log.e(TAG, "Error fetching session summaries: " + error);
                                    Toast.makeText(ProfileActivity.this, getString(R.string.error_fetching_sessions, error), Toast.LENGTH_LONG).show();
                                    handleFetchError();
                                });
                            }
                        });

                    } else {
                        Log.w(TAG, "Profile not found for ID: " + profileId);
                        Toast.makeText(ProfileActivity.this, R.string.error_profile_not_found, Toast.LENGTH_LONG).show();
                        handleFetchError();
                    }
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    Log.e(TAG, "Error fetching profile: " + error);
                    Toast.makeText(ProfileActivity.this, getString(R.string.error_fetching_profile, error), Toast.LENGTH_LONG).show();
                    handleFetchError();
                });
            }
        });
    }

    private void handleFetchError() {
        showProfileLoading(false);
        showSessionChartLoading(false);
        showSessionListLoading(false);
        clearChartAndList();
        // Maybe disable interaction elements if needed
    }


    private List<SessionSummary> sortSessionSummuries(List<SessionSummary> scores) {

        List<SessionSummary> sorted = new ArrayList<>(scores);
        Collections.sort(sorted, (o1, o2) -> {
            Date date1 = null, date2 = null;
            try {
                if (o1 != null && o1.getDate() != null) date1 = DATE_COMPARATOR_FORMAT.parse(o1.getDate());
                if (o2 != null && o2.getDate() != null) date2 = DATE_COMPARATOR_FORMAT.parse(o2.getDate());
            } catch (ParseException e) {
                Log.e(TAG, "Error parsing dates for sorting: " + (o1 != null ? o1.getDate() : "null") + ", " + (o2 != null ? o2.getDate() : "null"), e);
            }
            if (date1 == null && date2 == null) return 0;
            if (date1 == null) return 1;
            if (date2 == null) return -1;
            return date2.compareTo(date1);
        });
        return sorted;
    }

    private void clearChartAndList() {
        Log.d(TAG, "Clearing chart and list data/state.");
        sortedSessionSummaries.clear();
        highlightedSessionIndex = RecyclerView.NO_POSITION;
        isProgrammaticScroll = false;
        isChartGestureActive = false;
        cancelScrollSync();

        if (profileSessionAdapter != null) {
            profileSessionAdapter.setData(new ArrayList<>());
        }
        if (barChartProfileSessions != null) {
            SentimentChartHelper.updateProfileSessionSentimentChartData(barChartProfileSessions, null, this);
            SentimentChartHelper.applyInitialZoom(barChartProfileSessions, PROFILE_CHART_VISIBLE_GROUPS);
            barChartProfileSessions.setVisibility(View.GONE);
            tvSessionSentimentTitle.setVisibility(View.GONE);
        }
        if (rvProfileSessions != null) rvProfileSessions.setVisibility(View.GONE);
    }


    private void updateUIWithProfile(Profile profile) {
        if (profile == null) return;
        tvFullName.setText(profile.getFullName());
        String patientId = profile.getId() != null ? profile.getId() : "";
        tvId.setText(getString(R.string.profile_id_prefix) + patientId);

        String email = profile.getEmail() != null ? profile.getEmail() : "";
        tvEmail.setText(getString(R.string.profile_email_prefix) + email);
        tvEmail.setVisibility(email.isEmpty() ? View.GONE : View.VISIBLE);        String dob = profile.getDateOfBirth() != null ? profile.getDateOfBirth() : "";
        tvDateOfBirth.setText(getString(R.string.profile_dob_prefix) + dob);
        tvDateOfBirth.setVisibility(dob.isEmpty() ? View.GONE : View.VISIBLE);

        // Use the utility method to load the profile picture
        ProfilePictureUtil.loadProfilePicture(this, profile.getPictureUrl(), ivProfilePic);
        
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(profile.getFullName());
        }
    }

    private void showProfileLoading(boolean isLoading) {
        progressBarProfile.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        groupProfileContent.setVisibility(isLoading ? View.GONE : View.VISIBLE);
    }

    private void showSessionChartLoading(boolean isLoading) {
        progressBarSessionChart.setVisibility(isLoading ? View.VISIBLE : View.GONE);
    }

    private void showSessionListLoading(boolean isLoading) {
        progressBarSessions.setVisibility(isLoading ? View.VISIBLE : View.GONE);
    }

    private void updateSessionChart(List<SessionSummary> sessionSummaries) {
        boolean hasData = sessionSummaries != null && !sessionSummaries.isEmpty();
        showSessionChartLoading(false);
        if (hasData) {
            Log.d(TAG, "Updating session chart with " + sessionSummaries.size() + " scores.");
            SentimentChartHelper.updateProfileSessionSentimentChartData(barChartProfileSessions, sessionSummaries, this);
            barChartProfileSessions.setVisibility(View.VISIBLE);
            tvSessionSentimentTitle.setVisibility(View.VISIBLE);
            SentimentChartHelper.applyInitialZoom(barChartProfileSessions, PROFILE_CHART_VISIBLE_GROUPS);
            if (highlightedSessionIndex != RecyclerView.NO_POSITION && highlightedSessionIndex < sessionSummaries.size()) {
                barChartProfileSessions.highlightValue(highlightedSessionIndex, 0, false);
            } else {
                barChartProfileSessions.highlightValue(null);
            }
        } else {
            Log.d(TAG, "No session scores for chart. Clearing.");
            if (barChartProfileSessions != null) {
                SentimentChartHelper.updateProfileSessionSentimentChartData(barChartProfileSessions, null, this);
                SentimentChartHelper.applyInitialZoom(barChartProfileSessions, PROFILE_CHART_VISIBLE_GROUPS);
                barChartProfileSessions.setVisibility(View.GONE);
            }
            if (tvSessionSentimentTitle != null) tvSessionSentimentTitle.setVisibility(View.GONE);
        }
    }

    private void updateSessionList(List<SessionSummary> scores) {
        boolean hasData = scores != null && !scores.isEmpty();
        showSessionListLoading(false);
        if (hasData) {
            Log.d(TAG, "Updating session list with " + scores.size() + " scores.");
            profileSessionAdapter.setData(scores); // Clears old highlight
            rvProfileSessions.setVisibility(View.VISIBLE);
            // Restore highlight if needed
            if (highlightedSessionIndex != RecyclerView.NO_POSITION) {
                profileSessionAdapter.setHighlightPosition(highlightedSessionIndex);
            }
        } else {
            Log.d(TAG, "No session scores for list. Clearing.");
            if (profileSessionAdapter != null) profileSessionAdapter.setData(new ArrayList<>());
            if (rvProfileSessions != null) rvProfileSessions.setVisibility(View.GONE);
        }
    }


    // --- Synchronization Logic Implementation ---

    /**
     * Central function to set the highlight state and trigger sync scrolling.
     * Manages the temporary "bloom" highlight via the adapter.
     * @param index The index of the session/bar group to highlight. RecyclerView.NO_POSITION to clear.
     */
    private void setHighlight(int index) {
        // Validate index
        if (index < 0 || index >= sortedSessionSummaries.size()) {
            index = RecyclerView.NO_POSITION;
        }

        if (highlightedSessionIndex == index) {
            if (index != RecyclerView.NO_POSITION && profileSessionAdapter != null) {
                Log.v(TAG, "setHighlight: Index " + index + " already highlighted. Resetting adapter timer.");
                profileSessionAdapter.setHighlightPosition(index);
            }
            if(isProgrammaticScroll) {
                scrollSyncHandler.postDelayed(() -> {
                    if(isProgrammaticScroll) isProgrammaticScroll = false;
                }, PROGRAMMATIC_SCROLL_RESET_DELAY_MS);
            }
            return;
        }

        Log.d(TAG, "setHighlight: Setting highlight to index: " + index + " (Programmatic: " + isProgrammaticScroll + ")");
        highlightedSessionIndex = index;

        if (profileSessionAdapter != null) {
            profileSessionAdapter.setHighlightPosition(highlightedSessionIndex);
        }

        if (barChartProfileSessions != null && barChartProfileSessions.getData() != null) {
            barChartProfileSessions.post(() -> {
                if (highlightedSessionIndex != RecyclerView.NO_POSITION) {
                    barChartProfileSessions.highlightValue(highlightedSessionIndex, 0, false);
                } else {
                    barChartProfileSessions.highlightValue(null);
                }
            });
        }

        if (highlightedSessionIndex != RecyclerView.NO_POSITION) {
            if (isProgrammaticScroll) {
                Log.v(TAG,"setHighlight: Chart initiated, calling scrollToSessionIndex(" + highlightedSessionIndex + ", smooth=true)");
                scrollToSessionIndex(highlightedSessionIndex, true);
            } else {
                Log.v(TAG,"setHighlight: List initiated, calling centerChartToSessionIndex(" + highlightedSessionIndex + ", smooth=true)");
                centerChartToSessionIndex(highlightedSessionIndex, true);
            }
        } else {
            if(isProgrammaticScroll) {
                Log.v(TAG,"setHighlight: Highlight cleared programmatically. Resetting flag soon.");
                scrollSyncHandler.postDelayed(() -> {
                    if(isProgrammaticScroll) isProgrammaticScroll = false;
                }, PROGRAMMATIC_SCROLL_RESET_DELAY_MS);
            }
        }
    }



    /** Scrolls RecyclerView to the target index. Manages the isProgrammaticScroll flag reset. */
    private void scrollToSessionIndex(int index, boolean smooth) {
        if (sessionLayoutManager == null || profileSessionAdapter == null || index < 0 || index >= profileSessionAdapter.getItemCount()) {
            Log.w(TAG, "Cannot scroll list, invalid index or view null. Index: " + index);
            if (isProgrammaticScroll) {
                scrollSyncHandler.postDelayed(() -> { if(isProgrammaticScroll) isProgrammaticScroll = false; }, PROGRAMMATIC_SCROLL_RESET_DELAY_MS);
            }
            return;
        }

        if (!isProgrammaticScroll) {
            Log.w(TAG, "scrollToSessionIndex called but isProgrammaticScroll is FALSE. Potential logic issue.");
            isProgrammaticScroll = true;
        }
        Log.v(TAG, "scrollToSessionIndex: Scrolling list PROG to index: " + index + ", Smooth: " + smooth);

        if (smooth) {
            smoothScroller.setTargetPosition(index);
            sessionLayoutManager.startSmoothScroll(smoothScroller);
            scrollSyncHandler.postDelayed(() -> {
                if(isProgrammaticScroll) {
                    isProgrammaticScroll = false;
                    Log.v(TAG, "RV Smooth Scroll END (est.), resetting isProgrammaticScroll = false");
                }
            }, SMOOTH_SCROLL_DURATION_APPROX_MS);
        } else {
            sessionLayoutManager.scrollToPositionWithOffset(index, rvProfileSessions.getPaddingTop());
            scrollSyncHandler.postDelayed(() -> {
                if(isProgrammaticScroll) {
                    isProgrammaticScroll = false;
                    Log.v(TAG, "RV Non-Smooth Scroll END, resetting isProgrammaticScroll = false");
                }
            }, PROGRAMMATIC_SCROLL_RESET_DELAY_MS);
        }
    }

    /** Centers the BarChart view to the given session index. Manages the isProgrammaticScroll flag reset. */
    private void centerChartToSessionIndex(int index, boolean animated) {
        if (barChartProfileSessions == null || barChartProfileSessions.getData() == null || index < 0 || index >= barChartProfileSessions.getBarData().getEntryCount()) {
            Log.w(TAG, "Cannot center chart, invalid index or view null. Index: " + index);
            if (isProgrammaticScroll) {
                scrollSyncHandler.postDelayed(() -> { if(isProgrammaticScroll) isProgrammaticScroll = false; }, PROGRAMMATIC_SCROLL_RESET_DELAY_MS);
            }
            return;
        }

        if (!isProgrammaticScroll) {
            Log.w(TAG, "centerChartToSessionIndex called but isProgrammaticScroll is FALSE. Potential logic issue.");
            isProgrammaticScroll = true;
        }

        Log.v(TAG, "centerChartToSessionIndex: Centering chart PROG to index: " + index + ", Animated: " + animated);

        long animationDuration = animated ? 500L : 0L;
        long resetDelay = animated ? animationDuration + 150L : PROGRAMMATIC_SCROLL_RESET_DELAY_MS;

        barChartProfileSessions.post(() -> {
            if (animated) {
                barChartProfileSessions.centerViewToAnimated(index, 0f, YAxis.AxisDependency.LEFT, animationDuration);
            } else {
                barChartProfileSessions.centerViewTo(index, 0f, YAxis.AxisDependency.LEFT);
            }
        });

        scrollSyncHandler.postDelayed(() -> {
            if(isProgrammaticScroll) {
                isProgrammaticScroll = false;
                Log.v(TAG, "Chart Center/Move END (est.), resetting isProgrammaticScroll = false");
            }
        }, resetDelay);
    }



    /** Moves the chart view based on the list's scroll position. Called by RV scroll listener. */
    private void syncChartToRecyclerViewScroll() {
        if (barChartProfileSessions == null || sessionLayoutManager == null || profileSessionAdapter == null || profileSessionAdapter.getItemCount() == 0 || barChartProfileSessions.getData() == null
                || isProgrammaticScroll || isChartGestureActive) { // Guard against programmatic/gesture sync
            return;
        }
        int firstVisible = sessionLayoutManager.findFirstVisibleItemPosition();
        int lastVisible  = sessionLayoutManager.findLastVisibleItemPosition();
        if (firstVisible != RecyclerView.NO_POSITION && lastVisible != RecyclerView.NO_POSITION) {
            float centerIndex;
            View firstView = sessionLayoutManager.findViewByPosition(firstVisible);
            if (firstVisible == 0 && firstView != null && firstView.getTop() >= rvProfileSessions.getPaddingTop() - 5) {
                centerIndex = -SentimentChartHelper.CHART_PADDING_FACTOR; // Align chart start
            } else {
                centerIndex = firstVisible + (lastVisible - firstVisible) / 2.0f;
                int count = barChartProfileSessions.getBarData().getEntryCount();
                centerIndex = Math.max(-SentimentChartHelper.CHART_PADDING_FACTOR, Math.min(centerIndex, count - 1 + SentimentChartHelper.CHART_PADDING_FACTOR));
            }
            Log.v(TAG, "syncChartToRecyclerViewScroll: Syncing chart to index ~" + String.format("%.2f", centerIndex));
            barChartProfileSessions.centerViewToAnimated(centerIndex, 0f, YAxis.AxisDependency.LEFT, 200);
        }
    }

    /** Scrolls the RecyclerView based on the chart's visible range. Called by chart gesture listener. */
    private void syncRecyclerViewToChartScroll(boolean smooth) {
        if (rvProfileSessions == null || barChartProfileSessions == null || barChartProfileSessions.getData() == null
                || sessionLayoutManager == null || profileSessionAdapter == null || profileSessionAdapter.getItemCount() == 0) {
            Log.w(TAG, "syncRecyclerViewToChartScroll: Skipped (views null or no data)");
            if (isProgrammaticScroll) {
                scrollSyncHandler.postDelayed(() -> { if(isProgrammaticScroll) isProgrammaticScroll = false; }, PROGRAMMATIC_SCROLL_RESET_DELAY_MS);
            }
            return;
        }

        if (!isProgrammaticScroll) {
            Log.w(TAG, "syncRecyclerViewToChartScroll: Called but isProgrammaticScroll is FALSE. Skipping to avoid unexpected list scroll.");
            return;
        }

        float lowX = barChartProfileSessions.getLowestVisibleX();
        float highX = barChartProfileSessions.getHighestVisibleX();
        float visibleXRange = barChartProfileSessions.getVisibleXRange();
        float centerXF = lowX + visibleXRange / 2f; // Center of the *visible* chart range

        int centerIndex = Math.round(centerXF);
        int count = profileSessionAdapter.getItemCount();
        centerIndex = Math.max(0, Math.min(centerIndex, count - 1));

        Log.v(TAG, "syncRecyclerViewToChartScroll: Syncing list PROG to chart center index " + centerIndex + " (Smooth=" + smooth + ")");

        scrollToSessionIndex(centerIndex, smooth);
    }


    /** Cancels any pending scroll synchronization runnables. */
    private void cancelScrollSync() {
        boolean cancelled = false;
        if (syncChartRunnable != null) {
            scrollSyncHandler.removeCallbacks(syncChartRunnable);
            syncChartRunnable = null;
            cancelled = true;
        }
        if (syncListRunnable != null) {
            scrollSyncHandler.removeCallbacks(syncListRunnable);
            syncListRunnable = null;
            cancelled = true;
        }
        if (cancelled) Log.v(TAG,"cancelScrollSync: Cancelled pending sync runnables.");
    }


    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause: Cancelling sync and resetting flags.");
        cancelScrollSync();
        isProgrammaticScroll = false;
        isChartGestureActive = false;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy: Removing handler callbacks.");
        scrollSyncHandler.removeCallbacksAndMessages(null);
    }
}