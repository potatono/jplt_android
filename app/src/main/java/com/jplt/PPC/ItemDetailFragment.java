package com.jplt.PPC;

import android.app.Activity;
import android.support.design.widget.CollapsingToolbarLayout;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.jplt.PPC.dummy.DummyContent;
import com.jplt.PPC.podcast.Episodes;

/**
 * A fragment representing a single Item detail screen.
 * This fragment is either contained in a {@link ItemListActivity}
 * in two-pane mode (on tablets) or a {@link ItemDetailActivity}
 * on handsets.
 */
public class ItemDetailFragment extends Fragment {
//    /**
//     * The fragment argument representing the item ID that this fragment
//     * represents.
//     */
//    public static final String ARG_ITEM_ID = "item_id";
//
//    /**
//     * The dummy content this fragment is presenting.
//     */
//    private Episodes.Episode mItem;
//
//    /**
//     * Mandatory empty constructor for the fragment manager to instantiate the
//     * fragment (e.g. upon screen orientation changes).
//     */
//    public ItemDetailFragment() {
//    }
//
//    @Override
//    public void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//
//        Log.i("Shithead","in onCreate..");
//
//        if (getArguments().containsKey(ARG_ITEM_ID)) {
//            // Load the dummy content specified by the fragment
//            // arguments. In a real-world scenario, use a Loader
//            // to load content from a content provider.
//            Episodes episodes = Episodes.getInstance(this.getContext());
//            mItem = episodes.episode_map.get(getArguments().getString(ARG_ITEM_ID));
//            Log.i("Shithead", mItem.toString());
//        }
//    }
//
//    @Override
//    public View onCreateView(LayoutInflater inflater, ViewGroup container,
//                             Bundle savedInstanceState) {
//        View rootView = inflater.inflate(R.layout.item_detail, container, false);
//
//        Log.i("Shithead","in onCreateView..");
//        // Show the dummy content as text in a TextView.
//        if (mItem != null) {
//            ((TextView) rootView.findViewById(R.id.title)).setText(mItem.title);
//
//            if (mItem.remoteCoverURL != null) {
//                ImageView cover = rootView.findViewById(R.id.cover);
//                Glide.with(this).load(mItem.remoteCoverURL).into(cover);
//            }
//
//        }
//
//        return rootView;
//    }
}
