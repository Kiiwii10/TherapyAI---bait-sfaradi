package com.example.therapyai.util;

import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import java.util.Calendar;

public class DateInputMask implements TextWatcher {
    private final EditText editText;
    private String current = "";
    private final String ddmmyyyy = "DDMMYYYY";
    private final Calendar cal = Calendar.getInstance();

    public DateInputMask(EditText editText) {
        this.editText = editText;
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        // No-op
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
        // No-op
    }

    @Override
    public void afterTextChanged(Editable s) {
        if (!s.toString().equals(current)) {
            // Remove all non-digits
            String clean = s.toString().replaceAll("[^\\d]", "");
            String cleanC = current.replaceAll("[^\\d]", "");

            int cleanLength = clean.length();
            int sel = cleanLength;

            // Correct for partial backspace
            for (int i = 2; i <= cleanLength && i < 6; i += 3) {
                sel++;
            }

            if (clean.equals(cleanC)) sel--;

            // Enforce a max of 8 digits (DDMMYYYY)
            if (cleanLength < 8) {
                // If user hasn't typed all 8 digits yet, pad with placeholder
                clean = clean + ddmmyyyy.substring(cleanLength);              } else {
                // If user typed full 8 digits, just format without any corrections
                // Let validation handle all date checks
                clean = clean.substring(0, 8);
            }

            // Insert slashes to create DD/MM/YYYY
            clean = String.format(
                    "%s/%s/%s",
                    clean.substring(0, 2),
                    clean.substring(2, 4),
                    clean.substring(4, 8)
            );

            sel = sel < 0 ? 0 : sel;
            current = clean;
            editText.removeTextChangedListener(this);
            editText.setText(current);
            editText.setSelection(Math.min(sel, current.length()));
            editText.addTextChangedListener(this);
        }
    }
}

