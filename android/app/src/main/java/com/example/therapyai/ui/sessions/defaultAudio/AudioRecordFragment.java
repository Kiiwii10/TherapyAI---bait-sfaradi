package com.example.therapyai.ui.sessions.defaultAudio;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.therapyai.R;
import com.example.therapyai.data.local.models.NoteCard;
import com.example.therapyai.ui.adapters.NoteCardAdapter;
import com.example.therapyai.ui.views.AudioVisualizerView;
import com.example.therapyai.ui.viewmodels.SessionViewModel;
import com.example.therapyai.util.AESUtil; // For GCM_IV_LENGTH constant for file check
import com.google.android.material.button.MaterialButton;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AudioRecordFragment extends Fragment {

    private static final String TAG = "AudioRecordFragment";

    private AudioVisualizerView waveView;
    private MaterialButton btnStart, btnPauseResume, btnStop, btnAddNote;
    private RecyclerView rvNotes;
    private TextView timerTextView;
    private View recordingIndicator;
    private TextView recordingStatusText;
    private View processingLayout;
    private TextView processingText;

    private NoteCardAdapter noteCardAdapter;
    private SessionViewModel viewModel;
    private NavController navController;

    private RecordingService recordingService;
    private boolean isBound = false;
    private boolean sessionFinishedOrCancelled = false; // To prevent multiple navigations/cleanups

    private ActivityResultLauncher<String> requestPermissionLauncher;

    private @ColorInt int themedTextColorSecondary;
    private Handler uiUpdateHandler = new Handler(Looper.getMainLooper());
    private Runnable uiUpdateRunnable;
    private static final long UI_UPDATE_INTERVAL = 250; // Faster update for timer
    private Handler blinkHandler = new Handler(Looper.getMainLooper());
    private Runnable blinkRunnable;
    private ExecutorService executorService = Executors.newSingleThreadExecutor();


    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");
        viewModel = new ViewModelProvider(requireActivity()).get(SessionViewModel.class);

        requestPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        Log.i(TAG, "RECORD_AUDIO permission granted.");
                        // Now that permission is granted, re-evaluate UI or trigger action if pending
                        updateUiBasedOnServiceState();
                        // If user tried to start and was blocked by permission, they might need to click again
                        // or we can have a flag to auto-start. For now, UI update is sufficient.
                    } else {
                        Log.w(TAG, "RECORD_AUDIO permission denied.");
                        Toast.makeText(requireContext(), R.string.toast_permission_denied, Toast.LENGTH_LONG).show();
                        updateUiBasedOnServiceState(); // Buttons likely disabled
                    }
                });

        requireActivity().getOnBackPressedDispatcher().addCallback(this,
                new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        // Only handle back press if recording is active to show confirmation
                        if (isBound && recordingService != null && recordingService.isRecording()) {
                            stopUiUpdates(); // Stop UI updates while dialog is shown
                            showCancelConfirmationDialog();
                        } else {
                            // If not recording, or service not bound, let parent activity/nav handle it
                            setEnabled(false); // Disable this callback
                            if (navController.popBackStack()) {
                                // Successfully popped, do nothing more
                            } else {
                                // If cannot pop, then finish activity
                                requireActivity().finish();
                            }
                            setEnabled(true); // Re-enable for future if fragment is revisited
                        }
                    }
                });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        Log.d(TAG, "onCreateView");
        return inflater.inflate(R.layout.fragment_audio_record, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Log.d(TAG, "onViewCreated");
        navController = NavHostFragment.findNavController(this);

        initViews(view);
        setupNotesRecyclerView();
        setupButtonListeners();
        loadThemeColors();

        viewModel.otherNotes.observe(getViewLifecycleOwner(), notes -> {
            Log.d(TAG, "Observed otherNotes update from ViewModel. Count: " + (notes != null ? notes.size() : 0));
            if (notes != null && noteCardAdapter != null) {
                noteCardAdapter.updateNotes(new ArrayList<>(notes));
            } else if (noteCardAdapter != null) {
                noteCardAdapter.updateNotes(new ArrayList<>()); // Clear adapter if notes are null
            }
        });
        sessionFinishedOrCancelled = false; // Reset flag
    }

    @Override
    public void onStart() {
        super.onStart();
        Log.d(TAG, "onStart - Binding to service.");
        // Start and bind to service
        Intent serviceIntent = new Intent(requireContext(), RecordingService.class);
        // Android O+ requires starting foreground services explicitly if they will run long
        // bindService with BIND_AUTO_CREATE handles service creation if not running.
        // If RecordingService is meant to be a foreground service from the start,
        // ensure it calls startForeground itself.
        try {
            // requireActivity().startService(serviceIntent); // May be needed if service needs to run independently long term
            requireActivity().bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
        } catch (IllegalStateException e) {
            Log.e(TAG, "Cannot bind service when activity is not resumed or being finished.", e);
            Toast.makeText(getContext(), "Error initializing recording.", Toast.LENGTH_SHORT).show();
        }
    }


    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
        // UI updates are started once service is connected
        if (isBound && recordingService != null) {
            updateUiBasedOnServiceState();
            startUiUpdates(); // Restart UI polling if service is already bound
        }
        // Permission check can also be here if crucial for resume
        checkAudioPermission();
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.d(TAG, "onPause");
        stopUiUpdates();
    }

    @Override
    public void onStop() {
        super.onStop();
        Log.d(TAG, "onStop - Unbinding from service.");
        if (isBound) {
            if (recordingService != null) {
                recordingService.setAmplitudeListener(null); // Prevent callbacks to detached fragment
                // Do not stop service here if it's meant to record in background
                // Only unbind. Service lifecycle managed by startService/stopSelf or foreground state.
            }
            try {
                requireActivity().unbindService(serviceConnection);
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "Service already unbound or not registered: " + e.getMessage());
            }
            isBound = false;
        }
    }


    @Override
    public void onDestroyView() {
        super.onDestroyView();
        Log.d(TAG, "onDestroyView");
        stopUiUpdates();
        blinkHandler.removeCallbacksAndMessages(null);
        if (processingLayout != null) {
            processingLayout.setVisibility(View.GONE);
        }
        // Release View references
        waveView = null;
        btnStart = null; btnPauseResume = null; btnStop = null; btnAddNote = null;
        rvNotes = null;
        timerTextView = null;
        recordingIndicator = null;
        recordingStatusText = null;
        noteCardAdapter = null; // adapter holds context too
        processingLayout = null; // Also nullify the new reference
        processingText = null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdownNow(); // Force shutdown
        }
    }

    private void initViews(View view) {
        waveView = view.findViewById(R.id.waveView_frag);
        btnStart = view.findViewById(R.id.btnStart_frag);
        btnPauseResume = view.findViewById(R.id.btnPauseResume_frag);
        btnStop = view.findViewById(R.id.btnStop_frag);
        btnAddNote = view.findViewById(R.id.btnAddNote_frag);
        timerTextView = view.findViewById(R.id.timerTextView_frag);
        recordingIndicator = view.findViewById(R.id.recordingIndicator_frag);
        recordingStatusText = view.findViewById(R.id.recordingStatusText_frag);
        rvNotes = view.findViewById(R.id.rvNotes_frag);
        processingLayout = view.findViewById(R.id.processingLayout_frag);
        processingText = view.findViewById(R.id.processingText_frag);

        // Initial state based on view model or defaults
        if (timerTextView != null) timerTextView.setText(formatElapsedTime(0));
        if (waveView != null) waveView.resetWaveform();
    }

    private void setupNotesRecyclerView() {
        noteCardAdapter = new NoteCardAdapter(new ArrayList<>(), this::showEditNoteDialog);
        rvNotes.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvNotes.setAdapter(noteCardAdapter);
        // Initial data load will come from ViewModel observer
    }

    private void loadThemeColors() {
        TypedValue typedValue = new TypedValue();
        Resources.Theme theme = requireContext().getTheme();
        // Try to resolve android.R.attr.textColorSecondary first
        if (theme.resolveAttribute(android.R.attr.textColorSecondary, typedValue, true)) {
            themedTextColorSecondary = typedValue.data;
        } else {
            // Fallback to a custom attribute or default color if not found
            try {
                if (theme.resolveAttribute(R.attr.colorOnSurfaceVariant, typedValue, true)) { // Example fallback
                    themedTextColorSecondary = typedValue.data;
                } else {
                    themedTextColorSecondary = ContextCompat.getColor(requireContext(), R.color.grey); // Hardcoded fallback
                }
            } catch (Resources.NotFoundException e) {
                themedTextColorSecondary = ContextCompat.getColor(requireContext(), R.color.grey); // Hardcoded fallback
            }
        }
    }

    /**
     * This centralizes the UI changes for a cleaner implementation.
     * @param isProcessing True to show overlay and disable buttons, false to hide.
     */
    private void showProcessingState(boolean isProcessing) {
        if (getView() == null) return;

        if (processingLayout != null) {
            processingLayout.setVisibility(isProcessing ? View.VISIBLE : View.GONE);
        }

        // When processing, disable all interaction with recording controls.
        // When not processing, let updateUiBasedOnServiceState handle button states.
        if (isProcessing) {
            if (btnStart != null) btnStart.setEnabled(false);
            if (btnPauseResume != null) btnPauseResume.setEnabled(false);
            if (btnStop != null) btnStop.setEnabled(false);
            if (btnAddNote != null) btnAddNote.setEnabled(false);
        } else {
            updateUiBasedOnServiceState();
        }
    }
    private void setupButtonListeners() {
        btnStart.setOnClickListener(v -> attemptStartRecording());
        btnPauseResume.setOnClickListener(v -> togglePauseResume());
        btnStop.setOnClickListener(v -> confirmStopRecording());
        btnAddNote.setOnClickListener(v -> showAddNoteDialog());
    }

    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            if (getContext() == null || isDetached()) { // Fragment safety check
                Log.w(TAG, "onServiceConnected called but fragment context is null or detached.");
                return;
            }
            Log.i(TAG, "RecordingService connected.");
            RecordingService.LocalBinder binder = (RecordingService.LocalBinder) service;
            recordingService = binder.getService();
            isBound = true;

            recordingService.setAmplitudeListener(amplitude -> {
                if (waveView != null && getActivity() != null) { // Check view and activity
                    getActivity().runOnUiThread(() -> {
                        if (waveView != null) waveView.updateAmplitude(amplitude); // Double check waveView
                    });
                }
            });
            updateUiBasedOnServiceState(); // Reflect service's current state
            startUiUpdates();              // Start polling for timer/UI updates
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            if (getContext() == null || isDetached()) {
                Log.w(TAG, "onServiceDisconnected called but fragment context is null or detached.");
                return;
            }
            Log.w(TAG, "RecordingService disconnected unexpectedly!");
            Toast.makeText(requireContext(), R.string.toast_service_disconnected, Toast.LENGTH_LONG).show();
            isBound = false;
            recordingService = null;
            stopUiUpdates();
            updateUiBasedOnServiceState(); // UI to reflect stopped state

            // Attempt to salvage data if possible, though unexpected disconnect is bad
            if (!sessionFinishedOrCancelled) { // If session wasn't properly finished
                // Check if service managed to create segment files before disconnecting
                List<File> lastSegmentFiles = (recordingService != null) ? recordingService.getEncryptedSegmentFiles() : null;
                String lastDek = (recordingService != null) ? recordingService.getEncryptedDEK_Base64() : null;

                if (lastSegmentFiles != null && !lastSegmentFiles.isEmpty() && lastDek != null && !lastDek.isEmpty()) {
                    Log.w(TAG, "Service disconnected, but " + lastSegmentFiles.size() + " encrypted segments and DEK exist. Attempting to use them.");
                    try {
                        // Stitch segments together for final file
                        File stitchedFile = recordingService.stitchSegmentsToDecryptedFile();
                        viewModel.setEncryptedAudioFilePath(stitchedFile.getAbsolutePath());
                        viewModel.setEncryptedDataEncryptionKey(lastDek);
                        navigateToNextStep(); // Risky, but better than losing data if possible
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to stitch segments after service disconnect", e);
                        viewModel.postErrorMessage(getString(R.string.error_service_unavailable_stop));
                        if (navController.getCurrentDestination() != null && navController.getCurrentDestination().getId() == R.id.audioRecordFragment) {
                            navController.popBackStack();
                        }
                    }
                } else {
                    Log.e(TAG, "Service disconnected, no valid recording segments or DEK found. Data likely lost for this attempt.");
                    viewModel.postErrorMessage(getString(R.string.error_service_unavailable_stop));
                    // Consider navigating back or to an error screen
                    if (navController.getCurrentDestination() != null && navController.getCurrentDestination().getId() == R.id.audioRecordFragment) {
                        navController.popBackStack();
                    }
                }
                sessionFinishedOrCancelled = true; // Mark as handled
            }
        }
    };

    private void startUiUpdates() {
        stopUiUpdates(); // Ensure no double runs
        if (!isBound || recordingService == null || !isAdded()) { // Check fragment is added
            return;
        }
        Log.d(TAG, "Starting UI update polling.");
        uiUpdateRunnable = new Runnable() {
            @Override
            public void run() {
                if (isBound && recordingService != null && getView() != null && isAdded()) {
                    updateUiBasedOnServiceState(); // Update all UI elements
                    if (recordingService.isRecording()) { // Continue polling if recording
                        uiUpdateHandler.postDelayed(this, UI_UPDATE_INTERVAL);
                    } else {
                        stopUiUpdates(); // Stop if not recording
                    }
                } else {
                    stopUiUpdates(); // Stop if unbound, service null, or view destroyed
                }
            }
        };
        uiUpdateHandler.post(uiUpdateRunnable);
    }

    private void stopUiUpdates() {
        if (uiUpdateRunnable != null) {
            Log.d(TAG, "Stopping UI update polling.");
            uiUpdateHandler.removeCallbacks(uiUpdateRunnable);
            uiUpdateRunnable = null;
        }
    }

    private void updateUiBasedOnServiceState() {
        if (getView() == null || !isAdded() || viewModel == null) { // Ensure fragment is attached and views available
            Log.w(TAG,"updateUiBasedOnServiceState: View not available or fragment not fully initialized.");
            return;
        }

        boolean hasPermission = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
        boolean serviceReady = isBound && recordingService != null;
        boolean currentlyRecording = serviceReady && recordingService.isRecording();
        boolean currentlyPaused = serviceReady && recordingService.isPaused();
        long elapsedTime = serviceReady ? recordingService.getElapsedTimeMillis() : 0;

        if (timerTextView != null) timerTextView.setText(formatElapsedTime(elapsedTime));

        if (btnStart != null) {
            btnStart.setEnabled(hasPermission && serviceReady && !currentlyRecording);
            btnStart.setVisibility(currentlyRecording ? View.GONE : View.VISIBLE);
        }
        if (btnPauseResume != null) {
            btnPauseResume.setEnabled(currentlyRecording);
            btnPauseResume.setVisibility(currentlyRecording ? View.VISIBLE : View.GONE);
            if (currentlyRecording) {
                if (currentlyPaused) {
                    btnPauseResume.setText(R.string.button_resume);
                    btnPauseResume.setIconResource(R.drawable.ic_play); // Ensure this drawable exists
                    btnPauseResume.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.custom_resume_green)));
                    btnPauseResume.setTextColor(ContextCompat.getColor(requireContext(), R.color.on_custom_resume_green));
                    btnPauseResume.setIconTint(ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.on_custom_resume_green)));
                    pauseRecordingIndicator();
                } else {
                    btnPauseResume.setText(R.string.button_pause);
                    btnPauseResume.setIconResource(R.drawable.baseline_paused_24); // Ensure this drawable exists
                    btnPauseResume.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.orange))); // Example color
                    btnPauseResume.setTextColor(ContextCompat.getColor(requireContext(), R.color.white));
                    btnPauseResume.setIconTint(ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.white)));
                    resumeRecordingIndicator(); // Will also handle text
                }
            }
        }
        if (btnStop != null) {
            btnStop.setEnabled(currentlyRecording);
            btnStop.setVisibility(currentlyRecording ? View.VISIBLE : View.GONE);
        }
        if (btnAddNote != null) {
            btnAddNote.setEnabled(currentlyRecording);
        }

        if (recordingStatusText != null) {
            if (!hasPermission) {
                recordingStatusText.setText(R.string.toast_permission_required);
                recordingStatusText.setTextColor(Color.RED);
                stopRecordingIndicator();
            } else if (!serviceReady) {
                recordingStatusText.setText(R.string.status_service_connecting);
                recordingStatusText.setTextColor(themedTextColorSecondary);
                stopRecordingIndicator();
            } else if (!currentlyRecording) {
                recordingStatusText.setText(R.string.status_ready_to_record);
                recordingStatusText.setTextColor(themedTextColorSecondary);
                stopRecordingIndicator();
                if (waveView != null) waveView.resetWaveform();
                if (timerTextView != null && elapsedTime == 0) timerTextView.setText(formatElapsedTime(0)); // Ensure timer resets
            }
            // Active recording/paused status text is handled by indicator methods
        }
        if (!currentlyRecording && !currentlyPaused && waveView != null) {
            waveView.resetWaveform(); // Ensure waveform is cleared when not recording
        }
    }


    private void attemptStartRecording() {
        if (!isBound || recordingService == null) {
            Toast.makeText(requireContext(), R.string.toast_service_not_ready, Toast.LENGTH_SHORT).show();
            Log.w(TAG, "Attempted to start recording but service is not bound.");
            // Try to bind again if not bound
            if (!isBound) {
                Intent serviceIntent = new Intent(requireContext(), RecordingService.class);
                requireActivity().bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
            }
            return;
        }
        if (recordingService.isRecording()) {
            Log.w(TAG, "Start button pressed, but already recording.");
            return; // Already recording
        }

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Audio permission not granted. Requesting...");
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
        } else {
            // Permission granted, show consent then start
            showConsentDialog();
        }
    }

    private void startActualRecording() {
        if (!isBound || recordingService == null) {
            Log.e(TAG, "Cannot start recording, service not bound or null.");
            Toast.makeText(requireContext(), R.string.toast_recording_start_failed, Toast.LENGTH_SHORT).show();
            updateUiBasedOnServiceState(); // Refresh UI
            return;
        }
        if (viewModel == null) {
            Log.e(TAG, "ViewModel is null, cannot proceed with recording start.");
            return;
        }

        try {
            viewModel.clearSensitiveSessionData(); // Clear any old session data (paths, DEKs)
            Log.d(TAG, "Requesting service to start recording.");
            recordingService.startRecording(); // This can throw IOException or SecurityException

            sessionFinishedOrCancelled = false; // Reset flag for new session
            updateUiBasedOnServiceState();
            startUiUpdates(); // Ensure UI polling is active
            Log.i(TAG, "Recording start command sent to service successfully.");

        } catch (SecurityException se) {
            // This specific exception comes from RecordingService if KEK auth is needed
            Log.e(TAG, "SecurityException during startRecording: " + se.getMessage(), se);
            viewModel.requestUserAuthentication(SessionViewModel.AuthReason.START_RECORDING);
            updateUiBasedOnServiceState(); // Update UI to reflect failure/auth needed state
        } catch (IOException e) {
            Log.e(TAG, "IOException during startRecording: " + e.getMessage(), e);
            Toast.makeText(requireContext(), getString(R.string.toast_recording_start_failed) + ": " + e.getMessage(), Toast.LENGTH_LONG).show();
            viewModel.postErrorMessage(getString(R.string.toast_recording_start_failed) + ": " + e.getMessage());
            updateUiBasedOnServiceState();
        }
    }


    private void togglePauseResume() {
        if (!isBound || recordingService == null || !recordingService.isRecording()) {
            Log.w(TAG, "Toggle pause/resume called but not in a valid recording state.");
            return;
        }
        if (recordingService.isPaused()) {
            recordingService.resumeRecording();
        } else {
            recordingService.pauseRecording();
        }
        updateUiBasedOnServiceState(); // Update UI immediately
    }

    private void confirmStopRecording() {
        if (!isBound || recordingService == null || !recordingService.isRecording()) {
            Log.w(TAG, "Confirm stop called but not in a valid recording state.");
            return;
        }
        stopUiUpdates(); // Pause UI updates while dialog is shown
        showStopConfirmationDialog();
    }

//    private void stopActualRecordingAndProceed() {
//        if (sessionFinishedOrCancelled) { // Prevent double execution
//            Log.w(TAG, "stopActualRecordingAndProceed: Already finished or cancelled.");
//            return;
//        }
//        if (!isBound || recordingService == null) {
//            Log.e(TAG, "Cannot stop recording, service not bound or null.");
//            viewModel.postErrorMessage(getString(R.string.error_service_unavailable_stop));
//            if (isAdded()) startUiUpdates(); // Resume UI updates if dialog was cancelled
//            return;
//        }
//
//        // Get data before stopping service
//        String finalEncryptedDekBase64 = recordingService.getEncryptedDEK_Base64();
//        long finalElapsedTime = recordingService.getElapsedTimeMillis();
//
//        recordingService.stopRecordingAndForeground(); // Stops service's recording logic
//
//        // Get segment files AFTER stopping service (after finalization)
//        List<File> finalSegmentFiles = recordingService.getEncryptedSegmentFiles();
//        sessionFinishedOrCancelled = true; // Mark as finished
//
//        if (timerTextView != null) timerTextView.setText(formatElapsedTime(finalElapsedTime));
//        updateUiBasedOnServiceState(); // Reflect that recording has stopped
//
//        // Validate the results from the service
//        if (finalSegmentFiles != null && !finalSegmentFiles.isEmpty() && finalEncryptedDekBase64 != null && !finalEncryptedDekBase64.isEmpty()) {
//            Log.i(TAG, "Recording stopped. " + finalSegmentFiles.size() + " encrypted segments and DEK available. Stitching and proceeding.");
//
//            try {
//                // Stitch segments into final decrypted audio file
//                File stitchedFile = recordingService.stitchSegmentsToDecryptedFile();
//                Log.d(TAG, "Stitched audio file: " + stitchedFile.getAbsolutePath() + ", Size: " + stitchedFile.length());
//                Log.d(TAG, "Encrypted DEK (b64 prefix): " + (finalEncryptedDekBase64.length() > 16 ? finalEncryptedDekBase64.substring(0,16) : finalEncryptedDekBase64));
//
//                viewModel.setEncryptedAudioFilePath(stitchedFile.getAbsolutePath());
//                viewModel.setEncryptedDataEncryptionKey(finalEncryptedDekBase64);
//                navigateToNextStep();
//            } catch (Exception e) {
//                Log.e(TAG, "Failed to stitch segments together", e);
//                viewModel.postErrorMessage(getString(R.string.error_finalize_recording_failed) + ": " + e.getMessage());
//                viewModel.clearSensitiveSessionData(); // Clear any partial data
//            }
//        } else {
//            Log.e(TAG, "Stop recording: Critical data missing from service.");
//            if (finalSegmentFiles == null || finalSegmentFiles.isEmpty()) {
//                Log.e(TAG, "No encrypted segment files available");
//            } else {
//                Log.d(TAG, "Found " + finalSegmentFiles.size() + " segment files");
//                for (int i = 0; i < finalSegmentFiles.size(); i++) {
//                    File segment = finalSegmentFiles.get(i);
//                    Log.d(TAG, "Segment " + i + ": " + segment.getName() + ", exists: " + segment.exists() + ", size: " + segment.length());
//                }
//            }
//            if (finalEncryptedDekBase64 == null || finalEncryptedDekBase64.isEmpty()) {
//                Log.e(TAG, "Encrypted DEK is missing from service.");
//            }
//            viewModel.postErrorMessage(getString(R.string.error_finalize_recording_failed));
//            viewModel.clearSensitiveSessionData(); // Clear any partial data
//        }
//    }

    private void stopActualRecordingAndProceed() {
        if (sessionFinishedOrCancelled) {
            Log.w(TAG, "stopActualRecordingAndProceed: Already finished or cancelled.");
            return;
        }
        if (!isBound || recordingService == null) {
            Log.e(TAG, "Cannot stop recording, service not bound or null.");
            viewModel.postErrorMessage(getString(R.string.error_service_unavailable_stop));
            if (isAdded()) startUiUpdates();
            showProcessingState(false); // Hide progress on failure
            return;
        }


        String finalEncryptedDekBase64 = recordingService.getEncryptedDEK_Base64();
        long finalElapsedTime = recordingService.getElapsedTimeMillis();

        recordingService.stopRecordingAndForeground();

        List<File> finalSegmentFiles = recordingService.getEncryptedSegmentFiles();
        sessionFinishedOrCancelled = true;

        if (finalSegmentFiles != null && !finalSegmentFiles.isEmpty() && finalEncryptedDekBase64 != null && !finalEncryptedDekBase64.isEmpty()) {
            Log.i(TAG, "Recording stopped. " + finalSegmentFiles.size() + " encrypted segments and DEK available. Proceeding.");

            List<String> finalSegmentPaths = new ArrayList<>();
            for (File file : finalSegmentFiles) {
                finalSegmentPaths.add(file.getAbsolutePath());
            }

            viewModel.setEncryptedAudioFilePaths(finalSegmentPaths);
            viewModel.setEncryptedDataEncryptionKey(finalEncryptedDekBase64);

            // This will trigger the navigation. The progress bar is still visible at this point.
            navigateToNextStep();

        } else {
            Log.e(TAG, "Stop recording: Critical data missing from service.");
            viewModel.postErrorMessage(getString(R.string.error_finalize_recording_failed));
            viewModel.clearSensitiveSessionData();
            // If we fail here, we MUST hide the progress bar.
            if (isAdded()) {
                showProcessingState(false);
            }
        }
    }

    private void checkAudioPermission() {
        if (getContext() == null) return; // Fragment not attached
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Audio permission is already granted.");
        } else {
            Log.i(TAG, "Audio permission not granted. Launching request...");
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
        }
        // Update UI based on current permission state, even if already granted
        updateUiBasedOnServiceState();
    }


    private void showConsentDialog() {
        if (getContext() == null) return;
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.dialog_title_consent)
                .setMessage(R.string.dialog_message_consent)
                .setPositiveButton(R.string.button_begin_recording, (dialog, which) -> startActualRecording())
                .setNegativeButton(R.string.button_cancel, (dialog, which) -> updateUiBasedOnServiceState()) // Re-enable start button if dialog cancelled
                .setCancelable(false)
                .show();
    }

    private void showStopConfirmationDialog() {
        if (getContext() == null) return;
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.dialog_title_finish_session)
                .setMessage(R.string.dialog_message_finish_session)
                .setPositiveButton(R.string.button_yes_finish, (dialog, which) -> {
                    // This part is correct. Show the progress indicator immediately.
                    showProcessingState(true);
                    // Use a short delay to allow the UI to update before starting the heavy task.
                    new Handler(Looper.getMainLooper()).postDelayed(this::stopActualRecordingAndProceed, 100);
                })
                .setNegativeButton(R.string.button_no_cancel, (dialog, which) -> {
                    if (isAdded()) startUiUpdates();
                })
                .setOnCancelListener(dialog -> {
                    if (isAdded()) startUiUpdates();
                })
                .show();
    }

    private void showCancelConfirmationDialog() {
        if (getContext() == null) return;
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.dialog_title_cancel_recording)
                .setMessage(R.string.dialog_message_cancel_recording)
                .setPositiveButton(R.string.button_yes_cancel, (dialog, which) -> {
                    cancelRecordingAndCleanup();
                    // Navigate back or finish activity after cleanup
                    if (navController.getCurrentDestination() != null && navController.getCurrentDestination().getId() == R.id.audioRecordFragment) {
                        if (!navController.popBackStack()) { // Try to pop, if stack empty, finish activity
                            requireActivity().finish();
                        }
                    } else {
                        requireActivity().finish(); // Fallback if nav state is unexpected
                    }
                })
                .setNegativeButton(R.string.button_no_keep_recording, (dialog, which) -> {
                    if (isAdded()) startUiUpdates(); // Resume UI polling if user decides to keep recording
                })
                .setOnCancelListener(dialog -> {
                    if (isAdded()) startUiUpdates(); // Also resume if dialog is dismissed
                })
                .show();
    }


    private void showAddNoteDialog() {
        if (getContext() == null || !isBound || recordingService == null || !recordingService.isRecording()) {
            viewModel.postErrorMessage(getString(R.string.error_cannot_add_note_no_session));
            return;
        }

        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_note, null);
        EditText etNote = dialogView.findViewById(R.id.etNote);
        CheckBox cbTimestamp = dialogView.findViewById(R.id.cbTimestamp);
        cbTimestamp.setChecked(!recordingService.isPaused()); // Default to timestamp if not paused

        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.dialog_title_add_note)
                .setView(dialogView)
                .setPositiveButton(R.string.button_save, (dialog, i) -> {
                    String noteText = etNote.getText().toString().trim();
                    if (!noteText.isEmpty()) {
                        String title;
                        String timestampStr = "";
                        long currentElapsedMillis = recordingService.getElapsedTimeMillis();
                        if (cbTimestamp.isChecked()) {
                            timestampStr = formatElapsedTime(currentElapsedMillis);
                            title = getString(R.string.note_title_timestamped, timestampStr);
                        } else {
                            int nextIndex = (viewModel.otherNotes.getValue() != null ? viewModel.otherNotes.getValue().size() : 0);
                            title = getString(R.string.note_title_generic, nextIndex + 1);
                        }
                        NoteCard newNote = new NoteCard(title, timestampStr, noteText, -1); // VM will assign final index if needed
                        viewModel.saveOtherNote(newNote);
                        if (rvNotes != null && noteCardAdapter != null && noteCardAdapter.getItemCount() > 0) {
                            rvNotes.smoothScrollToPosition(noteCardAdapter.getItemCount() - 1);
                        }
                    }
                })
                .setNegativeButton(R.string.button_cancel, null)
                .show();
    }

    private void showEditNoteDialog(NoteCard noteCardToEdit) {
        if (getContext() == null || noteCardToEdit == null) return;

        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_note, null);
        EditText etNote = dialogView.findViewById(R.id.etNote);
        dialogView.findViewById(R.id.cbTimestamp).setVisibility(View.GONE); // No timestamp editing
        etNote.setText(noteCardToEdit.getContent());

        new AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.dialog_title_edit_note) + " (" + noteCardToEdit.getTitle() + ")")
                .setView(dialogView)
                .setPositiveButton(R.string.button_save, (dialog, i) -> {
                    String updatedNoteText = etNote.getText().toString().trim();
                    // Only save if content actually changed
                    if (!updatedNoteText.equals(noteCardToEdit.getContent())) {
                        noteCardToEdit.setContent(updatedNoteText);
                        viewModel.saveOtherNote(noteCardToEdit); // ViewModel handles update or add logic
                    }
                })
                .setNeutralButton(R.string.button_delete, (dialog, i) -> {
                    viewModel.deleteOtherNote(noteCardToEdit);
                })
                .setNegativeButton(R.string.button_cancel, null)
                .show();
    }


    private void navigateToNextStep() {
        Log.d(TAG, "Navigating from AudioRecordFragment to AddSummaryFragment.");
        if (navController != null && navController.getCurrentDestination() != null &&
                navController.getCurrentDestination().getId() == R.id.audioRecordFragment) {
            navController.navigate(R.id.action_audioRecordFragment_to_addSummaryFragment);
        } else {
            Log.e(TAG, "Cannot navigate: NavController is null, or not on AudioRecordFragment, or action is invalid. Current dest: " +
                    (navController != null && navController.getCurrentDestination() != null ? navController.getCurrentDestination().getLabel() : "null"));
            viewModel.postErrorMessage("Navigation error after recording.");
        }
    }

    private void cancelRecordingAndCleanup() {
        Log.i(TAG, "User cancelled recording. Cleaning up...");
        sessionFinishedOrCancelled = true; // Mark as handled

        if (isBound && recordingService != null) {
            List<File> segmentFilesToCancel = recordingService.getEncryptedSegmentFiles(); // May be empty if startRecording failed early
            // Service handles its own stop/cleanup of MediaRecorder and streams
            recordingService.stopRecordingAndForeground(); // Ensure service stops everything

            if (segmentFilesToCancel != null && !segmentFilesToCancel.isEmpty()) {
                Log.d(TAG, "Securely deleting " + segmentFilesToCancel.size() + " encrypted segment files on cancel");
                // Run secure delete in background to avoid blocking UI thread
                if (executorService != null && !executorService.isShutdown()) {
                    executorService.submit(() -> {
                        for (File segmentFile : segmentFilesToCancel) {
                            if (segmentFile.exists()) {
                                AESUtil.secureDelete(segmentFile);
                                Log.d(TAG, "Background secure delete finished for segment: " + segmentFile.getName());
                            }
                        }
                        Log.d(TAG, "Background secure delete finished for all cancelled segment files.");
                    });
                } else {
                    Log.w(TAG, "Executor service not available, deleting files synchronously.");
                    for (File segmentFile : segmentFilesToCancel) {
                        if (segmentFile.exists()) {
                            AESUtil.secureDelete(segmentFile);
                        }
                    }
                }
            } else {
                Log.d(TAG, "No encrypted segment files to delete or list was empty.");
            }
        }
        viewModel.clearSensitiveSessionData(); // Clear DEK and path from ViewModel
        Log.d(TAG, "Cleanup after cancellation finished.");
    }


    // --- Recording Indicator Blink ---
    private void startRecordingIndicator() {
        if (recordingIndicator == null || recordingStatusText == null || !isAdded()) return;
        recordingIndicator.setVisibility(View.VISIBLE);
        recordingStatusText.setText(R.string.status_recording);
        recordingStatusText.setTextColor(Color.RED);

        blinkHandler.removeCallbacks(blinkRunnable); // Remove existing
        blinkRunnable = new Runnable() {
            private boolean isVisible = true;
            @Override
            public void run() {
                if (getView() != null && isAdded() && isBound && recordingService != null &&
                        recordingService.isRecording() && !recordingService.isPaused()) {
                    isVisible = !isVisible;
                    if(recordingIndicator != null) recordingIndicator.setAlpha(isVisible ? 1.0f : 0.4f);
                    blinkHandler.postDelayed(this, 600); // Blink speed
                } else { // Stop blinking if conditions are not met
                    blinkHandler.removeCallbacks(this);
                    if(recordingIndicator != null) recordingIndicator.setAlpha(1.0f); // Reset alpha
                }
            }
        };
        if(recordingIndicator != null) recordingIndicator.setAlpha(1.0f); // Start fully visible
        blinkHandler.post(blinkRunnable);
    }

    private void pauseRecordingIndicator() {
        if (recordingIndicator == null || recordingStatusText == null || !isAdded()) return;
        blinkHandler.removeCallbacks(blinkRunnable);
        if(recordingIndicator != null) {
            recordingIndicator.setAlpha(1.0f); // Solid
            recordingIndicator.setVisibility(View.VISIBLE); // Ensure visible
        }
        if(recordingStatusText != null) {
            recordingStatusText.setText(R.string.status_paused);
            recordingStatusText.setTextColor(ContextCompat.getColor(requireContext(), R.color.orange)); // Pause color
        }
    }

    private void resumeRecordingIndicator() {
        // This will set text to "Recording" and start blinking
        startRecordingIndicator();
    }

    private void stopRecordingIndicator() {
        if (recordingIndicator == null || !isAdded()) return;
        blinkHandler.removeCallbacks(blinkRunnable);
        if(recordingIndicator != null) {
            recordingIndicator.setAlpha(1.0f);
            recordingIndicator.setVisibility(View.INVISIBLE);
        }
        // Status text will be updated by updateUiBasedOnServiceState to "Ready to record" or similar
    }

    private String formatElapsedTime(long elapsedMillis) {
        long seconds = (elapsedMillis / 1000) % 60;
        long minutes = (elapsedMillis / (1000 * 60)) % 60;
        long hours = (elapsedMillis / (1000 * 60 * 60));
        if (hours > 0) {
            return String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
        }
    }
}