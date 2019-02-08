package com.jplt.PPC;

import android.content.Context;
import android.support.annotation.Nullable;
import android.util.Log;

import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

public class Episodes {
    public enum ChangeType { ADDED, MODIFIED, REMOVED };

    public abstract static class EpisodeChangeHandler {
        public abstract void onChange(ChangeType type, Episode episode);
    }

    protected static final String TAG = "JPLT:Episodes";
    protected static Episodes instance = null;

    private List<EpisodeChangeHandler> changeHandlers = new ArrayList<>();

    public List<Episode> episodes = new ArrayList<>();
    public HashMap<String, Episode> episode_map = new HashMap<>();

    private Episodes() {
        this.setupListener();
    }

    public static Episodes getInstance() {
        if (Episodes.instance == null)
            Episodes.instance = new Episodes();

        return Episodes.instance;
    }

    public CollectionReference getCollection() {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        String pid = "prealpha";
        CollectionReference ref = db.collection("podcasts")
                .document(pid)
                .collection("episodes");

        return ref;
    }

    public void addListener(EpisodeChangeHandler handler) {
        changeHandlers.add(handler);
    }
    public void removeListener(EpisodeChangeHandler handler) {
        changeHandlers.remove(handler);
    }

    private void setupListener() {
        CollectionReference col = getCollection();

        col.orderBy("createDate", Query.Direction.DESCENDING)
           .addSnapshotListener(new EventListener<QuerySnapshot>() {
            @Override
            public void onEvent(@Nullable QuerySnapshot snapshot,
                                @Nullable FirebaseFirestoreException e)
            {
                if (e != null) {
                    Log.w(TAG, "Listen failed.", e);
                    return;
                }

                for (DocumentChange diff : snapshot.getDocumentChanges()) {
                    String id = diff.getDocument().getId();

                    Log.i(TAG, id);
                    if (diff.getType() == DocumentChange.Type.REMOVED) {
                        episode_map.remove(id);
                    }
                    else {
                        HashMap<String, Object> data = (HashMap<String,Object>) diff.getDocument().getData();
                        Episode episode = new Episode(id, data);
                        episode_map.put(id, episode);
                    }
                }

                episodes.clear();
                for (Episode ep : episode_map.values()) {
                    episodes.add(ep);
                }

                // Because we're collecting in a hash we need sort again.. TODO FIXME GROSS
                Collections.sort(episodes, new Comparator<Episode>() {
                    @Override
                    public int compare(Episode lhs, Episode rhs) {
                        if (lhs == null && rhs == null)
                            return 0;
                        else if (lhs == null)
                            return -1;
                        else if (rhs == null)
                            return 1;
                        else
                            return rhs.createDate.compareTo(lhs.createDate);
                    }
                });

                for (DocumentChange diff : snapshot.getDocumentChanges()) {
                    String id = diff.getDocument().getId();
                    ChangeType type = ChangeType.valueOf(diff.getType().name());
                    Episode episode = episode_map.get(id);

                    for (EpisodeChangeHandler handler : changeHandlers) {
                        handler.onChange(type, episode);
                    }
                }
            }
        });
    }


}
