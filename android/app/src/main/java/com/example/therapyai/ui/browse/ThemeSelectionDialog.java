package com.example.therapyai.ui.browse;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.therapyai.R;

public class ThemeSelectionDialog extends Dialog implements View.OnClickListener {

    private LinearLayout lightThemeOption, darkThemeOption, systemThemeOption;
    private ThemeSelectedListener listener;

    public interface ThemeSelectedListener {
        void onThemeSelected(String theme);
    }

    public ThemeSelectionDialog(Context context, ThemeSelectedListener listener) {
        super(context);
        this.listener = listener;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_theme_selection);

        getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        lightThemeOption = findViewById(R.id.light_theme_option);
        darkThemeOption = findViewById(R.id.dark_theme_option);
        systemThemeOption = findViewById(R.id.system_theme_option);

        lightThemeOption.setOnClickListener(this);
        darkThemeOption.setOnClickListener(this);
        systemThemeOption.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.light_theme_option) {
            listener.onThemeSelected("Light");
        } else if (id == R.id.dark_theme_option) {
            listener.onThemeSelected("Dark");
        } else if (id == R.id.system_theme_option) {
            listener.onThemeSelected("System");
        }
        dismiss();
    }
}
