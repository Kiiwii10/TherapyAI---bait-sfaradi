package com.example.therapyai.ui.search;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.example.therapyai.R;
import com.example.therapyai.data.local.models.FinalSessionDetail;
import com.example.therapyai.data.local.models.FinalTranscriptEntry;
import com.example.therapyai.ui.viewmodels.DataViewModel;
import com.example.therapyai.util.SentimentChartHelper;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.listener.OnChartValueSelectedListener;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.imageview.ShapeableImageView;

import java.util.List;

public class SessionOverviewFragment extends Fragment {    
    private static final String TAG = "SessionOverviewFrag";
    private static final float OVERVIEW_VISIBLE_GROUPS = 10f;
    private DataViewModel dataViewModel;    private TextView tvPatientInfo, tvTherapistInfo, tvSessionDate, tvSummary, tvGeneralNotes;
    private MaterialCardView cardPatientInfo;
    private BarChart barChart;
    private Context mContext;
    private String patientId; // Store patient ID for profile navigation

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        mContext = context;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        dataViewModel = new ViewModelProvider(requireActivity()).get(DataViewModel.class);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_session_overview, container, false);
        bindViews(view);
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setupChart();
        observeViewModel();
    }    private void bindViews(View view) {
        tvPatientInfo = view.findViewById(R.id.tvOverviewPatientInfo);
        tvTherapistInfo = view.findViewById(R.id.tvOverviewTherapistInfo);
        tvSessionDate = view.findViewById(R.id.tvOverviewSessionDate);
        tvSummary = view.findViewById(R.id.tvOverviewSummary);
        tvGeneralNotes = view.findViewById(R.id.tvOverviewGeneralNotes);
        barChart = view.findViewById(R.id.barChartSentiment);
        cardPatientInfo = view.findViewById(R.id.cardPatientInfo);
        
        // Set click listener for patient card to navigate to patient's profile
        cardPatientInfo.setOnClickListener(v -> {
            if (patientId != null && !patientId.isEmpty()) {
                Intent intent = new Intent(getActivity(), ProfileActivity.class);
                intent.putExtra(ProfileActivity.EXTRA_PROFILE_ID, patientId);
                startActivity(intent);
            }
        });
    }

    private void setupChart() {
        if (mContext == null) {
            Log.e(TAG, "Context is null during setupChart!");
            return;
        }
        SentimentChartHelper.setupSentimentBarChart(barChart, mContext, false, true, true);



        barChart.setOnChartValueSelectedListener(new OnChartValueSelectedListener() {
            @Override
            public void onValueSelected(Entry e, Highlight h) {
                int index = (int) e.getX();
                Log.d(TAG, "Overview chart bar selected at index: " + index);
                dataViewModel.requestNavigationAndHighlight(index);
            }
            @Override public void onNothingSelected() { /* Do nothing */ }
        });

        ViewGroup.LayoutParams params = barChart.getLayoutParams();
        if (params != null) {
            int heightInDp = 240;
            float density = getResources().getDisplayMetrics().density;
            params.height = (int)(heightInDp * density);
            barChart.setLayoutParams(params);
        }

        int bottomMarginDp = 16;
        if (params instanceof ViewGroup.MarginLayoutParams) {
            ((ViewGroup.MarginLayoutParams) params).bottomMargin =
                    (int)(bottomMarginDp * getResources().getDisplayMetrics().density);
            barChart.setLayoutParams(params);
        }
    }

    private void observeViewModel() {
        dataViewModel.getSessionDetail().observe(getViewLifecycleOwner(), finalSessionDetail -> {
            if (finalSessionDetail != null) {
                Log.d(TAG, "Updating overview UI and chart with session data.");
                updateUI(finalSessionDetail);
                List<FinalTranscriptEntry> entries = finalSessionDetail.getTranscriptEntries();
                if(mContext != null) {
                    SentimentChartHelper.updateSentimentBarChartData(barChart, entries, mContext);
                }



                if (barChart != null && entries != null && !entries.isEmpty()) {
                    barChart.post(() -> {
                        if(barChart != null) {
                            SentimentChartHelper.applyInitialZoom(barChart, OVERVIEW_VISIBLE_GROUPS);
                        }
                    });
                } else if (barChart != null) {
                    barChart.fitScreen();
                }
            } else {
                Log.d(TAG, "SessionDetail is null in observer. Clearing UI and chart.");
                clearUI();
                if(mContext != null && barChart != null) {
                    SentimentChartHelper.updateSentimentBarChartData(barChart, null, mContext);
                    barChart.fitScreen();
                }
            }
        });
    }    private void updateUI(FinalSessionDetail session) {
        String unknown = getString(R.string.unknown_value);
        String na = getString(R.string.not_available_abbr);

        // Store the patient ID for navigation
        patientId = session.getPatientId();        // Update text views with patient and therapist info
        String patientInfo = String.format("Name: %s\nID: %s\nEmail: %s",
                session.getPatientName() != null ? session.getPatientName() : unknown,
                session.getPatientId() != null ? session.getPatientId() : unknown,
                session.getPatientEmail() != null ? session.getPatientEmail() : unknown);
        tvPatientInfo.setText(patientInfo);

        String therapistInfo = String.format("Name: %s\nEmail: %s",
                session.getTherapistName() != null ? session.getTherapistName() : unknown,
                session.getTherapistEmail() != null ? session.getTherapistEmail() : unknown);
        tvTherapistInfo.setText(therapistInfo);

        tvSessionDate.setText(String.format("Date: %s",
                session.getTreatmentDate() != null ? session.getTreatmentDate() : na));
        tvSummary.setText(session.getSummary() != null ? session.getSummary() : getString(R.string.overview_no_summary));

        if (session.getGeneralNotes() != null && !session.getGeneralNotes().isEmpty()) {
            StringBuilder notesBuilder = new StringBuilder();
            for (String note : session.getGeneralNotes()) {
                notesBuilder.append("• ").append(note).append("\n");
            }
            tvGeneralNotes.setText(notesBuilder.toString().trim());
            tvGeneralNotes.setVisibility(View.VISIBLE);
        } else {
            tvGeneralNotes.setText(R.string.overview_no_general_notes);
        }
    }    private void clearUI() {
        String loading = getString(R.string.loading);
        patientId = null;        String loadingPatientInfo = String.format("Name: %s\nID: %s\nEmail: %s", loading, loading, loading);
        tvPatientInfo.setText(loadingPatientInfo);
        
        String loadingTherapistInfo = String.format("Name: %s\nEmail: %s", loading, loading);
        tvTherapistInfo.setText(loadingTherapistInfo);
        
        tvSessionDate.setText(String.format("Date: %s", loading));
        tvSummary.setText(loading);
        tvGeneralNotes.setText("");
        tvGeneralNotes.setVisibility(View.GONE);
    }



    @Override
    public void onDetach() {
        super.onDetach();
        mContext = null;

    }
}
