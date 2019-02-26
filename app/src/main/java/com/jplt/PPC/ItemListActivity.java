package com.jplt.PPC;

import android.app.ActionBar;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.AppBarLayout;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.support.design.widget.FloatingActionButton;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import com.firebase.ui.auth.AuthUI;
import com.google.firebase.auth.FirebaseAuth;

import java.util.Arrays;
import java.util.List;

import com.bumptech.glide.Glide;

/**
 * An activity representing a list of Items. This activity
 * has different presentations for handset and tablet-size devices. On
 * handsets, the activity presents a list of items, which when touched,
 * lead to a {@link ItemDetailActivity} representing
 * item details. On tablets, the activity presents the list of items and
 * item details side-by-side using two vertical panes.
 */
public class ItemListActivity extends AppCompatActivity {

    public static int INTENT_AUTHENTICATE = 1;
    public static int INTENT_PROFILE = 2;

    RecyclerView.Adapter mAdapter = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_item_list);

        AppBarLayout appbar = findViewById(R.id.app_bar);
        Drawable drawable = ContextCompat.getDrawable(this, R.drawable.appbar);
        CenterCropDrawable cropped = new CenterCropDrawable(drawable);
        cropped.setBounds(0,0,appbar.getMeasuredWidth(),50);
        appbar.setBackground(cropped);

        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle("");
        setSupportActionBar(toolbar);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Context context = view.getContext();
                Intent intent = new Intent(context, ItemDetailActivity.class);
                context.startActivity(intent);
            }
        });

        if (ensureAuthenticated()) {
            Profile.initMe();
            setupRecyclerView();
        }
    }

    protected boolean ensureAuthenticated() {

        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            // Choose authentication providers
            List<AuthUI.IdpConfig> providers = Arrays.asList(new AuthUI.IdpConfig.PhoneBuilder().build());

            // Create and launch sign-in intent
            startActivityForResult(
                    AuthUI.getInstance()
                            .createSignInIntentBuilder()
                            .setAvailableProviders(providers)
                            .build(),
                    INTENT_AUTHENTICATE);

            return false;
        }

        return true;
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == INTENT_AUTHENTICATE) {
            Log.i("AUTH", "Auth complete, refreshing view..");
            Profile.initMe();
            setupRecyclerView();
        }
        else if (requestCode == INTENT_PROFILE && resultCode == RESULT_OK) {
            Log.i("PROFILE", "Profile changed, refreshing view..");
            Episodes.getInstance().refresh();
        }
    }

    private void setupRecyclerView() {
        RecyclerView recyclerView = findViewById(R.id.item_list);
        Episodes episodes = Episodes.getInstance();

        DividerItemDecoration dividerItemDecoration = new DividerItemDecoration(recyclerView.getContext(),
                DividerItemDecoration.HORIZONTAL);
        recyclerView.addItemDecoration(dividerItemDecoration);

        mAdapter = new SimpleItemRecyclerViewAdapter(this, episodes.episodes);
        episodes.addListener(new Episodes.EpisodeChangeHandler() {
            @Override
            public void onChange(Episodes.ChangeType type, Episode episode) {
                mAdapter.notifyDataSetChanged();
            }
        });

        recyclerView.setAdapter(mAdapter);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.edit_profile:
                Log.i("MENU", "Edit Profile");
                Intent intent = new Intent(this, ProfileActivity.class);
                startActivityForResult(intent, INTENT_PROFILE);
                return true;
            case R.id.toggle_testing:
                Log.i("MENU", "Toggle testing");

                Episodes.PID = Episodes.PID == "prealpha" ? "testing" : "prealpha";
                Episodes.getInstance().refresh();

                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public static class SimpleItemRecyclerViewAdapter
            extends RecyclerView.Adapter<SimpleItemRecyclerViewAdapter.ViewHolder> {

        private final ItemListActivity mParentActivity;
        private final List<Episode> mValues;
        private final View.OnClickListener mOnClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Episode item = (Episode) view.getTag();
                Context context = view.getContext();
                Intent intent = new Intent(context, ItemDetailActivity.class);
                intent.putExtra("item_id", item.id);

                context.startActivity(intent);
            }
        };

        SimpleItemRecyclerViewAdapter(ItemListActivity parent,
                                      List<Episode> items) {
            mValues = items;
            mParentActivity = parent;
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_list_content, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(final ViewHolder holder, int position) {
            Episode episode = mValues.get(position);
            holder.mTitleView.setText(episode.title);
            holder.mDateView.setText(episode.getCreatedAgo());

            if (episode.remoteThumbURL != null) {
                Glide.with(mParentActivity).load(episode.remoteThumbURL).into(holder.mCoverView);
            }
            else if (episode.remoteCoverURL != null) {
                Glide.with(mParentActivity).load(episode.remoteCoverURL).into(holder.mCoverView);
            }

            if (episode.profile.username != null) {
                holder.mUsernameView.setText(episode.profile.username);
            }

            if (episode.profile.remoteThumbURL != null) {
                Glide.with(mParentActivity).load(episode.profile.remoteThumbURL).into(holder.mProfileView);
            }

            holder.itemView.setTag(mValues.get(position));
            holder.itemView.setOnClickListener(mOnClickListener);
        }

        @Override
        public int getItemCount() {
            return mValues.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {

            final TextView mTitleView;
            final ImageView mCoverView;
            final TextView mDateView;
            final TextView mUsernameView;
            final ImageView mProfileView;

            ViewHolder(View view) {
                super(view);
                mTitleView = (TextView) view.findViewById(R.id.title);
                mCoverView = (ImageView) view.findViewById(R.id.cover);
                mDateView = view.findViewById(R.id.date);
                mUsernameView = view.findViewById(R.id.username);
                mProfileView = view.findViewById(R.id.profile_image);
            }
        }
    }
}
