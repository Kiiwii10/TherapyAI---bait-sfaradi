package com.example.therapyai.ui.sessions.defaultAudio;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.security.keystore.UserNotAuthenticatedException; // Important
import android.util.Base64;
import android.util.Log;
import androidx.core.app.NotificationCompat;

import com.example.therapyai.R;
import com.example.therapyai.ui.sessions.session.SessionHostActivity;
import com.example.therapyai.util.AESUtil;
import com.example.therapyai.util.HIPAAKeyManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays; // For clearing sensitive byte arrays
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;

public class RecordingService extends Service {

    private static final String TAG = "RecordingService";
    private static final String CHANNEL_ID = "RecordingServiceChannel";
    private static final int NOTIFICATION_ID = 1;
    private static final int CIPHER_REINIT_INTERVAL = 1000; // Reinitialize cipher every 1000 chunks (~30-60 seconds)
    private final IBinder binder = new LocalBinder();
    private int chunkCounter = 0;
    // Real-time encryption components
    private AudioRecord audioRecord;
    private Cipher encryptionCipher; // MODIFIED: Replaced CipherOutputStream with a direct Cipher object
    private FileOutputStream fileOutputStream;
    private Thread recordingThread;

    // --- Multi-File Encryption Specific ---
    private List<File> encryptedSegmentFiles = new ArrayList<>(); // List of encrypted segment files
    private File currentSegmentFile = null;         // Current segment being written
    private SecretKey currentSessionDEK_plaintext = null; // Plaintext DEK for audio encryption
    private String currentEncryptedDEK_b64 = null;   // KEK-encrypted DEK, base64 encoded
    private int segmentCounter = 0;                 // Counter for segment files
    // --- End Multi-File Encryption Specific ---

    // Audio recording configuration
    private static final int SAMPLE_RATE = 44100;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int BUFFER_SIZE = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);

    private volatile boolean isRecording = false;
    private volatile boolean isPaused = false;

    private Handler amplitudeHandler;
    private Runnable amplitudeRunnable;
    private AmplitudeListener amplitudeListener;

    private long startTimeMillis = 0;
    private long timeWhenPausedMillis = 0;

    // Executor for potential cleanup tasks if needed, though not strictly used in this flow for AESUtil.secureDelete
    private ExecutorService executorService = Executors.newSingleThreadExecutor();


    public interface AmplitudeListener {
        void onAmplitudeChanged(int amplitude);
    }

    public class LocalBinder extends Binder {
        RecordingService getService() {
            return RecordingService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate");
        amplitudeHandler = new Handler(Looper.getMainLooper());
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand received");
        // If service is killed, it will be restarted.
        // We don't want it to auto-start recording, state managed by client.
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind");
        return binder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.d(TAG, "onUnbind");
        return true; // Allow re-binding
    }

    @Override
    public void onRebind(Intent intent) {
        super.onRebind(intent);
        Log.d(TAG, "onRebind");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.w(TAG, "onDestroy - Service is being destroyed!");
        // Ensure recording is stopped and resources are cleaned up
        stopRecordingInternal(true, false); // stopForeground=true, encryptionSetupFailed=false (normal shutdown)

        if (!executorService.isShutdown()) {
            executorService.shutdown();
        }
        clearSensitiveKeys();
        Log.d(TAG,"RecordingService onDestroy finished.");
    }

    private void clearSensitiveKeys() {
        if (currentSessionDEK_plaintext != null) {
            // Overwrite plaintext DEK bytes if accessible, otherwise just nullify
            // For SecretKey objects, nullifying is the primary way if bytes aren't directly held.
            currentSessionDEK_plaintext = null;
            Log.d(TAG, "Plaintext DEK cleared from service memory.");
        }
        currentEncryptedDEK_b64 = null; // This is already encrypted, but good practice
    }    /**
     * Starts the recording process with real-time encryption.
     * 1. Generates a Data Encryption Key (DEK).
     * 2. Encrypts the DEK with a Key Encryption Key (KEK) from Android Keystore.
     * 3. Sets up AudioRecord with CipherOutputStream for real-time encryption.
     * 4. Records audio directly to encrypted file (no temporary raw file needed).
     *
     * @throws IOException If file operations or AudioRecord setup fails.
     * @throws SecurityException If KEK access requires user authentication and it's not provided,
     *                           or other security issues arise during key generation/encryption.
     */
    public void startRecording() throws IOException, SecurityException {
        if (isRecording) {
            Log.w(TAG, "startRecording called but already recording.");
            return;
        }
        Log.i(TAG, "Attempting to start real-time encrypted recording...");

        // --- Key Generation and Encryption ---
        byte[] dekBytesPlain = null;
        byte[] kekEncryptedDekWithIv = null;

        try {
            // 1. Generate plaintext DEK for this session
            currentSessionDEK_plaintext = AESUtil.generateRandomAesKey();
            dekBytesPlain = currentSessionDEK_plaintext.getEncoded();
            Log.d(TAG, "Plaintext DEK generated for audio encryption. Hash: " + Arrays.hashCode(dekBytesPlain));

            // 2. Get KEK from Android Keystore
            SecretKey kek = HIPAAKeyManager.getOrCreateKey();

            // 3. Encrypt plaintext DEK with KEK
            kekEncryptedDekWithIv = AESUtil.encryptAesGcm(dekBytesPlain, kek);
            currentEncryptedDEK_b64 = Base64.encodeToString(kekEncryptedDekWithIv, Base64.NO_WRAP);
            Log.d(TAG, "DEK encrypted with KEK. Encrypted DEK (b64 prefix): " +
                    (currentEncryptedDEK_b64.length() > 16 ? currentEncryptedDEK_b64.substring(0,16) : currentEncryptedDEK_b64));

            // --- Multi-File Encryption Setup ---
            // Clear any existing segment files
            encryptedSegmentFiles.clear();
            segmentCounter = 0;
            
            // Create first segment file
            createNewSegmentFile();
            
            Log.i(TAG, "Multi-file encrypted recording initialized. First segment: " + currentSegmentFile.getAbsolutePath());

            // REMOVED: CipherOutputStream is no longer used.
            // cipherOutputStream = new CipherOutputStream(fileOutputStream, cipher);

            // Set up AudioRecord for real-time recording with memory-conscious buffer size
            int optimizedBufferSize = Math.max(BUFFER_SIZE, BUFFER_SIZE * 2); // Use minimum safe buffer size
            audioRecord = new AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    optimizedBufferSize // Optimized buffer size for memory efficiency
            );

            Log.d(TAG, "AudioRecord buffer size: " + optimizedBufferSize + " bytes");

            if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                throw new IOException("AudioRecord initialization failed");
            }

            // Start recording
            audioRecord.startRecording();
            isRecording = true;
            isPaused = false;

            // Start recording thread for real-time encryption
            startRecordingThread();

            Log.i(TAG, "Real-time encrypted recording started. First segment -> " + currentSegmentFile.getAbsolutePath());

            startForeground(NOTIFICATION_ID, createNotification(getString(R.string.status_recording)));
            startTimeMillis = SystemClock.elapsedRealtime();
            timeWhenPausedMillis = 0;

        } catch (UserNotAuthenticatedException unae) {
            Log.e(TAG, "Keystore key authentication required (likely for KEK).", unae);
            stopRecordingInternal(false, true);
            clearSensitiveKeys();
            throw new SecurityException("User authentication required for Keystore key.", unae);
        } catch (Exception e) {
            Log.e(TAG, "Failed to start real-time encrypted recording.", e);
            stopRecordingInternal(false, true);
            clearSensitiveKeys();
            if (e instanceof IOException) throw (IOException) e;
            throw new SecurityException("Real-time encryption setup or recording start failed: " + e.getMessage(), e);
        } finally {
            // Securely clear intermediate byte arrays
            if (dekBytesPlain != null) Arrays.fill(dekBytesPlain, (byte) 0);
            if (kekEncryptedDekWithIv != null) Arrays.fill(kekEncryptedDekWithIv, (byte) 0);
        }    }

    /**
     * This thread manually encrypts chunks of data using Cipher.update()
     * with aggressive memory management to prevent OOM errors.
     */
    private void startRecordingThread() {
        recordingThread = new Thread(new Runnable() {
            @Override
            public void run() {
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO);

                // Use smaller, more memory-efficient buffers
                byte[] audioBuffer = new byte[BUFFER_SIZE];
                int maxAmplitude = 0;
                long lastAmplitudeUpdate = 0;

                try {
                    while (isRecording && !Thread.currentThread().isInterrupted()) {
                        if (!isPaused) {
                            int bytesRead = audioRecord.read(audioBuffer, 0, audioBuffer.length);

                            if (bytesRead > 0) {
                                try {
                                    // MULTI-FILE APPROACH: Create new segment file periodically
                                    if (chunkCounter > 0 && chunkCounter % CIPHER_REINIT_INTERVAL == 0) {
                                        Log.d(TAG, "Creating new segment file to prevent OOM (chunk: " + chunkCounter + ")");
                                        
                                        // Finalize current segment
                                        finalizeCurrentSegment();
                                        
                                        // Create new segment file
                                        createNewSegmentFile();
                                        
                                        Log.d(TAG, "New segment file created: " + currentSegmentFile.getName());
                                    }

                                    // Encrypt the audio chunk for current segment
                                    byte[] encryptedChunk = encryptionCipher.update(audioBuffer, 0, bytesRead);
                                    
                                    if (encryptedChunk != null && encryptedChunk.length > 0) {
                                        fileOutputStream.write(encryptedChunk);
                                        fileOutputStream.flush(); // Ensure data is written immediately
                                    }
                                    
                                    // Immediately clear the encrypted chunk to free memory
                                    if (encryptedChunk != null) {
                                        Arrays.fill(encryptedChunk, (byte) 0);
                                    }
                                    
                                    chunkCounter++;
                                    
                                    // Memory cleanup every 100 chunks
                                    if (chunkCounter % 100 == 0) {
                                        System.gc();
                                    }
                                    
                                } catch (Exception e) {
                                    Log.e(TAG, "Error during encryption: " + e.getMessage(), e);
                                    break;
                                }

                                // Amplitude calculation (unchanged)
                                long currentTime = System.currentTimeMillis();
                                int currentMaxAmplitude = calculateAmplitude(audioBuffer, bytesRead);
                                if (currentMaxAmplitude > maxAmplitude) {
                                    maxAmplitude = currentMaxAmplitude;
                                }

                                if (currentTime - lastAmplitudeUpdate >= 100) {
                                    final int amplitudeToReport = maxAmplitude;
                                    amplitudeHandler.post(() -> {
                                        if (amplitudeListener != null) {
                                            amplitudeListener.onAmplitudeChanged(amplitudeToReport);
                                        }
                                    });
                                    maxAmplitude = 0;
                                    lastAmplitudeUpdate = currentTime;
                                }
                            } else if (bytesRead < 0) {
                                Log.e(TAG, "AudioRecord read error: " + bytesRead);
                                break;
                            }
                        } else {
                            try {
                                Thread.sleep(100);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                break;
                            }
                        }
                    }
                } finally {
                    // Always clear sensitive buffers
                    Arrays.fill(audioBuffer, (byte) 0);
                    
                    // Reset chunk counter
                    chunkCounter = 0;
                    
                    // Force garbage collection to help with memory cleanup
                    System.gc();
                }

                Log.d(TAG, "Recording thread finished");
            }
        });
        recordingThread.start();
    }


    /**
     * Calculates amplitude from PCM audio data (similar to MediaRecorder.getMaxAmplitude()).
     */
    private int calculateAmplitude(byte[] audioBuffer, int bytesRead) {
        int maxAmplitude = 0;
        // Process 16-bit PCM data (2 bytes per sample)
        for (int i = 0; i < bytesRead - 1; i += 2) {
            short sample = (short) ((audioBuffer[i + 1] << 8) | (audioBuffer[i] & 0xFF));
            int amplitude = Math.abs(sample);
            if (amplitude > maxAmplitude) {
                maxAmplitude = amplitude;
            }
        }
        // Scale to match MediaRecorder range (0-32767)
        return maxAmplitude;
    }

    /**
     * Handles the finalization of the manual encryption and cleans up resources.
     * It now calls cipher.doFinal() to complete the encryption process, which is critical for
     * stream integrity (especially for GCM mode), and then closes the file stream.
     */
    private void stopRecordingInternal(boolean stopForegroundService, boolean encryptionSetupFailed) {
        Log.d(TAG, "stopRecordingInternal called. stopForeground=" + stopForegroundService + ", encryptionSetupFailed=" + encryptionSetupFailed);

        // Signal the recording thread to stop and wait for it to finish.
        isRecording = false;
        isPaused = false;
        if (recordingThread != null && recordingThread.isAlive()) {
            recordingThread.interrupt();
            try {
                recordingThread.join(1000); // Wait up to 1 second for thread to finish
            } catch (InterruptedException e) {
                Log.w(TAG, "Interrupted while waiting for recording thread to finish");
                Thread.currentThread().interrupt();
            }
            recordingThread = null;
        }

        // Stop AudioRecord
        if (audioRecord != null) {
            try {
                if (audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                    audioRecord.stop();
                    Log.d(TAG, "AudioRecord stopped.");
                }
            } catch (IllegalStateException e) {
                Log.e(TAG, "Error stopping AudioRecord: " + e.getMessage());
            }
            audioRecord.release();
            audioRecord = null;
        }

        // Finalize current segment
        try {
            if (encryptionCipher != null && fileOutputStream != null) {
                Log.d(TAG, "Finalizing current segment...");
                finalizeCurrentSegment();
                Log.d(TAG, "Current segment finalized. Total segments: " + encryptedSegmentFiles.size());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error finalizing current segment", e);
            encryptionSetupFailed = true;
        } finally {
            fileOutputStream = null;
            encryptionCipher = null;
            currentSegmentFile = null;
        }

        // Validate encryption if recording was successful
        if (!encryptionSetupFailed && !encryptedSegmentFiles.isEmpty() && currentSessionDEK_plaintext != null) {
            if (validateEncryptedSegments()) {
                Log.i(TAG, "Multi-file encryption validation passed for " + encryptedSegmentFiles.size() + " segments.");
            } else {
                Log.e(TAG, "Multi-file encryption validation failed");
                // Clean up invalid encrypted files
                cleanupSegmentFiles();
            }
        }

        if (stopForegroundService) {
            Log.d(TAG, "Stopping foreground service state.");
            stopForeground(true);
        }

        if (encryptionSetupFailed) {
            Log.w(TAG, "Encryption setup failed. Cleaning up potentially incomplete/invalid files.");
            cleanupSegmentFiles();
            currentEncryptedDEK_b64 = null;
            currentSessionDEK_plaintext = null;
        }
    }
    public void pauseRecording() {
        if (!isRecording || isPaused || audioRecord == null) return;
        try {
            isPaused = true;
            timeWhenPausedMillis = SystemClock.elapsedRealtime() - startTimeMillis;
            updateNotification(getString(R.string.status_paused));
            Log.i(TAG, "Recording paused.");
        } catch (Exception e) {
            Log.e(TAG, "Failed to pause recording", e);
        }
    }

    public void resumeRecording() {
        if (!isRecording || !isPaused || audioRecord == null) return;
        try {
            isPaused = false;
            startTimeMillis = SystemClock.elapsedRealtime() - timeWhenPausedMillis; // Adjust start time
            updateNotification(getString(R.string.status_recording));
            Log.i(TAG, "Recording resumed.");
        } catch (Exception e) {
            Log.e(TAG, "Failed to resume recording", e);
        }
    }

    /**
     * Stops recording and the foreground service.
     * The encrypted file and encrypted DEK remain available.
     * Plaintext DEK is cleared after this call if not needed by client.
     */
    public void stopRecordingAndForeground() {
        Log.i(TAG, "Stopping recording and foreground service.");
        stopRecordingInternal(true, false); // stopForeground=true, not an encryptionSetupFailure
        // The plaintext DEK (currentSessionDEK_plaintext) is NOT cleared here.
        // It's assumed the client (AudioRecordFragment) has retrieved the encrypted file path
        // and encrypted DEK. The plaintext DEK will be cleared when the service is fully unbound/destroyed
        // or if a new recording starts.
    }


    public boolean isRecording() { return isRecording; }
    public boolean isPaused() { return isPaused; }

    public List<File> getEncryptedSegmentFiles() {
        return new ArrayList<>(encryptedSegmentFiles);
    }

    /**
     * @return The Base64 encoded (IV + KEK-encrypted DEK). Null if not generated or error.
     */
    public String getEncryptedDEK_Base64() {
        return currentEncryptedDEK_b64;
    }    public long getElapsedTimeMillis() {
        if (!isRecording && startTimeMillis == 0) return 0; // Not started or explicitly stopped and reset
        if (!isRecording && startTimeMillis != 0) return timeWhenPausedMillis; // Was paused then stopped
        return isPaused ? timeWhenPausedMillis : (SystemClock.elapsedRealtime() - startTimeMillis);
    }

    public void setAmplitudeListener(AmplitudeListener listener) { this.amplitudeListener = listener; }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Audio Recording",
                    NotificationManager.IMPORTANCE_LOW); // Low importance = no sound/vibration
            channel.setDescription("Displays status while recording audio session");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification createNotification(String text) {
        Intent notificationIntent = new Intent(this, SessionHostActivity.class); // Or your main app activity
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP); // Resume existing or create new
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.app_name)) // Or specific title
                .setContentText(text)
                .setSmallIcon(R.drawable.baseline_mic_24) // Ensure this icon exists
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .build();
    }

    private void updateNotification(String text) {
        Notification notification = createNotification(text);
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, notification);
        }
    }

    /**
     * Creates a new segment file and initializes encryption for it
     */
    private void createNewSegmentFile() throws Exception {
        segmentCounter++;
        currentSegmentFile = new File(getCacheDir(), "enc_segment_" + System.currentTimeMillis() + "_" + segmentCounter + ".aes");
        
        // Close previous stream if exists
        if (fileOutputStream != null) {
            try {
                fileOutputStream.close();
            } catch (IOException e) {
                Log.w(TAG, "Error closing previous segment stream", e);
            }
        }
        
        // Set up new encrypted output stream
        fileOutputStream = new FileOutputStream(currentSegmentFile);
        encryptionCipher = AESUtil.createEncryptionCipher(currentSessionDEK_plaintext);
        
        // Write IV to beginning of segment file
        byte[] iv = encryptionCipher.getIV();
        if (iv == null || iv.length != AESUtil.GCM_IV_LENGTH) {
            throw new SecurityException("Generated IV is not of the expected GCM length.");
        }
        fileOutputStream.write(iv);
        fileOutputStream.flush();
        
        Log.d(TAG, "Created new segment file: " + currentSegmentFile.getName());
    }
    
    /**
     * Finalizes the current segment and adds it to the list
     */
    private void finalizeCurrentSegment() throws Exception {
        if (encryptionCipher != null && fileOutputStream != null) {
            // Finalize encryption for current segment
            byte[] finalBlock = encryptionCipher.doFinal();
            if (finalBlock != null && finalBlock.length > 0) {
                fileOutputStream.write(finalBlock);
                fileOutputStream.flush();
                Arrays.fill(finalBlock, (byte) 0);
            }
            
            fileOutputStream.close();
            
            // Add completed segment to list
            if (currentSegmentFile != null && currentSegmentFile.exists()) {
                encryptedSegmentFiles.add(currentSegmentFile);
                Log.d(TAG, "Finalized segment: " + currentSegmentFile.getName() + " (size: " + currentSegmentFile.length() + " bytes)");
            }
        }
    }
    
    /**
     * Validates that all encrypted segments can be decrypted
     */
    private boolean validateEncryptedSegments() {
        if (encryptedSegmentFiles.isEmpty() || currentSessionDEK_plaintext == null) {
            Log.e(TAG, "Cannot validate: no encrypted segments or DEK is missing");
            return false;
        }
        
        // Validate each segment individually
        for (int i = 0; i < encryptedSegmentFiles.size(); i++) {
            File segmentFile = encryptedSegmentFiles.get(i);
            if (!validateSingleSegment(segmentFile, i)) {
                return false;
            }
        }
        
        Log.d(TAG, "All " + encryptedSegmentFiles.size() + " segments validated successfully");
        return true;
    }
    
    /**
     * Validates a single encrypted segment
     */
    private boolean validateSingleSegment(File segmentFile, int segmentIndex) {
        if (!segmentFile.exists()) {
            Log.e(TAG, "Segment " + segmentIndex + " does not exist: " + segmentFile.getName());
            return false;
        }
        
        File tempDecryptedFile = null;
        try {
            tempDecryptedFile = new File(getCacheDir(), "validation_segment_" + segmentIndex + "_" + System.currentTimeMillis() + ".tmp");
            AESUtil.decryptFile(segmentFile, tempDecryptedFile, currentSessionDEK_plaintext);
            
            if (tempDecryptedFile.exists() && tempDecryptedFile.length() > 0) {
                Log.d(TAG, "Segment " + segmentIndex + " validation successful. Decrypted size: " + tempDecryptedFile.length());
                return true;
            } else {
                Log.e(TAG, "Segment " + segmentIndex + " validation failed: Decrypted file is empty or doesn't exist");
                return false;
            }
        } catch (Exception e) {
            Log.e(TAG, "Segment " + segmentIndex + " validation failed: Cannot decrypt", e);
            return false;
        } finally {
            if (tempDecryptedFile != null && tempDecryptedFile.exists()) {
                AESUtil.secureDelete(tempDecryptedFile);
            }
        }
    }
    
    /**
     * Cleans up all segment files
     */
    private void cleanupSegmentFiles() {
        for (File segmentFile : encryptedSegmentFiles) {
            if (segmentFile.exists()) {
                Log.d(TAG, "Deleting segment file: " + segmentFile.getName());
                AESUtil.secureDelete(segmentFile);
            }
        }
        encryptedSegmentFiles.clear();
        
        if (currentSegmentFile != null && currentSegmentFile.exists()) {
            Log.d(TAG, "Deleting current segment file: " + currentSegmentFile.getName());
            AESUtil.secureDelete(currentSegmentFile);
        }
        currentSegmentFile = null;
    }
    
    /**
     * Stitches all encrypted segments into a single decrypted audio file
     * This method should be called when preparing to send the audio
     */
    public File stitchSegmentsToDecryptedFile() throws Exception {
        if (encryptedSegmentFiles.isEmpty() || currentSessionDEK_plaintext == null) {
            throw new IllegalStateException("No encrypted segments or DEK not available for stitching");
        }
        
        File stitchedFile = new File(getCacheDir(), "stitched_audio_" + System.currentTimeMillis() + ".pcm");
        
        try (FileOutputStream stitchedOutput = new FileOutputStream(stitchedFile)) {
            Log.i(TAG, "Stitching " + encryptedSegmentFiles.size() + " segments into decrypted file");
            
            for (int i = 0; i < encryptedSegmentFiles.size(); i++) {
                File segmentFile = encryptedSegmentFiles.get(i);
                File tempDecrypted = null;
                
                try {
                    // Decrypt segment to temporary file
                    tempDecrypted = new File(getCacheDir(), "temp_decrypt_segment_" + i + "_" + System.currentTimeMillis() + ".tmp");
                    AESUtil.decryptFile(segmentFile, tempDecrypted, currentSessionDEK_plaintext);
                    
                    // Copy decrypted data to stitched file
                    try (FileInputStream segmentInput = new FileInputStream(tempDecrypted)) {
                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        while ((bytesRead = segmentInput.read(buffer)) != -1) {
                            stitchedOutput.write(buffer, 0, bytesRead);
                        }
                        Arrays.fill(buffer, (byte) 0);
                    }
                    
                    Log.d(TAG, "Stitched segment " + i + ": " + segmentFile.getName());
                    
                } finally {
                    // Clean up temporary decrypted file
                    if (tempDecrypted != null && tempDecrypted.exists()) {
                        AESUtil.secureDelete(tempDecrypted);
                    }
                }
            }
            
            stitchedOutput.flush();
        }
        
        Log.i(TAG, "Stitching completed. Final file size: " + stitchedFile.length() + " bytes");
        return stitchedFile;
    }
    /**
     * Provides diagnostic information about the current recording state.
     * Useful for debugging encryption issues.
     */
    public String getDiagnosticInfo() {
        StringBuilder info = new StringBuilder();
        info.append("RecordingService Diagnostic Info (Multi-File Encryption):\n");
        info.append("- isRecording: ").append(isRecording).append("\n");
        info.append("- isPaused: ").append(isPaused).append("\n");
        info.append("- audioRecord: ").append(audioRecord != null ? "present (state: " + audioRecord.getState() + ")" : "null").append("\n");
        info.append("- recordingThread: ").append(recordingThread != null ? "present (alive: " + recordingThread.isAlive() + ")" : "null").append("\n");
        info.append("- encryptedSegmentFiles: ").append(encryptedSegmentFiles.size()).append(" segments\n");
        for (int i = 0; i < encryptedSegmentFiles.size(); i++) {
            File segment = encryptedSegmentFiles.get(i);
            info.append("  [").append(i).append("] ").append(segment.getName());
            info.append(" (exists: ").append(segment.exists()).append(", size: ");
            info.append(segment.exists() ? segment.length() : "N/A").append(")\n");
        }
        info.append("- currentSegmentFile: ").append(currentSegmentFile != null ? currentSegmentFile.getName() : "null");
        if (currentSegmentFile != null) {
            info.append(" (exists: ").append(currentSegmentFile.exists()).append(", size: ");
            info.append(currentSegmentFile.exists() ? currentSegmentFile.length() : "N/A").append(")");
        }
        info.append("\n");
        info.append("- segmentCounter: ").append(segmentCounter).append("\n");
        info.append("- currentSessionDEK_plaintext: ").append(currentSessionDEK_plaintext != null ? "present" : "null").append("\n");
        info.append("- currentEncryptedDEK_b64: ").append(currentEncryptedDEK_b64 != null ? "present (length: " + currentEncryptedDEK_b64.length() + ")" : "null").append("\n");
        info.append("- encryptionCipher: ").append(encryptionCipher != null ? "present" : "null").append("\n");
        return info.toString();
    }
}