package com.example.therapyai.ui.sessions.session;

import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.Toast;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar; // Keep if you use it for other things
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.NavDestination;
import androidx.navigation.fragment.NavHostFragment;

import com.example.therapyai.BaseSessionActivity; // Assuming this exists and is set up
import com.example.therapyai.R;
import com.example.therapyai.data.local.EphemeralPrefs;
import com.example.therapyai.data.local.SessionManager;
import com.example.therapyai.data.local.models.CardItem;
import com.example.therapyai.data.local.models.NoteCard;
import com.example.therapyai.data.local.models.Profile;
import com.example.therapyai.data.local.models.SessionSummary;
import com.example.therapyai.data.remote.models.SessionSubmissionResponse;
import com.example.therapyai.data.repository.RecordingRepository;
import com.example.therapyai.data.repository.SearchRepository;
import com.example.therapyai.ui.viewmodels.SessionViewModel;
import com.example.therapyai.util.AESUtil;
import com.example.therapyai.util.Event; // Make sure this Event class is correct
import com.example.therapyai.util.ProfilePictureUtil;
import com.example.therapyai.util.SentimentChartHelper;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.imageview.ShapeableImageView;
import com.github.mikephil.charting.charts.BarChart;

import java.io.File;
import java.util.List;

public class SessionHostActivity extends BaseSessionActivity {

    private static final String TAG = "SessionHostActivity";
    public static final String EXTRA_CARD_ITEM = "extra_card_item_session";
    private static final int REQUEST_CODE_CONFIRM_DEVICE_CREDENTIALS = 123;

    private SessionViewModel viewModel;
    private NavController navController;
    private ProgressBar progressBar;

    private MaterialButton btnPatientDetails;
    private MaterialButton btnSessionNotes;

    private SessionViewModel.AuthReason pendingAuthReason = null; // Store reason for auth request
    private SearchRepository searchRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_session_host);

        CardItem initialCard = null;
        if (getIntent().hasExtra(EXTRA_CARD_ITEM)) {
            initialCard = (CardItem) getIntent().getSerializableExtra(EXTRA_CARD_ITEM);
        }

        if (initialCard == null) {
            Log.e(TAG, "Critical Error: No CardItem provided to start session. Cannot proceed.");
            Toast.makeText(this, "Error: Session data missing. Cannot start.", Toast.LENGTH_LONG).show();
            finish(); // Exit activity if essential data is missing
            return;
        }

        viewModel = new ViewModelProvider(this).get(SessionViewModel.class);
        searchRepository = SearchRepository.getInstance();
        // Set initial card only if ViewModel doesn't have one (e.g. on fresh start, not config change)
        if (viewModel.selectedCard.getValue() == null) {
            viewModel.setSelectedCard(initialCard);
        }


        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment_session);
        if (navHostFragment == null) {
            Log.e(TAG, "Critical Error: NavHostFragment not found.");
            Toast.makeText(this, "Error: Navigation setup failed.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        navController = navHostFragment.getNavController();

        // Pass initialCard to the start destination of the graph if needed by the fragment
        // The graph's start destination should handle this argument.
        Bundle startArgs = new Bundle();
        startArgs.putSerializable("selectedCardItem", initialCard); // Ensure key matches fragment arg
        // Check if graph is already set to avoid issues on config changes
        if (navController.getCurrentDestination() == null) {
            navController.setGraph(R.navigation.session_nav_graph, startArgs);
        }


        Toolbar toolbar = findViewById(R.id.sessionToolbar);
        // setSupportActionBar(toolbar); // If you want to use it as ActionBar

        btnPatientDetails = toolbar.findViewById(R.id.btnPatientDetails);
        btnSessionNotes = toolbar.findViewById(R.id.btnSessionNotes);
        progressBar = findViewById(R.id.activityProgressBar_session);

        btnPatientDetails.setOnClickListener(v -> showPatientDetailsDialog());
        btnSessionNotes.setOnClickListener(v -> showSessionNotesDialog());

        setupViewModelObservers(); // Moved after ViewModel init
        setupNavControllerListener();
        setupBackPressHandler();

        Log.d(TAG, "onCreate completed. Initial card: " + initialCard.getTitle());
    }

    private void setupViewModelObservers() {
        viewModel.selectedCard.observe(this, cardItem -> {
            if (cardItem == null) return;
            Log.d(TAG,"Selected Card Observer (Activity): " + cardItem.getTitle());
            boolean hasNotes = cardItem.getSessionNotes() != null && !cardItem.getSessionNotes().trim().isEmpty();
            updateNotesButtonVisibility(hasNotes);
            // Update toolbar title or other UI elements if they depend on the selected card
            // getSupportActionBar().setTitle(cardItem.getTitle()); // Example
        });

        viewModel.patientData.observe(this, patientData -> {
            boolean hasPatientData = patientData != null && !patientData.trim().isEmpty();
            updatePatientDetailsButtonVisibility(hasPatientData);
            Log.d(TAG, "Patient Data Observer (Activity): " + (hasPatientData ? "Data available" : "No data"));
        });

        viewModel.isLoading.observe(this, isLoading -> {
            if (progressBar != null) {
                progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
            }
            // Optionally disable/enable other interactive elements during loading
        });

        viewModel.userAuthRequired.observe(this, new Event.EventObserver<>(authReason -> {
            Log.i(TAG, "User authentication required for: " + authReason);
            this.pendingAuthReason = authReason;
            KeyguardManager keyguardManager = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
            if (keyguardManager != null && keyguardManager.isKeyguardSecure()) {
                Intent intent = keyguardManager.createConfirmDeviceCredentialIntent(
                        getString(R.string.auth_dialog_title_secure_action), // More generic title
                        getString(R.string.auth_dialog_description_secure_action)
                );
                if (intent != null) {
                    try {
                        startActivityForResult(intent, REQUEST_CODE_CONFIRM_DEVICE_CREDENTIALS);
                    } catch (Exception e) {
                        Log.e(TAG, "Could not launch confirm device credential intent", e);
                        Toast.makeText(this, R.string.error_launching_auth_intent, Toast.LENGTH_LONG).show();
                        viewModel.setLoading(false); // Ensure loading is reset
                        this.pendingAuthReason = null;
                    }
                } else {
                    // This case means device is secure, but system couldn't create the intent.
                    // This is rare but could happen (e.g., no screen lock actually set despite being "secure").
                    Log.w(TAG, "Device is secure, but createConfirmDeviceCredentialIntent returned null.");
                    Toast.makeText(this, R.string.error_auth_intent_failed_os_issue, Toast.LENGTH_LONG).show();
                    viewModel.setLoading(false);
                    this.pendingAuthReason = null;
                }
            } else {
                // Device is not secured with PIN/Pattern/Password. Keystore keys requiring auth cannot be used.
                Log.w(TAG, "Keyguard is not secure. Cannot enforce Keystore key user authentication.");
                Toast.makeText(this, R.string.error_device_not_secure_for_auth, Toast.LENGTH_LONG).show();
                viewModel.setLoading(false); // Reset loading
                // The operation that required auth (KEK access) will fail.
                // For START_RECORDING, user won't be able to start.
                // For SUBMIT_SESSION, upload will fail.
                // This is a critical state; the app's security relies on this.
                // You might consider forcing logout or disabling features if device isn't secure.
                // For now, the specific operation just won't proceed.
                this.pendingAuthReason = null;
            }
        }));

        viewModel.errorMessage.observe(this, new Event.EventObserver<>(message -> {
            Log.w(TAG, "Displaying error message from ViewModel: " + message);
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            // Ensure loading indicator is turned off on error
            if (Boolean.TRUE.equals(viewModel.isLoading.getValue())) {
                viewModel.setLoading(false);
            }
        }));

        viewModel.triggerSubmit.observe(this, new Event.EventObserver<>(unit -> {
            Log.d(TAG, "Submit event observed. ViewModel isLoading: " + viewModel.isLoading.getValue());
            // isLoading should have been set to true by requestSubmit() in ViewModel
            uploadDataBasedOnSessionType();
        }));

        viewModel.triggerDelete.observe(this, new Event.EventObserver<>(unit -> {
            Log.d(TAG, "Delete event observed. ViewModel isLoading: " + viewModel.isLoading.getValue());
            showDeleteConfirmationDialog();
        }));

        viewModel.navigationCommand.observe(this, new Event.EventObserver<>(command -> {
            if (command instanceof SessionViewModel.NavigationCommand.ToDirection) {
                navController.navigate(((SessionViewModel.NavigationCommand.ToDirection) command).actionId);
            } else if (command instanceof SessionViewModel.NavigationCommand.Back) {
                navController.popBackStack();
            }
        }));
    }

    private void setupNavControllerListener() {
        navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
            Log.d(TAG, "Navigated to: " + destination.getLabel() + " (ID: " + destination.getId() + ")");
            updateToolbarForDestination(destination);
        });
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_CONFIRM_DEVICE_CREDENTIALS) {
            SessionViewModel.AuthReason reason = pendingAuthReason; // Capture before clearing
            pendingAuthReason = null; // Clear pending reason

            if (resultCode == RESULT_OK) {
                Log.i(TAG, "Device credentials confirmed successfully for reason: " + reason);
                if (reason == SessionViewModel.AuthReason.START_RECORDING) {
                    // User needs to tap "Start Recording" again. Fragment UI should be ready.
                    Toast.makeText(this, R.string.toast_auth_success_try_again_start, Toast.LENGTH_LONG).show();
                    // ViewModel's isLoading should be false already from requestUserAuthentication
                } else if (reason == SessionViewModel.AuthReason.SUBMIT_SESSION) {
                    Log.d(TAG, "Retrying session submission after successful auth.");
                    viewModel.setLoading(true); // Set loading before retrying submit
                    uploadDataBasedOnSessionType(); // Retry the upload
                }
            } else {
                Log.w(TAG, "Device credentials confirmation failed or was cancelled for reason: " + reason);
                Toast.makeText(this, R.string.toast_auth_failed_action_cancelled, Toast.LENGTH_LONG).show();
                viewModel.setLoading(false); // Ensure loading is off if auth failed
                // If auth failed for submission, the submit process is halted.
                // If for start recording, user can't start.
            }
        }
    }

    private void updateToolbarForDestination(@NonNull NavDestination destination) {
        // Example: Show/hide toolbar buttons based on destination
        // int destId = destination.getId();
        // if (destId == R.id.audioRecordFragment) { ... }
        // For now, just log and rely on selected card for notes button
        CardItem card = viewModel.selectedCard.getValue();
        boolean hasNotes = card != null && card.getSessionNotes() != null && !card.getSessionNotes().trim().isEmpty();
        updateNotesButtonVisibility(hasNotes); // Always update based on current card
        
        // Update patient details button visibility based on patient data availability
        String patientData = viewModel.patientData.getValue();
        updatePatientDetailsButtonVisibility(patientData != null && !patientData.trim().isEmpty());
    }

    private void updateNotesButtonVisibility(boolean hasNotes) {
        if (btnSessionNotes != null) {
            btnSessionNotes.setVisibility(hasNotes ? View.VISIBLE : View.GONE);
            Log.d(TAG, "Notes Button visibility: " + (hasNotes ? "VISIBLE" : "GONE"));
        }
    }

    private void updatePatientDetailsButtonVisibility(boolean hasPatientData) {
        if (btnPatientDetails != null) {
            btnPatientDetails.setVisibility(hasPatientData ? View.VISIBLE : View.GONE);
            Log.d(TAG, "Patient Details Button visibility: " + (hasPatientData ? "VISIBLE" : "GONE"));
        }
    }

    private void showPatientDetailsDialog() {
        String patientInfo = viewModel.patientData.getValue();
        if (patientInfo == null || patientInfo.trim().isEmpty()) {
            Log.w(TAG, "Patient details button clicked, but no data.");
            Toast.makeText(this, R.string.toast_no_patient_details_yet, Toast.LENGTH_SHORT).show();
            return;
        }
        
        showEnhancedProfileDialog(patientInfo);
    }

    private void showEnhancedProfileDialog(String patientData) {
        // Parse patient data to extract profile information
        Profile profile = parsePatientDataToProfile(patientData);
        
        if (profile == null) {
            // Fallback to simple dialog if parsing fails
            new AlertDialog.Builder(this)
                    .setTitle(R.string.title_patient_details)
                    .setMessage(patientData)
                    .setPositiveButton(R.string.button_ok, null)
                    .show();
            return;
        }

        // Create enhanced profile dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_profile_details, null);
        builder.setView(dialogView);
        
        // Initialize views
        ShapeableImageView ivProfilePic = dialogView.findViewById(R.id.ivProfilePic);
        android.widget.TextView tvFullName = dialogView.findViewById(R.id.tvFullName);
        android.widget.TextView tvId = dialogView.findViewById(R.id.tvId);
        android.widget.TextView tvEmail = dialogView.findViewById(R.id.tvEmail);
        android.widget.TextView tvDateOfBirth = dialogView.findViewById(R.id.tvDateOfBirth);
        android.widget.TextView tvSessionSentimentTitle = dialogView.findViewById(R.id.tvSessionSentimentTitle);
        FrameLayout frameLayoutSessionChart = dialogView.findViewById(R.id.frameLayoutSessionChart);
        BarChart barChartProfileSessions = dialogView.findViewById(R.id.barChartProfileSessions);
        ProgressBar progressBarProfile = dialogView.findViewById(R.id.progressBarProfile);
        ProgressBar progressBarSessionChart = dialogView.findViewById(R.id.progressBarSessionChart);
        View groupProfileContent = dialogView.findViewById(R.id.groupProfileContent);
        android.widget.TextView tvLocalProfileNote = dialogView.findViewById(R.id.tvLocalProfileNote);
        
        // Set up profile data
        updateDialogProfileUI(profile, tvFullName, tvId, tvEmail, tvDateOfBirth, ivProfilePic);
        
        // Show profile content immediately
        progressBarProfile.setVisibility(View.GONE);
        groupProfileContent.setVisibility(View.VISIBLE);
        
        // Try to fetch additional profile data from cloud if we have an ID
        if (profile.getId() != null && !profile.getId().isEmpty()) {
            fetchCloudProfileData(profile.getId(), dialogView, tvFullName, tvId, tvEmail, tvDateOfBirth, 
                                ivProfilePic, tvSessionSentimentTitle, frameLayoutSessionChart, 
                                barChartProfileSessions, progressBarSessionChart, tvLocalProfileNote);
        } else {
            // Show local profile note since this is purely from session data
            tvLocalProfileNote.setVisibility(View.VISIBLE);
            tvSessionSentimentTitle.setVisibility(View.GONE);
            frameLayoutSessionChart.setVisibility(View.GONE);
        }
        
        builder.setTitle(R.string.title_patient_details)
               .setPositiveButton(R.string.button_ok, null)
               .show();
    }

    private Profile parsePatientDataToProfile(String patientData) {
        try {
            // Parse the patient data string format: "{id: ..., name: ..., email: ..., date_of_birth: ...}"
            String cleanData = patientData.replace("{", "").replace("}", "");
            String[] parts = cleanData.split(", ");
            
            Profile profile = new Profile();
            
            for (String part : parts) {
                String[] keyValue = part.split(": ", 2);
                if (keyValue.length == 2) {
                    String key = keyValue[0].trim();
                    String value = keyValue[1].trim();
                    
                    switch (key) {
                        case "id":
                            profile.setId(value);
                            break;
                        case "name":
                            profile.setFullName(value);
                            break;
                        case "email":
                            profile.setEmail(value);
                            break;
                        case "date_of_birth":
                            profile.setDateOfBirth(value);
                            break;
                    }
                }
            }
            
            return profile;
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse patient data: " + patientData, e);
            return null;
        }
    }

    private void updateDialogProfileUI(Profile profile, android.widget.TextView tvFullName, 
                                     android.widget.TextView tvId, android.widget.TextView tvEmail, 
                                     android.widget.TextView tvDateOfBirth, ShapeableImageView ivProfilePic) {
        if (profile == null) return;
        
        tvFullName.setText(profile.getFullName() != null ? profile.getFullName() : "Unknown");
        
        String patientId = profile.getId() != null ? profile.getId() : "";
        tvId.setText(getString(R.string.profile_id_prefix) + patientId);
        
        String email = profile.getEmail() != null ? profile.getEmail() : "";
        tvEmail.setText(getString(R.string.profile_email_prefix) + email);
        tvEmail.setVisibility(email.isEmpty() ? View.GONE : View.VISIBLE);
        
        String dob = profile.getDateOfBirth() != null ? profile.getDateOfBirth() : "";
        tvDateOfBirth.setText(getString(R.string.profile_dob_prefix) + dob);
        tvDateOfBirth.setVisibility(dob.isEmpty() ? View.GONE : View.VISIBLE);
        
        // Use the utility method to load the profile picture
        ProfilePictureUtil.loadProfilePicture(this, profile.getPictureUrl(), ivProfilePic);
    }

    private void fetchCloudProfileData(String profileId, View dialogView, 
                                     android.widget.TextView tvFullName, android.widget.TextView tvId, 
                                     android.widget.TextView tvEmail, android.widget.TextView tvDateOfBirth, 
                                     ShapeableImageView ivProfilePic, android.widget.TextView tvSessionSentimentTitle,
                                     FrameLayout frameLayoutSessionChart, BarChart barChartProfileSessions,
                                     ProgressBar progressBarSessionChart, android.widget.TextView tvLocalProfileNote) {
        
        progressBarSessionChart.setVisibility(View.VISIBLE);
        
        searchRepository.performProfileSearch(profileId, new SearchRepository.ProfileSearchCallback() {
            @Override
            public void onProfilesFound(List<Profile> profiles) {
                runOnUiThread(() -> {
                    if (profiles != null && !profiles.isEmpty()) {
                        Profile cloudProfile = profiles.get(0);
                        Log.d(TAG, "Found cloud profile: " + cloudProfile.getFullName());
                        
                        // Update UI with cloud profile data (more complete)
                        updateDialogProfileUI(cloudProfile, tvFullName, tvId, tvEmail, tvDateOfBirth, ivProfilePic);
                        
                        // Fetch session data for sentiment chart
                        fetchSessionDataForChart(cloudProfile.getId(), tvSessionSentimentTitle, 
                                               frameLayoutSessionChart, barChartProfileSessions, 
                                               progressBarSessionChart, tvLocalProfileNote);
                    } else {
                        // No cloud profile found, show local profile note
                        runOnUiThread(() -> {
                            progressBarSessionChart.setVisibility(View.GONE);
                            tvLocalProfileNote.setVisibility(View.VISIBLE);
                        });
                    }
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    Log.w(TAG, "Failed to fetch cloud profile: " + error);
                    progressBarSessionChart.setVisibility(View.GONE);
                    tvLocalProfileNote.setVisibility(View.VISIBLE);
                });
            }
        });
    }

    private void fetchSessionDataForChart(String profileId, android.widget.TextView tvSessionSentimentTitle,
                                        FrameLayout frameLayoutSessionChart, BarChart barChartProfileSessions,
                                        ProgressBar progressBarSessionChart, android.widget.TextView tvLocalProfileNote) {
        searchRepository.performSessionSearch(profileId, new SearchRepository.SessionSearchCallback() {
            @Override
            public void onSessionsFound(List<SessionSummary> sessionSummaries) {
                runOnUiThread(() -> {
                    progressBarSessionChart.setVisibility(View.GONE);
                    
                    if (sessionSummaries != null && !sessionSummaries.isEmpty()) {
                        Log.d(TAG, "Found " + sessionSummaries.size() + " sessions for chart");
                        
                        // Setup and show chart
                        SentimentChartHelper.setupSentimentBarChart(barChartProfileSessions, SessionHostActivity.this, true, true, true);
                        SentimentChartHelper.updateProfileSessionSentimentChartData(barChartProfileSessions, sessionSummaries, SessionHostActivity.this);
                        
                        tvSessionSentimentTitle.setVisibility(View.VISIBLE);
                        frameLayoutSessionChart.setVisibility(View.VISIBLE);
                        barChartProfileSessions.setVisibility(View.VISIBLE);
                        
                        // Apply initial zoom
                        barChartProfileSessions.post(() -> 
                            SentimentChartHelper.applyInitialZoom(barChartProfileSessions, 6f));
                    } else {
                        Log.d(TAG, "No sessions found for chart");
                        tvSessionSentimentTitle.setVisibility(View.GONE);
                        frameLayoutSessionChart.setVisibility(View.GONE);
                    }
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    Log.w(TAG, "Failed to fetch session data: " + error);
                    progressBarSessionChart.setVisibility(View.GONE);
                    tvSessionSentimentTitle.setVisibility(View.GONE);
                    frameLayoutSessionChart.setVisibility(View.GONE);
                });
            }
        });
    }

    private void showSessionNotesDialog() {
        CardItem card = viewModel.selectedCard.getValue();
        if (card == null || card.getSessionNotes() == null || card.getSessionNotes().trim().isEmpty()) {
            Log.w(TAG, "Session notes button clicked, but no notes available.");
            Toast.makeText(this, R.string.toast_no_session_notes, Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.title_session_notes)
                .setMessage(card.getSessionNotes())
                .setPositiveButton(R.string.button_ok, null)
                .show();
    }

    private void uploadDataBasedOnSessionType() {
        CardItem card = viewModel.getSelectedCardValue();
        if (card == null) {
            handleUploadError("Selected card is null. Cannot determine session type.");
            return;
        }
        String sessionType = card.getType();
        List<NoteCard> notesToUpload = viewModel.combinedNotes.getValue();
        String patientInfo = viewModel.patientData.getValue();
        String therapistInfo = SessionManager.getInstance().getTherapistDetails();

        // --- Get the list of encrypted audio paths and the ENCRYPTED DEK from ViewModel ---
        List<String> encryptedAudioPaths = viewModel.encryptedAudioFilePaths.getValue();
        String encryptedDekBase64 = viewModel.encryptedDataEncryptionKey.getValue();
        // ---

        // ... (null checks for notes, patientInfo, etc.)

        Log.i(TAG, "Starting upload for session type: " + sessionType);
        RecordingRepository repository = RecordingRepository.getInstance();

        switch (sessionType) {
            case "Default Audio":
                if (encryptedAudioPaths == null || encryptedAudioPaths.isEmpty()) {
                    handleUploadError("Encrypted audio files are missing.");
                    return;
                }
                if (encryptedDekBase64 == null || encryptedDekBase64.isEmpty()) {
                    handleUploadError("Session encryption key (DEK) for audio is missing.");
                    return;
                }
                Log.d(TAG, "Uploading Default Audio. Segments: " + encryptedAudioPaths.size() + ". DEK (b64 prefix): " + (encryptedDekBase64.length() > 16 ? encryptedDekBase64.substring(0,16) : ""));

                repository.uploadRecordingSession(
                        encryptedAudioPaths, // Pass the LIST of paths
                        encryptedDekBase64,
                        patientInfo,
                        therapistInfo,
                        notesToUpload,
                        createSubmissionCallback(),
                        createProgressCallback()
                );
                break;
            // ... other cases
            default:
                Log.e(TAG, "Unknown session type for upload: " + sessionType);
                handleUploadError("Cannot upload data for unknown session type: " + sessionType);
                break;
        }
    }

//    private void uploadDataBasedOnSessionType() {
//        CardItem card = viewModel.getSelectedCardValue();
//        if (card == null) {
//            handleUploadError("Selected card is null. Cannot determine session type.");
//            return;
//        }
//        String sessionType = card.getType();
//        List<NoteCard> notesToUpload = viewModel.combinedNotes.getValue(); // Combined notes include summary
//        String patientInfo = viewModel.patientData.getValue();
//        String therapistInfo = SessionManager.getInstance().getTherapistDetails(); // Assuming this is available
//
//        // --- Get encrypted audio path and ENCRYPTED DEK from ViewModel ---
//        String encryptedAudioPath = viewModel.encryptedAudioFilePath.getValue();
//        String encryptedDekBase64 = viewModel.encryptedDataEncryptionKey.getValue();
//        // ---
//
//        if (sessionType == null || notesToUpload == null || patientInfo == null || therapistInfo == null) {
//            handleUploadError(getString(R.string.error_missing_data_for_upload_check_all_fields));
//            return;
//        }
//
//        Optional<NoteCard> summaryOpt = notesToUpload.stream().filter(n -> "Summary".equals(n.getTitle())).findFirst();
//        if (!summaryOpt.isPresent() || summaryOpt.get().getContent() == null || summaryOpt.get().getContent().trim().isEmpty()) {
//            handleUploadError(getString(R.string.error_summary_cannot_be_empty_for_submit));
//            return;
//        }
//
//        Log.i(TAG, "Starting upload for session type: " + sessionType);
//        RecordingRepository repository = RecordingRepository.getInstance(); // Assuming singleton
//
//        switch (sessionType) {
//            case "Default Audio": // Make sure this type string matches exactly
//                if (encryptedAudioPath == null || !new File(encryptedAudioPath).exists()) {
//                    handleUploadError("Encrypted audio file is missing or invalid: " + encryptedAudioPath);
//                    return;
//                }
//                if (encryptedDekBase64 == null || encryptedDekBase64.isEmpty()) {
//                    handleUploadError("Session encryption key (DEK) for audio is missing.");
//                    return;
//                }
//                Log.d(TAG, "Uploading Default Audio. Path: " + encryptedAudioPath + ". DEK (b64 prefix): " + (encryptedDekBase64.length() > 16 ? encryptedDekBase64.substring(0,16) : encryptedDekBase64));
//                repository.uploadRecordingSession(
//                        encryptedAudioPath,
//                        encryptedDekBase64, // Pass the encrypted DEK
//                        patientInfo,
//                        therapistInfo,
//                        notesToUpload,
//                        createSubmissionCallback(),
//                        createProgressCallback()
//                );
//                break;
//            // Add cases for "VR", "Physio" if they also produce encrypted files with DEKs
//            // case "VR":
//            // String encryptedVrDataPath = viewModel.vrDataPath.getValue();
//            // String encryptedVrDek = viewModel.encryptedVrDataDek.getValue(); // Example
//            // if (encryptedVrDataPath == null || ...) { ... }
//            // repository.uploadVrSession(encryptedVrDataPath, encryptedVrDek, ...);
//            // break;
//            default:
//                Log.e(TAG, "Unknown session type for upload: " + sessionType);
//                handleUploadError("Cannot upload data for unknown session type: " + sessionType);
//                break;
//        }
//    }

    private RecordingRepository.RecordingSubmissionCallback createSubmissionCallback() {
        return new RecordingRepository.RecordingSubmissionCallback() {
            @Override
            public void onSuccess(SessionSubmissionResponse response) {
                runOnUiThread(() -> {
                    // --- FINAL PROGRESS UPDATE ---
                    // Set progress to 100% just before hiding the progress bar.
                    if (viewModel.isLoading.getValue() == Boolean.TRUE) {
                        viewModel.updateUploadProgress(100);
                        viewModel.updateUploadStatus("Upload Complete!");
                    }

                    // A very brief delay can make the 100% feel more satisfying to the user
                    // before the UI disappears. Optional but good UX.
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        viewModel.setLoading(false); // This hides the progress UI in ReviewSubmitFragment
                        Log.i(TAG, "Upload successful! Session ID: " + response.getSessionId() + ", Message: " + response.getMessage());
                        showSuccessDialog(); // This will also trigger cleanupAndFinish
                    }, 200); // 200ms delay
                });
            }

            @Override
            public void onFailure(String errorMessage) {
                final String lowerErrorMessage = errorMessage != null ? errorMessage.toLowerCase() : "";
                // Check for specific phrases indicating key authentication issues for KEK
                // These can come from UserNotAuthenticatedException or AEADBadTagException if KEK fails
                if (lowerErrorMessage.contains("user not authenticated") ||
                        lowerErrorMessage.contains("keystore operation failed") || // Generic Keystore issue
                        lowerErrorMessage.contains("key authentication required") ||
                        lowerErrorMessage.contains("aeadbadtagexception") || // This often means wrong key or auth issue for GCM
                        (lowerErrorMessage.contains("keystore") && lowerErrorMessage.contains("authentication")) ) {
                    runOnUiThread(() -> {
                        Log.w(TAG, "Upload failed due to Keystore authentication/key issue: " + errorMessage);
                        // Don't call handleUploadError as it shows a generic dialog.
                        // Instead, request user authentication again.
                        viewModel.requestUserAuthentication(SessionViewModel.AuthReason.SUBMIT_SESSION);
                        // setLoading(false) is handled by requestUserAuthentication
                    });
                } else {
                    // For other errors, handle them as generic upload failures.
                    handleUploadError(errorMessage); // Shows generic error dialog
                }
            }
        };
    }

    private RecordingRepository.UploadProgressCallback createProgressCallback() {
        return new RecordingRepository.UploadProgressCallback() {
            @Override
            public void onProgress(int progressPercent, String statusMessage) {
                // Ensure UI updates are on the main thread
                runOnUiThread(() -> {
                    viewModel.updateUploadProgress(progressPercent);
                    viewModel.updateUploadStatus(statusMessage);
                    Log.d(TAG, "Upload progress: " + progressPercent + "% - " + statusMessage);
                });
            }
        };
    }

    private void handleUploadError(String errorMessage) {
        runOnUiThread(() -> {
            Log.e(TAG, "Upload failed: " + errorMessage);
            viewModel.setLoading(false); // Ensure loading is off
            showErrorDialog(getString(R.string.dialog_title_upload_failed), errorMessage);
        });
    }

    private void setupBackPressHandler() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (Boolean.TRUE.equals(viewModel.isLoading.getValue())) {
                    Toast.makeText(SessionHostActivity.this, R.string.toast_upload_in_progress, Toast.LENGTH_SHORT).show();
                    return; // Prevent back during critical ops
                }
                if (navController.getCurrentDestination() != null &&
                        navController.getCurrentDestination().getId() == navController.getGraph().getStartDestinationId()) {
                    showDiscardConfirmationDialog();
                } else {
                    showDiscardConfirmationDialog();
                }
            }
        });
    }

    private void showSuccessDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_title_success)
                .setMessage(R.string.dialog_message_session_submitted_successfully)
                .setPositiveButton(R.string.button_ok, (dialog, which) -> {
                    cleanupAndFinish(false); // false = not a deletion, but a successful submission
                })
                .setCancelable(false)
                .show();
    }

    private void showDeleteConfirmationDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_title_delete_session_confirmation)
                .setMessage(R.string.dialog_message_are_you_sure_delete_session)
                .setPositiveButton(R.string.button_delete, (dialog, which) -> {
                    cleanupAndFinish(true); // true = it's a deletion
                })
                .setNegativeButton(R.string.button_cancel, (dialog, which) -> {
                    viewModel.setLoading(false); // Reset loading if cancelled
                })
                .setOnCancelListener(dialog -> viewModel.setLoading(false)) // Also reset if dismissed
                .show();
    }

    private void showErrorDialog(String title, String message) {
        // Ensure loading is always turned off when an error dialog is shown
        if (Boolean.TRUE.equals(viewModel.isLoading.getValue())) {
            viewModel.setLoading(false);
        }
        String fullMessage = message;
        // Optionally append generic advice for upload failures
        if (getString(R.string.dialog_title_upload_failed).equals(title)) {
            fullMessage += "\n\n" + getString(R.string.error_check_connection_and_retry);
        }
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(fullMessage)
                .setPositiveButton(R.string.button_ok, null) // User acknowledges error
                .show();
    }
    private void showDiscardConfirmationDialog() {
        new AlertDialog.Builder(SessionHostActivity.this)
                .setTitle(R.string.dialog_title_cancel_submission)
                .setMessage(R.string.dialog_message_cancel_submission)
                .setPositiveButton(R.string.button_yes_discard, (dialog, which) -> {
                    cleanupAndFinish(true);
                })
                .setNegativeButton(R.string.button_no_stay, (dialog, which) -> {

                    viewModel.setLoading(false);
                })
                .setOnCancelListener(dialog -> {
                    viewModel.setLoading(false);
                    Log.d(TAG, "Discard dialog dismissed, ensuring loading state is false.");
                })
                .show();
    }


    private void cleanupAndFinish(boolean isDeletionOrDiscard) {
        Log.i(TAG, "Cleanup and Finish. Is Deletion/Discard: " + isDeletionOrDiscard);

        List<String> encryptedAudioPaths = viewModel.encryptedAudioFilePaths.getValue();

        if (encryptedAudioPaths != null && !encryptedAudioPaths.isEmpty()) {
            for (String path : encryptedAudioPaths) {
                File audioFile = new File(path);
                if (audioFile.exists()) {
                    Log.d(TAG, "Securely deleting session audio file: " + audioFile.getAbsolutePath());
                    AESUtil.secureDelete(audioFile);
                }
            }
        }
        // TODO: Add similar cleanup for VR/Physio files if they are locally stored and encrypted

        viewModel.clearSensitiveSessionData(); // Clears paths and DEK from ViewModel

        String sessionType = (viewModel.getSelectedCardValue() != null) ? viewModel.getSelectedCardValue().getType() : "Unknown";
        if (isDeletionOrDiscard) {
            logAuditEvent("SESSION_DISCARDED_OR_DELETED", "User action. Session Type: " + sessionType);
            Toast.makeText(this, R.string.toast_session_discarded, Toast.LENGTH_SHORT).show();
        } else {
            // This is for successful submission
            logAuditEvent("SESSION_SUBMITTED_SUCCESSFULLY", "Session Type: " + sessionType);
            Toast.makeText(this, R.string.toast_session_submitted, Toast.LENGTH_SHORT).show();
        }

        finish(); // Close the activity
    }

    private void logAuditEvent(String eventType, String details) {
        String userId = EphemeralPrefs.getInstance().getUserId(); // Assuming EphemeralPrefs is initialized
        Log.i("AUDIT_EVENT", eventType + " - User: " + (userId != null ? userId : "UNKNOWN_USER") + " - Details: " + details);
    }
}