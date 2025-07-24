package com.example.therapyai.ui.sessions;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SearchView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.core.view.MenuProvider;
import androidx.core.view.MenuHost;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.therapyai.R;
import com.example.therapyai.data.local.models.CardItem;
import com.example.therapyai.data.local.models.CategoryItem;
import com.example.therapyai.ui.adapters.CategoryAdapter;
import com.example.therapyai.ui.adapters.SessionCardAdapter;
import com.example.therapyai.ui.dialogs.SaveCardDialogFragment;
import com.example.therapyai.ui.sessions.session.SessionHostActivity;
import com.example.therapyai.ui.viewmodels.CardViewModel;
import com.example.therapyai.ui.viewmodels.CategoryViewModel;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;


public class TherapistSessionFragment extends Fragment implements
        SessionCardAdapter.OnCardInteractionListener,
        CategoryAdapter.OnCategoryInteractionListener,
        SaveCardDialogFragment.SaveCardDialogListener {
    private static final String TAG = "TherapistSessionFragment";
    private RecyclerView cardRecyclerView;

    private SessionCardAdapter sessionCardAdapter;
    private boolean isEditMode = false;
    private ItemTouchHelper itemTouchHelper;
    private FloatingActionButton fabAdd;
    private RecyclerView recyclerViewCategories;
    private CategoryAdapter categoryAdapter;
    private List<CategoryItem> categoryItemList = new ArrayList<>();
    private CardViewModel cardViewModel;
    private CategoryViewModel categoryViewModel;

    private boolean isSearchActive = false;
    private String currentSearchQuery = "";
    private String selectedCategory = "All";
    private MenuProvider therapistMenuProvider;
//    private CategoryItem allCategoryItem;
//    private CategoryItem addCategoryItem;

    private static final String PREF_SWIPE_RIGHT_ACTION = "pref_swipe_right_action";
    private static final String SWIPE_ACTION_DELETE = "delete";
    private static final String SWIPE_ACTION_EDIT = "edit";


    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_therapist_session, container, false);
        Log.d("TherapistSessionFragment", "onCreateView: ");
//        cardViewModel = new ViewModelProvider(this).get(CardViewModel.class);
//        categoryViewModel = new ViewModelProvider(this).get(CategoryViewModel.class);

        try {
            cardViewModel = new ViewModelProvider(requireActivity()).get(CardViewModel.class);
            categoryViewModel = new ViewModelProvider(requireActivity()).get(CategoryViewModel.class);
            Log.d(TAG, "Fragment ViewModels initialized using Activity scope.");
        } catch (IllegalStateException e) {
            Log.e(TAG, "Error getting ViewModels from Activity in Fragment", e);
            // maybe show an error message placeholder
        }

        setupCardRecyclerView(view);
        setupCategoryRecyclerView(view);
        setupItemTouchHelper();
        setupMenuProvider();
        setupBackPressHandler();
        setupFab(view);

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Log.d("TherapistSessionFragment", "onViewCreated");

        // --- Observe LiveData ---
        cardViewModel.cardListLiveData.observe(getViewLifecycleOwner(), cards -> {
            Log.d("TherapistSessionFragment",
                    "LiveData Observer: Updating adapter with " + (cards != null ? cards.size() : 0) + " items.");
            if (sessionCardAdapter != null) {
                sessionCardAdapter.setItems(cards);
                if (isSearchActive && !currentSearchQuery.isEmpty()) {
                    sessionCardAdapter.filter(currentSearchQuery);
                }
            }
        });        
        categoryViewModel.categoryListLiveData.observe(getViewLifecycleOwner(), categories -> {
            Log.d(TAG, "Observer: Category list updated.");
            if (categoryAdapter != null) {
                categoryItemList = new ArrayList<>(categories);
                categoryAdapter.setCategories(categories);
                
                // Preserve current selection instead of always defaulting to "All"
                String categoryToSelect = (selectedCategory != null && !selectedCategory.isEmpty()) ? selectedCategory : "All";
                selectCategoryUI(categoryToSelect);
                categoryAdapter.notifyDataSetChanged();
            } else {
                categoryItemList = new ArrayList<>();
                categoryAdapter.setCategories(new ArrayList<>());
            }
        });

        cardViewModel.isLoading.observe(getViewLifecycleOwner(), isLoading -> updateLoadingIndicator(isLoading));
        categoryViewModel.isLoading.observe(getViewLifecycleOwner(), isLoading -> updateLoadingIndicator(isLoading));

        cardViewModel.errorLiveData.observe(getViewLifecycleOwner(), this::showErrorToast);
        categoryViewModel.errorLiveData.observe(getViewLifecycleOwner(), this::showErrorToast);


    }

    private void setupFab(View view) {
        fabAdd = view.findViewById(R.id.fabAddCard);
        fabAdd.setOnClickListener(v -> showSaveCardDialog(null));
    }

    private void setupCardRecyclerView(View view) {
        cardRecyclerView = view.findViewById(R.id.cardRecyclerView);
        cardRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));

        sessionCardAdapter = new SessionCardAdapter(requireContext(), this);
        cardRecyclerView.setAdapter(sessionCardAdapter);

        cardRecyclerView.setPadding(0, 0, 0, 0);
        cardRecyclerView.setClipToPadding(true);
        cardRecyclerView.addItemDecoration(new CardDividerDecoration(requireContext()));
    }


    private void setupCategoryRecyclerView(View view) {
        recyclerViewCategories = view.findViewById(R.id.categoryRecyclerView);
        recyclerViewCategories.setLayoutManager(
                new LinearLayoutManager(requireContext(),
                        LinearLayoutManager.HORIZONTAL,
                        false));

        categoryAdapter = new CategoryAdapter(requireContext(), this);
        recyclerViewCategories.setAdapter(categoryAdapter);
    }

    private void setupItemTouchHelper() {
        ItemTouchHelper.SimpleCallback callback = new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN,
                 ItemTouchHelper.RIGHT
        ) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                if (isEditMode) {
                    int fromPos = viewHolder.getBindingAdapterPosition();
                    int toPos = target.getBindingAdapterPosition();
                    if (fromPos != RecyclerView.NO_POSITION && toPos != RecyclerView.NO_POSITION) {
                        sessionCardAdapter.swapItems(fromPos, toPos);
                        return true;
                    }
                }
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getBindingAdapterPosition();
                if (position == RecyclerView.NO_POSITION) return;
                if (isEditMode) {
                    sessionCardAdapter.notifyItemChanged(position); return;
                }
                if (direction == ItemTouchHelper.RIGHT) {
                    String action = getSwipeRightActionPreference();
                    CardItem swipedItem = sessionCardAdapter.getItemAt(position);
                    if (swipedItem != null) {
                        if (SWIPE_ACTION_EDIT.equals(action)) {
                            editItem(swipedItem); // Pass item
                        } else {
                            showDeleteDialog(swipedItem); // Pass item
                        }
                        sessionCardAdapter.notifyItemChanged(position);
                    } else {
                        Log.e("TherapistSessionFragment", "Swiped item at position " + position + " is null!");
                        sessionCardAdapter.notifyItemChanged(position);
                    }

                }
            }


            @Override
            public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView,
                                    @NonNull RecyclerView.ViewHolder viewHolder,
                                    float dX, float dY, int actionState, boolean isCurrentlyActive) {

                if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE && !isEditMode) {
                    View itemView = viewHolder.itemView;
                    Paint paint = new Paint();
                    Drawable icon = null;
                    int iconColor = Color.WHITE;

                    if (dX > 0) {
                        String action = getSwipeRightActionPreference();
                        if (SWIPE_ACTION_EDIT.equals(action)) {
                            paint.setColor(Color.rgb(76, 175, 80)); // Green for Edit
                            icon = ContextCompat.getDrawable(requireContext(), R.drawable.baseline_edit_24);
                        } else { // Default to Delete
                            paint.setColor(Color.rgb(244, 67, 54)); // Red for Delete
                            icon = ContextCompat.getDrawable(requireContext(), R.drawable.baseline_delete_forever_24);
                        }

                        // Draw background
                        c.drawRect(
                                itemView.getLeft(),
                                itemView.getTop(),
                                itemView.getLeft() + dX,
                                itemView.getBottom(),
                                paint
                        );

                        // Draw icon
                        if (icon != null) {
                            icon.setTint(iconColor);
                            int iconSize = icon.getIntrinsicHeight();
                            int iconMargin = (itemView.getHeight() - iconSize) / 2;
                            int iconTop = itemView.getTop() + iconMargin;
                            int iconBottom = iconTop + iconSize;
                            int iconLeft = itemView.getLeft() + iconMargin;
                            int iconRight = iconLeft + iconSize;
                            icon.setBounds(iconLeft, iconTop, iconRight, iconBottom);
                            icon.draw(c);
                        }
                    }

                }
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
            }

            @Override
            public int getMovementFlags(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                int dragFlags = isEditMode ? (ItemTouchHelper.UP | ItemTouchHelper.DOWN) : 0;
                int swipeFlags = !isEditMode ? ItemTouchHelper.RIGHT : 0;
                return makeMovementFlags(dragFlags, swipeFlags);
            }

            @Override
            public void onSelectedChanged(@Nullable RecyclerView.ViewHolder viewHolder, int actionState) {
                super.onSelectedChanged(viewHolder, actionState);
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                    assert viewHolder != null;
                    viewHolder.itemView.setAlpha(0.9f);
                }
            }

            @Override
            public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                super.clearView(recyclerView, viewHolder);
                viewHolder.itemView.setAlpha(1.0f);
                if (isEditMode) {
                    List<CardItem> currentOrder = sessionCardAdapter.getCurrentItems();
                    Log.d("TherapistSessionFragment", "Drag finished. Requesting order update.");
                    cardViewModel.updateCardOrder(currentOrder);
                }
            }

        };

        itemTouchHelper = new ItemTouchHelper(callback);
        itemTouchHelper.attachToRecyclerView(cardRecyclerView);
    }

    private String getSwipeRightActionPreference() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
        return prefs.getString(PREF_SWIPE_RIGHT_ACTION, SWIPE_ACTION_DELETE);
    }



    private void setupMenuProvider() {
        MenuHost menuHost = requireActivity();
        LifecycleOwner viewLifecycleOwner = getViewLifecycleOwner(); // Get the view's lifecycle owner

        // Create the MenuProvider (can be an anonymous class as before, or a separate variable)
        therapistMenuProvider = new MenuProvider() {
            @Override
            public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
                menuInflater.inflate(R.menu.menu_main, menu);

                final Menu finalMenu = menu;

                MenuItem searchItem = menu.findItem(R.id.action_search);
                SearchView searchView = (SearchView) searchItem.getActionView();

                searchView.setQueryHint("Search cards...");

                searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                    // ... (rest of listener code remains the same)
                    @Override
                    public boolean onQueryTextSubmit(String query) {
                        isSearchActive = true;
                        currentSearchQuery = query;
                        if (sessionCardAdapter != null) sessionCardAdapter.filter(query); // Safety check
                        return false;
                    }

                    @Override
                    public boolean onQueryTextChange(String newText) {
                        isSearchActive = !newText.isEmpty();
                        currentSearchQuery = newText;
                        if (sessionCardAdapter != null) sessionCardAdapter.filter(newText); // Safety check
                        return true;
                    }
                });

                searchItem.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
                    @Override
                    public boolean onMenuItemActionExpand(MenuItem item) {
                        if (finalMenu != null) { // Safety check
                            finalMenu.findItem(R.id.action_edit_cards).setVisible(false);
                            finalMenu.findItem(R.id.action_delete).setVisible(false);
                        }
                        return true;
                    }

                    @Override
                    public boolean onMenuItemActionCollapse(MenuItem item) {
                        if (finalMenu != null) { // Safety check
                            finalMenu.findItem(R.id.action_edit_cards).setVisible(true);
                            finalMenu.findItem(R.id.action_delete).setVisible(isEditMode);
                        }
                        isSearchActive = false;
                        currentSearchQuery = "";
                        if (sessionCardAdapter != null) sessionCardAdapter.filter(""); // Safety check
                        return true;
                    }
                });
            }

            @Override
            public boolean onMenuItemSelected(@NonNull MenuItem menuItem) {
                int itemId = menuItem.getItemId();
                if (itemId == R.id.action_edit_cards) {
                    toggleEditMode();
                    return true;
                } else if (itemId == R.id.action_delete) {
                    deleteSelectedItems();
                    return true;
                }
                return false;
            }

            @Override
            public void onPrepareMenu(@NonNull Menu menu) {

                MenuItem editItem = menu.findItem(R.id.action_edit_cards);
                MenuItem deleteItem = menu.findItem(R.id.action_delete);
                MenuItem searchItem = menu.findItem(R.id.action_search);

                if (editItem == null || deleteItem == null || searchItem == null) {
                    Log.w(TAG, "Menu items not found during onPrepareMenu");
                    return;
                }


                editItem.setIcon(isEditMode ?
                        R.drawable.baseline_done_all_24 :
                        R.drawable.baseline_edit_24);
                deleteItem.setVisible(isEditMode);

                if (isEditMode) {
                    if (searchItem.isActionViewExpanded()) {
                        searchItem.collapseActionView();
                    }
                    searchItem.setVisible(false);
                } else {
                    searchItem.setVisible(true);
                }


                if (isAdded() && getContext() != null) {
                    int tintColor = getThemeColor(R.attr.colorOnPrimaryBackground);
                    if (editItem.getIcon() != null) {
                        editItem.getIcon().setTint(tintColor);
                    }
                    if (deleteItem.getIcon() != null) {
                        deleteItem.getIcon().setTint(tintColor);
                    }
                    if (searchItem.getIcon() != null) {
                        searchItem.getIcon().setTint(tintColor);
                    }
                } else {
                    Log.w(TAG, "Fragment not attached during onPrepareMenu, cannot apply tint.");
                }
            }
        };

        menuHost.addMenuProvider(therapistMenuProvider, viewLifecycleOwner, Lifecycle.State.RESUMED);
    }

    private void updateLoadingIndicator(Boolean isLoading) {
        if (isLoading != null) {
            // progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE); // idk if to leave this in
            Log.d(TAG,"Loading state: " + isLoading);
        }
    }
    // Helper to show toasts
    private void showErrorToast(String message) {
        if (message != null && !message.isEmpty()) {
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
        }
    }

    private void showSaveCardDialog(@Nullable CardItem cardToEdit) {
        SaveCardDialogFragment dialogFragment = (cardToEdit == null)
                ? SaveCardDialogFragment.newInstance()
                : SaveCardDialogFragment.newInstance(cardToEdit);
        dialogFragment.setSaveCardDialogListener(this);
        dialogFragment.show(getParentFragmentManager(), "SaveCardDialog");
    }

    @Override
    public void onCardSaved() {
        Log.d("TherapistSessionFragment", "onCardSaved callback received, reloading cards.");
    }

    private void setupBackPressHandler() {
        requireActivity().getOnBackPressedDispatcher().addCallback(
                getViewLifecycleOwner(),
                new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        if (isEditMode) {
                            toggleEditMode();
                        } else {
                            setEnabled(false);
                            requireActivity().onBackPressed();
                        }
                    }
                }
        );
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d("TherapistSessionFragment", "onResume: Reloading cards");
        
        // Ensure "All" is selected as default if no other category is selected
        if (selectedCategory == null || selectedCategory.isEmpty()) {
            selectedCategory = "All";
        }
        
        loadAndFilterCards();
        loadCategories();
    }

    private void loadAndFilterCards() {
        Log.d("TherapistSessionFragment", "Requesting card load for category: " + selectedCategory);
        cardViewModel.loadCards(selectedCategory);

    }

    private void loadCategories() {
        Log.d("TherapistSessionFragment", "Requesting category load");
        categoryViewModel.loadAllCategories();
    }



    private void toggleEditMode() {
        isEditMode = !isEditMode;
        sessionCardAdapter.setEditMode(isEditMode);
        selectedCategory = isEditMode ? "All" : selectedCategory;

        if (isEditMode && !"All".equalsIgnoreCase(selectedCategory)) {
            selectedCategory = "All";
            selectCategoryUI(selectedCategory);
            categoryAdapter.notifyDataSetChanged();
            loadAndFilterCards();
        }

        requireActivity().invalidateOptionsMenu();
    }

    private void showDeleteDialog(CardItem itemToDelete) {
        if (itemToDelete == null) return;
        new AlertDialog.Builder(requireContext())
                .setTitle("Delete Item")
                .setMessage("Are you sure you want to delete '" + itemToDelete.getTitle() + "'?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    cardViewModel.deleteCard(itemToDelete);
                    Toast.makeText(requireContext(), "Deleting...", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", (dialog, which) -> {
                    int currentPosition = sessionCardAdapter.findPositionById(itemToDelete.getId());
                    if (currentPosition != RecyclerView.NO_POSITION) {
                        sessionCardAdapter.notifyItemChanged(currentPosition);
                    }
                })
                .setOnCancelListener(dialog -> {
                    int currentPosition = sessionCardAdapter.findPositionById(itemToDelete.getId());
                    if (currentPosition != RecyclerView.NO_POSITION) {
                        sessionCardAdapter.notifyItemChanged(currentPosition);
                    }
                })
                .show();
    }

    private void editItem(CardItem itemToEdit) {
        if (itemToEdit == null) {
            Log.e(TAG, "Attempted to edit a null CardItem.");
            return;
        }
        showSaveCardDialog(itemToEdit);
    }

    private void deleteSelectedItems() {
        List<CardItem> itemsToDelete = sessionCardAdapter.getSelectedItems();
        if (itemsToDelete.isEmpty()) {
            Toast.makeText(requireContext(), "No items selected", Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(requireContext())
                .setTitle("Delete Selected")
                .setMessage("Are you sure you want to delete the " + itemsToDelete.size() + " selected items?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    cardViewModel.deleteSelectedCards(itemsToDelete);
                    toggleEditMode();
                    Toast.makeText(requireContext(), itemsToDelete.size() + " items deleted", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public void onCardClick(int position, CardItem cardItem) {
        if (isEditMode) {
            sessionCardAdapter.toggleSelection(position);
        } else {
            if (cardItem == null) { Log.e(TAG, "Clicked item is null!"); return; }
            Intent intent = new Intent(requireContext(), SessionHostActivity.class);
            intent.putExtra(SessionHostActivity.EXTRA_CARD_ITEM, cardItem);
//            intent.putExtra(FormActivity.EXTRA_CARD_ID, cardItem.getId());
//            intent.putExtra(FormActivity.EXTRA_TITLE, cardItem.getTitle());
//            intent.putExtra(FormActivity.EXTRA_DESCRIPTION, cardItem.getDescription());
            startActivity(intent);
        }
    }

    @Override
    public void onCardLongClick(int position, CardItem cardItem) {
        if (!isEditMode) {
            toggleEditMode();
            sessionCardAdapter.selectItem(position);
        }
    }

    @Override
    public void onDeleteClick(int position, CardItem cardItem) {
        showDeleteDialog(cardItem);
    }

    @Override
    public void onEditClick(int position, CardItem cardItem) {
        editItem(cardItem);
    }

    @Override
    public void onDragHandle(RecyclerView.ViewHolder viewHolder) {
        if (isEditMode) {
            itemTouchHelper.startDrag(viewHolder);
        }
    }

//    private void filterCards() {
//        if (isSearchActive && !currentSearchQuery.isEmpty()) {
//            sessionCardAdapter.filter(currentSearchQuery);
//        } else {
//            if ("All".equalsIgnoreCase(selectedCategory)) {
//                sessionCardAdapter.filterCategory(null);
//            } else {
//                sessionCardAdapter.filterCategory(selectedCategory);
//            }
//        }
//    }

    @Override
    public void onCategorySelected(String categoryName) {
        if (isEditMode){
            return;
        }
        if (!Objects.equals(categoryName, "+")) {
            boolean newlySelected = false;
            for (CategoryItem cat : categoryItemList) {
                boolean shouldBeSelected = cat.getName().equalsIgnoreCase(categoryName);
                if(cat.isSelected() && shouldBeSelected) { // Clicking already selected category
                    if (!"All".equalsIgnoreCase(categoryName)) {
                        cat.setSelected(false);
                        selectedCategory = "All";
                        selectCategoryUI("All");
                        newlySelected = false;
                    } else {
                        // Clicking "All" when it's already selected - do nothing
                        return;
                    }
                } else if (shouldBeSelected) {
                    cat.setSelected(true);
                    selectedCategory = categoryName;
                    newlySelected = true;
                } else {
                    cat.setSelected(false);
                }
            }
            if (!newlySelected && !"All".equalsIgnoreCase(selectedCategory)) {
                selectedCategory = "All";
            }

            selectCategoryUI(selectedCategory);
            
            isSearchActive = false;
            currentSearchQuery = "";
            loadAndFilterCards();
        }
        else{
            showAddCategoryDialog();
        }
    }

    @Override
    public void onCategoryLongPressed(String categoryName, int position) {
        showEditDeleteCategoryDialog(categoryName, position);
    }

    private void showEditDeleteCategoryDialog(String categoryName, int position) {
        if ("All".equalsIgnoreCase(categoryName) || "+".equalsIgnoreCase(categoryName)) {
            Toast.makeText(requireContext(),
                    "Cannot modify system categories",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(requireContext())
                .setTitle("Edit/Delete Category")
                .setItems(new String[]{"Edit", "Delete"}, (dialog, which) -> {
                    switch (which) {
                        case 0: // Edit
                            showEditCategoryDialog(categoryName, position);
                            break;
                        case 1: // Delete
                            // Get the category from database
                            CategoryItem categoryToDelete = findCategoryByNameInternal(categoryName);
                            if (categoryToDelete != null) {
                                // Show confirmation dialog with warning about cards
                                new AlertDialog.Builder(requireContext())
                                        .setTitle("Delete Category")
                                        .setMessage("Are you sure you want to delete '" + categoryName + "'? Associated cards will be updated.")
                                        .setPositiveButton("Delete", (innerDialog, innerWhich) -> {
                                            categoryViewModel.deleteCategoryAndAssociations(categoryToDelete);
                                            Toast.makeText(requireContext(), "Deleting category...", Toast.LENGTH_SHORT).show();
                                            if (selectedCategory.equalsIgnoreCase(categoryName)) {
                                                selectedCategory = "All";
                                                selectCategoryUI("All"); // Update UI state locally
                                            }
                                        })
                                        .setNegativeButton("Cancel", null).show();
                            } break;
                    }
                }).setNegativeButton("Cancel", null).show();
    }

    private void showEditCategoryDialog(String oldName, int position) {
        // Don't allow editing "All" or "+" categories
        if ("All".equalsIgnoreCase(oldName) || "+".equalsIgnoreCase(oldName)) {
            Toast.makeText(requireContext(),
                    "Cannot edit system categories",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        final EditText input = new EditText(requireContext());
        input.setText(oldName);

        new AlertDialog.Builder(requireContext())
                .setTitle("Edit Category")
                .setView(input)
                .setPositiveButton("Save", (dialog, which) -> {
                    String newName = input.getText().toString().trim();
                    if (newName.isEmpty() || newName.equalsIgnoreCase(oldName)) {
                        Toast.makeText(requireContext(), "Invalid name", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    categoryViewModel.renameCategory(oldName, newName);
                    Toast.makeText(requireContext(), "Updating category...", Toast.LENGTH_SHORT).show();
                    if (selectedCategory.equalsIgnoreCase(oldName)) {
                        selectedCategory = newName;
                    }

                }).setNegativeButton("Cancel", null).show();
    }


    private void selectCategoryUI(String categoryNameToSelect) {
        if (categoryItemList == null) return;
        
        Log.d(TAG, "Selecting category UI: " + categoryNameToSelect);
        
        // Update the selected status for each category
        for (CategoryItem cat : categoryItemList) {
            boolean selected = cat.getName().equalsIgnoreCase(categoryNameToSelect);
            cat.setSelected(selected);
            if (selected) {
                Log.d(TAG, "Category selected: " + cat.getName());
            }
        }
        
        // Ensure the adapter reflects the changes
        if (categoryAdapter != null) {
            categoryAdapter.notifyDataSetChanged();
        }
    }


    private void showAddCategoryDialog() {
        final EditText input = new EditText(requireContext());
        input.setHint("Category name");

        new AlertDialog.Builder(requireContext())
                .setTitle("Add Category")
                .setView(input)
                .setPositiveButton("Add", (dialog, which) -> {
                    String newCat = input.getText().toString().trim();
                    if (!newCat.isEmpty()) {
                        categoryViewModel.addCategory(newCat);
                        Toast.makeText(requireContext(), "Adding category...", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(requireContext(), "Name cannot be empty", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private CategoryItem findCategoryByNameInternal(String name) {
        if (categoryItemList == null || name == null) return null;
        for (CategoryItem item : categoryItemList) {
            if (name.equalsIgnoreCase(item.getName())) {
                return item;
            }
        }
        return null;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        cardRecyclerView = null;
        recyclerViewCategories = null;
        fabAdd = null;
        sessionCardAdapter = null;
        categoryAdapter = null;
    }

    private int getThemeColor(int attr) {
        TypedValue typedValue = new TypedValue();
        requireContext().getTheme().resolveAttribute(attr, typedValue, true);
        return typedValue.data;
    }

}
