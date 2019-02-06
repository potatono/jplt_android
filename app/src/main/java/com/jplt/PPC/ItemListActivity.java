package com.jplt.PPC;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.firebase.ui.auth.AuthUI;
import com.google.firebase.auth.FirebaseAuth;
import com.jplt.PPC.podcast.Episodes;

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

    /**
     * Whether or not the activity is in two-pane mode, i.e. running on a tablet
     * device.
     */
    private boolean mTwoPane;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_item_list);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setTitle(getTitle());

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });

        if (findViewById(R.id.item_detail_container) != null) {
            // The detail container view will be present only in the
            // large-screen layouts (res/values-w900dp).
            // If this view is present, then the
            // activity should be in two-pane mode.
            mTwoPane = true;
        }

        View recyclerView = findViewById(R.id.item_list);
        assert recyclerView != null;
        setupRecyclerView((RecyclerView) recyclerView);

        ensureAuthenticated();
    }

    protected void ensureAuthenticated() {

        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            // Choose authentication providers
            List<AuthUI.IdpConfig> providers = Arrays.asList(new AuthUI.IdpConfig.PhoneBuilder().build());


            // Create and launch sign-in intent
            startActivityForResult(
                    AuthUI.getInstance()
                            .createSignInIntentBuilder()
                            .setAvailableProviders(providers)
                            .build(),
                    200);
        }
    }
    private void setupRecyclerView(@NonNull final RecyclerView recyclerView) {
        Episodes episodes = Episodes.getInstance(this);
        final RecyclerView.Adapter adapter = new SimpleItemRecyclerViewAdapter(this, episodes.episodes, mTwoPane);
        episodes.addListener(new Episodes.EpisodeChangeHandler() {
            @Override
            public void onChange(Episodes.ChangeType type, Episodes.Episode episode) {
                adapter.notifyDataSetChanged();
            }
        });

        recyclerView.setAdapter(adapter);
    }

    public static class SimpleItemRecyclerViewAdapter
            extends RecyclerView.Adapter<SimpleItemRecyclerViewAdapter.ViewHolder> {

        private final ItemListActivity mParentActivity;
        private final List<Episodes.Episode> mValues;
        private final boolean mTwoPane;
        private final View.OnClickListener mOnClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Episodes.Episode item = (Episodes.Episode) view.getTag();
                Context context = view.getContext();
                Intent intent = new Intent(context, ItemDetailActivity.class);
                intent.putExtra("item_id", item.id);

                context.startActivity(intent);
            }
        };

        SimpleItemRecyclerViewAdapter(ItemListActivity parent,
                                      List<Episodes.Episode> items,
                                      boolean twoPane) {
            mValues = items;
            mParentActivity = parent;
            mTwoPane = twoPane;
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_list_content, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(final ViewHolder holder, int position) {
            Episodes.Episode episode = mValues.get(position);
            holder.mTitleView.setText(episode.title);

            if (episode.remoteCoverURL != null) {
                Glide.with(mParentActivity).load(episode.remoteCoverURL).into(holder.mCoverView);
            }

            //holder.mCoverView.setImageURI(mValues.get(position).remoteCoverURL);
            //holder.mContentView.setText(mValues.get(position).title);

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

            ViewHolder(View view) {
                super(view);
                mTitleView = (TextView) view.findViewById(R.id.title);
                mCoverView = (ImageView) view.findViewById(R.id.cover);
            }
        }
    }
}