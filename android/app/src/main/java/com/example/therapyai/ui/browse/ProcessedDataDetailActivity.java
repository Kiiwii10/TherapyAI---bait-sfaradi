package com.example.therapyai.ui.browse;

import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.widget.ViewPager2;

import com.example.therapyai.R;
import com.example.therapyai.data.repository.ProcessedDataRepository;
// Import new components
import com.example.therapyai.ui.adapters.TranscriptPagerAdapter;
import com.example.therapyai.ui.viewmodels.TranscriptDetailViewModel;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

public class ProcessedDataDetailActivity extends AppCompatActivity {

    public static final String EXTRA_DATA_ID = "data_id";

    private String dataId;
    private TranscriptDetailViewModel viewModel;

    private ProgressBar progressBar;
    private Button btnSendRevised;
    private ViewPager2 viewPager;
    private TabLayout tabLayout;
    private TranscriptPagerAdapter pagerAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_processed_data_detail);

        dataId = getIntent().getStringExtra(EXTRA_DATA_ID);
        if (dataId == null) {
            Toast.makeText(this, "Error: Missing data ID", Toast.LENGTH_LONG).show();
            Log.e("ProcessedDataDetail", "Data ID is null in Intent extras");
            finish();
            return;
        }

        viewModel = new ViewModelProvider(this).get(TranscriptDetailViewModel.class);

        progressBar = findViewById(R.id.progressBar);
        btnSendRevised = findViewById(R.id.btnSendRevised);
        viewPager = findViewById(R.id.viewPager);
        tabLayout = findViewById(R.id.tabLayout);
        Toolbar toolbar = findViewById(R.id.toolbar);

        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Review Transcript");
        }

        pagerAdapter = new TranscriptPagerAdapter(this);
        viewPager.setAdapter(pagerAdapter);

        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            switch (position) {
                case 0:
                    tab.setText("Details");
                    break;
                case 1:
                    tab.setText("Transcript");
                    break;
            }
        }).attach();


        viewModel.isLoading().observe(this, isLoading -> {
            progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
            btnSendRevised.setEnabled(!isLoading);
        });

        viewModel.getErrorState().observe(this, error -> {
            if (error != null && !error.isEmpty()) {
                Toast.makeText(this, "Error: " + error, Toast.LENGTH_LONG).show();
            }
        });

        viewModel.getTranscriptDetail().observe(this, detail -> {
            if (detail != null && getSupportActionBar() != null && detail.getPatientName() != null) {
                getSupportActionBar().setTitle("Review: " + detail.getPatientName());
            }
        });

        viewModel.loadData(dataId);
        btnSendRevised.setOnClickListener(v -> sendRevisedData());

        setupBackPressedHandler();
    }

    private void setupBackPressedHandler() {
        OnBackPressedCallback callback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (Boolean.TRUE.equals(viewModel.hasUnsavedChanges().getValue())) {
                    showUnsavedChangesDialog("Go back");
                } else {
                    setEnabled(false);
                    ProcessedDataDetailActivity.super.onBackPressed();
                }
            }
        };
        getOnBackPressedDispatcher().addCallback(this, callback);
    }

    private void sendRevisedData() {
        viewModel.submitData(new ProcessedDataRepository.OperationCallback() {
            @Override
            public void onSuccess() {
                Toast.makeText(ProcessedDataDetailActivity.this, "Transcript submitted successfully!", Toast.LENGTH_SHORT).show();
                finish();
            }

            @Override
            public void onError(String errorMessage) {
                Toast.makeText(ProcessedDataDetailActivity.this, "Submission failed: " + errorMessage, Toast.LENGTH_LONG).show();
            }
        });
    }


    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            if (Boolean.TRUE.equals(viewModel.hasUnsavedChanges().getValue())) {
                showUnsavedChangesDialog("Go up");
            } else {
                finish();
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showUnsavedChangesDialog(String actionContext) {
        new AlertDialog.Builder(this)
                .setTitle("Discard Changes?")
                .setMessage("You have unsaved edits. If you " + actionContext + ", your changes will be lost. Are you sure?")
                .setPositiveButton("Discard Changes", (dialog, which) -> {
                    finish();
                })
                .setNegativeButton("Cancel", null)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }
}