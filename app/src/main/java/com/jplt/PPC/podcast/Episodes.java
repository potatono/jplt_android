package com.jplt.PPC.podcast;

import android.content.Context;
import android.media.Image;
import android.support.annotation.Nullable;
import android.util.Log;
import android.net.Uri;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.QuerySnapshot;
import com.jplt.PPC.R;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class Episodes {
    public enum ChangeType { ADDED, MODIFIED, REMOVED };

    public abstract static class EpisodeChangeHandler {
        public abstract void onChange(ChangeType type, Episode episode);
    }


    protected static final String TAG = "JPLT:Episodes";
    protected static Episodes instance = null;

    private Context ctx;
    private List<EpisodeChangeHandler> changeHandlers = new ArrayList<>();

    public List<Episode> episodes = new ArrayList<>();
    public HashMap<String, Episode> episode_map = new HashMap<>();

    private Episodes(Context ctx) {
        this.ctx = ctx;
        this.setupListener();
    }

    public static Episodes getInstance(Context ctx) {
        if (Episodes.instance == null)
            Episodes.instance = new Episodes(ctx);

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

        col.addSnapshotListener(new EventListener<QuerySnapshot>() {
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


    /**
     * A Episode item representing a piece of content.
     */
    public class Episode {
        public String id;
        public String title;
        public Image cover;
        public String remoteCoverURL;
        public String owner;
        public String localPath;
        public String remoteURL;

        public Episode(String id, String title, String owner, String remoteCoverURL, String remoteURL) {
            this.id = id;
            this.title = title;
            this.owner = owner;
            this.remoteCoverURL = remoteCoverURL;
            this.remoteURL = remoteURL;
        }

        public Episode() {
            this.id = UUID.randomUUID().toString();
            this.title = "New Episode";
            this.owner = FirebaseAuth.getInstance().getUid();
            this.localPath = createLocalPath("sound.m4a");
        }

        public Episode(String id, HashMap<String, Object> data) {
            this.id = id;
            this.title = (String)data.get("title");
            this.owner = (String)data.get("owner");
            this.remoteURL = (String)data.get("remoteURL");
            this.remoteCoverURL = (String)data.get("remoteCoverURL");
        }

        @Override
        public String toString() {
            return title;
        }

        public String createLocalPath(String filename) {
            String root = Episodes.this.ctx.getFilesDir().getAbsolutePath();
            String eps = root + "/" + this.id;
            new File(eps).mkdirs();

            return eps + "/" + filename;
        }
    }
}
