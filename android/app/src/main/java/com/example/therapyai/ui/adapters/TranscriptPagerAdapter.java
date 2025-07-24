package com.example.therapyai.ui.adapters;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.example.therapyai.ui.browse.TranscriptDetailsFragment;
import com.example.therapyai.ui.browse.TranscriptEditorFragment;

public class TranscriptPagerAdapter extends FragmentStateAdapter {

    private static final int NUM_PAGES = 2;

    public TranscriptPagerAdapter(@NonNull FragmentActivity fragmentActivity) {
        super(fragmentActivity);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        switch (position) {
            case 0:
                return new TranscriptDetailsFragment();
            case 1:
                return new TranscriptEditorFragment();
            default:
                throw new IllegalArgumentException("Invalid position: " + position);
        }
    }

    @Override
    public int getItemCount() {
        return NUM_PAGES;
    }
}
