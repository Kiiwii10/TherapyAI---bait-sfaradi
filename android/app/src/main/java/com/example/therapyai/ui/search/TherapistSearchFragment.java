package com.example.therapyai.ui.search;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.example.therapyai.R;
import com.example.therapyai.data.local.models.Profile;
import com.example.therapyai.data.local.models.SessionSummary;
import com.example.therapyai.data.local.models.SearchItem;
import com.example.therapyai.data.repository.SearchRepository;
import com.example.therapyai.ui.adapters.SearchAdapter;

import java.util.ArrayList;
import java.util.List;

public class TherapistSearchFragment extends Fragment {

    private static final String TAG = "TherapistSearchFrag";

    private SearchRepository searchRepository;
    private RecyclerView rvSearchResults;
    private SearchAdapter adapter;
    private ProgressBar progressBar;
    private SearchView searchView;
    private SwipeRefreshLayout swipeRefreshLayout;

    private final List<Profile> profilesList = new ArrayList<>();
    private final List<SessionSummary> sessionsList = new ArrayList<>();

    private boolean profilesLoading = false;
    private boolean sessionsLoading = false;
    
    private String currentQuery = ""; // Store the current search query for refresh


    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        searchRepository = SearchRepository.getInstance();

    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_therapist_search, container, false);

        rvSearchResults = view.findViewById(R.id.rvSearchResults);
        progressBar = view.findViewById(R.id.progressBar);
        searchView = view.findViewById(R.id.searchView);
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);

        setupSearchView();
        setupRecyclerView();
        setupSwipeRefresh();

        return view;
    }

    private void setupSearchView() {
        searchView.setIconifiedByDefault(false);
        searchView.setQueryHint(getString(R.string.search_hint));
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                currentQuery = query; // Store query for refresh
                performSearch(query);
                hideKeyboard();
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                return false;
            }
        });
    }

    private void setupRecyclerView() {
        adapter = new SearchAdapter(requireContext(), new SearchAdapter.OnItemClickListener() {
            @Override
            public void onProfileClick(Profile profile) {
                Log.d(TAG, "Profile clicked: " + profile.getId());
                Intent intent = new Intent(requireContext(), ProfileActivity.class);
                intent.putExtra(ProfileActivity.EXTRA_PROFILE_ID, profile.getId());
                startActivity(intent);
            }

            @Override
            public void onSessionClick(SessionSummary sessionSummary) {
                Log.d(TAG, "Session clicked: " + sessionSummary.getId());
                Intent intent = new Intent(requireContext(), DataActivity.class);
                intent.putExtra(DataActivity.EXTRA_SESSION_ID, sessionSummary.getId());
                intent.putExtra(DataActivity.EXTRA_PATIENT_ID, sessionSummary.getPatientId());
                intent.putExtra(DataActivity.EXTRA_THERAPIST_ID, sessionSummary.getTherapistId());
                startActivity(intent);
            }
        });

        rvSearchResults.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvSearchResults.setAdapter(adapter);

    }

    private void setupSwipeRefresh() {
        swipeRefreshLayout.setColorSchemeResources(
                com.google.android.material.R.color.design_default_color_primary,
                com.google.android.material.R.color.design_default_color_primary_dark,
                com.google.android.material.R.color.design_default_color_secondary
        );
        
        swipeRefreshLayout.setOnRefreshListener(() -> {
            Log.d(TAG, "Swipe refresh triggered");
            if (currentQuery != null && !currentQuery.trim().isEmpty()) {
                refreshSearch();
            } else {
                // No previous search to refresh
                swipeRefreshLayout.setRefreshing(false);
                Toast.makeText(requireContext(), R.string.search_refresh_no_query, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void refreshSearch() {
        Log.d(TAG, "Refreshing search for query: " + currentQuery);
        performSearch(currentQuery, true); // true indicates this is a refresh
    }

    private void performSearch(String query) {
        performSearch(query, false); // false indicates this is not a refresh
    }

    private void performSearch(String query, boolean isRefresh) {
        if (query == null || query.trim().isEmpty()) {
            Toast.makeText(requireContext(), R.string.search_query_empty, Toast.LENGTH_SHORT).show();
            if (isRefresh) {
                swipeRefreshLayout.setRefreshing(false);
            }
            return;
        }
        Log.d(TAG, "Performing search for query: " + query + " (refresh: " + isRefresh + ")");

        // Don't show main progress bar if this is a refresh (SwipeRefreshLayout shows its own indicator)
        if (!isRefresh) {
            progressBar.setVisibility(View.VISIBLE);
        }
        
        profilesLoading = true;
        sessionsLoading = true;

        profilesList.clear();
        sessionsList.clear();

        updateUnifiedList();

        searchRepository.performProfileSearch(query, new SearchRepository.ProfileSearchCallback() {
            @Override
            public void onProfilesFound(List<Profile> profiles) {
                if (!isAdded() || getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    profilesLoading = false;
                    Log.d(TAG, "Profiles found: " + profiles.size());
                    profilesList.addAll(profiles);
                    updateUnifiedList();
                });
            }

            @Override
            public void onError(String error) {
                if (!isAdded() || getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    profilesLoading = false;
                    Log.e(TAG, "Profile search error: " + error);
                    Toast.makeText(requireContext(), getString(R.string.search_error_profiles, error), Toast.LENGTH_SHORT).show();
                    updateUnifiedList();
                });
            }
        });

        searchRepository.performSessionSearch(query, new SearchRepository.SessionSearchCallback() {
            @Override
            public void onSessionsFound(List<SessionSummary> sessionSummaries) {
                if (!isAdded() || getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    sessionsLoading = false;
                    Log.d(TAG, "Sessions found: " + sessionSummaries.size());
                    sessionsList.addAll(sessionSummaries);
                    updateUnifiedList();
                });
            }

            @Override
            public void onError(String error) {
                if (!isAdded() || getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    sessionsLoading = false;
                    Log.e(TAG, "Session search error: " + error);
                    Toast.makeText(requireContext(), getString(R.string.search_error_sessions, error), Toast.LENGTH_SHORT).show();
                    updateUnifiedList();
                });
            }
        });
    }

    private void updateUnifiedList() {
        if (!isAdded()) return;

        List<SearchItem> newList = new ArrayList<>();

        newList.add(new SearchItem(SearchItem.TYPE_HEADER_PROFILES, getString(R.string.header_profiles), profilesLoading));

        if (!profilesLoading || !profilesList.isEmpty()) {
            for (Profile p : profilesList) {
                newList.add(new SearchItem(p));
            }
            if (!profilesLoading && profilesList.isEmpty()) {// we can place a placeholder here
            }
        }

        newList.add(new SearchItem(SearchItem.TYPE_HEADER_SESSIONS, getString(R.string.header_sessions), sessionsLoading));

        if (!sessionsLoading || !sessionsList.isEmpty()) {
            for (SessionSummary s : sessionsList) {
                newList.add(new SearchItem(s));
            }
            if (!sessionsLoading && sessionsList.isEmpty()) { // we can place a placeholder here
            }
        }


        adapter.setItems(newList);

        if (!profilesLoading && !sessionsLoading) {
            progressBar.setVisibility(View.GONE);
            // Stop refresh indicator if it's active
            if (swipeRefreshLayout.isRefreshing()) {
                swipeRefreshLayout.setRefreshing(false);
            }
        }
    }

    private void hideKeyboard() {
        if (getContext() == null || getActivity() == null) return;
        InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        View view = getActivity().getCurrentFocus();
        if (view == null) {
            view = new View(getContext());
        }
        if (imm != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
        searchView.clearFocus();
    }
}