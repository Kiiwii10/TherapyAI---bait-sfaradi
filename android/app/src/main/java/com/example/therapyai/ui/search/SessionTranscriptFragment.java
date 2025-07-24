package com.example.therapyai.ui.search;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSmoothScroller;
import androidx.recyclerview.widget.RecyclerView;

import com.example.therapyai.R;
import com.example.therapyai.data.local.models.FinalTranscriptEntry;
import com.example.therapyai.ui.adapters.FinalTranscriptAdapter;
import com.example.therapyai.ui.viewmodels.DataViewModel;
import com.example.therapyai.util.SentimentChartHelper;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.listener.ChartTouchListener;
import com.github.mikephil.charting.listener.OnChartGestureListener;
import com.github.mikephil.charting.listener.OnChartValueSelectedListener;

import java.util.ArrayList;
import java.util.List;


public class SessionTranscriptFragment extends Fragment {

    private static final String TAG = "SessionTranscriptFrag";
    private static final float TRANSCRIPT_VISIBLE_GROUPS = 6f;
    private DataViewModel dataViewModel;
    private RecyclerView rvTranscript;
    private FinalTranscriptAdapter transcriptAdapter;
    private BarChart barChartTranscript;
    private LinearLayoutManager layoutManager;
    private Context mContext;
    private LinearSmoothScroller smoothScroller;

    private final Handler scrollSyncHandler = new Handler(Looper.getMainLooper());
    private boolean isProgrammaticScroll = false;
    private static final int SCROLL_SYNC_DEBOUNCE_MS = 150; // Increased debounce for safety

    private Runnable syncChartRunnable = null;
    private Runnable syncListRunnable = null;

    // To map chart indices (patient-only) to original transcript indices
    private List<Integer> patientEntryOriginalIndices = new ArrayList<>();
    // To map original transcript indices (patient-only) to chart indices
    private List<FinalTranscriptEntry> currentlyPlottedPatientEntries = new ArrayList<>();


    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        mContext = context;
        smoothScroller = new LinearSmoothScroller(mContext) {
            @Override protected int getVerticalSnapPreference() {
                return LinearSmoothScroller.SNAP_TO_START;
            }
            @Override
            protected void onTargetFound(View targetView, RecyclerView.State state, Action action) {
                super.onTargetFound(targetView, state, action);
                // After smooth scroll finishes, reset the flag
                scrollSyncHandler.postDelayed(() -> isProgrammaticScroll = false, 50);
            }

            @Override
            protected void onStop() {
                super.onStop();
                // Fallback in case onTargetFound is not always called or if scroll is cancelled
                // Add a slight delay to allow any immediate post-scroll actions to complete
                scrollSyncHandler.postDelayed(() -> {
                    if (isProgrammaticScroll) { // Check if it's still true
                        isProgrammaticScroll = false;
                    }
                }, 100);
            }
        };
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        dataViewModel = new ViewModelProvider(requireActivity()).get(DataViewModel.class);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_session_transcript, container, false);
        rvTranscript = view.findViewById(R.id.rvTranscript);
        barChartTranscript = view.findViewById(R.id.barChartTranscript);
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setupRecyclerView();
        setupChart();
        observeViewModel();
    }

    private void setupRecyclerView() {
        if (mContext == null) return;
        transcriptAdapter = new FinalTranscriptAdapter(mContext, null);
        layoutManager = new LinearLayoutManager(mContext);
        rvTranscript.setLayoutManager(layoutManager);
        rvTranscript.setAdapter(transcriptAdapter);
        rvTranscript.setItemAnimator(null);


        rvTranscript.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                if (!isProgrammaticScroll && transcriptAdapter.getItemCount() > 0 && dy != 0) { // only if actually scrolled
                    cancelScrollSync();
                    syncChartRunnable = () -> syncChartToRecyclerViewScroll();
                    scrollSyncHandler.postDelayed(syncChartRunnable, SCROLL_SYNC_DEBOUNCE_MS);
                }
            }
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    if (!isProgrammaticScroll) {
                        // Ensure final sync when scrolling stops, if not already covered by onScrolled
                        // This can help catch drifts if onScrolled debounce misses the final state.
                        cancelScrollSync(); // cancel previous ones
                        syncChartRunnable = () -> syncChartToRecyclerViewScroll();
                        scrollSyncHandler.postDelayed(syncChartRunnable, SCROLL_SYNC_DEBOUNCE_MS + 50); // a bit more delay
                    }
                } else if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    if (isProgrammaticScroll) { // User started dragging during programmatic scroll
                        isProgrammaticScroll = false; // Give control to user
                        cancelScrollSync();
                    }
                }
            }
        });
    }


    private void setupChart() {
        if (mContext == null) {
            Log.e(TAG, "Context is null during setupChart!");
            return;
        }
        SentimentChartHelper.setupSentimentBarChart(barChartTranscript, mContext, true, false, false);

        barChartTranscript.setOnChartValueSelectedListener(new OnChartValueSelectedListener() {
            @Override
            public void onValueSelected(Entry e, Highlight h) {
                int chartIndex = (int) e.getX(); // This is the index in the patient-only list
                Log.d(TAG, "Transcript chart bar selected at chartIndex: " + chartIndex);
                if (chartIndex >= 0 && chartIndex < patientEntryOriginalIndices.size()) {
                    int originalTranscriptIndex = patientEntryOriginalIndices.get(chartIndex);
                    Log.d(TAG, "Mapped to original transcript index: " + originalTranscriptIndex);

                    cancelScrollSync();
                    isProgrammaticScroll = true; // Set before initiating scroll
                    dataViewModel.requestHighlight(originalTranscriptIndex); // Highlight in RV
                } else {
                    Log.w(TAG, "Chart index out of bounds for patientEntryOriginalIndices mapping.");
                }
            }
            @Override public void onNothingSelected() {}
        });

        barChartTranscript.setOnChartGestureListener(new OnChartGestureListener() {
            private boolean wasDraggedOrFlung = false;

            @Override public void onChartGestureStart(MotionEvent me, ChartTouchListener.ChartGesture lastPerformedGesture) {
                if (lastPerformedGesture == ChartTouchListener.ChartGesture.DRAG ||
                        lastPerformedGesture == ChartTouchListener.ChartGesture.FLING) {
                    Log.v(TAG,"ChartGesture: START " + lastPerformedGesture);
                    wasDraggedOrFlung = true;
                    isProgrammaticScroll = true; // Temporarily set to true to prevent RV sync during chart drag
                    cancelScrollSync();
                } else {
                    wasDraggedOrFlung = false;
                }
            }

            @Override public void onChartGestureEnd(MotionEvent me, ChartTouchListener.ChartGesture lastPerformedGesture) {
                if (wasDraggedOrFlung) {
                    Log.v(TAG, "ChartGesture: END " + lastPerformedGesture + ". Lowest X: " + barChartTranscript.getLowestVisibleX());
                    wasDraggedOrFlung = false;
                    // Delay resetting isProgrammaticScroll and triggering sync
                    // to allow chart to settle and avoid immediate re-sync loop.
                    scrollSyncHandler.postDelayed(() -> {
                        isProgrammaticScroll = false; // Now allow RV to sync from chart
                        syncRecyclerViewToChartScroll(true); // Sync RV to final chart position
                    }, SCROLL_SYNC_DEBOUNCE_MS / 2); // Short delay
                }
            }
            @Override public void onChartTranslate(MotionEvent me, float dX, float dY) {
                // This is called continuously during drag. We'll sync onChartGestureEnd.
                if (wasDraggedOrFlung) {
                    // Could potentially sync list here with heavy debouncing if needed, but onChartGestureEnd is usually better
                }
            }
            @Override public void onChartFling(MotionEvent me1, MotionEvent me2, float velocityX, float velocityY) {
                // Sync on onChartGestureEnd for fling as well
            }
            @Override public void onChartLongPressed(MotionEvent me) { wasDraggedOrFlung = false; }
            @Override public void onChartDoubleTapped(MotionEvent me) { wasDraggedOrFlung = false; }
            @Override public void onChartScale(MotionEvent me, float scaleX, float scaleY) {
                wasDraggedOrFlung = false; // If scaling, handle separately or after gesture ends
                Log.v(TAG, "ChartGesture: SCALE");
                scrollSyncHandler.postDelayed(() -> {
                    isProgrammaticScroll = false;
                    syncRecyclerViewToChartScroll(true);
                }, SCROLL_SYNC_DEBOUNCE_MS);
            }
            @Override public void onChartSingleTapped(MotionEvent me) {
                wasDraggedOrFlung = false;
                Highlight h = barChartTranscript.getHighlightByTouchPoint(me.getX(), me.getY());
                if (h != null) {
                    int chartIndex = (int) h.getX();
                    Log.d(TAG, "Transcript chart single tapped at chartIndex: " + chartIndex);
                    if (chartIndex >= 0 && chartIndex < patientEntryOriginalIndices.size()) {
                        int originalTranscriptIndex = patientEntryOriginalIndices.get(chartIndex);
                        Log.d(TAG, "Mapped to original transcript index for tap: " + originalTranscriptIndex);
                        cancelScrollSync();
                        isProgrammaticScroll = true;
                        dataViewModel.requestHighlight(originalTranscriptIndex);
                    }
                }
            }
        });
    }


    private void observeViewModel() {
        dataViewModel.getSessionDetail().observe(getViewLifecycleOwner(), sessionDetail -> {
            if (sessionDetail != null && sessionDetail.getTranscriptEntries() != null) {
                List<FinalTranscriptEntry> fullTranscript = sessionDetail.getTranscriptEntries();
                Log.d(TAG, "Updating transcript UI with " + fullTranscript.size() + " total entries.");

                int firstVisibleOriginal = layoutManager.findFirstVisibleItemPosition();
                View firstViewOriginal = layoutManager.findViewByPosition(firstVisibleOriginal);
                int topOffsetOriginal = (firstViewOriginal == null) ? 0 : (firstViewOriginal.getTop() - rvTranscript.getPaddingTop());

                transcriptAdapter.setData(fullTranscript, sessionDetail.getTimedNotes());

                // Regenerate mapping and update chart
                patientEntryOriginalIndices.clear();
                currentlyPlottedPatientEntries.clear();
                if (fullTranscript != null) {
                    for (int i = 0; i < fullTranscript.size(); i++) {
                        FinalTranscriptEntry entry = fullTranscript.get(i);
                        if ("Patient".equalsIgnoreCase(entry.getSpeaker()) && entry.getSentimentScore() != null) {
                            patientEntryOriginalIndices.add(i); // Store original index
                            currentlyPlottedPatientEntries.add(entry); // Store the entry itself for the chart
                        }
                    }
                }
                // Update chart with only patient entries
                SentimentChartHelper.updateSentimentBarChartData(barChartTranscript, currentlyPlottedPatientEntries, mContext);


                if (firstVisibleOriginal != RecyclerView.NO_POSITION && firstVisibleOriginal < transcriptAdapter.getItemCount()) {
                    layoutManager.scrollToPositionWithOffset(firstVisibleOriginal, topOffsetOriginal);
                }


                if (barChartTranscript != null && !currentlyPlottedPatientEntries.isEmpty()) {
                    barChartTranscript.post(() -> {
                        if(barChartTranscript != null) { // Check again as it's a post
                            SentimentChartHelper.applyInitialZoom(barChartTranscript, TRANSCRIPT_VISIBLE_GROUPS);
                            syncChartToRecyclerViewScroll(); // Initial sync after data load
                        }
                    });
                } else if (barChartTranscript != null) {
                    barChartTranscript.fitScreen(); // No data, fit screen
                }
            } else {
                Log.d(TAG, "Transcript data is null or empty. Clearing UI and chart.");
                transcriptAdapter.setData(new ArrayList<>(), new ArrayList<>());
                patientEntryOriginalIndices.clear();
                currentlyPlottedPatientEntries.clear();
                if (mContext != null && barChartTranscript != null) {
                    SentimentChartHelper.updateSentimentBarChartData(barChartTranscript, null, mContext);
                    barChartTranscript.fitScreen();
                }
            }
        });

        dataViewModel.getHighlightRequest().observe(getViewLifecycleOwner(), event -> {
            Integer originalIndexToHighlight = event.getContentIfNotHandled();
            if (originalIndexToHighlight != null && originalIndexToHighlight >= 0) {
                Log.d(TAG, "Highlight requested for original transcript index: " + originalIndexToHighlight);

                cancelScrollSync(); // Stop other syncs
                isProgrammaticScroll = true; // Set flag before scrolling RV

                scrollToTranscriptIndex(originalIndexToHighlight, true); // Scroll RV

                if (transcriptAdapter != null) {
                    transcriptAdapter.setHighlightPosition(originalIndexToHighlight);
                }

                // Highlight corresponding bar in chart if it's a patient entry
                if (barChartTranscript != null && barChartTranscript.getData() != null && !patientEntryOriginalIndices.isEmpty()) {
                    int chartIndexToHighlight = -1;
                    // Find the chart index for this original transcript index
                    for(int i=0; i < patientEntryOriginalIndices.size(); i++){
                        if(patientEntryOriginalIndices.get(i) == originalIndexToHighlight){
                            chartIndexToHighlight = i;
                            break;
                        }
                    }

                    if (chartIndexToHighlight != -1 && chartIndexToHighlight < barChartTranscript.getBarData().getEntryCount()) {
                        barChartTranscript.highlightValue(chartIndexToHighlight, 0, false); // datasetIndex 0
                        // Center chart view on the highlighted bar
                        final int finalChartIndexToHighlight = chartIndexToHighlight;
                        barChartTranscript.post(() -> { // Post to run after layout
                            if(barChartTranscript != null) {
                                barChartTranscript.centerViewToAnimated(finalChartIndexToHighlight, 0f, YAxis.AxisDependency.LEFT, 300);
                            }
                            // Reset programmatic scroll after animation attempt
                            scrollSyncHandler.postDelayed(() -> isProgrammaticScroll = false, 350);
                        });
                    } else {
                        barChartTranscript.highlightValue(null); // Clear highlight if not found or not a patient entry
                        isProgrammaticScroll = false; // Reset if no chart action
                    }
                } else {
                    isProgrammaticScroll = false; // Reset if no chart or data
                }
            }
        });
    }

    private void scrollToTranscriptIndex(int index, boolean smooth) {
        if (layoutManager == null || transcriptAdapter == null || index < 0 || index >= transcriptAdapter.getItemCount()) {
            Log.w(TAG, "Cannot scroll list, invalid index or adapter/layoutManager null. Index: " + index);
            isProgrammaticScroll = false; // Reset flag if scroll fails
            return;
        }
        Log.v(TAG, "Scrolling list to index: " + index + ", Smooth: " + smooth);

        // isProgrammaticScroll = true; // Already set by caller (highlightRequest or chart interaction)

        if (smooth) {
            smoothScroller.setTargetPosition(index);
            layoutManager.startSmoothScroll(smoothScroller);
            // isProgrammaticScroll will be reset by smoothScroller's onStop/onTargetFound
        } else {
            int offset = rvTranscript.getPaddingTop(); // Or calculate to center the item
            layoutManager.scrollToPositionWithOffset(index, offset);
            // Manually reset isProgrammaticScroll after a short delay for non-smooth scroll
            scrollSyncHandler.postDelayed(() -> isProgrammaticScroll = false, 100);
        }
    }

    private void syncChartToRecyclerViewScroll() {
        if (barChartTranscript == null || layoutManager == null || transcriptAdapter == null ||
                transcriptAdapter.getItemCount() == 0 || barChartTranscript.getData() == null ||
                currentlyPlottedPatientEntries.isEmpty() || isProgrammaticScroll) { // Check isProgrammaticScroll here
            return;
        }

        int firstVisibleItemOriginalIndex = layoutManager.findFirstVisibleItemPosition();
        int lastVisibleItemOriginalIndex = layoutManager.findLastVisibleItemPosition();

        if (firstVisibleItemOriginalIndex == RecyclerView.NO_POSITION) return;

        // Find the chart index corresponding to the central visible patient item in the RV
        int targetChartIndex = -1;
        int centerOriginalIndex = firstVisibleItemOriginalIndex + (lastVisibleItemOriginalIndex - firstVisibleItemOriginalIndex) / 2;

        // Find the closest patient item in the chart to the centerOriginalIndex
        int bestMatchOriginalIndex = -1;
        int minDiff = Integer.MAX_VALUE;

        for(int i = 0; i < patientEntryOriginalIndices.size(); i++) {
            int originalIdx = patientEntryOriginalIndices.get(i);
            if (originalIdx >= firstVisibleItemOriginalIndex && originalIdx <= lastVisibleItemOriginalIndex) {
                // This patient item is visible or partially visible in RV
                int diff = Math.abs(originalIdx - centerOriginalIndex);
                if (diff < minDiff) {
                    minDiff = diff;
                    targetChartIndex = i; // i is the index in currentlyPlottedPatientEntries
                    bestMatchOriginalIndex = originalIdx;
                }
            }
        }
        // If no patient items are visible, try to find the first patient item before or after current view
        if(targetChartIndex == -1){
            for(int i = 0; i < patientEntryOriginalIndices.size(); i++){
                if(patientEntryOriginalIndices.get(i) >= centerOriginalIndex){
                    targetChartIndex = i; // first patient after or at center
                    break;
                }
            }
            if(targetChartIndex == -1 && !patientEntryOriginalIndices.isEmpty()){
                targetChartIndex = patientEntryOriginalIndices.size() -1; // last patient
            }
        }


        if (targetChartIndex != -1) {
            Log.v(TAG, "SyncChart: RV scrolled. Centering chart on patient item (original index " + bestMatchOriginalIndex + ", chart index " + targetChartIndex + ")");
            // Temporarily disable RV scroll listener during chart animation
            final boolean oldProgrammaticFlag = isProgrammaticScroll;
            isProgrammaticScroll = true;
            barChartTranscript.centerViewToAnimated(targetChartIndex, 0f, YAxis.AxisDependency.LEFT, 200);
            scrollSyncHandler.postDelayed(() -> isProgrammaticScroll = oldProgrammaticFlag, 250);

        } else {
            Log.v(TAG, "SyncChart: RV scrolled, but no corresponding patient entry found in visible range to sync chart.");
        }
    }


    private void syncRecyclerViewToChartScroll(boolean smooth) {
        if (rvTranscript == null || barChartTranscript == null || barChartTranscript.getData() == null ||
                layoutManager == null || currentlyPlottedPatientEntries.isEmpty() || isProgrammaticScroll) { // Check isProgrammaticScroll
            return;
        }

        float lowestVisibleX_chart = barChartTranscript.getLowestVisibleX();
        float highestVisibleX_chart = barChartTranscript.getHighestVisibleX();
        // int centerChartIndex = (int) (lowestVisibleX_chart + (highestVisibleX_chart - lowestVisibleX_chart) / 2f);
        // Use the highlight if available, otherwise center
        Highlight currentHighlight = barChartTranscript.getHighlighted() != null && barChartTranscript.getHighlighted().length > 0 ? barChartTranscript.getHighlighted()[0] : null;
        int centerChartIndex;
        if (currentHighlight != null) {
            centerChartIndex = (int) currentHighlight.getX();
        } else {
            centerChartIndex = (int) (lowestVisibleX_chart + (highestVisibleX_chart - lowestVisibleX_chart) / 2f);
        }

        centerChartIndex = Math.max(0, Math.min(centerChartIndex, currentlyPlottedPatientEntries.size() - 1));

        if (centerChartIndex >= 0 && centerChartIndex < patientEntryOriginalIndices.size()) {
            int originalTranscriptIndex = patientEntryOriginalIndices.get(centerChartIndex);
            Log.v(TAG, "SyncList: Chart scrolled/scaled. Scrolling list to original index: " + originalTranscriptIndex + " (from chart index " + centerChartIndex + ")");

            // Set flag, scroll, then reset flag in scrollToTranscriptIndex or its callbacks
            // isProgrammaticScroll = true; // Set by the caller (e.g., onChartGestureEnd)
            scrollToTranscriptIndex(originalTranscriptIndex, smooth);
        } else {
            Log.w(TAG, "SyncList: Could not map chart index " + centerChartIndex + " to original transcript index.");
        }
    }

    private void cancelScrollSync() {
        if (syncChartRunnable != null) {
            scrollSyncHandler.removeCallbacks(syncChartRunnable);
            syncChartRunnable = null;
        }
        if (syncListRunnable != null) {
            scrollSyncHandler.removeCallbacks(syncListRunnable);
            syncListRunnable = null;
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        cancelScrollSync();
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mContext = null;
        cancelScrollSync();
        scrollSyncHandler.removeCallbacksAndMessages(null); // Clear all pending runnables
    }
}
