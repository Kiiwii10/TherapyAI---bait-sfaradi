package com.example.therapyai.data.repository;

import android.content.Context;
import android.security.keystore.UserNotAuthenticatedException; // Important
import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.therapyai.data.local.EphemeralPrefs;
import com.example.therapyai.data.local.models.NoteCard;
import com.example.therapyai.data.remote.TherapyApiImpl;
import com.example.therapyai.data.remote.models.SessionSubmissionResponse;
import com.example.therapyai.util.AESUtil;
import com.example.therapyai.util.AudioFormatUtil;
import com.example.therapyai.util.HIPAAKeyManager;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Arrays; // For clearing sensitive byte arrays
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import javax.crypto.AEADBadTagException; // Important
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okio.BufferedSink; // Correct import for BufferedSink

public class RecordingRepository {
    private static final String TAG = "RecordingRepository";

    private static RecordingRepository instance;
    private final TherapyApiImpl apiImpl;
    private final EphemeralPrefs ephemeralPrefs;
    private final Gson gson;

    private RecordingRepository(TherapyApiImpl apiImpl) {
        this.ephemeralPrefs = EphemeralPrefs.getInstance();
        this.apiImpl = apiImpl;
        this.gson = new Gson();
    }

    public static synchronized void init(boolean useMockData) {
        if (instance == null) {
            instance = new RecordingRepository(TherapyApiImpl.getInstance(useMockData));
        }
    }

    public static synchronized RecordingRepository getInstance() {
        if (instance == null) {
            throw new IllegalStateException("RecordingRepository not initialized! Call init() first.");
        }
        return instance;
    }

    public interface RecordingSubmissionCallback {
        void onSuccess(SessionSubmissionResponse response);
        void onFailure(String errorMessage); // errorMessage should be user-friendly or interpretable
    }

    public interface UploadProgressCallback {
        void onProgress(int progressPercent, String statusMessage);
    }


    public void uploadRecordingSession(List<String> encryptedAudioFilePaths,
                                       String encryptedDekBase64, // KEK-encrypted DEK (IV + Ciphertext), Base64
                                       String patientInfo,
                                       String therapistInfo,
                                       List<NoteCard> noteCards,
                                       RecordingSubmissionCallback callback,
                                       UploadProgressCallback progressCallback)
    {


        byte[] plaintextDekBytes = null;      // Intermediate byte array for DEK

        try {
            // HIPAA Compliance: Validate audio configuration before processing
            if (!AudioFormatUtil.validateAudioConfiguration()) {
                Log.e(TAG, "HIPAA Compliance Error: Audio configuration validation failed");
                callback.onFailure("Audio configuration does not meet security requirements.");
                logAuditEvent("SESSION_UPLOAD_ERROR_CONFIG", "Audio configuration failed HIPAA validation");
                return;
            }

            if (encryptedAudioFilePaths == null || encryptedAudioFilePaths.isEmpty()) {
                Log.e(TAG, "Encrypted audio file is missing or too small: " + encryptedAudioFilePaths +
                    " (exists: " + encryptedAudioFilePaths + ", length: " +
                    (encryptedAudioFilePaths == null ? encryptedAudioFilePaths.size() : "N/A") + ")");
                callback.onFailure("Encrypted audio file not found or is invalid.");
                logAuditEvent("SESSION_UPLOAD_ERROR_FILE", "Encrypted file missing/invalid: " + encryptedAudioFilePaths);
                return;
            }
            if (encryptedDekBase64 == null || encryptedDekBase64.isEmpty()) {
                Log.e(TAG, "Encrypted Data Encryption Key (DEK) is missing.");
                callback.onFailure("Session encryption key is missing.");
                logAuditEvent("SESSION_UPLOAD_ERROR_DEK", "Encrypted DEK missing.");
                return;
            }


            Log.i(TAG, "Preparing to upload session. Audio File: " + encryptedAudioFilePaths + ", Size: " + encryptedAudioFilePaths.size());
            Log.d(TAG, "Encrypted DEK (b64 prefix for log): " + (encryptedDekBase64.length() > 16 ? encryptedDekBase64.substring(0,16) : encryptedDekBase64));

            // --- 1. Decrypt the DEK using KEK from Keystore ---
            SecretKey kek = HIPAAKeyManager.getOrCreateKey();
            byte[] combinedIvAndEncryptedDek = Base64.decode(encryptedDekBase64, Base64.NO_WRAP);
            plaintextDekBytes = AESUtil.decryptAesGcm(combinedIvAndEncryptedDek, kek);
            final SecretKey plaintextSessionDEK = new SecretKeySpec(plaintextDekBytes, "AES");


            RequestBody requestFile = new RequestBody() {
                @Nullable
                @Override
                public MediaType contentType() {
                    return MediaType.parse(AudioFormatUtil.getAudioMimeType());
                }

                @Override
                public long contentLength() {
                    return -1; // Use chunked transfer encoding
                }

                @Override
                public void writeTo(@NonNull BufferedSink sink) throws IOException {
                    Log.d(TAG, "RequestBody.writeTo: Starting SEQUENTIAL on-the-fly decryption of " + encryptedAudioFilePaths.size() + " segments.");

                    final int totalFiles = encryptedAudioFilePaths.size();
                    if (totalFiles == 0) return;

//                    // --- STAGED PROGRESS CONFIGURATION ---
//                    final int PREPARATION_PROGRESS_MAX = 70; // Use 0-70% for the preparation stage
//                    final int UPLOAD_STAGE_PROGRESS = 85;    // Jump to 85% when upload begins

                    long estimatedDecryptedSize = 0;
                    for (String path : encryptedAudioFilePaths) {
                        long fileLen = new File(path).length();
                        // Subtract the IV length from each file's size for an accurate estimate.
                        estimatedDecryptedSize += Math.max(0, fileLen - AESUtil.GCM_IV_LENGTH);
                    }
                    if (estimatedDecryptedSize <= 0) {
                        Log.w(TAG, "Estimated decrypted size is zero. Cannot report progress accurately.");
                        // Fallback to a simple message.
                        if (progressCallback != null) {
                            progressCallback.onProgress(50, "Processing...");
                        }
                    }

                    OutputStream networkOutputStream = sink.outputStream();
                    long totalBytesProcessed = 0;
                    int lastReportedProgress = -1;

                    try {
                        // 2. Write the single WAV header to the network stream once at the start.
                        AudioFormatUtil.writeWavHeader(networkOutputStream, estimatedDecryptedSize);

                        byte[] buffer = new byte[8192]; // A reusable buffer for reading chunks.
                        int bytesRead;

                        // 3. Loop through each segment file, processing one at a time sequentially.
                        for (String path : encryptedAudioFilePaths) {
                            File segmentFile = new File(path);
                            Log.d(TAG, "Processing segment: " + segmentFile.getName());

                            // Use try-with-resources to ensure each file's streams are closed, freeing memory.
                            try (FileInputStream fis = new FileInputStream(segmentFile);
                                 InputStream cis = AESUtil.createDecryptingInputStream(fis, plaintextSessionDEK)) {

                                // 4. Read decrypted chunks from the current file until it's finished.
                                while ((bytesRead = cis.read(buffer)) != -1) {
                                    // Write the decrypted chunk to the network.
                                    networkOutputStream.write(buffer, 0, bytesRead);
                                    totalBytesProcessed += bytesRead;

                                    // 5. Calculate and report progress.
                                    if (estimatedDecryptedSize > 0 && progressCallback != null) {
                                        int progress = (int) ((totalBytesProcessed * 100) / estimatedDecryptedSize);

                                        // Only report when the percentage value actually changes to avoid spamming the UI thread.
                                        if (progress > lastReportedProgress) {
                                            // Clamp progress to 99% max. The final 100% is set on API success response.
                                            int displayProgress = Math.min(progress, 99);
                                            progressCallback.onProgress(displayProgress, "Uploading...");
                                            lastReportedProgress = progress;
                                        }
                                    }
                                }
                            } // <- fis and cis for this segment are closed here.
                        }

                        sink.flush();
                        Log.i(TAG, "Successfully streamed all segments. Total bytes processed: " + totalBytesProcessed);

                    } catch (Exception e) {
                        Log.e(TAG, "Error during sequential streaming decryption", e);
                        throw new IOException("Failed to decrypt and stream audio segments: " + e.getMessage(), e);
                    }
                }
            };

            // --- 3. Prepare metadata and execute API call (same as before) ---
            String originalFileName = "session_audio_" + UUID.randomUUID().toString() + AudioFormatUtil.getAudioFileExtension();
            MultipartBody.Part filePart = MultipartBody.Part.createFormData("audio_file", originalFileName, requestFile);

            String metadataJson = prepareMetadata(noteCards, patientInfo, therapistInfo);
            RequestBody metadataBody = RequestBody.create(MediaType.parse("application/json"), metadataJson);

            String token = ephemeralPrefs.getSessionToken();
            if (token == null || token.isEmpty()) {
                callback.onFailure("User session token is missing. Please log in again.");
                return;
            }

            apiImpl.uploadSessionData(
                    token,
                    filePart,
                    metadataBody,
                    new TherapyApiImpl.ApiCallback<SessionSubmissionResponse>() {
                        @Override
                        public void onSuccess(SessionSubmissionResponse result) {
                            Log.i(TAG, "Session upload (DEK decrypted stream) successful. Session ID: " + result.getSessionId());
                            logAuditEvent("SESSION_UPLOAD_SUCCESS", "Session ID: " + result.getSessionId());
                            if (progressCallback != null) {
                                progressCallback.onProgress(100, "Upload completed successfully!");
                            }
                            callback.onSuccess(result);
                            // File cleanup is handled by SessionHostActivity after success dialog
                        }

                        @Override
                        public void onError(String error) {
                            Log.e(TAG, "Session upload (DEK decrypted stream) API call failed. Error: " + error);
                            logAuditEvent("SESSION_UPLOAD_FAILURE_API", "API Error: " + error);
                            if (progressCallback != null) {
                                progressCallback.onProgress(0, "Upload failed: " + error);
                            }
                            callback.onFailure("Upload failed: " + error);
                            // File cleanup is NOT done here on failure, allowing for retry. SessionHostActivity handles cleanup.
                        }
                    });

        } catch (UserNotAuthenticatedException unae) {
            Log.e(TAG, "Keystore key authentication required to decrypt DEK.", unae);
            // This specific message is crucial for SessionHostActivity to trigger re-auth
            callback.onFailure("Key authentication required: User not authenticated for Keystore key operation.");
            logAuditEvent("SESSION_UPLOAD_ERROR_KEK_AUTH", unae.getMessage());
        } catch (AEADBadTagException ae) {
            Log.e(TAG, "AEADBadTagException while decrypting DEK. Likely KEK auth issue, corrupted DEK, or wrong key.", ae);
            // This also often indicates an issue with the KEK (e.g., auth expired, or key changed/invalidated)
            callback.onFailure("Key authentication error or session key corrupt: AEADBadTagException.");
            logAuditEvent("SESSION_UPLOAD_ERROR_DEK_DECRYPT_AEAD", ae.getMessage());
        } catch (Exception e) { // Catch other general exceptions during setup
            Log.e(TAG, "General error during upload preparation (DEK decryption or initial setup).", e);
            callback.onFailure("Error preparing upload: " + e.getMessage());
            logAuditEvent("SESSION_UPLOAD_PREPARATION_ERROR_GENERAL", e.getMessage());
        } finally {
            // Securely clear the plaintext DEK bytes from memory
            if (plaintextDekBytes != null) {
                Arrays.fill(plaintextDekBytes, (byte) 0);
                Log.d(TAG, "Plaintext DEK bytes cleared from repository memory.");
            }
            // The SecretKey object plaintextSessionDEK will be garbage collected.
        }
    }


    private String prepareMetadata(List<NoteCard> noteCards, String patientData, String therapistData) {
        JsonObject metadata = new JsonObject();
        JsonArray generalNotesArray = new JsonArray();
        JsonArray timedNotesArray = new JsonArray();
        String summaryContent = "";

        for (NoteCard card : noteCards) {
            if ("Summary".equals(card.getTitle())) {
                summaryContent = card.getContent() != null ? card.getContent() : "";
            } else if (card.getTimestamp() != null && !card.getTimestamp().isEmpty()) {
                JsonObject noteObject = new JsonObject();
                noteObject.addProperty("timestamp", card.getTimestamp());
                noteObject.addProperty("content", card.getContent() != null ? card.getContent() : "");
                timedNotesArray.add(noteObject);
            } else { // General note without specific timestamp
                JsonObject noteObject = new JsonObject();
                // noteObject.addProperty("title", card.getTitle()); // Optionally include title if distinct
                noteObject.addProperty("content", card.getContent() != null ? card.getContent() : "");
                generalNotesArray.add(noteObject);
            }
        }          SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault());
        String currentDate = sdf.format(new Date());// Parse therapist data from malformed JSON string to proper JSON object
        JsonObject therapistObject = parseActorData(therapistData, "therapist");
        JsonObject patientObject = parseActorData(patientData, "patient");

        metadata.addProperty("therapist", therapistObject.toString());
        metadata.addProperty("patient", patientObject.toString());
        metadata.addProperty("session_date", currentDate);
        metadata.addProperty("summary", summaryContent);
//        Log.d(TAG, "general_notes are: " + generalNotesArray.toString());
//        Log.d(TAG, "timed_notes are: " + timedNotesArray.toString());
        if (generalNotesArray.size() > 0) metadata.add("general_notes", generalNotesArray);
        if (timedNotesArray.size() > 0) metadata.add("timed_notes", timedNotesArray);
        // Add app version, device info, etc. if needed
        // metadata.addProperty("app_version", BuildConfig.VERSION_NAME);

        return gson.toJson(metadata);
    }

    private void logAuditEvent(String eventType, String details) {
        String userId = ephemeralPrefs.getUserId();
        Log.i("AUDIT_EVENT", eventType + " - User: " + (userId != null ? userId : "UNKNOWN") + " - Details: " + details);
    }

//    /**
//     * Format bytes into human readable format
//     */
//    private String formatBytes(long bytes) {
//        if (bytes < 1024) return bytes + " B";
//        else if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
//        else if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
//        else return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
//    }

    /**
     * Parse malformed JSON string data from SessionManager/FormFragment into proper JSON object
     * Input format: "{id: value, name: value, email: value, ...}"
     * Output: proper JSON object with string values
     */
    private JsonObject parseActorData(String malformedJsonString, String actorType) {
        JsonObject actorObject = new JsonObject();
        
        try {
            // Remove surrounding braces and split by commas
            String content = malformedJsonString.trim();
            if (content.startsWith("{") && content.endsWith("}")) {
                content = content.substring(1, content.length() - 1);
            }
            
            // Split by comma and parse key-value pairs
            String[] pairs = content.split(",\\s*");
            for (String pair : pairs) {
                String[] keyValue = pair.split(":\\s*", 2);
                if (keyValue.length == 2) {
                    String key = keyValue[0].trim();
                    String value = keyValue[1].trim();
                    
                    // Remove any quotes from key
                    if (key.startsWith("\"") && key.endsWith("\"")) {
                        key = key.substring(1, key.length() - 1);
                    }
                    
                    // Remove any quotes from value
                    if (value.startsWith("\"") && value.endsWith("\"")) {
                        value = value.substring(1, value.length() - 1);
                    }
                    
                    actorObject.addProperty(key, value);
                }
            }
            
            Log.d(TAG, "Parsed " + actorType + " data: " + actorObject.toString());
            
        } catch (Exception e) {
            Log.e(TAG, "Error parsing " + actorType + " data: " + malformedJsonString, e);
            // Fallback: create minimal object
            actorObject.addProperty("id", "unknown");
            actorObject.addProperty("name", "Unknown");
            actorObject.addProperty("error", "Failed to parse: " + e.getMessage());
        }
        
        return actorObject;
    }
}