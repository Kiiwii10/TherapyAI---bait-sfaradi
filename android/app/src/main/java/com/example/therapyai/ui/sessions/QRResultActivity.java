package com.example.therapyai.ui.sessions;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.therapyai.R;
import com.example.therapyai.util.AESUtil;

public class QRResultActivity extends AppCompatActivity {
    private EditText passphraseEditText;
    private Button decryptButton;
    private TextView decryptedResultTextView;

    private String encryptedData;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_qr_result);

        passphraseEditText = findViewById(R.id.passphraseEditText);
        decryptButton = findViewById(R.id.decryptButton);
        decryptedResultTextView = findViewById(R.id.decryptedResultTextView);

        encryptedData = getIntent().getStringExtra("ENCRYPTED_DATA");

        decryptButton.setOnClickListener(v -> onDecryptClicked());
    }

    private void onDecryptClicked() {
        String passphrase = passphraseEditText.getText().toString().trim();
        if (passphrase.length() != 6) {
            Toast.makeText(this, "Passphrase must be 6 digits", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            // Decrypt
            String decrypted = AESUtil.decrypt(encryptedData, passphrase);
            decryptedResultTextView.setText("Decrypted Data:\n" + decrypted);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Decryption failed. Invalid passphrase?", Toast.LENGTH_LONG).show();
        }
    }
}

