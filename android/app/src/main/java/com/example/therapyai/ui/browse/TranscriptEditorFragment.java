package com.example.therapyai.ui.browse;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.therapyai.R;
import com.example.therapyai.data.local.models.TranscriptItem;
import com.example.therapyai.ui.adapters.TranscriptAdapter;
import com.example.therapyai.ui.viewmodels.TranscriptDetailViewModel;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;

public class TranscriptEditorFragment extends Fragment {
    private static final String TAG = "TranscriptEditorFrag";

    private TranscriptDetailViewModel viewModel;
    private RecyclerView transcriptRecyclerView;
    private TranscriptAdapter transcriptAdapter;
    private MaterialButton btnSwapSpeakers;
    private TranscriptItem recentlyDeletedItem = null;
    private int recentlyDeletedItemPosition = -1;

    private ColorDrawable swipeBackground;
    private Drawable deleteIcon;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(TranscriptDetailViewModel.class);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_transcript_editor, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        transcriptRecyclerView = view.findViewById(R.id.transcriptRecyclerView);
        btnSwapSpeakers = view.findViewById(R.id.btnSwapSpeakers);

        initSwipeDrawables();
        setupRecyclerView();
        setupObservers();
        attachItemTouchHelper();

        btnSwapSpeakers.setOnClickListener(v -> {
            viewModel.swapAllSpeakers();
            Toast.makeText(requireContext(), "Attempted to swap all speakers", Toast.LENGTH_SHORT).show();
        });
    }

    private void initSwipeDrawables() {
        swipeBackground = new ColorDrawable(ContextCompat.getColor(requireContext(), R.color.swipe_delete_background));
        deleteIcon = ContextCompat.getDrawable(requireContext(), R.drawable.baseline_delete_forever_24);
        assert deleteIcon != null;
        deleteIcon.setTint(Color.WHITE);
    }

    private void setupRecyclerView() {
        transcriptAdapter = new TranscriptAdapter(new ArrayList<>(), requireContext(), this::onTranscriptDataChanged);
        LinearLayoutManager layoutManager = new LinearLayoutManager(requireContext());
        transcriptRecyclerView.setLayoutManager(layoutManager);
        transcriptRecyclerView.setAdapter(transcriptAdapter);

        DividerItemDecoration dividerItemDecoration = new DividerItemDecoration(
                transcriptRecyclerView.getContext(),
                layoutManager.getOrientation()
        );
        transcriptRecyclerView.addItemDecoration(dividerItemDecoration);
    }

    private void setupObservers() {
        viewModel.getEditableTranscriptItems().observe(getViewLifecycleOwner(), transcriptItems -> {
            if (transcriptItems != null) {
                transcriptAdapter.updateData(transcriptItems);
            } else {
                transcriptAdapter.updateData(new ArrayList<>());
            }

        });

    }

    private void onTranscriptDataChanged() {

        viewModel.notifyTranscriptChanged(transcriptAdapter.getCurrentData());
    }

    private void attachItemTouchHelper() {
        ItemTouchHelper.SimpleCallback itemTouchCallback = new ItemTouchHelper.SimpleCallback(
                0, ItemTouchHelper.LEFT
        ) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getBindingAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    handleItemDeletion(position);
                }
            }

            @Override
            public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, float dX, float dY, int actionState, boolean isCurrentlyActive) {
                View itemView = viewHolder.itemView;
                int iconMargin = (int) getResources().getDimension(R.dimen.swipe_icon_margin);
                int iconSize = (int) getResources().getDimension(R.dimen.swipe_icon_size);

                if (dX < 0) {

                    swipeBackground.setBounds(
                            (int) (itemView.getRight() + dX),
                            itemView.getTop(),
                            itemView.getRight(),
                            itemView.getBottom()
                    );
                    swipeBackground.draw(c);

                    int itemHeight = itemView.getBottom() - itemView.getTop();
                    int iconTop = itemView.getTop() + (itemHeight - iconSize) / 2;
                    int iconBottom = iconTop + iconSize;

                    int iconRight = itemView.getRight() - iconMargin;
                    int iconLeft = iconRight - iconSize;

                    if (deleteIcon != null) {
                        deleteIcon.setBounds(iconLeft, iconTop, iconRight, iconBottom);
                        deleteIcon.draw(c);
                    }

                } else {
                    swipeBackground.setBounds(0, 0, 0, 0);
                }

                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
            }
        };

        new ItemTouchHelper(itemTouchCallback).attachToRecyclerView(transcriptRecyclerView);
    }


    private void handleItemDeletion(int position) {
        List<TranscriptItem> currentList = transcriptAdapter.getCurrentData();
        if (position >= 0 && position < currentList.size()) {
            recentlyDeletedItem = currentList.get(position);
            recentlyDeletedItemPosition = position;

            transcriptAdapter.removeItem(position);

            Snackbar snackbar = Snackbar.make(requireView(),
                    "Sentence deleted",
                    Snackbar.LENGTH_LONG);
            snackbar.setAction("UNDO", v -> {
                undoDelete();
            });
            snackbar.addCallback(new Snackbar.Callback() {
                @Override
                public void onDismissed(Snackbar transientBottomBar, int event) {
                    if (event != DISMISS_EVENT_ACTION) {
                        recentlyDeletedItem = null;
                        recentlyDeletedItemPosition = -1;
                    }
                    super.onDismissed(transientBottomBar, event);
                }
            });
            snackbar.show();
        }
    }

    private void undoDelete() {
        if (recentlyDeletedItem != null && recentlyDeletedItemPosition != -1) {
            transcriptAdapter.insertItem(recentlyDeletedItemPosition, recentlyDeletedItem);

            transcriptRecyclerView.scrollToPosition(recentlyDeletedItemPosition);

            recentlyDeletedItem = null;
            recentlyDeletedItemPosition = -1;

            Toast.makeText(requireContext(), "Deletion undone", Toast.LENGTH_SHORT).show();
        }
    }
}
