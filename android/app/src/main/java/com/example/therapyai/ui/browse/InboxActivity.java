package com.example.therapyai.ui.browse;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.therapyai.R;
import com.example.therapyai.data.local.SessionManager;
import com.example.therapyai.data.local.models.PendingInboxItem;
import com.example.therapyai.ui.adapters.ProcessedDataAdapter;
import com.example.therapyai.ui.viewmodels.InboxViewModel;

import java.util.ArrayList;

public class InboxActivity extends AppCompatActivity {
    private InboxViewModel viewModel;
    private ProcessedDataAdapter adapter;
    private RecyclerView recyclerView;
    private SwipeRefreshLayout swipeRefreshLayout;
    private TextView emptyStateTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_inbox);

        SessionManager.UserType userType = SessionManager.getInstance().getUserType();
        if (userType != SessionManager.UserType.THERAPIST) {
            Toast.makeText(this, "Access Denied.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        recyclerView = findViewById(R.id.recyclerView);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        emptyStateTextView = findViewById(R.id.emptyStateTextView);
        Toolbar toolbar = findViewById(R.id.toolbar);

        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Inbox");
        }

        viewModel = new ViewModelProvider(this).get(InboxViewModel.class);
        adapter = new ProcessedDataAdapter(new ArrayList<>(), this::onItemClick);

        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(adapter);

        DividerItemDecoration dividerItemDecoration = new DividerItemDecoration(
                recyclerView.getContext(),
                layoutManager.getOrientation() // Use the layout manager's orientation
        );
        recyclerView.addItemDecoration(dividerItemDecoration);




        swipeRefreshLayout.setOnRefreshListener(() -> {
            viewModel.refreshData();
        });

        viewModel.getPendingItems().observe(this, pendingItems -> {

            if (pendingItems != null && !pendingItems.isEmpty()) {
                adapter.updateData(pendingItems);
                recyclerView.setVisibility(View.VISIBLE);
                emptyStateTextView.setVisibility(View.GONE);
            } else {
                if (!Boolean.TRUE.equals(viewModel.isLoading().getValue())) {
                    adapter.updateData(new ArrayList<>());
                    recyclerView.setVisibility(View.GONE);
                    emptyStateTextView.setVisibility(View.VISIBLE);
                    emptyStateTextView.setText("No items require review.");
                }
            }
        });

        viewModel.isLoading().observe(this, isLoading -> {
            swipeRefreshLayout.setRefreshing(isLoading);
        });

        viewModel.getErrorState().observe(this, error -> {
            if (error != null && !error.isEmpty()) {
                Toast.makeText(this, error, Toast.LENGTH_LONG).show();
                if (adapter.getItemCount() == 0) {
                    recyclerView.setVisibility(View.GONE);
                    emptyStateTextView.setVisibility(View.VISIBLE);
                    emptyStateTextView.setText("Error: " + error);
                }
            }
        });

    }

    private void onItemClick(PendingInboxItem data) {
        Intent intent = new Intent(this, ProcessedDataDetailActivity.class);
        intent.putExtra(ProcessedDataDetailActivity.EXTRA_DATA_ID, data.getId());
        startActivity(intent);
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