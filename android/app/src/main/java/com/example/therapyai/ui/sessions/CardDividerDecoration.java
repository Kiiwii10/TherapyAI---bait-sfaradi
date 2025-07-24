package com.example.therapyai.ui.sessions;


import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

public class CardDividerDecoration extends RecyclerView.ItemDecoration {
    private Paint paint;

    public CardDividerDecoration(Context context) {
        paint = new Paint();
        paint.setColor(ContextCompat.getColor(context, android.R.color.darker_gray));
        paint.setStrokeWidth(1);
    }

    @Override
    public void onDrawOver(@NonNull Canvas canvas, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        int left = parent.getPaddingLeft();
        int right = parent.getWidth() - parent.getPaddingRight();

        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            RecyclerView.LayoutParams params = (RecyclerView.LayoutParams) child.getLayoutParams();

            float top = child.getTop() + params.topMargin;
            canvas.drawLine(left, top, right, top, paint);

            if (i == parent.getChildCount() - 1) {
                float bottom = child.getBottom() + params.bottomMargin;
                canvas.drawLine(left, bottom, right, bottom, paint);
            }
        }
    }
}
