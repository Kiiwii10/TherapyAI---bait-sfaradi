package com.example.therapyai.ui.search;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.example.therapyai.R;
import com.example.therapyai.data.local.SessionManager;
import com.example.therapyai.data.local.models.Profile;
import com.example.therapyai.data.local.models.SessionSummary;
import com.example.therapyai.data.local.models.SearchItem;
import com.example.therapyai.data.repository.SearchRepository;
import com.example.therapyai.ui.adapters.SearchAdapter;

import androidx.appcompat.widget.SearchView;
import android.view.inputmethod.InputMethodManager;

import java.util.Locale;
import java.util.ArrayList;
import java.util.List;

public class PatientSearchFragment extends Fragment {

    private static final String TAG = "PatientSearchFragment";

    private SearchRepository searchRepository;
    private RecyclerView rvSearchResults;
    private SearchAdapter adapter;
    private ProgressBar progressBar;
    private TextView tvEmptyState;
    private SearchView searchViewPatient;
    private SwipeRefreshLayout swipeRefreshLayout;

    private Profile patientProfile = null;
    private final List<SessionSummary> fullSessionsList = new ArrayList<>();
    private final List<SessionSummary> displayedSessionsList = new ArrayList<>();

    private boolean profileLoading = false;
    private boolean sessionsLoading = false;
    private String patientId;
    private String profileError = null;
    private String sessionsError = null;


    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        searchRepository = SearchRepository.getInstance();
        patientId = SessionManager.getInstance().getUserId(); // Get patient ID early

        if (patientId == null || patientId.isEmpty()) {
            Log.e(TAG, "Could not retrieve logged-in patient ID.");
            // Handle error state appropriately, maybe prevent fragment loading
        } else {
            Log.d(TAG,"Patient ID for fetching data: " + patientId);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // Use a layout that has RecyclerView, ProgressBar, TextView (EmptyState), and SearchView
        // Assuming fragment_patient_history has these with correct IDs
        View view = inflater.inflate(R.layout.fragment_patient_history, container, false);

        rvSearchResults = view.findViewById(R.id.rvSessionHistory); // Ensure ID matches layout
        progressBar = view.findViewById(R.id.progressBar);
        tvEmptyState = view.findViewById(R.id.tvEmptyState);
        searchViewPatient = view.findViewById(R.id.searchViewPatient); // Ensure ID matches layout
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);

        setupRecyclerView();
        setupSearchView();
        setupSwipeRefresh();

        // Fetch data only if patientId is valid
        if (patientId != null && !patientId.isEmpty()) {
            fetchPatientData();
        } else {
            showErrorState(getString(R.string.error_cannot_load_history_no_id));
        }

        return view;
    }

    private void setupSearchView() {
        searchViewPatient.setIconifiedByDefault(false);
        searchViewPatient.setQueryHint(getString(R.string.search_session_history_hint));
        searchViewPatient.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                filterSessions(query); // Filter only sessions
                hideKeyboard();
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                filterSessions(newText); // Filter only sessions
                return true;
            }
        });
    }

    private void setupRecyclerView() {
        adapter = new SearchAdapter(requireContext(), new SearchAdapter.OnItemClickListener() {
            @Override
            public void onProfileClick(Profile profile) {
                // Handle click on the patient's own profile
                // Optional: Navigate to a dedicated profile detail screen or show a toast
                Log.d(TAG, "Patient profile clicked: " + profile.getId());
                Intent intent = new Intent(requireContext(), ProfileActivity.class);
                intent.putExtra(ProfileActivity.EXTRA_PROFILE_ID, profile.getId());
                // You might want a flag or different logic in ProfileActivity
                // if it's the *logged-in* patient's profile vs. one found in therapist search.
                startActivity(intent);
                // Toast.makeText(requireContext(), "Showing profile: " + profile.getFullName(), Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onSessionClick(SessionSummary sessionSummary) {
                // Handle click on a session summary
                Log.d(TAG, "Session clicked: " + sessionSummary.getId());
                Intent intent = new Intent(requireContext(), DataActivity.class);
                intent.putExtra(DataActivity.EXTRA_SESSION_ID, sessionSummary.getId());
                intent.putExtra(DataActivity.EXTRA_PATIENT_ID, patientId); // Pass patient ID
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
            Log.d(TAG, "Swipe refresh triggered for patient data");
            refreshPatientData();
        });
    }

    private void refreshPatientData() {
        Log.d(TAG, "Refreshing patient data");
        fetchPatientData(true); // true indicates this is a refresh
    }

    private void fetchPatientData() {
        fetchPatientData(false); // false indicates this is not a refresh
    }

    private void fetchPatientData(boolean isRefresh) {
        if (profileLoading || sessionsLoading || patientId == null || patientId.isEmpty()) {
            if (isRefresh) {
                swipeRefreshLayout.setRefreshing(false);
            }
            return;
        }

        Log.d(TAG, "Fetching profile and sessions for patient ID: " + patientId + " (refresh: " + isRefresh + ")");
        profileLoading = true;
        sessionsLoading = true;
        profileError = null;
        sessionsError = null;
        
        // Don't show main progress bar if this is a refresh (SwipeRefreshLayout shows its own indicator)
        if (!isRefresh) {
            progressBar.setVisibility(View.VISIBLE);
            tvEmptyState.setVisibility(View.GONE);
            rvSearchResults.setVisibility(View.GONE);
        }
        
        patientProfile = null; // Clear previous data
        fullSessionsList.clear();
        displayedSessionsList.clear();
        updateSearchResultsUI(); // Update UI to show loading state

        // Fetch Profile
        searchRepository.getPatientProfile(new SearchRepository.ProfileCallback() {
            @Override
            public void onProfileFound(Profile profile) {
                if (!isAdded() || getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    profileLoading = false;
                    patientProfile = profile;
                    Log.d(TAG, "Patient profile fetched successfully.");
                    updateSearchResultsUI();
                    checkLoadingComplete();
                });
            }

            @Override
            public void onError(String error) {
                if (!isAdded() || getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    profileLoading = false;
                    profileError = error; // Store error
                    Log.e(TAG, "Error fetching patient profile: " + error);
                    // Don't show toast immediately, wait for both calls
                    updateSearchResultsUI();
                    checkLoadingComplete();
                });
            }
        });

        // Fetch Sessions
        searchRepository.getPatientSessions(new SearchRepository.SessionSearchCallback() {
            @Override
            public void onSessionsFound(List<SessionSummary> sessionSummaries) {
                if (!isAdded() || getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    sessionsLoading = false;
                    Log.d(TAG, "Patient sessions found: " + sessionSummaries.size());
                    fullSessionsList.clear();
                    fullSessionsList.addAll(sessionSummaries);
                    // Apply initial filter (usually empty query)
                    filterSessions(searchViewPatient.getQuery().toString());
                    updateSearchResultsUI();
                    checkLoadingComplete();
                });
            }

            @Override
            public void onError(String error) {
                if (!isAdded() || getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    sessionsLoading = false;
                    sessionsError = error; // Store error
                    Log.e(TAG, "Error fetching patient sessions: " + error);
                    // Don't show toast immediately, wait for both calls
                    updateSearchResultsUI();
                    checkLoadingComplete();
                });
            }
        });
    }

    private void checkLoadingComplete() {
        if (!profileLoading && !sessionsLoading) {
            progressBar.setVisibility(View.GONE);
            // Stop refresh indicator if it's active
            if (swipeRefreshLayout.isRefreshing()) {
                swipeRefreshLayout.setRefreshing(false);
            }
            
            // Show errors if any occurred
            if (profileError != null) {
                Toast.makeText(requireContext(), getString(R.string.error_fetching_profile, profileError), Toast.LENGTH_LONG).show();
            }
            if (sessionsError != null) {
                Toast.makeText(requireContext(), getString(R.string.error_fetching_sessions, sessionsError), Toast.LENGTH_LONG).show();
            }

            // Update empty/error state based on combined results
            if (patientProfile == null && fullSessionsList.isEmpty()) {
                // Determine if it's an error or just no data
                String msg = (profileError != null || sessionsError != null) ?
                        getString(R.string.error_loading_patient_data) : // Generic error if specifics failed
                        getString(R.string.patient_no_data_found); // No profile AND no sessions
                showErrorState(msg); // Use showErrorState for consistency
            } else if (fullSessionsList.isEmpty() && !searchViewPatient.getQuery().toString().isEmpty()) {
                showEmptyState(getString(R.string.patient_no_sessions_match_filter)); // Keep this specific message
            }
            else {
                rvSearchResults.setVisibility(View.VISIBLE);
                tvEmptyState.setVisibility(View.GONE);
            }
        }
    }


    // Filters ONLY the session list
    private void filterSessions(String query) {
        displayedSessionsList.clear();
        String lowerCaseQuery = query.toLowerCase(Locale.getDefault());

        if (lowerCaseQuery.isEmpty()) {
            displayedSessionsList.addAll(fullSessionsList);
        } else {
            for (SessionSummary summary : fullSessionsList) {
                boolean titleMatch = summary.getTitle() != null && summary.getTitle().toLowerCase(Locale.getDefault()).contains(lowerCaseQuery);
                boolean descMatch = summary.getDescriptionPreview() != null && summary.getDescriptionPreview().toLowerCase(Locale.getDefault()).contains(lowerCaseQuery);
                 boolean dateMatch = summary.getDate() != null && summary.getDate().contains(lowerCaseQuery);
                 boolean therapistMatch = summary.getTherapistName() != null && summary.getTherapistName().toLowerCase(Locale.getDefault()).contains(lowerCaseQuery);

                if (titleMatch || descMatch || dateMatch || therapistMatch) {
                    displayedSessionsList.add(summary);
                }
            }
        }
        updateSearchResultsUI();

        if (!profileLoading && !sessionsLoading) {
            if (patientProfile == null && fullSessionsList.isEmpty()) {
                // Already handled by checkLoadingComplete
            } else if (displayedSessionsList.isEmpty() && !fullSessionsList.isEmpty()) {
                showEmptyState(getString(R.string.patient_no_sessions_match_filter));
            } else if (!displayedSessionsList.isEmpty()) {
                rvSearchResults.setVisibility(View.VISIBLE);
                tvEmptyState.setVisibility(View.GONE);
            }
        }
    }

    private void updateSearchResultsUI() {
        if (!isAdded()) return;

        List<SearchItem> newList = new ArrayList<>();

        if (patientProfile != null) {
             newList.add(new SearchItem(SearchItem.TYPE_HEADER_PROFILES, getString(R.string.my_profile_header), profileLoading));
            newList.add(new SearchItem(patientProfile));
        } else if (profileLoading) {
            newList.add(new SearchItem(SearchItem.TYPE_HEADER_PROFILES, getString(R.string.my_profile_header), true));
        } else if (profileError != null) {
            // Optional: Show an error indicator for the profile section
            // You could add a specific error item type or just rely on the toast
        }

        if (sessionsLoading || !fullSessionsList.isEmpty() || sessionsError != null) {
            newList.add(new SearchItem(
                    SearchItem.TYPE_HEADER_SESSIONS,
                    getString(R.string.session_history_title),
                    sessionsLoading
            ));
        }


        if (!sessionsLoading) {
            for (SessionSummary s : displayedSessionsList) {
                newList.add(new SearchItem(s));
            }
        }

        adapter.setItems(newList);

        progressBar.setVisibility(profileLoading || sessionsLoading ? View.VISIBLE : View.GONE);

        if (profileLoading || sessionsLoading) {
            rvSearchResults.setVisibility(View.GONE);
            tvEmptyState.setVisibility(View.GONE);
        }

    }

    private void showErrorState(String errorMessage) {
        profileLoading = false;
        sessionsLoading = false;
        progressBar.setVisibility(View.GONE);
        rvSearchResults.setVisibility(View.GONE);
        tvEmptyState.setVisibility(View.VISIBLE);
        tvEmptyState.setText(errorMessage);
        Log.e(TAG, "Displaying error state: " + errorMessage);
        patientProfile = null;
        fullSessionsList.clear();
        displayedSessionsList.clear();
        adapter.setItems(new ArrayList<>());
    }

    private void showEmptyState(String message) {
        progressBar.setVisibility(View.GONE);
        if (patientProfile == null && displayedSessionsList.isEmpty()){
            rvSearchResults.setVisibility(View.GONE);
            tvEmptyState.setVisibility(View.VISIBLE);
            tvEmptyState.setText(message);
        } else if (!displayedSessionsList.isEmpty() || patientProfile != null) {
            // We have something to show (profile or filtered sessions)
            rvSearchResults.setVisibility(View.VISIBLE);
            tvEmptyState.setVisibility(View.GONE);
            if (displayedSessionsList.isEmpty() && !fullSessionsList.isEmpty() && patientProfile != null) {

                if (displayedSessionsList.isEmpty() && !fullSessionsList.isEmpty() && queryNotEmpty()){
                    tvEmptyState.setVisibility(View.VISIBLE);
                    tvEmptyState.setText(getString(R.string.patient_no_sessions_match_filter));
                }
            }
        } else {
            rvSearchResults.setVisibility(View.GONE);
            tvEmptyState.setVisibility(View.VISIBLE);
            tvEmptyState.setText(message);
        }


    }
    private boolean queryNotEmpty() {
        return searchViewPatient != null && searchViewPatient.getQuery() != null && !searchViewPatient.getQuery().toString().isEmpty();
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
        if (searchViewPatient != null) {
            searchViewPatient.clearFocus();
        }
    }


}