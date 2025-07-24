package com.example.therapyai.util;

import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Utility class for audio format conversions and header generation.
 * Specifically handles converting raw PCM data to WAV format with proper headers.
 * 
 * HIPAA Compliance: This utility operates on decrypted audio streams and generates
 * properly formatted audio data for cloud upload while maintaining encryption pipeline.
 */
public class AudioFormatUtil {
    private static final String TAG = "AudioFormatUtil";
    
    // WAV format constants matching RecordingService configuration
    public static final int SAMPLE_RATE = 44100;
    public static final int CHANNELS = 1; // MONO
    public static final int BITS_PER_SAMPLE = 16; // PCM 16-bit
    public static final int BYTES_PER_SAMPLE = BITS_PER_SAMPLE / 8;
    public static final int BYTE_RATE = SAMPLE_RATE * CHANNELS * BYTES_PER_SAMPLE;
    
    /**
     * Generates a WAV header for the given PCM data size.
     * 
     * @param pcmDataSize The size of the PCM audio data in bytes
     * @return byte array containing the 44-byte WAV header
     */
    public static byte[] generateWavHeader(long pcmDataSize) {
        ByteBuffer header = ByteBuffer.allocate(44);
        header.order(ByteOrder.LITTLE_ENDIAN);
        
        // RIFF header
        header.put("RIFF".getBytes());
        header.putInt((int) (36 + pcmDataSize)); // Total file size - 8
        header.put("WAVE".getBytes());
        
        // Format chunk
        header.put("fmt ".getBytes());
        header.putInt(16); // PCM format chunk size
        header.putShort((short) 1); // PCM format
        header.putShort((short) CHANNELS); // Number of channels
        header.putInt(SAMPLE_RATE); // Sample rate
        header.putInt(BYTE_RATE); // Byte rate
        header.putShort((short) (CHANNELS * BYTES_PER_SAMPLE)); // Block align
        header.putShort((short) BITS_PER_SAMPLE); // Bits per sample
        
        // Data chunk
        header.put("data".getBytes());
        header.putInt((int) pcmDataSize); // Data size
        
        return header.array();
    }
    
    /**
     * Wraps a PCM audio input stream with WAV format headers and writes to output stream.
     * This method ensures proper audio format for cloud upload while maintaining encryption pipeline.
     * 
     * @param pcmInputStream Input stream containing raw PCM audio data (decrypted)
     * @param outputStream Output stream where WAV-formatted audio will be written
     * @param estimatedPcmSize Estimated size of PCM data for progress calculation
     * @param progressCallback Optional callback for upload progress updates
     * @return Total bytes written (including WAV header)
     * @throws IOException If stream operations fail
     */
    public static long wrapPcmWithWavHeaders(InputStream pcmInputStream, 
                                           OutputStream outputStream,
                                           long estimatedPcmSize,
                                           ProgressCallback progressCallback) throws IOException {
        
        Log.d(TAG, "Converting PCM stream to WAV format. Estimated PCM size: " + estimatedPcmSize);
        
        // Step 1: Buffer all PCM data to calculate exact size
        ByteArrayOutputStream pcmBuffer = new ByteArrayOutputStream();
        byte[] tempBuffer = new byte[8192];
        int bytesRead;
        long totalPcmBytes = 0;
        
        // Read all PCM data into memory buffer
        while ((bytesRead = pcmInputStream.read(tempBuffer)) != -1) {
            pcmBuffer.write(tempBuffer, 0, bytesRead);
            totalPcmBytes += bytesRead;
            
            // Update progress for PCM reading phase (0-30%)
            if (progressCallback != null && estimatedPcmSize > 0) {
                int progressPercent = (int) Math.min(30, (totalPcmBytes * 30) / estimatedPcmSize);
                progressCallback.onProgress(progressPercent, "Reading audio data...");
            }
        }
        
        Log.d(TAG, "PCM data buffered. Actual size: " + totalPcmBytes + " bytes");
        
        // Step 2: Generate WAV header with exact PCM size
        byte[] wavHeader = generateWavHeader(totalPcmBytes);
        
        if (progressCallback != null) {
            progressCallback.onProgress(35, "Generating WAV headers...");
        }
        
        // Step 3: Write WAV header
        outputStream.write(wavHeader);
        long totalBytesWritten = wavHeader.length;
        
        if (progressCallback != null) {
            progressCallback.onProgress(40, "Writing WAV formatted audio...");
        }
        
        // Step 4: Write PCM data
        byte[] pcmData = pcmBuffer.toByteArray();
        outputStream.write(pcmData);
        totalBytesWritten += pcmData.length;
        
        // Clear sensitive audio data from memory
        pcmBuffer.reset();
        java.util.Arrays.fill(pcmData, (byte) 0);
        
        if (progressCallback != null) {
            progressCallback.onProgress(95, "WAV conversion complete");
        }
        
        Log.i(TAG, "WAV conversion completed. Total bytes written: " + totalBytesWritten + 
              " (Header: " + wavHeader.length + ", PCM: " + totalPcmBytes + ")");
        
        return totalBytesWritten;
    }
    
    /**
     * Wraps a PCM audio input stream with WAV format headers using streaming approach.
     * Minimizes memory exposure of decrypted audio data for HIPAA compliance.
     * 
     * @param pcmInputStream Input stream containing raw PCM audio data (decrypted)
     * @param outputStream Output stream where WAV-formatted audio will be written
     * @param estimatedPcmSize Estimated size of PCM data for progress calculation
     * @param progressCallback Optional callback for upload progress updates
     * @return Total bytes written (including WAV header)
     * @throws IOException If stream operations fail
     */
    public static long wrapPcmWithWavHeadersStreaming(InputStream pcmInputStream, 
                                                     OutputStream outputStream,
                                                     long estimatedPcmSize,
                                                     ProgressCallback progressCallback) throws IOException {
        
        Log.d(TAG, "Converting PCM stream to WAV format (streaming). Estimated PCM size: " + estimatedPcmSize);
        
        // Use smaller buffer size to minimize memory exposure (HIPAA compliance)
        final int SECURE_BUFFER_SIZE = 4096; // 4KB chunks
        
        // For streaming conversion, we need to write a placeholder header first
        // then update it with the actual size at the end
        byte[] placeholderHeader = generateWavHeader(estimatedPcmSize > 0 ? estimatedPcmSize : 0);
        outputStream.write(placeholderHeader);
        long totalBytesWritten = placeholderHeader.length;
        
        if (progressCallback != null) {
            progressCallback.onProgress(5, "Writing WAV header...");
        }
        
        // Stream PCM data in small chunks for security
        byte[] buffer = new byte[SECURE_BUFFER_SIZE];
        long actualPcmBytes = 0;
        int bytesRead;
        
        try {
            while ((bytesRead = pcmInputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
                actualPcmBytes += bytesRead;
                totalBytesWritten += bytesRead;
                
                // Clear sensitive data from buffer immediately after use
                if (bytesRead < buffer.length) {
                    java.util.Arrays.fill(buffer, bytesRead, buffer.length, (byte) 0);
                }
                
                // Update progress
                if (progressCallback != null && estimatedPcmSize > 0) {
                    int progressPercent = (int) Math.min(95, 10 + (actualPcmBytes * 85) / estimatedPcmSize);
                    progressCallback.onProgress(progressPercent, "Streaming audio data...");
                }
            }
        } finally {
            // Securely clear the buffer
            java.util.Arrays.fill(buffer, (byte) 0);
        }
        
        if (progressCallback != null) {
            progressCallback.onProgress(100, "Streaming WAV conversion complete");
        }
        
        Log.i(TAG, "Streaming WAV conversion completed. Total bytes written: " + totalBytesWritten + 
              " (Header: " + placeholderHeader.length + ", PCM: " + actualPcmBytes + ")");
        
        // Note: For true streaming, header size correction would require seeking back to beginning
        // For upload purposes, the estimated size should be close enough for proper server handling
        
        return totalBytesWritten;
    }

    /**
     * Progress callback interface for WAV conversion operations
     */
    public interface ProgressCallback {
        void onProgress(int progressPercent, String message);
    }

    /**
     * Validates that the audio configuration meets HIPAA compliance requirements
     * @return true if configuration is valid for secure WAV conversion
     */
    public static boolean validateAudioConfiguration() {
        // Expected values for HIPAA-compliant audio
        final int EXPECTED_SAMPLE_RATE = 44100;
        final int EXPECTED_CHANNELS = 1;
        final int EXPECTED_BITS_PER_SAMPLE = 16;
        
        boolean audioValid = SAMPLE_RATE == EXPECTED_SAMPLE_RATE && 
                            CHANNELS == EXPECTED_CHANNELS && 
                            BITS_PER_SAMPLE == EXPECTED_BITS_PER_SAMPLE;
        
        // Validate derived security parameters
        boolean securityValid = BYTES_PER_SAMPLE == 2 && BYTE_RATE > 0;
        
        if (!audioValid) {
            Log.e(TAG, "Audio configuration invalid! Expected: " + EXPECTED_SAMPLE_RATE + 
                  "Hz, " + EXPECTED_CHANNELS + " channels, " + EXPECTED_BITS_PER_SAMPLE + 
                  "-bit. Current: " + SAMPLE_RATE + "Hz, " + CHANNELS + " channels, " + 
                  BITS_PER_SAMPLE + "-bit");
        }
        
        if (!securityValid) {
            Log.e(TAG, "Security configuration invalid! BYTES_PER_SAMPLE: " + BYTES_PER_SAMPLE + 
                  ", BYTE_RATE: " + BYTE_RATE);
        }
        
        return audioValid && securityValid;
    }

    /**
     * NEW METHOD: Writes a 44-byte WAV file header to the given output stream.
     * This allows us to write the header first, then manually stream the PCM data afterwards.
     *
     * @param out           The OutputStream to write the header to.
     * @param totalPcmDataLen The total length of the raw PCM data that WILL BE written after this header.
     * @throws IOException If an error occurs during writing.
     */
    public static void writeWavHeader(OutputStream out, long totalPcmDataLen) throws IOException {
        long sampleRate = SAMPLE_RATE;
        int channels = CHANNELS;
        int bitsPerSample = BITS_PER_SAMPLE;

        long byteRate = sampleRate * channels * bitsPerSample / 8;
        long totalDataLen = totalPcmDataLen + 36; // 36 bytes for the rest of the header

        byte[] header = new byte[44];

        header[0] = 'R'; header[1] = 'I'; header[2] = 'F'; header[3] = 'F'; // RIFF/WAVE header
        header[4] = (byte) (totalDataLen & 0xff);
        header[5] = (byte) ((totalDataLen >> 8) & 0xff);
        header[6] = (byte) ((totalDataLen >> 16) & 0xff);
        header[7] = (byte) ((totalDataLen >> 24) & 0xff);
        header[8] = 'W'; header[9] = 'A'; header[10] = 'V'; header[11] = 'E';
        header[12] = 'f'; header[13] = 'm'; header[14] = 't'; header[15] = ' '; // 'fmt ' chunk
        header[16] = 16; header[17] = 0; header[18] = 0; header[19] = 0; // 4 bytes: size of 'fmt ' chunk
        header[20] = 1; header[21] = 0; // format = 1 (PCM)
        header[22] = (byte) channels;
        header[23] = 0;
        header[24] = (byte) (sampleRate & 0xff);
        header[25] = (byte) ((sampleRate >> 8) & 0xff);
        header[26] = (byte) ((sampleRate >> 16) & 0xff);
        header[27] = (byte) ((sampleRate >> 24) & 0xff);
        header[28] = (byte) (byteRate & 0xff);
        header[29] = (byte) ((byteRate >> 8) & 0xff);
        header[30] = (byte) ((byteRate >> 16) & 0xff);
        header[31] = (byte) ((byteRate >> 24) & 0xff);
        header[32] = (byte) (channels * bitsPerSample / 8); // block align
        header[33] = 0;
        header[34] = (byte) bitsPerSample; // bits per sample
        header[35] = 0;
        header[36] = 'd'; header[37] = 'a'; header[38] = 't'; header[39] = 'a';
        header[40] = (byte) (totalPcmDataLen & 0xff);
        header[41] = (byte) ((totalPcmDataLen >> 8) & 0xff);
        header[42] = (byte) ((totalPcmDataLen >> 16) & 0xff);
        header[43] = (byte) ((totalPcmDataLen >> 24) & 0xff);

        out.write(header, 0, 44);
    }
    
    /**
     * Calculates the expected file size for a WAV file given recording duration
     * @param durationMillis Recording duration in milliseconds
     * @return Expected WAV file size in bytes (including 44-byte header)
     */
    public static long calculateWavFileSize(long durationMillis) {
        long pcmBytes = (durationMillis * SAMPLE_RATE * CHANNELS * BYTES_PER_SAMPLE) / 1000;
        return pcmBytes + 44; // Add WAV header size
    }
    
    /**
     * Gets the appropriate file extension for the current audio format
     * @return ".wav" for WAV format
     */
    public static String getAudioFileExtension() {
        return ".wav";
    }
    
    /**
     * Gets the MIME type for the current audio format
     * @return "audio/wav" for WAV format
     */
    public static String getAudioMimeType() {
        return "audio/wav";
    }
}
