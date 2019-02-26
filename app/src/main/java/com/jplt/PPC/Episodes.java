package com.jplt.PPC;

import android.content.Context;
import android.support.annotation.Nullable;
import android.util.Log;

import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
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

    public static String PID = "prealpha";
    protected static final String TAG = "JPLT:Episodes";
    protected static Episodes instance = null;

    private List<EpisodeChangeHandler> changeHandlers = new ArrayList<>();
    private ListenerRegistration listenerRegistration = null;

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
        CollectionReference ref = db.collection("podcasts")
                .document(Episodes.PID)
                .collection("episodes");

        return ref;
    }

    public void addListener(EpisodeChangeHandler handler) {
        changeHandlers.add(handler);
    }
    public void removeListener(EpisodeChangeHandler handler) {
        changeHandlers.remove(handler);
    }
    public void refresh() {
        this.clear();
        this.setupListener();
    }

    private void clear() {
        for (Episode episode: episodes) {
            for (EpisodeChangeHandler handler : changeHandlers) {
                handler.onChange(ChangeType.REMOVED, episode);
            }
        }
        episodes.clear();
        episode_map.clear();
    }
    private void setupListener() {
        CollectionReference col = getCollection();

        if (listenerRegistration != null) {
            listenerRegistration.remove();
        }

        listenerRegistration = col.orderBy("createDate", Query.Direction.DESCENDING)
                .addSnapshotListener(new EventListener<QuerySnapshot>() {
                    @Override
                    public void onEvent(@Nullable QuerySnapshot snapshot,
                                        @Nullable FirebaseFirestoreException e) {
                        if (e != null) {
                            Log.w(TAG, "Listen failed.", e);
                            return;
                        }

                        for (DocumentChange diff : snapshot.getDocumentChanges()) {
                            String id = diff.getDocument().getId();

                            Log.i(TAG, id);
                            if (diff.getType() == DocumentChange.Type.REMOVED) {
                                episode_map.remove(id);
                                episodes.remove(diff.getOldIndex());
                            }
                            else if (diff.getType() == DocumentChange.Type.ADDED) {
                                HashMap<String, Object> data = (HashMap<String, Object>) diff.getDocument().getData();
                                final Episode episode = new Episode(id, data);
                                episode.profile.load(new OnSuccessListener<Profile>() {
                                    @Override
                                    public void onSuccess(Profile profile) {
                                        for (EpisodeChangeHandler handler : changeHandlers) {
                                            handler.onChange(ChangeType.MODIFIED, episode);
                                        }
                                    }
                                });
                                episode_map.put(id, episode);
                                episodes.add(diff.getNewIndex(), episode);
                            }
                            else {
                                Episode episode = episode_map.get(diff.getDocument().getId());
                                episode.setData(diff.getDocument().getData());
                            }
                        }

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
