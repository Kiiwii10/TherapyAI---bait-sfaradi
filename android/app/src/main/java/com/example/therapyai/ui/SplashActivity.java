// package com.example.therapyai.ui;

// import android.annotation.SuppressLint;
// import android.content.Intent;
// import android.os.Bundle;

// import android.view.View;
// import android.view.animation.Animation;
// import android.view.animation.AnimationUtils;
// import android.widget.ImageView;
// import android.widget.TextView;
// import android.widget.ProgressBar;
// import android.widget.Toast;

// import androidx.appcompat.app.AppCompatActivity;

// import com.example.therapyai.R;
// import com.example.therapyai.TherapyAIApp;
// import com.example.therapyai.data.local.LocalStorageManager;
// import com.example.therapyai.data.local.SessionManager;
// import com.example.therapyai.data.repository.AuthRepository;
// import com.example.therapyai.ui.welcome.LoginActivity;
// import com.example.therapyai.ui.welcome.WelcomeActivity;

// @SuppressLint("CustomSplashScreen")
// public class SplashActivity extends AppCompatActivity {

//     @Override
//     protected void onCreate(Bundle savedInstanceState) {
//         super.onCreate(savedInstanceState);
//         setContentView(R.layout.activity_splash);


//         ImageView splashLogo = findViewById(R.id.splashLogo);
//         TextView appName = findViewById(R.id.appName);
//         ProgressBar loadingIndicator = findViewById(R.id.loadingIndicator);

//         Animation fadeIn = AnimationUtils.loadAnimation(this, R.anim.fade_in);
//         splashLogo.startAnimation(fadeIn);
//         appName.startAnimation(fadeIn);
//         loadingIndicator.startAnimation(fadeIn);

//         if (getSupportActionBar() != null) {
//             getSupportActionBar().hide();
//         }


//         AuthRepository authRepository = AuthRepository.getInstance();
//         authRepository.loginFromMemory(new AuthRepository.LoginCallback() {
//             @Override
//             public void onSuccess(boolean success) {
//                 runOnUiThread(() -> {
//                     if (success) {
//                         navigateToMainActivity();
//                     } else {
//                         navigateToWelcomeActivity();
//                     }
//                 });
//             }

//             @Override
//             public void onError(String error) {
//                 runOnUiThread(() -> {
//                     navigateToWelcomeActivity();
//                 });
//             }
//         });

//     }

//     private void navigateToMainActivity() {
//         Intent mainIntent = new Intent(SplashActivity.this, MainActivity.class);
//         startActivity(mainIntent);
//         finish();
//     }

//     private void navigateToWelcomeActivity() {
//         Intent welcomeIntent = new Intent(SplashActivity.this, WelcomeActivity.class);
//         startActivity(welcomeIntent);
//         finish();
//     }
// }
