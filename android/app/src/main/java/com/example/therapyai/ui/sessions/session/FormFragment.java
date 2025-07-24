package com.example.therapyai.ui.sessions.session;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import com.example.therapyai.R;
import com.example.therapyai.data.local.models.CardItem;
import com.example.therapyai.data.local.models.Profile;
import com.example.therapyai.data.repository.SearchRepository;
import com.example.therapyai.ui.sessions.ScanQRActivity;
import com.example.therapyai.ui.viewmodels.SessionViewModel;
import com.example.therapyai.util.AESUtil;
import com.example.therapyai.util.DateInputMask;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Objects;

public class FormFragment extends Fragment {

    private static final String TAG = "FormFragment";    
    private RadioGroup radioGroup;
    private RadioButton radioQrCode, radioManual;
    private LinearLayout layoutQrCode, layoutManual;
    private TextInputEditText etPasscode;
    private TextInputLayout tilPasscode;
    private Button btnScanQr;
    private TextView tvQrStatus;
    private TextView tvPassCodeStatus;
    private TextInputEditText etFirstName, etLastName,etEmail, etPatientId, etDateOfBirth;
    private TextInputLayout tilFirstName, tilLastName, tilEmail, tilPatientId, tilDateOfBirth;
    private CheckBox checkboxAccept;
    private Button btnStartSession;
    private TextView formTitleTextView;
    private ProgressBar progressBarSearch;

    private SessionViewModel viewModel;
    private NavController navController;
    private CardItem selectedCard;

    private boolean isQrScanned = false;
    private String encryptedQrData = null;
    private ActivityResultLauncher<Intent> qrScanLauncher;
    private SearchRepository searchRepository;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(SessionViewModel.class);
        registerQrScanResultHandler();

        searchRepository = SearchRepository.getInstance();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_form, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        navController = NavHostFragment.findNavController(this);
        initViews(view);
        setupListeners();
        updateStartSessionButtonState();

        selectedCard = viewModel.selectedCard.getValue();
        if (selectedCard == null) {
            Log.e(TAG, "Error: Selected CardItem is null in arguments.");
            Toast.makeText(getContext(), "Error: Session data missing.", Toast.LENGTH_LONG).show();
            requireActivity().finish();
        } else {
            Log.d(TAG, "Received CardItem: " + selectedCard.getTitle() + ", Type: " + selectedCard.getType());
        }

        if (formTitleTextView != null) {
            formTitleTextView.setText(selectedCard.getTitle());
            Log.d(TAG, "Setting fragment title to: " + selectedCard.getTitle());
        } else {
            Log.w(TAG, "Could not set fragment title - Card or TextView is null.");
            if (formTitleTextView != null) formTitleTextView.setText("");
        }
    }

    private void registerQrScanResultHandler() {
        qrScanLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == AppCompatActivity.RESULT_OK && result.getData() != null) {
                        encryptedQrData = result.getData().getStringExtra("ENCRYPTED_DATA");
                        if (encryptedQrData != null && !encryptedQrData.isEmpty()) {
                            isQrScanned = true;
                            tvQrStatus.setText("✓ QR code scanned successfully");
                            tvQrStatus.setVisibility(View.VISIBLE);
                            updateStartSessionButtonState();
                        } else {
                            Toast.makeText(requireContext(), "Failed to get data from QR code", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(requireContext(), "QR scan cancelled or failed", Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    private void initViews(View view) {
        formTitleTextView = view.findViewById(R.id.formTitleTextView);

        radioGroup = view.findViewById(R.id.radioGroup);
        radioQrCode = view.findViewById(R.id.radioQrCode);
        radioManual = view.findViewById(R.id.radioManual);

        layoutQrCode = view.findViewById(R.id.layoutQrCode);
        layoutManual = view.findViewById(R.id.layoutManual);
        etPasscode = view.findViewById(R.id.etPasscode);
        tilPasscode = view.findViewById(R.id.tilPasscode);
        btnScanQr = view.findViewById(R.id.btnScanQr);
        tvQrStatus = view.findViewById(R.id.tvQrStatus);
        tvPassCodeStatus = view.findViewById(R.id.tvPassCodeStatus);

        etFirstName = view.findViewById(R.id.etFirstName);
        tilFirstName = view.findViewById(R.id.tilFirstName);
        etLastName = view.findViewById(R.id.etLastName);
        tilLastName = view.findViewById(R.id.tilLastName);
        etEmail = view.findViewById(R.id.etEmail);
        tilEmail = view.findViewById(R.id.tilEmail);
        etDateOfBirth = view.findViewById(R.id.etDateOfBirth);
        tilDateOfBirth = view.findViewById(R.id.tilDateOfBirth);
        etPatientId = view.findViewById(R.id.etPatientId);
        tilPatientId = view.findViewById(R.id.tilPatientId);
        checkboxAccept = view.findViewById(R.id.checkboxAccept);

        btnStartSession = view.findViewById(R.id.btnStartSession);
        etDateOfBirth.addTextChangedListener(new DateInputMask(etDateOfBirth));

        progressBarSearch = view.findViewById(R.id.progressBarSearch);
    }    private void setupListeners() {
        radioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.radioQrCode) {
                layoutQrCode.setVisibility(View.VISIBLE);
                layoutManual.setVisibility(View.GONE);
                resetManualFields();
            } else if (checkedId == R.id.radioManual) {
                layoutQrCode.setVisibility(View.GONE);
                layoutManual.setVisibility(View.VISIBLE);
                resetQrFields();
            }
            updateStartSessionButtonState();
        });

        progressBarSearch.setVisibility(View.GONE);
        btnScanQr.setOnClickListener(v -> launchQrScanner());

        TextWatcher formWatcher = new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            public void afterTextChanged(Editable s) {
                // Clear errors when user types
                if (etFirstName.hasFocus()) clearFieldError(tilFirstName);
                if (etLastName.hasFocus()) clearFieldError(tilLastName);
                if (etEmail.hasFocus()) clearFieldError(tilEmail);
                if (etDateOfBirth.hasFocus()) clearFieldError(tilDateOfBirth);
                if (etPatientId.hasFocus()) clearFieldError(tilPatientId);
                if (etPasscode.hasFocus()) clearFieldError(tilPasscode);
                
                updateStartSessionButtonState(); 
            }
        };        etPasscode.addTextChangedListener(formWatcher);
        etPasscode.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                clearFieldError(tilPasscode);
            } else {
                // Validate when focus is lost
                validatePasscodeField();
            }
        });
          etFirstName.addTextChangedListener(formWatcher);
          etFirstName.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                clearFieldError(tilFirstName);
            } else {
                // Validate when focus is lost
                validateFirstNameField();
            }
        });
          etLastName.addTextChangedListener(formWatcher);
          etLastName.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                clearFieldError(tilLastName);
            } else {
                // Validate when focus is lost
                validateLastNameField();
            }
        });
          etEmail.addTextChangedListener(formWatcher);
          etEmail.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                clearFieldError(tilEmail);
            } else {
                // Validate when focus is lost
                validateEmailField();
            }
        });
          etPatientId.addTextChangedListener(formWatcher);
          etPatientId.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                clearFieldError(tilPatientId);
            } else {
                // Validate when focus is lost
                validatePatientIdField();
            }
        });

        etDateOfBirth.addTextChangedListener(formWatcher);
        etDateOfBirth.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                String currentText = etDateOfBirth.getText().toString().trim();
                if (currentText.isEmpty()) {
                    etDateOfBirth.setText("DD/MM/YYYY");
                    etDateOfBirth.setSelection(0);
                }
                clearFieldError(tilDateOfBirth);
            } else {
                // Validate when focus is lost
                validateDateOfBirthField();
            }
        });
        
        checkboxAccept.setOnCheckedChangeListener((v, i) -> updateStartSessionButtonState());

        btnStartSession.setOnClickListener(v -> {
            if (isFormInputValid()) {
                if (radioQrCode.isChecked()) {
                    String passcode = etPasscode.getText().toString().trim();
                    decryptAndProceed(encryptedQrData, passcode);
                } else {
                    performManualSearchAndProceed();
                }            } else {
                showValidationErrors();
            }
        });
    }


    private void performManualSearchAndProceed() {
        // Get data from fields
        final String firstName = Objects.requireNonNull(etFirstName.getText()).toString().trim();
        final String lastName = Objects.requireNonNull(etLastName.getText()).toString().trim();
        final String email = Objects.requireNonNull(etEmail.getText()).toString().trim();
        final String patientId = Objects.requireNonNull(etPatientId.getText()).toString().trim();
        final String dateOfBirth = Objects.requireNonNull(etDateOfBirth.getText()).toString().trim();

        progressBarSearch.setVisibility(View.VISIBLE);
        btnStartSession.setEnabled(false);

        searchRepository.performProfileSearch(patientId, new SearchRepository.ProfileSearchCallback() {
            @Override
            public void onProfilesFound(List<Profile> profiles) {
                if (!isAdded() || getActivity() == null) return;

                getActivity().runOnUiThread(() -> {
                    progressBarSearch.setVisibility(View.GONE);

                    Log.d(TAG, "Profile search completed. Profiles found: " + profiles.size());

                    if (profiles.isEmpty()) {
                        showProceedWithoutProfileDialog(firstName, lastName, email, patientId, dateOfBirth);
                    } else if (profiles.size() == 1) {
                        Profile foundProfile = profiles.get(0);
                        // Since ID is unique, any profile found means this ID already exists
                        // Therapist must either use existing profile or correct the ID
                        Log.i(TAG, "Profile exists for ID: " + patientId);
                        showExistingProfileDialog(foundProfile, firstName, lastName, email, patientId, dateOfBirth);
                    }
                     else {
                        // This case should not happen based on requirements (unique IDs in israel)
                        // but in testing it does, we will consider this the no results case.
                        showProceedWithoutProfileDialog(firstName, lastName, email, patientId, dateOfBirth);
                    }
                });
            }

            @Override
            public void onError(String error) {
                if (!isAdded() || getActivity() == null) return;

                getActivity().runOnUiThread(() -> {
                    progressBarSearch.setVisibility(View.GONE);
                    btnStartSession.setEnabled(true);
                    Log.e(TAG, "Profile search error: " + error);
                    Toast.makeText(requireContext(), getString(R.string.search_error_profiles, error), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void showProceedWithoutProfileDialog(String firstName, String lastName, String email, String patientId, String dateOfBirth) {
        if (!isAdded() || getContext() == null) return;

        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.dialog_no_profile_title)
                .setMessage(R.string.dialog_no_profile_message)
                .setPositiveButton(R.string.dialog_proceed_button, (dialog, which) -> {
                    Log.i(TAG, "Proceeding without matching profile for ID: " + patientId);
                    String manualPatientData = "{" +
                            "id: " + patientId + ", " +
                            "name: " + firstName + " " +lastName + ", " +
                            "email: " + email + ", " +
                            "date_of_birth: " + dateOfBirth + "}";
                    proceedWithManualData(manualPatientData);
                })
                .setNegativeButton(R.string.dialog_cancel_button, (dialog, which) -> {
                    Log.d(TAG, "User cancelled proceeding without profile.");
                    btnStartSession.setEnabled(true);
                    dialog.dismiss();
                })
                .setCancelable(false)
                .show();
    }

    private void showExistingProfileDialog(Profile foundProfile, String manualFirstName, String manualLastName, 
                                          String manualEmail, String manualPatientId, String manualDateOfBirth) {
        if (!isAdded() || getContext() == null) return;

        String foundName = foundProfile.getFullName() != null ? foundProfile.getFullName() : "Unknown";
        String foundEmail = foundProfile.getEmail() != null ? foundProfile.getEmail() : "Not specified";
        String foundDob = foundProfile.getDateOfBirth() != null ? foundProfile.getDateOfBirth() : "Not specified";
        
        String manualName = manualFirstName + " " + manualLastName;
        
        // Check if the entered data matches the existing profile
        boolean dobMatches = foundDob.equals(manualDateOfBirth);
        
        String message;
        if (dobMatches) {
            message = getString(R.string.dialog_existing_profile_match_message) + "\n\n" +
                    "Existing Profile:\n" +
                    "• Name: " + foundName + "\n" +
                    "• Email: " + foundEmail + "\n" +
                    "• DOB: " + foundDob + "\n\n" +
                    getString(R.string.dialog_existing_profile_match_choice);
        } else {
            message = getString(R.string.dialog_existing_profile_mismatch_message) + "\n\n" +
                    "Existing Profile for ID " + manualPatientId + ":\n" +
                    "• Name: " + foundName + "\n" +
                    "• Email: " + foundEmail + "\n" +
                    "• DOB: " + foundDob + "\n\n" +
                    "Your Entry:\n" +
                    "• Name: " + manualName + "\n" +
                    "• Email: " + manualEmail + "\n" +
                    "• DOB: " + manualDateOfBirth + "\n\n" +
                    getString(R.string.dialog_existing_profile_mismatch_choice);
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext())
                .setTitle(R.string.dialog_existing_profile_title)
                .setMessage(message)
                .setPositiveButton(R.string.dialog_use_existing_profile, (dialog, which) -> {
                    Log.i(TAG, "Therapist chose to use existing profile for ID: " + manualPatientId);
                    String patientData = foundProfile.toStringSession(); // Use complete cloud profile data
                    proceedWithManualData(patientData);
                })
                .setNegativeButton(R.string.dialog_correct_patient_id, (dialog, which) -> {
                    Log.d(TAG, "Therapist will correct patient ID.");
                    // Clear the patient ID field to allow correction
                    etPatientId.setText("");
                    etPatientId.requestFocus();
                    btnStartSession.setEnabled(true);
                    dialog.dismiss();
                })
                .setCancelable(false);

        builder.show();
    }


    private void launchQrScanner() {
        Intent intent = new Intent(requireContext(), ScanQRActivity.class);
        qrScanLauncher.launch(intent);
    }

    private void decryptAndProceed(String encryptedData, String passcode) {
        Calendar calendar = Calendar.getInstance();
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH) + 1; // Months are zero-based
        int day = calendar.get(Calendar.DAY_OF_MONTH);
        String currentDate = day + "/" + month + "/" + year;

        try {
            String decryptedData = AESUtil.decrypt(encryptedData, passcode);
            List<String> data = Arrays.asList(decryptedData.split(","));
            if (data.get(data.size()-1) == null || !Objects.equals(data.get(data.size() - 1), currentDate)) {
                throw new Exception("Decrypted data is empty, invalid, or expired.");
            }
            
            final String patientId = data.get(0);
            final String patientName = data.get(1);
            final String patientEmail = data.get(2);
            final String patientDob = data.get(3);
            
            // Try to fetch complete profile from cloud first
            progressBarSearch.setVisibility(View.VISIBLE);
            btnStartSession.setEnabled(false);
            
            searchRepository.performProfileSearch(patientId, new SearchRepository.ProfileSearchCallback() {
                @Override
                public void onProfilesFound(List<Profile> profiles) {
                    if (!isAdded() || getActivity() == null) return;
                    
                    getActivity().runOnUiThread(() -> {
                        progressBarSearch.setVisibility(View.GONE);
                        
                        String patientData;
                        if (profiles != null && !profiles.isEmpty()) {
                            Profile foundProfile = profiles.get(0);
                            Log.i(TAG, "QR scan: Found complete profile for ID: " + patientId);
                            patientData = foundProfile.toStringSession(); // Use complete profile data
                        } else {
                            Log.i(TAG, "QR scan: No cloud profile found, using QR data for ID: " + patientId);
                            // Use QR data as fallback
                            patientData = "{" +
                                    "id: " + patientId + ", " +
                                    "name: " + patientName + ", " +
                                    "email: " + patientEmail + ", " +
                                    "date_of_birth: " + patientDob + "}";
                        }
                        
                        viewModel.setPatientData(patientData);
                        navigateToNextStep();
                    });
                }
                
                @Override
                public void onError(String error) {
                    if (!isAdded() || getActivity() == null) return;
                    
                    getActivity().runOnUiThread(() -> {
                        progressBarSearch.setVisibility(View.GONE);
                        Log.w(TAG, "QR scan: Profile search failed, using QR data: " + error);
                        
                        // Use QR data as fallback
                        String patientData = "{" +
                                "id: " + patientId + ", " +
                                "name: " + patientName + ", " +
                                "email: " + patientEmail + ", " +
                                "date_of_birth: " + patientDob + "}";
                        
                        viewModel.setPatientData(patientData);
                        navigateToNextStep();
                    });
                }
            });
            
        } catch (Exception e) {
            Log.e(TAG, "Decryption failed", e);
            tvPassCodeStatus.setText("✗ PIN and QR code don't match");
            tvPassCodeStatus.setVisibility(View.VISIBLE);
            Toast.makeText(requireContext(), "Decryption failed. Invalid passcode or QR code.", Toast.LENGTH_LONG).show();
        }
    }

    private void proceedWithManualData(String patientData) {
        viewModel.setPatientData(patientData);
        navigateToNextStep();
    }

    private void navigateToNextStep() {
        if (selectedCard == null || selectedCard.getType() == null) {
            Log.e(TAG, "Cannot navigate, session type is unknown.");
            Toast.makeText(requireContext(),"Error: Unknown session type.", Toast.LENGTH_SHORT).show();
            return;
        }

        int actionId;
        switch (selectedCard.getType()) {
            case "Default Audio":
                actionId = R.id.action_formFragment_to_audioRecordFragment;
                break;
                // NOTE: here we add new sessions.
//            case "VR":
//                actionId = R.id.action_formFragment_to_vrRecordFragment;
//                break;
//            case "Relaxation":
//                actionId = R.id.action_formFragment_to_relaxationFragment;
//                break;
            default:
                Log.e(TAG, "Unsupported session type for navigation: " + selectedCard.getType());
                Toast.makeText(requireContext(),"Error: Session type not supported.", Toast.LENGTH_SHORT).show();
                return;
        }

        Log.d(TAG, "Navigating to next step with action ID: " + actionId);
        navController.navigate(actionId);
    }


    private void updateStartSessionButtonState() {
        btnStartSession.setEnabled(isFormInputValid());
    }

    private boolean isFormInputValid() {
        if (!radioQrCode.isChecked() && !radioManual.isChecked()) return false;

        if (radioQrCode.isChecked()) {
            return isQrScanned && validatePasscode();
        } else {
            // Only check validation status without showing errors
            return validateNames() && 
                   validateEmail() && 
                   validateDateOfBirth() && 
                   validatePatientId() && 
                   checkboxAccept.isChecked();
        }
    }

    private boolean validatePasscode() {
        String passcode = etPasscode.getText() == null ? "" : etPasscode.getText().toString().trim();
        return passcode.length() == 6 && TextUtils.isDigitsOnly(passcode);
    }    
    private boolean validateNames() {
        String firstName = etFirstName.getText() == null ? "" : etFirstName.getText().toString().trim();
        String lastName = etLastName.getText() == null ? "" : etLastName.getText().toString().trim();
        
        boolean firstNameValid = !firstName.isEmpty() && firstName.length() >= 2 && firstName.matches("[a-zA-Z\\s]+");
        boolean lastNameValid = !lastName.isEmpty() && lastName.length() >= 2 && lastName.matches("[a-zA-Z\\s]+");
        
        return firstNameValid && lastNameValid;
    }
    
    private boolean validateEmail() {
        String email = etEmail.getText() == null ? "" : etEmail.getText().toString().trim();
        return !email.isEmpty() && Patterns.EMAIL_ADDRESS.matcher(email).matches();
    }      private boolean validateDateOfBirth() {
        String dob = etDateOfBirth.getText() == null ? "" : etDateOfBirth.getText().toString().trim();
        
        // Check if empty or placeholder
        if (dob.isEmpty() || dob.equals("DD/MM/YYYY")) {
            return false;
        }
        
        // Check if it matches the required format
        if (!dob.matches("\\d{2}/\\d{2}/\\d{4}")) {
            return false;
        }
        
        try {
            // Parse the date
            String[] parts = dob.split("/");
            int day = Integer.parseInt(parts[0]);
            int month = Integer.parseInt(parts[1]);
            int year = Integer.parseInt(parts[2]);
            
            // Basic validity check
            if (year < 1900) {
                return false;
            }
            
            // Check if the date is valid (e.g., no February 30)
            if (!isDateValid(year, month, day)) {
                return false;
            }
            
            // Check if date is in the future
            Calendar inputDate = Calendar.getInstance();
            inputDate.set(year, month - 1, day); // Month is 0-based in Calendar
            
            Calendar today = Calendar.getInstance();
            
            if (inputDate.after(today)) {
                return false;
            }
            
            return true;
        } catch (Exception e) {
            return false;
        }
    }    
    private boolean validatePatientId() {
        String patientId = etPatientId.getText() == null ? "" : etPatientId.getText().toString().trim();
        return patientId.length() == 9 && TextUtils.isDigitsOnly(patientId);
    }private void resetQrFields() {
        etPasscode.setText("");
        clearFieldError(tilPasscode);
        isQrScanned = false;
        encryptedQrData = null;
        tvQrStatus.setVisibility(View.GONE);
        tvPassCodeStatus.setVisibility(View.GONE);
    }
      private void resetManualFields() {
        etFirstName.setText("");
        clearFieldError(tilFirstName);
        etLastName.setText("");
        clearFieldError(tilLastName);
        etEmail.setText("");
        clearFieldError(tilEmail);
        etDateOfBirth.setText("");
        clearFieldError(tilDateOfBirth);
        etPatientId.setText("");
        clearFieldError(tilPatientId);
        checkboxAccept.setChecked(false);
    }    private void showValidationErrors() {
        // Validate all fields and show specific error messages
        if (radioQrCode.isChecked()) {
            if (!isQrScanned) {
                Toast.makeText(requireContext(), "Please scan QR code first", Toast.LENGTH_SHORT).show();
                return;
            }
            validatePasscodeField();
        } else if (radioManual.isChecked()) {
            validateFirstNameField();
            validateLastNameField();
            validateEmailField();
            validateDateOfBirthField();
            validatePatientIdField();
        }
          if (!checkboxAccept.isChecked()) {
            Toast.makeText(requireContext(), "Please accept the terms and conditions", Toast.LENGTH_SHORT).show();
        }
    }

    // Helper method to safely clear field errors
    private void clearFieldError(TextInputLayout textInputLayout) {
        if (textInputLayout != null) {
            textInputLayout.setError(null);
        }
    }

    // Helper method to safely set field errors
    private void setFieldError(TextInputLayout textInputLayout, String errorMessage) {
        if (textInputLayout != null) {
            textInputLayout.setError(errorMessage);
        }
    }    /**
     * Individual field validation methods for real-time validation
     */    private void validatePasscodeField() {
        String passcode = etPasscode.getText() == null ? "" : etPasscode.getText().toString().trim();
        
        if (passcode.isEmpty()) {
            setFieldError(tilPasscode, "PIN is required");
        } else if (passcode.length() != 6) {
            setFieldError(tilPasscode, "PIN must be exactly 6 digits");
        } else if (!TextUtils.isDigitsOnly(passcode)) {
            setFieldError(tilPasscode, "PIN must contain only numbers");
        } else {
            clearFieldError(tilPasscode);
        }
    }
      private void validateFirstNameField() {
        String firstName = etFirstName.getText() == null ? "" : etFirstName.getText().toString().trim();
        
        if (firstName.isEmpty()) {
            setFieldError(tilFirstName, "First name is required");
//        } else if (firstName.length() < 2) {
//            setFieldError(tilFirstName, "First name must be at least 2 characters");
        } else if (!firstName.matches("[a-zA-Z\\s]+")) {
            setFieldError(tilFirstName, "First name can only contain letters and spaces");
        } else {
            clearFieldError(tilFirstName);
        }
    }
      private void validateLastNameField() {
        String lastName = etLastName.getText() == null ? "" : etLastName.getText().toString().trim();
        
        if (lastName.isEmpty()) {
            setFieldError(tilLastName, "Last name is required");
//        } else if (lastName.length() < 2) {
//            setFieldError(tilLastName, "Last name must be at least 2 characters");
        } else if (!lastName.matches("[a-zA-Z\\s]+")) {
            setFieldError(tilLastName, "Last name can only contain letters and spaces");
        } else {
            clearFieldError(tilLastName);
        }
    }
      private void validateEmailField() {
        String email = etEmail.getText() == null ? "" : etEmail.getText().toString().trim();
        
        if (email.isEmpty()) {
            setFieldError(tilEmail, "Email is required");
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            setFieldError(tilEmail, "Please enter a valid email address");
        } else {
            clearFieldError(tilEmail);
        }
    }
      private void validatePatientIdField() {
        String patientId = etPatientId.getText() == null ? "" : etPatientId.getText().toString().trim();
        
        if (patientId.isEmpty()) {
            setFieldError(tilPatientId, "Patient ID is required");
        } else if (patientId.length() != 9) {
            setFieldError(tilPatientId, "Patient ID must be exactly 9 digits");
        } else if (!TextUtils.isDigitsOnly(patientId)) {
            setFieldError(tilPatientId, "Patient ID must contain only numbers");
        } else {
            clearFieldError(tilPatientId);
        }
    }private void validateDateOfBirthField() {
        String dob = etDateOfBirth.getText() == null ? "" : etDateOfBirth.getText().toString().trim();
        
        if (dob.isEmpty() || dob.equals("DD/MM/YYYY")) {
            setFieldError(tilDateOfBirth, "Date of birth is required");
            return;
        }
        
        // Check format first
        if (!dob.matches("\\d{2}/\\d{2}/\\d{4}")) {
            setFieldError(tilDateOfBirth, "Please use DD/MM/YYYY format");
            return;
        }
        
        try {
            String[] parts = dob.split("/");
            int day = Integer.parseInt(parts[0]);
            int month = Integer.parseInt(parts[1]);
            int year = Integer.parseInt(parts[2]);
            
            // Check year range
            if (year < 1900) {
                setFieldError(tilDateOfBirth, "Year must be 1900 or later");
                return;
            }
            
            // Check if date is in the future
            Calendar inputDate = Calendar.getInstance();
            inputDate.set(year, month - 1, day);
            Calendar today = Calendar.getInstance();
            
            if (inputDate.after(today)) {
                setFieldError(tilDateOfBirth, "Date cannot be in the future");
                return;
            }
            
            // Check if date is valid (e.g., no February 30)
            if (!isDateValid(year, month, day)) {
                setFieldError(tilDateOfBirth, "Invalid date (e.g., February 30th doesn't exist)");
                return;
            }
            
            clearFieldError(tilDateOfBirth);
        } catch (Exception e) {
            setFieldError(tilDateOfBirth, "Invalid date format");
        }
    }

    /**
     * Validates if a date is legitimate (e.g., checking for February 30)
     */
    private boolean isDateValid(int year, int month, int day) {
        // Month is 1-based in our input but 0-based in Calendar
        Calendar cal = Calendar.getInstance();
        cal.setLenient(false); // This will make the calendar strict about date validation
        
        try {
            cal.set(Calendar.YEAR, year);
            cal.set(Calendar.MONTH, month - 1); // Convert to 0-based
            cal.set(Calendar.DAY_OF_MONTH, day);
            
            // getTime() will throw an exception if the date is invalid
            cal.getTime();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}