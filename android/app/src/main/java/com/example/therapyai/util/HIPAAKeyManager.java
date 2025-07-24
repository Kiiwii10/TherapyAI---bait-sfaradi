package com.example.therapyai.util;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Log;

import java.security.KeyStore;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

public class HIPAAKeyManager {
    private static final String KEY_ALIAS = "hipaa_master_key";
    private static final String TAG = "HIPAAKeyManager";

    public static SecretKey getOrCreateKey() throws Exception {
        // DIAGNOSTIC: Log keystore access attempt
        Log.d(TAG, "DIAGNOSTIC: Attempting to get or create HIPAA key");
        Log.d(TAG, "DIAGNOSTIC: Current time: " + System.currentTimeMillis());
        Log.d(TAG, "DIAGNOSTIC: Thread: " + Thread.currentThread().getName());
        
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);

        if (!keyStore.containsAlias(KEY_ALIAS)) {
            Log.d(TAG, "DIAGNOSTIC: Key does not exist, creating new key with 12-hour auth validity");
            KeyGenerator keyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");

            keyGenerator.init(new KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setUserAuthenticationRequired(true)
                    .setUserAuthenticationValidityDurationSeconds(60 * 60 * 12) // 12 hours - workday
                    .build());

            SecretKey newKey = keyGenerator.generateKey();
            Log.d(TAG, "DIAGNOSTIC: New key created successfully");
            return newKey;
        }

        Log.d(TAG, "DIAGNOSTIC: Key exists, retrieving from keystore");
        try {
            SecretKey existingKey = (SecretKey) keyStore.getKey(KEY_ALIAS, null);
            Log.d(TAG, "DIAGNOSTIC: Existing key retrieved successfully");
            return existingKey;
        } catch (Exception e) {
            Log.e(TAG, "DIAGNOSTIC: Failed to retrieve existing key: " + e.getClass().getSimpleName() + " - " + e.getMessage(), e);
            throw e;
        }
    }

    public static void deleteKey() throws Exception {
        Log.d(TAG, "Attempting to delete key: " + KEY_ALIAS);
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);

        if (keyStore.containsAlias(KEY_ALIAS)) {
            keyStore.deleteEntry(KEY_ALIAS);
            Log.i(TAG, "Key '" + KEY_ALIAS + "' deleted successfully.");
        } else {
            Log.w(TAG, "Key '" + KEY_ALIAS + "' not found in Keystore. Nothing to delete.");
        }
    }
}
