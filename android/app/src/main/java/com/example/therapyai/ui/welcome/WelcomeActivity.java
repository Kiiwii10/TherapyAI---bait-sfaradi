package com.example.therapyai.ui.welcome;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import android.app.AlertDialog;
import android.app.KeyguardManager;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import com.example.therapyai.R;
import com.example.therapyai.data.local.models.StatsItem;
import com.example.therapyai.ui.adapters.StatsPageAdapter;
import com.google.android.material.button.MaterialButton;
import com.tbuonomo.viewpagerdotsindicator.DotsIndicator;

import java.util.ArrayList;
import java.util.List;

public class WelcomeActivity extends AppCompatActivity {
    private RecyclerView statsRecyclerView;
    private List<StatsItem> statsItems;

    private ViewPager2 statsViewPager;
    private StatsPageAdapter statsPageAdapter;

    private DotsIndicator dotsIndicator;
    private TextView moreAbout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_welcome);

        // Initialize views
        statsViewPager = findViewById(R.id.statsViewPager);
        MaterialButton loginButton = findViewById(R.id.loginButton);
//        MaterialButton signUpButton = findViewById(R.id.signUpButton);

        // Setup ViewPager2
        setupStatsViewPager();

        dotsIndicator = findViewById(R.id.dotsIndicator);
        dotsIndicator.attachTo(statsViewPager);


        // Setup click listeners
        loginButton.setOnClickListener(v -> {
            KeyguardManager keyguardManager = (KeyguardManager) this.getSystemService(KEYGUARD_SERVICE);
            if (keyguardManager.isKeyguardSecure()) {
                startActivity(new Intent(this, LoginActivity.class));
            } else {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("Forgot Password")
                        .setMessage("Secure lock screen hasn't been set up.\n"
                        + "Go to 'Settings -> Security -> Screen lock' to set up a lock screen");
                builder.setPositiveButton("Settings", (dialog, which) -> {
                    Intent intent = new Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS);
                    startActivity(intent);
                });
                builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());
                builder.show();
            }
        });
//        signUpButton.setOnClickListener(v -> startActivity(new Intent(this, SignUpActivity.class)));

        moreAbout = findViewById(R.id.moreAboutSpanish);

        // Set a click listener
        moreAbout.setOnClickListener(v -> {
            String url = "https://ba-sfaradi.co.il/";
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(android.net.Uri.parse(url));
            startActivity(intent);
        });

    }

    private void setupStatsViewPager() {
        List<StatsItem> statsItems = new ArrayList<>();

        statsItems.add(new StatsItem("The Spanish Home", "Tailor Made AI Therapy Partner",R.drawable.bait_sfaradi_logo));
        statsItems.add(new StatsItem("Better Experience", "Fast & Easy Session Documentation",R.drawable.appreciate));
        statsItems.add(new StatsItem("Productive Sessions", "Better, Uninterrupted Sessions", R.drawable.knowledge));
        statsItems.add(new StatsItem("Quality Analytics", "Providing Data Driven AI Solutions", R.drawable.projections));
        statsItems.add(new StatsItem("Effortless Tracking", "Track Your Progress", R.drawable.undraw_slider));

        statsPageAdapter = new StatsPageAdapter(statsItems);
        statsViewPager.setAdapter(statsPageAdapter);
    }
}
