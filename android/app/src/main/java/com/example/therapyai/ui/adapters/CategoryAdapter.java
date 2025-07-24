package com.example.therapyai.ui.adapters;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.therapyai.R;
import com.example.therapyai.data.local.models.CategoryItem;

import java.util.ArrayList;
import java.util.List;

public class CategoryAdapter extends RecyclerView.Adapter<CategoryAdapter.CategoryViewHolder> {

    public interface OnCategoryInteractionListener {
        /** Called when a category is clicked to change which category is “active.” */
        void onCategorySelected(String categoryName);

        /** Called when the user long-presses for edit/delete. */
        void onCategoryLongPressed(String categoryName, int position);
    }

    private final List<CategoryItem> categoryItems;
    private final Context context;
    private final OnCategoryInteractionListener listener;

    public CategoryAdapter(Context context, OnCategoryInteractionListener listener) {
        this.context = context;
        this.listener = listener;
        this.categoryItems = new ArrayList<>();
    }

    public void setCategories(List<CategoryItem> categories) {
        this.categoryItems.clear();
        this.categoryItems.addAll(categories);
        notifyDataSetChanged();
    }

    public List<CategoryItem> getCategories() {
        return categoryItems;
    }

    @NonNull
    @Override
    public CategoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // For a more Material look, you might inflate a Chip or MaterialButton
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_category_button, parent, false);

        return new CategoryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CategoryViewHolder holder, int position) {
        CategoryItem item = categoryItems.get(position);
        holder.categoryBtn.setText(item.getName());

        // Get the colors from your theme attributes
        TypedValue typedValue = new TypedValue();
        Resources.Theme theme = context.getTheme();
        theme.resolveAttribute(R.attr.colorCategoryBtn, typedValue, true);
        @ColorInt int colorCategoryBtn = typedValue.data;
        theme.resolveAttribute(R.attr.colorSelectedCategoryBtn, typedValue, true);
        @ColorInt int colorSelectedCategoryBtn = typedValue.data;
        theme.resolveAttribute(R.attr.colorOnCategoryBtnText, typedValue, true);
        @ColorInt int colorOnCategoryBtnText = typedValue.data;

        if (item.isSelected()) {
            holder.categoryBtn.setBackgroundTintList(ColorStateList.valueOf(colorSelectedCategoryBtn));
            holder.categoryBtn.setTextColor(colorOnCategoryBtnText);
            holder.categoryBtn.setTypeface(Typeface.DEFAULT_BOLD);
        } else {
            holder.categoryBtn.setBackgroundTintList(ColorStateList.valueOf(colorCategoryBtn));
            holder.categoryBtn.setTextColor(colorOnCategoryBtnText);
            holder.categoryBtn.setTypeface(Typeface.DEFAULT);
        }

        holder.categoryBtn.setOnClickListener(v -> {
            listener.onCategorySelected(item.getName());
        });

        holder.categoryBtn.setOnLongClickListener(v -> {
            listener.onCategoryLongPressed(item.getName(), holder.getAdapterPosition());
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return categoryItems.size();
    }


    static class CategoryViewHolder extends RecyclerView.ViewHolder {
        Button categoryBtn;

        public CategoryViewHolder(@NonNull View itemView) {
            super(itemView);
            categoryBtn = itemView.findViewById(R.id.btnCategory);

        }

    }

}

