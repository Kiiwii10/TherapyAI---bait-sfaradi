package com.example.therapyai.ui.dialogs;

import java.util.HashSet;
import java.util.Set;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.therapyai.R;
import com.example.therapyai.data.local.models.CardItem;
import com.example.therapyai.data.local.models.CategoryItem;
import com.example.therapyai.ui.viewmodels.CardViewModel;
import com.example.therapyai.ui.viewmodels.CategoryViewModel;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import java.util.ArrayList;
import java.util.List;

public class SaveCardDialogFragment extends DialogFragment {
    private static final String TAG = "SaveCardDialogFragment";

    private static final String ARG_CARD_ITEM = "card_item_serializable";
    private static final String STATE_CURRENT_STEP = "current_step";
    private static final String STATE_SELECTED_TYPE = "selected_type";
    private static final String STATE_SELECTED_CATEGORIES = "selected_categories";
    private static final String STATE_TITLE = "title";
    private static final String STATE_DESCRIPTION = "description";
    private static final String STATE_SESSION_NOTES = "session_notes";

    private CardViewModel cardViewModel;
    private CategoryViewModel categoryViewModel;
    private CardItem cardToEdit = null;

    private static final int STEP_TYPE = 1;
    private static final int STEP_CATEGORY = 2;
    private static final int STEP_DETAILS = 3;


    private int currentStep = STEP_TYPE;

    private LinearLayout stepTypeLayout, stepCategoryLayout, stepDetailsLayout;
    private RadioGroup rgSessionType;
    private ChipGroup chipGroupCategories;
    private EditText etTitle, etDescription;
    private TextView tvDialogTitle;
    private EditText etSessionNotes;


    private String selectedType = null;
    private Set<String> selectedCategoriesSet = new HashSet<>();
    private String currentTitle = "";
    private String currentDescription = "";
    private String currentSessionNotes = "";
    private List<CategoryItem> availableCategories = new ArrayList<>();
    private String title = "";
    private String description = "";

    private final String[] SESSION_TYPES = {"Default Audio", "VR", "Relaxation"};

    public interface SaveCardDialogListener {
        void onCardSaved();
    }
    private SaveCardDialogListener listener;

    /**
     * Creates a new instance for Adding a card.
     */
    public static SaveCardDialogFragment newInstance() {
        return new SaveCardDialogFragment();
    }


    /**
     * Creates a new instance for Editing a card.
     * @param cardToEdit The CardItem to edit (MUST be Parcelable or Serializable).
     */
    public static SaveCardDialogFragment newInstance(@NonNull CardItem cardToEdit) {
        SaveCardDialogFragment fragment = new SaveCardDialogFragment();
        Bundle args = new Bundle();
        args.putSerializable(ARG_CARD_ITEM, cardToEdit);
        fragment.setArguments(args);
        return fragment;
    }


    public void setSaveCardDialogListener(SaveCardDialogListener listener) {
        this.listener = listener;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            cardViewModel = new ViewModelProvider(requireActivity()).get(CardViewModel.class);
            categoryViewModel = new ViewModelProvider(requireActivity()).get(CategoryViewModel.class);
            Log.d(TAG, "ViewModels initialized using Activity scope.");
        } catch (IllegalStateException e) {
            Log.e(TAG, "Error getting ViewModels from Activity", e);
            Toast.makeText(getContext(), "Error initializing dialog components.", Toast.LENGTH_SHORT).show();
            dismissAllowingStateLoss();
            return;
        }

        if (savedInstanceState != null) {
            Log.d(TAG, "Restoring instance state");
            currentStep = savedInstanceState.getInt(STATE_CURRENT_STEP, STEP_TYPE);
            selectedType = savedInstanceState.getString(STATE_SELECTED_TYPE);
            currentTitle = savedInstanceState.getString(STATE_TITLE, "");
            currentDescription = savedInstanceState.getString(STATE_DESCRIPTION, "");
            currentSessionNotes = savedInstanceState.getString(STATE_SESSION_NOTES, "");
            ArrayList<String> savedList = savedInstanceState.getStringArrayList(STATE_SELECTED_CATEGORIES);
            if (savedList != null) {
                selectedCategoriesSet = new HashSet<>(savedList);
            }
            if(savedInstanceState.containsKey(ARG_CARD_ITEM)) {
                cardToEdit = (CardItem) savedInstanceState.getSerializable(ARG_CARD_ITEM);
            }

        } else {
            handleArguments();
        }
    }

    private void hideKeyboard() {
        View view = getDialog() != null ? getDialog().getCurrentFocus() : null;
        if (view != null) {
            android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
            }
        }
    }

    private void handleArguments() {
        Bundle args = getArguments();
        if (args != null && args.containsKey(ARG_CARD_ITEM)) {
            cardToEdit = (CardItem) args.getSerializable(ARG_CARD_ITEM);
            if (cardToEdit != null) {
                Log.d(TAG, "Editing card received via argument: " + cardToEdit.getTitle());
                selectedType = cardToEdit.getType();
                currentTitle = cardToEdit.getTitle();
                currentDescription = cardToEdit.getDescription();
                if (cardToEdit.getCategories() != null) {
                    selectedCategoriesSet.addAll(cardToEdit.getCategories());
                }
            } else {
                Log.w(TAG, "Received null CardItem from arguments.");
            }
        } else {
            Log.d(TAG, "No arguments found or no card item passed, assuming Add new card.");
            cardToEdit = null;
        }
    }


    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_CURRENT_STEP, currentStep);
        outState.putString(STATE_SELECTED_TYPE, selectedType);
        outState.putStringArrayList(STATE_SELECTED_CATEGORIES, new ArrayList<>(selectedCategoriesSet));

        if (etTitle != null) outState.putString(STATE_TITLE, etTitle.getText().toString());
        if (etDescription != null) outState.putString(STATE_DESCRIPTION, etDescription.getText().toString());
        if (etSessionNotes != null) outState.putString(STATE_SESSION_NOTES, etSessionNotes.getText().toString());

        if (cardToEdit != null) {
            outState.putSerializable(ARG_CARD_ITEM, cardToEdit);
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireActivity());
        LayoutInflater inflater = requireActivity().getLayoutInflater();
        View view = inflater.inflate(R.layout.dialog_save_card_multi_step, null);

        tvDialogTitle = view.findViewById(R.id.tvDialogTitle);
        stepTypeLayout = view.findViewById(R.id.stepTypeLayout);
        stepCategoryLayout = view.findViewById(R.id.stepCategoryLayout);
        stepDetailsLayout = view.findViewById(R.id.stepDetailsLayout);
        rgSessionType = view.findViewById(R.id.rgSessionType);
        chipGroupCategories = view.findViewById(R.id.chipGroupCategories);
        etTitle = view.findViewById(R.id.etCardTitle);
        etDescription = view.findViewById(R.id.etCardDescription);
        etSessionNotes = view.findViewById(R.id.etSessionNotes);

        Log.d(TAG, "onCreateDialog: Setting up observers.");
        setupObservers();

        Log.d(TAG, "onCreateDialog: Requesting categoryViewModel.loadUserCategories()");
        categoryViewModel.loadUserCategories();


        setupStepType();
        setupStepCategory();
        setupStepDetails();

        builder.setView(view)
                .setPositiveButton(R.string.button_save, null)
                .setNegativeButton(R.string.button_cancel, null)
                .setNeutralButton(R.string.button_back, null);

        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(dialogInterface -> {
            Button positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            Button negativeButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
            Button neutralButton = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);

            positiveButton.setText(R.string.button_next);

            positiveButton.setOnClickListener(v -> handleNextOrSave());
            neutralButton.setOnClickListener(v -> handleBack());

            updateStepVisibilityAndButtons(dialog);
        });


        return dialog;
    }
    

    private void setupObservers() {
        Log.d(TAG, "Setting up observers");
        // Observe user categories (excluding "All", "+")
        categoryViewModel.userCategoryListLiveData.observe(this, categories -> {
            Log.d(TAG, "##### Observer triggered! Received categories: " + (categories == null ? "null" : categories.size()) + " #####");
            availableCategories = categories != null ? categories : new ArrayList<>();
            populateCategoryChips();
        });

        if (categoryViewModel.userCategoryListLiveData.getValue() == null) {
            categoryViewModel.loadUserCategories();
        }
    }

    private void updateStepVisibilityAndButtons(AlertDialog dialog) {
        if (dialog == null) return;
        Button positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        Button neutralButton = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);

        stepTypeLayout.setVisibility(currentStep == STEP_TYPE ? View.VISIBLE : View.GONE);
        stepCategoryLayout.setVisibility(currentStep == STEP_CATEGORY ? View.VISIBLE : View.GONE);
        stepDetailsLayout.setVisibility(currentStep == STEP_DETAILS ? View.VISIBLE : View.GONE);
        // NOTE: Add more steps visibility logic here

        switch (currentStep) {
            case STEP_TYPE:
                tvDialogTitle.setText("Step 1: Select Session Type");
                positiveButton.setText(R.string.button_next);
                positiveButton.setEnabled(selectedType != null);
                neutralButton.setVisibility(View.GONE);
                break;
            case STEP_CATEGORY:
                tvDialogTitle.setText("Step 2: Select Categories (Optional)");
                positiveButton.setText(R.string.button_next);
                positiveButton.setEnabled(true);
                neutralButton.setVisibility(View.VISIBLE);
                break;
            case STEP_DETAILS:
                tvDialogTitle.setText("Step 3: Enter Details");
                positiveButton.setText(R.string.button_save);
                positiveButton.setEnabled(true);
                neutralButton.setVisibility(View.VISIBLE);
                break;
            // NOTE: Add cases for more steps
        }
    }

    private void handleNextOrSave() {
        AlertDialog dialog = (AlertDialog) getDialog();
        if (dialog == null) return;

        hideKeyboard();

        if (currentStep == STEP_TYPE) {
            if (selectedType == null) {
                Toast.makeText(requireContext(), R.string.error_select_type, Toast.LENGTH_SHORT).show();
                return;
            }
            if (!selectedType.equals("Default Audio")) { // NOTE: for now only Default Audio is supported
                Toast.makeText(requireContext(), R.string.error_type_not_available, Toast.LENGTH_SHORT).show();
                return;
            }
            currentStep = STEP_CATEGORY;
            updateStepVisibilityAndButtons(dialog);

        } else if (currentStep == STEP_CATEGORY) {
            currentStep = STEP_DETAILS;
            updateStepVisibilityAndButtons(dialog);
        } else if (currentStep == STEP_DETAILS) {
            saveCard();
        }
    }

    private void handleBack() {
        AlertDialog dialog = (AlertDialog) getDialog();
        if (currentStep == STEP_CATEGORY) {
            currentStep = STEP_TYPE;
        } else if (currentStep == STEP_DETAILS) {
            title = etTitle.getText().toString();
            description = etDescription.getText().toString();
            currentStep = STEP_CATEGORY;
        }
        updateStepVisibilityAndButtons(dialog);
    }


    private void setupStepType() {
        Log.d(TAG, "setupStepType: Starting. Initial selectedType = [" + selectedType + "]");
        rgSessionType.removeAllViews();
        int checkedCount = 0;

        for (String type : SESSION_TYPES) {
            RadioButton rb = new RadioButton(requireContext());
            rb.setText(type);
            rb.setId(View.generateViewId());
            rb.setLayoutParams(new RadioGroup.LayoutParams(RadioGroup.LayoutParams.MATCH_PARENT, RadioGroup.LayoutParams.WRAP_CONTENT));

            boolean shouldBeChecked = type.equals(selectedType);

            if (shouldBeChecked) {
                Log.d(TAG, ">>>> setupStepType: SETTING CHECKED for type: [" + type + "] <<<<");
                rb.setChecked(true);
                checkedCount++;
            } else {
                rb.setChecked(false);
            }
            rgSessionType.addView(rb);
        }

        Log.d(TAG, "setupStepType: Finished loop. Total buttons checked programmatically: " + checkedCount);
        if (checkedCount > 1) {
            Log.e(TAG, "setupStepType: ERROR - More than one RadioButton was checked programmatically!");
        }

        rgSessionType.setOnCheckedChangeListener((group, checkedId) -> {
            RadioButton rb = group.findViewById(checkedId);
            Log.d(TAG, "OnCheckedChangeListener: checkedId = " + checkedId);
            if (rb != null) {
                String newlyCheckedText = rb.getText().toString();
                Log.d(TAG, "OnCheckedChangeListener: Button Text = [" + newlyCheckedText + "]. Updating selectedType.");
                selectedType = newlyCheckedText;
                updateNextButtonState();

            } else if (checkedId == -1) {
                Log.d(TAG, "OnCheckedChangeListener: Selection cleared (checkedId = -1).");
                selectedType = null;
                updateNextButtonState();
            } else {
                Log.w(TAG,"OnCheckedChangeListener: RadioButton not found for checkedId: " + checkedId);
            }
        });
    }


    private void updateNextButtonState() {
        Dialog currentDialog = getDialog();
        if (currentDialog instanceof AlertDialog && currentStep == STEP_TYPE) {
            AlertDialog ad = (AlertDialog) currentDialog;
            Button positiveButton = ad.getButton(AlertDialog.BUTTON_POSITIVE);
            if (positiveButton != null) {
                positiveButton.setEnabled(selectedType != null);
                Log.d(TAG, "updateNextButtonState: Next button enabled = " + (selectedType != null));
            } else {
                Log.w(TAG, "updateNextButtonState: Positive button not found yet.");
            }
        }
    }

    private void setupStepCategory() {
        populateCategoryChips();
    }

    private void populateCategoryChips() {
        if (chipGroupCategories == null || !isAdded()) return;
        Log.d(TAG, "Populating category chips. Available: " + availableCategories.size() + ", Selected: " + selectedCategoriesSet.size());
        chipGroupCategories.removeAllViews();

        if (availableCategories.isEmpty()) {
            Log.d(TAG, "No user categories available to display chips.");
            return;
        }

        LayoutInflater inflater = getLayoutInflater();
        for (CategoryItem category : availableCategories) {
            Chip chip = (Chip) inflater.inflate(R.layout.chip_category_choice, chipGroupCategories, false);
            chip.setText(category.getName());
            chip.setCheckable(true);
            chip.setClickable(true);
            chip.setChecked(selectedCategoriesSet.contains(category.getName()));

            boolean isSelected = selectedCategoriesSet.contains(category.getName());
            chip.setChecked(isSelected);
            Log.v(TAG, "Chip '" + category.getName() + "' - isSelected in data: " + isSelected);


            chip.setOnCheckedChangeListener((buttonView, isChecked) -> {
                String catName = buttonView.getText().toString();
                if (isChecked) {
                    selectedCategoriesSet.add(catName);
                    Log.d(TAG, "Chip '" + catName + "' checked. Selected set: " + selectedCategoriesSet);
                } else {
                    selectedCategoriesSet.remove(catName);
                    Log.d(TAG, "Chip '" + catName + "' unchecked. Selected set: " + selectedCategoriesSet);
                }
            });
            chipGroupCategories.addView(chip);
        }
        Log.d(TAG, "Finished populating chips.");
    }

    private void setupStepDetails() {
        String titleToSet = "";
        String descToSet = "";
        String notesToSet = "";

        if (cardToEdit != null) {
            titleToSet = cardToEdit.getTitle() != null ? cardToEdit.getTitle() : "";
            descToSet = cardToEdit.getDescription() != null ? cardToEdit.getDescription() : "";
            notesToSet = cardToEdit.getSessionNotes() != null ? cardToEdit.getSessionNotes() : "";
        } else if (!currentTitle.isEmpty() || !currentDescription.isEmpty() || !currentSessionNotes.isEmpty()) {
            titleToSet = currentTitle;
            descToSet = currentDescription;
            notesToSet = currentSessionNotes;
        }

        etTitle.setText(titleToSet);
        etDescription.setText(descToSet);
        etSessionNotes.setText(notesToSet);
    }

    private void saveCard() {
        if (etTitle == null || etDescription == null || etSessionNotes == null) {
            Log.e(TAG, "EditText views are null, cannot save.");
            Toast.makeText(requireContext(), "Error saving card.", Toast.LENGTH_SHORT).show();
            dismissAllowingStateLoss();
            return;
        }
        String finalTitle = etTitle.getText().toString().trim();
        String finalDescription = etDescription.getText().toString().trim();
        String finalSessionNotes = etSessionNotes.getText().toString().trim();

        // Validation
        if (TextUtils.isEmpty(finalTitle)) { etTitle.setError("Title required"); return; }
        if (selectedType == null) { Toast.makeText(requireContext(), "Type required", Toast.LENGTH_SHORT).show(); currentStep=STEP_TYPE; updateStepVisibilityAndButtons((AlertDialog)getDialog()); return; }

        Set<String> finalCategories = new HashSet<>(selectedCategoriesSet);
        CardItem cardToSave;

        if (cardToEdit != null) {
            cardToSave = cardToEdit;
            Log.d(TAG, "Preparing to update card ID: " + cardToSave.getId());
        } else {
            cardToSave = new CardItem();
            Log.d(TAG, "Preparing to insert new card");
        }

        cardToSave.setTitle(finalTitle);
        cardToSave.setDescription(finalDescription);
        cardToSave.setType(selectedType);
        cardToSave.setCategories(finalCategories);
        cardToSave.setSessionNotes(finalSessionNotes);

        cardViewModel.saveCard(cardToSave);

        if (listener != null) {
            listener.onCardSaved();
        }
        dismiss();
    }
}