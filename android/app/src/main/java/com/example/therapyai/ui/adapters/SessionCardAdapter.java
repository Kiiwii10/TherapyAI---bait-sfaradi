package com.example.therapyai.ui.adapters;

import android.content.Context;
import android.content.res.Resources;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.example.therapyai.R;
import com.example.therapyai.data.local.models.CardItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SessionCardAdapter extends RecyclerView.Adapter<SessionCardAdapter.CardViewHolder> {
    private List<CardItem> cardItems = new ArrayList<>();
    private List<CardItem> allCardItems = new ArrayList<>();
    private Context context;
    private boolean isEditMode = false;

//    public interface OnCardInteractionListener {
//        void onCardClick(int position);
//        void onCardLongClick(int position);
//        void onDeleteClick(int position);
//        void onEditClick(int position);
//        void onDragHandle(RecyclerView.ViewHolder viewHolder);
//    }


    public interface OnCardInteractionListener {
        void onCardClick(int position, CardItem cardItem); // Pass item
        void onCardLongClick(int position, CardItem cardItem); // Pass item
        void onDeleteClick(int position, CardItem cardItem); // Pass item
        void onEditClick(int position, CardItem cardItem); // Pass item
        void onDragHandle(RecyclerView.ViewHolder viewHolder);
    }
    private OnCardInteractionListener listener;

    public SessionCardAdapter(Context context, OnCardInteractionListener listener) {
        this.context = context;
        this.listener = listener;
    }

    @NonNull
    @Override
    public CardViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_card_session, parent, false);
        return new CardViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CardViewHolder holder, int position) {
        if (position < 0 || position >= cardItems.size()) {
            Log.e("SessionCardAdapter", "Invalid position in onBindViewHolder: " + position);
            return; // Avoid crash
        }        
        CardItem item = cardItems.get(position);

        // Set basic content
        holder.titleTextView.setText(item.getTitle());
        holder.descriptionTextView.setText(item.getDescription());
        
        // Set session type if available
        String sessionType = item.getType();
        if (sessionType != null && !sessionType.isEmpty()) {
            holder.sessionTypeTextView.setText(sessionType);
            holder.sessionTypeTextView.setVisibility(View.VISIBLE);
        } else {
            holder.sessionTypeTextView.setVisibility(View.GONE);
        }

        // Configure visibility based on edit mode
        holder.dragHandle.setVisibility(isEditMode ? View.VISIBLE : View.GONE);
        holder.editButton.setVisibility(isEditMode ? View.VISIBLE : View.GONE);
        holder.deleteButton.setVisibility(isEditMode ? View.VISIBLE : View.GONE);
        holder.checkBox.setVisibility(isEditMode ? View.VISIBLE : View.GONE);

        TypedValue typedValue = new TypedValue();
        Resources.Theme theme = context.getTheme();
        theme.resolveAttribute(R.attr.colorPrimaryBackground, typedValue, true);
        @ColorInt int colorPriBackground = typedValue.data;
        theme.resolveAttribute(R.attr.colorSecondaryBackground, typedValue, true);
        @ColorInt int colorSecBackground = typedValue.data;
        theme.resolveAttribute(R.attr.colorOnPrimaryBackground, typedValue, true);
        @ColorInt int colorOnPrimaryBackground = typedValue.data;


        if (holder.dragHandle.getDrawable() != null) {
            holder.dragHandle.getDrawable().setTint(colorOnPrimaryBackground);
        }
        if (holder.editButton.getDrawable() != null) {
            holder.editButton.getDrawable().setTint(colorOnPrimaryBackground);
        }
        if (holder.deleteButton.getDrawable() != null) {
            holder.deleteButton.getDrawable().setTint(colorOnPrimaryBackground);
        }

        // Set selection state
        holder.checkBox.setChecked(item.isSelected());
        holder.itemView.setBackgroundColor(item.isSelected() && isEditMode ?
                colorSecBackground : colorPriBackground);

        // Set click listeners
        holder.itemView.setOnClickListener(v -> {
            int currentPosition = holder.getBindingAdapterPosition();
            if (listener != null && currentPosition != RecyclerView.NO_POSITION) {
                listener.onCardClick(currentPosition, cardItems.get(currentPosition));
            }
        });

        holder.checkBox.setOnClickListener(v -> {
            int currentPosition = holder.getBindingAdapterPosition();
            if (listener != null && currentPosition != RecyclerView.NO_POSITION) {
                // CheckBox click should *only* toggle selection in edit mode
                if(isEditMode) {
                    toggleSelection(currentPosition);
                } else {
                    // Or maybe treat it like a normal click if not in edit mode?
                    listener.onCardClick(currentPosition, cardItems.get(currentPosition));
                }
            }
        });

        holder.itemView.setOnLongClickListener(v -> {
            int currentPosition = holder.getBindingAdapterPosition();
            if (listener != null && currentPosition != RecyclerView.NO_POSITION) {
                listener.onCardLongClick(currentPosition, cardItems.get(currentPosition));
                return true; // Consume long click
            }
            return false;
        });

        holder.dragHandle.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN && listener != null) {
                listener.onDragHandle(holder); // Pass the ViewHolder for ItemTouchHelper
            }
            return false; // Allow other touch events if needed
        });

        holder.editButton.setOnClickListener(v -> {
            int currentPosition = holder.getBindingAdapterPosition();
            if (listener != null && currentPosition != RecyclerView.NO_POSITION) {
                listener.onEditClick(currentPosition, cardItems.get(currentPosition));
            }
        });

        holder.deleteButton.setOnClickListener(v -> {
            int currentPosition = holder.getBindingAdapterPosition();
            if (listener != null && currentPosition != RecyclerView.NO_POSITION) {
                listener.onDeleteClick(currentPosition, cardItems.get(currentPosition));
            }
        });
    }

    @Override
    public int getItemCount() {
        return cardItems.size();
    }
    public boolean isEditMode() {
        return isEditMode;
    }

    /**
     * Sets the adapter's edit mode state and updates UI accordingly.
     * Clears selections when exiting edit mode.
     * @param editMode True to enter edit mode, false otherwise.
     */
    public void setEditMode(boolean editMode) {
        if (this.isEditMode == editMode) return;

        this.isEditMode = editMode;
        if (!isEditMode) {
            // Clear selections when exiting edit mode
            clearSelectionsInternal();
        }
        notifyDataSetChanged(); // Update visibility of checkboxes, buttons etc.
    }


    /**
     * Sets the complete list of items received from the ViewModel.
     * Updates both the master list and the currently displayed list.
     * @param newItems The new list of items.
     */
    public void setItems(List<CardItem> newItems) {
        // Store the full list from the source
        this.allCardItems.clear();
        this.allCardItems.addAll(newItems != null ? newItems : new ArrayList<>());

        // Initially display the full list (filtering happens separately if needed)
        this.cardItems.clear();
        this.cardItems.addAll(this.allCardItems);

        Log.d("SessionCardAdapter", "setItems called. allCardItems: " + allCardItems.size() + ", cardItems: " + cardItems.size());
        notifyDataSetChanged(); // Refresh the entire view
    }

    /**
     * Toggles the selection state of an item at a given position.
     * ONLY works if in edit mode.
     * @param position The adapter position of the item to toggle.
     */
    public void toggleSelection(int position) {
        if (isEditMode && position >= 0 && position < cardItems.size()) {
            CardItem item = cardItems.get(position);
            item.setSelected(!item.isSelected());
            notifyItemChanged(position, "PAYLOAD_SELECTION_CHANGED");
        }
    }

    /**
     * Selects a specific item.
     * Used primarily when long-press initiates edit mode.
     * @param position The adapter position of the item to select.
     */
    public void selectItem(int position) {
        if (position >= 0 && position < cardItems.size()) {
            cardItems.get(position).setSelected(true);
            notifyItemChanged(position, "PAYLOAD_SELECTION_CHANGED");
        }
    }

    /**
     * Gets a list of currently selected CardItem objects.
     * @return A new list containing selected items.
     */
    public List<CardItem> getSelectedItems() {
        List<CardItem> selectedItems = new ArrayList<>();
        for (CardItem item : cardItems) { // Check only currently displayed items
            if (item.isSelected()) {
                selectedItems.add(item);
            }
        }
        return selectedItems;
    }

    /**
     * Checks if any item is currently selected.
     * @return True if at least one item is selected, false otherwise.
     */
    public boolean hasSelectedItems() {
        for (CardItem item : cardItems) {
            if (item.isSelected()) {
                return true;
            }
        }
        return false;
    }

    public void removeSelectedItems() {
        for (int i = cardItems.size() - 1; i >= 0; i--) {
            if (cardItems.get(i).isSelected()) {
                cardItems.remove(i);
                notifyItemRemoved(i);
            }
        }
    }

    /**
     * Clears the selection state of all items. Internal use.
     */
    private void clearSelectionsInternal() {
//        boolean changed = false;
        // Clear selection in the currently displayed list
        for (CardItem item : cardItems) {
            if (item.isSelected()) {
                item.setSelected(false);
//                changed = true;
            }
        }
    }



//    public void swapItems(int fromPosition, int toPosition) {
//        if (fromPosition < toPosition) {
//            for (int i = fromPosition; i < toPosition; i++) {
//                Collections.swap(cardItems, i, i + 1);
//            }
//        } else {
//            for (int i = fromPosition; i > toPosition; i--) {
//                Collections.swap(cardItems, i, i - 1);
//            }
//        }
//        notifyItemMoved(fromPosition, toPosition);
//    }

    /**
     * Swaps items at two positions within the adapter's *display* list.
     * This is for the UI effect of dragging. Persistence is handled separately.
     * @param fromPosition Starting position.
     * @param toPosition Ending position.
     */
    public void swapItems(int fromPosition, int toPosition) {
        if (fromPosition >= 0 && fromPosition < cardItems.size() &&
                toPosition >= 0 && toPosition < cardItems.size())
        {
            if (fromPosition == toPosition) return;
            Collections.swap(cardItems, fromPosition, toPosition);
            notifyItemMoved(fromPosition, toPosition);

            // Update positions *within the adapter's current view* after move.
            // This helps if the fragment reads positions immediately after a move
            // before the DB update happens. Recalculate based on index.
            // Note: This doesn't persist the order, clearView in ItemTouchHelper does.
            int start = Math.min(fromPosition, toPosition);
            int end = Math.max(fromPosition, toPosition);
            for(int i = start; i <= end; i++) {
                if (i < cardItems.size()) { // Boundary check
                     cardItems.get(i).setPosition(i);
                }
            }
            // Trigger update for the moved items to potentially redraw if needed
             notifyItemRangeChanged(start, end - start + 1, "PAYLOAD_POSITION_CHANGED"); // Optional payload

        } else {
            Log.w("SessionCardAdapter", "Invalid positions for swap: " + fromPosition + ", " + toPosition);
        }
    }

//    public void filter(String query) {
//        String lowerCaseQuery = (query == null) ? "" : query.toLowerCase().trim();
//        List<CardItem> filteredList = new ArrayList<>();
//        for (CardItem item : allCardItems) {
//            if ((item.getTitle() != null && item.getTitle().toLowerCase().contains(lowerCaseQuery)) ||
//                    (item.getDescription() != null && item.getDescription().toLowerCase().contains(lowerCaseQuery))) {
//                filteredList.add(item);
//            }
//        }
//        cardItems.clear();
//        cardItems.addAll(filteredList);
//        notifyDataSetChanged();
//    }

    /**
     * Filters the displayed list based on a text query in title or description.
     * Uses the 'allCardItems' list as the source.
     * @param query The text to search for (case-insensitive). Null or empty shows all.
     */
    public void filter(String query) {
        String lowerCaseQuery = (query == null) ? "" : query.toLowerCase().trim();
        List<CardItem> filteredList = new ArrayList<>();

        if (lowerCaseQuery.isEmpty()) {
            filteredList.addAll(allCardItems); // Show all from master list
        } else {
            for (CardItem item : allCardItems) { // Filter from master list
                if ((item.getTitle() != null && item.getTitle().toLowerCase().contains(lowerCaseQuery)) ||
                        (item.getDescription() != null && item.getDescription().toLowerCase().contains(lowerCaseQuery))) {
                    filteredList.add(item);
                }
            }
        }
        cardItems.clear();
        cardItems.addAll(filteredList);
        Log.d("SessionCardAdapter", "Filtered. Query: '" + query + "', Displaying: " + cardItems.size());
        notifyDataSetChanged(); // Refresh UI with filtered list
    }

    public void filterCategory(String category) {
        String targetCategory = (category == null || "All".equalsIgnoreCase(category)) ? null : category;
        List<CardItem> filteredList = new ArrayList<>();

        if (targetCategory == null) {
            filteredList.addAll(allCardItems);
        } else {
            for (CardItem item : allCardItems) {
                if (item.getCategories() != null && item.getCategories().contains(targetCategory)) {
                    filteredList.add(item);
                }
            }
        }
        cardItems.clear();
        cardItems.addAll(filteredList);
        notifyDataSetChanged();
    }

    /**
     * Gets the CardItem at a specific adapter position.
     * @param position The adapter position.
     * @return The CardItem, or null if position is invalid.
     */
    @Nullable
    public CardItem getItemAt(int position) {
        return (position >= 0 && position < cardItems.size()) ? cardItems.get(position) : null;
    }

    /**
     * Gets the list of items currently being displayed by the adapter.
     * @return A new list containing the currently displayed items.
     */
    public List<CardItem> getCurrentItems() {
        return new ArrayList<>(cardItems); // Return a copy
    }

    public void addItem(CardItem item) {
        allCardItems.add(item);
        cardItems.add(item);
        notifyItemInserted(cardItems.size() - 1);
    }

    public void removeItem(int position) {
        if (position < cardItems.size()) {
            CardItem item = cardItems.get(position);
            allCardItems.remove(item);
            cardItems.remove(position);
            notifyItemRemoved(position);
        }
    }


    public CardItem getItem(int position) {
        return position < cardItems.size() ? cardItems.get(position) : null;
    }

    public List<CardItem> getAllItems() {
        return new ArrayList<>(allCardItems);
    }

    /**
     * Finds the current adapter position of a card based on its ID.
     * Searches the currently displayed 'cardItems' list.
     * @param cardId The ID of the card to find.
     * @return The adapter position, or RecyclerView.NO_POSITION if not found.
     */
    public int findPositionById(long cardId) {
        for (int i = 0; i < cardItems.size(); i++) {
            if (cardItems.get(i).getId() == cardId) {
                return i;
            }
        }
        return RecyclerView.NO_POSITION; // Not found in the current display list
    }    
    
    
    static class CardViewHolder extends RecyclerView.ViewHolder {
        TextView titleTextView;
        TextView descriptionTextView;
        TextView sessionTypeTextView;
        ImageView dragHandle;
        ImageButton editButton;
        ImageButton deleteButton;
        CheckBox checkBox;

        public CardViewHolder(@NonNull View itemView) {
            super(itemView);
            titleTextView = itemView.findViewById(R.id.titleTextView);
            descriptionTextView = itemView.findViewById(R.id.descriptionTextView);
            sessionTypeTextView = itemView.findViewById(R.id.sessionTypeTextView);
            dragHandle = itemView.findViewById(R.id.dragHandle);
            editButton = itemView.findViewById(R.id.editButton);
            deleteButton = itemView.findViewById(R.id.deleteButton);
            checkBox = itemView.findViewById(R.id.checkBox);

        }
    }

    @Override
    public void onBindViewHolder(@NonNull CardViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (payloads.isEmpty()) {
            // No payload, do a full rebind
            super.onBindViewHolder(holder, position, payloads);
        } else {
            // We have payloads, handle partial updates
            CardItem item = cardItems.get(position);
            for (Object payload : payloads) {
                if ("PAYLOAD_SELECTION_CHANGED".equals(payload)) {
                    // Only update selection state and background
                    holder.checkBox.setChecked(item.isSelected());
                    TypedValue typedValue = new TypedValue();
                    Resources.Theme theme = context.getTheme();
                    theme.resolveAttribute(R.attr.colorPrimaryBackground, typedValue, true);
                    @ColorInt int colorPriBackground = typedValue.data;
                    theme.resolveAttribute(R.attr.colorSecondaryBackground, typedValue, true);
                    @ColorInt int colorSecBackground = typedValue.data;
                    holder.itemView.setBackgroundColor(item.isSelected() && isEditMode ?
                            colorSecBackground : colorPriBackground);
                    Log.d("SessionCardAdapter", "Partial update for selection at position: " + position);
                }
                // Add other payload types if needed (e.g., "PAYLOAD_POSITION_CHANGED")
            }
        }
    }
}

