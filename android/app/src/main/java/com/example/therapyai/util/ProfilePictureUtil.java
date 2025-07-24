package com.example.therapyai.util;

import android.content.Context;

import com.bumptech.glide.Glide;
import com.example.therapyai.R;
import com.google.android.material.imageview.ShapeableImageView;

/**
 * Utility class for handling profile picture loading and display
 */
public class ProfilePictureUtil {

    /**
     * Loads a profile picture into a ShapeableImageView with consistent styling
     *
     * @param context The context
     * @param pictureUrl The URL of the profile picture (can be null)
     * @param imageView The ShapeableImageView to load the image into
     */
    public static void loadProfilePicture(Context context, String pictureUrl, ShapeableImageView imageView) {
        if (pictureUrl != null && !pictureUrl.isEmpty()) {
            // We have a valid URL, just load it normally
            Glide.with(context)
                .load(pictureUrl)
                .circleCrop()
                .into(imageView);
        } else {
            // No valid URL, use a colored background with the icon
            int backgroundColor = context.getResources().getColor(R.color.profile_icon_background, null);
            
            // Load the default icon
            Glide.with(context)
                .load(R.drawable.baseline_account_circle_24)
                .circleCrop()
                .into(imageView);
            
            // Apply background color
            imageView.setBackgroundColor(backgroundColor);
        }
    }
}
