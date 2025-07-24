package com.example.therapyai.ui.sessions;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.example.therapyai.R;
import com.example.therapyai.ui.adapters.SessionCardAdapter;

// SwipeCallback.java
public class SwipeCallback extends ItemTouchHelper.SimpleCallback {
    private SessionCardAdapter adapter;
    private Paint paint;
    private ColorDrawable background;
//    private int deleteIconMargin;
    private Drawable deleteIcon;
    private Drawable archiveIcon;

    public SwipeCallback(SessionCardAdapter adapter, Context context) {
        super(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT);
        this.adapter = adapter;

        paint = new Paint();
        background = new ColorDrawable();
//        deleteIconMargin = (int) context.getResources().getDimension(R.dimen.delete_icon_margin);
        deleteIcon = ContextCompat.getDrawable(context, R.drawable.baseline_delete_forever_24);
        archiveIcon = ContextCompat.getDrawable(context, R.drawable.baseline_edit_24);

        if (deleteIcon != null) {
            deleteIcon.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_ATOP);
        }
        if (archiveIcon != null) {
            archiveIcon.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_ATOP);
        }
    }

    @Override
    public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
        return false;
    }

    @Override
    public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
        int position = viewHolder.getAdapterPosition();

        if (direction == ItemTouchHelper.LEFT) {
            adapter.removeItem(position);
        } else if (direction == ItemTouchHelper.RIGHT) {
            adapter.removeItem(position);
        }
    }

    @Override
    public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder,
                            float dX, float dY, int actionState, boolean isCurrentlyActive) {
        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);

        View itemView = viewHolder.itemView;
        int itemHeight = itemView.getBottom() - itemView.getTop();

        if (dX > 0) {
            background.setColor(Color.rgb(76, 175, 80));
            background.setBounds(itemView.getLeft(), itemView.getTop(),
                    itemView.getLeft() + ((int) dX), itemView.getBottom());
        } else if (dX < 0) {
            background.setColor(Color.rgb(244, 67, 54));
            background.setBounds(itemView.getRight() + ((int) dX), itemView.getTop(),
                    itemView.getRight(), itemView.getBottom());
        } else {
            background.setBounds(0, 0, 0, 0);
        }
        background.draw(c);

        // Draw icons
        int iconSize = deleteIcon.getIntrinsicWidth();
        int iconMargin = (itemHeight - iconSize) / 2;
        int iconTop = itemView.getTop() + iconMargin;
        int iconBottom = iconTop + iconSize;

        if (dX > 0) {
            int iconLeft = itemView.getLeft() + iconMargin;
            int iconRight = itemView.getLeft() + iconMargin + iconSize;
            archiveIcon.setBounds(iconLeft, iconTop, iconRight, iconBottom);
            archiveIcon.draw(c);
        } else if (dX < 0) {
            int iconLeft = itemView.getRight() - iconMargin - iconSize;
            int iconRight = itemView.getRight() - iconMargin;
            deleteIcon.setBounds(iconLeft, iconTop, iconRight, iconBottom);
            deleteIcon.draw(c);
        }
    }
}
