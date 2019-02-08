package com.jplt.PPC;

import android.net.Uri;
import android.support.annotation.NonNull;
import android.util.Log;

import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.util.Date;
import java.util.HashMap;
import java.util.UUID;

/**
 * A Episode item representing a piece of content.
 */
public class Episode {
    public String id;
    public String title;
    public String remoteCoverURL;
    public String owner;
    public String remoteURL;
    public Date createDate;
    public Long duration;

    public Episode() {
        this.id = UUID.randomUUID().toString();
        this.title = "New Episode";
        this.owner = FirebaseAuth.getInstance().getUid();
        this.remoteURL = null;
        this.remoteCoverURL = null;
        this.createDate = null;
        this.duration = new Long(0);
    }

    public Episode(String id, HashMap<String, Object> data) {
        this.id = id;
        this.title = (String)data.get("title");
        this.owner = (String)data.get("owner");
        this.remoteURL = (String)data.get("remoteURL");
        this.remoteCoverURL = (String)data.get("remoteCoverURL");
        this.createDate = (Date)data.get("createDate");
        this.duration = (Long)data.get("duration");
    }

    @Override
    public String toString() {
        return title;
    }

    public boolean isNew() {
        return (this.createDate == null);
    }

    String getRemotePath(String filename) {
        return "podcasts/" + owner + "/episodes/" + id + "/" + filename;
    }

    boolean canEdit() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();

        return user != null && user.getUid() == owner;
    }

    public void save() {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        String pid = "prealpha";

        CollectionReference col = db.collection("podcasts")
                .document(pid).collection("episodes");

        if (createDate == null)
            createDate = new Date();

        HashMap<String, Object> doc = new HashMap<>();
        doc.put("id", id);
        doc.put("title", title);
        doc.put("owner", owner);
        doc.put("createDate", new Timestamp(createDate));
        doc.put("duration", duration);

        if (remoteURL != null) {
            doc.put("remoteURL", remoteURL);
        }

        if (remoteCoverURL != null) {
            doc.put("remoteCoverURL", remoteCoverURL);
        }

        col.document(id).set(doc);
    }

    public interface UploadCompleteHandler {
        void onComplete(String downloadURL);
    }

    public void upload(Uri uri, final UploadCompleteHandler handler) {

        FirebaseStorage storage = FirebaseStorage.getInstance();
        final StorageReference ref = storage.getReference().child(getRemotePath(uri.getLastPathSegment()));
        final UploadTask uploadTask = ref.putFile(uri);

        // This is a little mind boggling, refer to Firebase Firestore docs.
        Task<Uri> urlTask = uploadTask.continueWithTask(new Continuation<UploadTask.TaskSnapshot, Task<Uri>>() {
            @Override
            public Task<Uri> then(@NonNull Task<UploadTask.TaskSnapshot> task) throws Exception {
                if (!task.isSuccessful()) {
                    throw task.getException();
                }

                // Continue with the task to get the download URL
                return ref.getDownloadUrl();
            }
        }).addOnCompleteListener(new OnCompleteListener<Uri>() {
            @Override
            public void onComplete(@NonNull Task<Uri> task) {
                if (task.isSuccessful()) {
                    Uri downloadUri = task.getResult();
                    handler.onComplete(downloadUri.toString());

                } else {
                    Log.e("Episode", "Upload failed.");
                }
            }
        });
    }

    public void uploadRecording(Uri uri) {
        upload(uri, new UploadCompleteHandler() {
            @Override
            public void onComplete(String downloadURL) {
                Episode.this.remoteURL = downloadURL;
                Episode.this.save();
            }
        });
    }

    public void uploadCover(Uri uri) {
        upload(uri, new UploadCompleteHandler() {
            @Override
            public void onComplete(String downloadURL) {
                Episode.this.remoteCoverURL = downloadURL;
                Episode.this.save();
            }
        });
    }


    public void delete(final OnCompleteListener<Void> deleteHandler) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        String pid = "prealpha";

        CollectionReference col = db.collection("podcasts")
                .document(pid).collection("episodes");
        DocumentReference doc = col.document(id);

        FirebaseStorage storage = FirebaseStorage.getInstance();
        final StorageReference ref = storage.getReference();

        Log.i("Episode", "Deleting episode " + id);

        OnCompleteListener<Void> docListener = new OnCompleteListener<Void>() {
            @Override
            public void onComplete(@NonNull Task<Void> task) {
                ref.child(getRemotePath("sound.aac")).delete();
                ref.child(getRemotePath("cover.jpg")).delete();
                deleteHandler.onComplete(task);
            }
        };

        doc.delete().addOnCompleteListener(docListener);
    }


}
