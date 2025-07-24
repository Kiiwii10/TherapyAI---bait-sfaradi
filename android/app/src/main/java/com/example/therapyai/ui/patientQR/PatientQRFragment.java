package com.example.therapyai.ui.patientQR;


import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import com.example.therapyai.R;

import java.security.SecureRandom;
import java.util.Calendar;
import java.util.Locale;

import com.example.therapyai.data.local.SessionManager;
import com.example.therapyai.util.AESUtil;
import com.google.android.material.checkbox.MaterialCheckBox;


public class PatientQRFragment extends Fragment {
    private Button acceptButton;
    private SessionManager sessionManager;
    private TextView passphraseTextView, termsLinkTextView;
    private MaterialCheckBox termsCheckbox;

    private static final String ALGORITHM = "AES/CBC/PKCS5Padding";


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_patient_qr, container, false);
        View DialogView = inflater.inflate(R.layout.dialog_qr_code, container, false);
        acceptButton = view.findViewById(R.id.acceptButton);
        passphraseTextView = DialogView.findViewById(R.id.passphraseTextView);
        termsLinkTextView = view.findViewById(R.id.termsLinkTextView);
        termsCheckbox = view.findViewById(R.id.termsCheckbox);

        termsCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            acceptButton.setEnabled(isChecked);
        });

        termsLinkTextView.setOnClickListener(v -> showTermsDialog());
        acceptButton.setOnClickListener(v -> onAcceptClicked());

        sessionManager = SessionManager.getInstance();

        return view;
    }

    private void onAcceptClicked() {
        try {
            String passphrase = generateSixDigitCode();

            Calendar calendar = Calendar.getInstance();
            int year = calendar.get(Calendar.YEAR);
            int month = calendar.get(Calendar.MONTH) + 1; // Months are zero-based
            int day = calendar.get(Calendar.DAY_OF_MONTH);
            String currentDate = day + "/" + month + "/" + year;

            String qrData = sessionManager.getUserId() + ","
                    + sessionManager.getUserFullName() + ","
                    + sessionManager.getUserEmail() + ","
                    + sessionManager.getUserDateOfBirth() + ","
                    + currentDate;

            String encryptedMessage = AESUtil.encrypt(qrData, passphrase);;

            // Generate QR code
            Bitmap qrBitmap = generateQRCode(encryptedMessage, 300, 300);
            showQRCodePopup(qrBitmap, passphrase);

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private Bitmap generateQRCode(String data, int width, int height) {
        com.google.zxing.MultiFormatWriter writer = new com.google.zxing.MultiFormatWriter();
        try {
            com.google.zxing.common.BitMatrix bitMatrix = writer.encode(
                    data,
                    com.google.zxing.BarcodeFormat.QR_CODE,
                    width,
                    height
            );
            com.journeyapps.barcodescanner.BarcodeEncoder encoder = new com.journeyapps.barcodescanner.BarcodeEncoder();
            return encoder.createBitmap(bitMatrix);
        } catch (com.google.zxing.WriterException e) {
            e.printStackTrace();
            return null;
        }
    }

    private void showQRCodePopup(Bitmap qrBitmap, String passphrase) {
        if (getContext() == null) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_qr_code, null);

        ImageView qrCodeImageView = dialogView.findViewById(R.id.dialog_qrCodeImageView);
        Button closeButton = dialogView.findViewById(R.id.dialog_closeButton);
        TextView passphraseTextView = dialogView.findViewById(R.id.passphraseTextView);

        if (qrBitmap != null) {
            qrCodeImageView.setImageBitmap(qrBitmap);
        }
        passphraseTextView.setText("Passphrase: " + passphrase);

        builder.setView(dialogView);
        AlertDialog dialog = builder.create();

        closeButton.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void showTermsDialog() {
        if (getContext() == null) return;

        // You can create a dedicated layout for this dialog or use a simple message dialog
        new AlertDialog.Builder(getContext())
                .setTitle(R.string.terms_and_conditions_title)
                .setMessage(R.string.terms_content_placeholder) // Load your actual terms here
                .setPositiveButton("Close", (dialog, which) -> {
                    dialog.dismiss();
                })
                .show();

    }


    /**
     * Generates a random 6-digit code, e.g. "374921".
     */
    private String generateSixDigitCode() {
        SecureRandom random = new SecureRandom();
        int num = random.nextInt(1000000);
        return String.format(Locale.US, "%06d", num);
    }


}
