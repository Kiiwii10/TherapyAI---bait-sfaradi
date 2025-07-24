package com.example.therapyai.ui.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.therapyai.R;
import com.example.therapyai.data.local.models.Profile;
import com.example.therapyai.data.local.models.SessionSummary;
import com.example.therapyai.data.local.models.SearchItem;
import com.example.therapyai.util.ProfilePictureUtil;
import com.google.android.material.imageview.ShapeableImageView;

import java.util.ArrayList;
import java.util.List;

public class SearchAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VT_HEADER_PROFILES = SearchItem.TYPE_HEADER_PROFILES;
    private static final int VT_PROFILE_ITEM    = SearchItem.TYPE_PROFILE;
    private static final int VT_HEADER_SESSIONS = SearchItem.TYPE_HEADER_SESSIONS;
    private static final int VT_SESSION_SUMMARY_ITEM = SearchItem.TYPE_SESSION_SUMMARY;

    private List<SearchItem> items = new ArrayList<>();
    private final Context context;
    private final OnItemClickListener listener;

    public interface OnItemClickListener {
        void onProfileClick(Profile profile);
        void onSessionClick(SessionSummary sessionSummary);
    }

    public SearchAdapter(Context context, OnItemClickListener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void setItems(List<SearchItem> newItems) {
        this.items = (newItems != null) ? newItems : new ArrayList<>();
        notifyDataSetChanged();
    }


    @Override
    public int getItemViewType(int position) {
        return items.get(position).getViewType();
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(context);
        switch (viewType) {
            case VT_HEADER_PROFILES:
            case VT_HEADER_SESSIONS: {
                View view = inflater.inflate(R.layout.item_search_section_header, parent, false);
                return new HeaderViewHolder(view);
            }
            case VT_PROFILE_ITEM: {
                View view = inflater.inflate(R.layout.item_search_profile, parent, false);
                return new ProfileViewHolder(view);
            }
            case VT_SESSION_SUMMARY_ITEM: {
                View view = inflater.inflate(R.layout.item_search_session_summary, parent, false); // RENAME/CREATE this layout
                return new SessionSummaryViewHolder(view);
            }
            default:
                throw new IllegalArgumentException("Unknown viewType in SearchAdapter: " + viewType);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        SearchItem item = items.get(position);

        switch (item.getViewType()) {
            case VT_HEADER_PROFILES:
            case VT_HEADER_SESSIONS:
                ((HeaderViewHolder) holder).bind(item);
                break;
            case VT_PROFILE_ITEM:
                Profile profile = item.getProfile();
                if (profile != null) {
                    ((ProfileViewHolder) holder).bind(profile, listener);
                }
                break;
            case VT_SESSION_SUMMARY_ITEM:
                SessionSummary summary = item.getSessionSummary();
                if (summary != null) {
                    ((SessionSummaryViewHolder) holder).bind(summary, listener);
                }
                break;
        }
    }


    static class HeaderViewHolder extends RecyclerView.ViewHolder {
        TextView tvHeaderTitle;
        ProgressBar pbHeader;

        HeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            tvHeaderTitle = itemView.findViewById(R.id.tvHeaderTitle);
            pbHeader = itemView.findViewById(R.id.pbHeader);
        }

        void bind(SearchItem item) {
            tvHeaderTitle.setText(item.getHeaderTitle());
            pbHeader.setVisibility(item.isShowProgress() ? View.VISIBLE : View.GONE);
        }
    }    static class ProfileViewHolder extends RecyclerView.ViewHolder {
        com.google.android.material.imageview.ShapeableImageView ivProfilePicture;
        TextView tvProfileName, tvProfileId, tvDateOfBirthValue;

        ProfileViewHolder(@NonNull View itemView) {
            super(itemView);
            ivProfilePicture = itemView.findViewById(R.id.ivProfilePicture);
            tvProfileName = itemView.findViewById(R.id.tvProfileName);
            tvProfileId = itemView.findViewById(R.id.tvProfileId);
            tvDateOfBirthValue = itemView.findViewById(R.id.tvDateOfBirthValue);
        }        void bind(Profile profile, OnItemClickListener listener) {
            tvProfileName.setText(profile.getFullName());
            tvProfileId.setText(profile.getId());
            tvDateOfBirthValue.setText(String.valueOf(profile.getDateOfBirth()));
            
            // Use the utility method to load the profile picture
            ProfilePictureUtil.loadProfilePicture(itemView.getContext(), profile.getPictureUrl(), ivProfilePicture);

            itemView.setOnClickListener(v -> listener.onProfileClick(profile));
        }
    }

    static class SessionSummaryViewHolder extends RecyclerView.ViewHolder {
        TextView tvSessionTitle, tvSessionDate, tvSessionDescription;

        SessionSummaryViewHolder(@NonNull View itemView) {
            super(itemView);
            tvSessionTitle = itemView.findViewById(R.id.tvSessionTitle);
            tvSessionDate = itemView.findViewById(R.id.tvSessionDate);
            tvSessionDescription = itemView.findViewById(R.id.tvSessionDescription);
        }

        void bind(SessionSummary summary, OnItemClickListener listener) {
            tvSessionTitle.setText(summary.getTitle());
            tvSessionDate.setText(summary.getDate());
            tvSessionDescription.setText(summary.getDescriptionPreview());
            itemView.setOnClickListener(v -> listener.onSessionClick(summary));
        }
    }

}