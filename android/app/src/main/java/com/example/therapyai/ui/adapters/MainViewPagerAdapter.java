package com.example.therapyai.ui.adapters;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.example.therapyai.R;
import com.example.therapyai.data.local.SessionManager;
import com.example.therapyai.ui.browse.BrowseFragment;
import com.example.therapyai.ui.search.PatientSearchFragment;
import com.example.therapyai.ui.patientQR.PatientQRFragment;
import com.example.therapyai.ui.search.TherapistSearchFragment;
import com.example.therapyai.ui.sessions.TherapistSessionFragment;

public class MainViewPagerAdapter extends FragmentStateAdapter {

    private final SessionManager.UserType userType;

    // Define positions (consistent order)
    private static final int POSITION_SESSION = 0;
    private static final int POSITION_DATA = 1;
    private static final int POSITION_BROWSE = 2;

    public MainViewPagerAdapter(@NonNull FragmentActivity fragmentActivity, SessionManager.UserType userType) {
        super(fragmentActivity);
        this.userType = userType;
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        switch (position) {
            case POSITION_SESSION:
                return (userType == SessionManager.UserType.THERAPIST) ?
                        new TherapistSessionFragment() : new PatientQRFragment();
            case POSITION_DATA:
                return (userType == SessionManager.UserType.THERAPIST) ?
                        new TherapistSearchFragment() : new PatientSearchFragment();
            case POSITION_BROWSE:
            default: // Fallback to Browse
                return new BrowseFragment();
        }
    }

    @Override
    public int getItemCount() {
        return 3;
    }

    // Helper methods to map menu item ID to ViewPager position
    public static int getPositionForMenuItem(int menuItemId) {
        if (menuItemId == R.id.nav_session) {
            return POSITION_SESSION;
        } else if (menuItemId == R.id.nav_data) {
            return POSITION_DATA;
        } else if (menuItemId == R.id.nav_browse) {
            return POSITION_BROWSE;
        }
        return POSITION_SESSION;
    }

    // Helper methods to map ViewPager position to menu item ID
    public static int getMenuItemIdForPosition(int position) {
        switch (position) {
            case POSITION_SESSION:
                return R.id.nav_session;
            case POSITION_DATA:
                return R.id.nav_data;
            case POSITION_BROWSE:
                return R.id.nav_browse;
            default:
                return R.id.nav_browse; // Default or handle error
        }
    }
}