package com.example.therapyai.ui.sessions.session;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;

import com.example.therapyai.R;
import com.example.therapyai.ui.viewmodels.SessionViewModel; // <<< Use SessionViewModel

public class AddSummaryFragment extends Fragment {

    private static final String TAG = "AddSummaryFragment";
    private SessionViewModel viewModel;
    private NavController navController;
    private EditText etSummary;
    private Button btnSaveSummary;

    public static AddSummaryFragment newInstance() {
        return new AddSummaryFragment();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");
        viewModel = new ViewModelProvider(requireActivity()).get(SessionViewModel.class);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        Log.d(TAG, "onCreateView");
        View view = inflater.inflate(R.layout.fragment_add_summary, container, false);
        etSummary = view.findViewById(R.id.etAddSummary);
        btnSaveSummary = view.findViewById(R.id.btnSaveSummary);
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Log.d(TAG, "onViewCreated");
        navController = NavHostFragment.findNavController(this);

        viewModel.summaryNote.observe(getViewLifecycleOwner(), summaryNote -> {
            if (summaryNote != null && summaryNote.getContent() != null && etSummary.getText().toString().isEmpty()) {
                Log.d(TAG, "Pre-filling summary EditText from ViewModel.");
                etSummary.setText(summaryNote.getContent());
            } else if (summaryNote == null) {
                Log.d(TAG, "Summary note is null in ViewModel.");
            }
        });

        btnSaveSummary.setOnClickListener(v -> {
            String summaryText = etSummary.getText().toString().trim();
            if (!summaryText.isEmpty()) {
                Log.d(TAG, "Save Summary button clicked. Updating ViewModel.");
                viewModel.updateSummary(summaryText);

                Log.d(TAG, "Navigating to ReviewSubmitFragment.");
                navController.navigate(R.id.action_addSummaryFragment_to_reviewSubmitFragment);

            } else {
                etSummary.setError(getString(R.string.error_summary_cannot_be_empty));
                Toast.makeText(getContext(), R.string.error_summary_cannot_be_empty, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        Log.d(TAG, "onDestroyView");
        // Release view references
        etSummary = null;
        btnSaveSummary = null;
    }
}