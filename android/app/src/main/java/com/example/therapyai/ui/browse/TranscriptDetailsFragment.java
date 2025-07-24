package com.example.therapyai.ui.browse;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.therapyai.R;
import com.example.therapyai.ui.viewmodels.TranscriptDetailViewModel;

public class TranscriptDetailsFragment extends Fragment {

    private TranscriptDetailViewModel viewModel;
    private TextView tvPatientName, tvSessionDate, tvSummary;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(TranscriptDetailViewModel.class);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_transcript_details, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        tvPatientName = view.findViewById(R.id.tvPatientName);
        tvSessionDate = view.findViewById(R.id.tvSessionDate);
        tvSummary = view.findViewById(R.id.tvSummary);

        viewModel.getTranscriptDetail().observe(getViewLifecycleOwner(), detail -> {
            if (detail != null) {
                tvPatientName.setText(detail.getPatientName() != null ? detail.getPatientName() : "N/A");
                tvSessionDate.setText(detail.getSessionDate() != null ? detail.getSessionDate() : "N/A");
                tvSummary.setText(detail.getSummary() != null ? detail.getSummary() : "No summary available.");
            } else {
                tvPatientName.setText("Loading...");
                tvSessionDate.setText("");
                tvSummary.setText("");
            }
        });
    }
}