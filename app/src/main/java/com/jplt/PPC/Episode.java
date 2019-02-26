package com.jplt.PPC;

import android.icu.util.DateInterval;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.util.Log;

import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FileDownloadTask;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.OnProgressListener;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.File;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A Episode item representing a piece of content.
 */
public class Episode {
    public String id;
    public String title;
    public String remoteThumbURL;
    public String remoteCoverURL;
    public String owner;
    public String filename;
    public String remoteURL;
    public Date createDate;
    public Long duration;
    public Profile profile;

    public Episode() {
        this.id = UUID.randomUUID().toString();
        this.title = "New Episode";
        this.owner = FirebaseAuth.getInstance().getUid();
        this.filename = null;
        this.remoteURL = null;
        this.remoteCoverURL = null;
        this.remoteThumbURL = null;
        this.createDate = null;
        this.duration = new Long(0);

        if (Profile.me != null)
            this.profile = Profile.me;
        else
            this.profile = new Profile();
    }

    public Episode(String id, HashMap<String, Object> data) {
        this.id = id;
        this.setData(data);
    }

    public void setData(Map<String, Object> data) {
        this.title = (String)data.get("title");
        this.owner = (String)data.get("owner");
        this.profile = Profile.getById(this.owner);

        if (data.containsKey("filename")) {
            this.filename = (String)data.get("filename");
        }
        else if (data.containsKey("localURL")) {
            this.filename = Uri.parse((String) data.get("localURL")).getLastPathSegment();
        }
        else if (data.containsKey("remoteURL")) {
            this.filename = Uri.parse((String) data.get("remoteURL")).getLastPathSegment();
            this.filename = Uri.decode(this.filename);
            this.filename = this.filename.substring(this.filename.lastIndexOf('/') + 1);
        }

        this.remoteURL = (String)data.get("remoteURL");
        this.remoteThumbURL = (String)data.get("remoteThumbURL");
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
        String pid = Episodes.PID;

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

        if (filename != null) {
            doc.put("filename", filename);
        }

        if (remoteURL != null) {
            doc.put("remoteURL", remoteURL);
        }

        if (remoteCoverURL != null) {
            doc.put("remoteCoverURL", remoteCoverURL);
        }

        col.document(id).set(doc);
    }


    public void upload(Uri uri, final OnSuccessListener<String> handler) {

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
                    handler.onSuccess(downloadUri.toString());

                } else {
                    Log.e("Episode", "Upload failed.");
                }
            }
        });
    }

    public void uploadRecording(final Uri uri) {
        upload(uri, new OnSuccessListener<String>() {
            @Override
            public void onSuccess(String downloadURL) {
                Episode.this.filename = uri.getLastPathSegment();
                Episode.this.remoteURL = downloadURL;
                Episode.this.save();
            }
        });
    }

    public void uploadCover(final Uri uri) {
        upload(uri, new OnSuccessListener<String>() {
            @Override
            public void onSuccess(String downloadURL) {
                Episode.this.filename = uri.getLastPathSegment();
                Episode.this.remoteCoverURL = downloadURL;
                Episode.this.save();
            }
        });
    }

    public void delete(final OnCompleteListener<Void> deleteHandler) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        String pid = Episodes.PID;

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

    public String getCreatedAgo() {
        if (createDate == null) {
            return "A moment ago";
        }

        Date now = new Date();
        long interval = now.getTime() - createDate.getTime();

        final int periods[] = { 365, 24, 60, 60, 1000 };
        final String names[] = { "year", "day", "hour", "minute" };

        for (int i=0; i<names.length; i++) {
            long period = 1;

            for (int j=i; j<periods.length; j++) {
                period = period * periods[j];
            }

            if (interval > period) {
                long n = interval / period;

                String result = n + " " + names[i];
                if (n > 1) result += "s";
                result += " ago";

                return result;
            }
        }

        return "A moment ago";
    }

    public void downloadRecording(Uri localUri, OnSuccessListener<FileDownloadTask.TaskSnapshot> successListener, OnProgressListener progressListener) {
        if (remoteURL == null) {
            Log.e("EPISODE", "Cannot download recording, remoteURL is null.");
            return;
        }

        FirebaseStorage storage = FirebaseStorage.getInstance();
        Log.i("EPISODE", "Remote path is " + getRemotePath(filename));
        final StorageReference ref = storage.getReference().child(getRemotePath(filename));
        FileDownloadTask task = ref.getFile(localUri);

        task.addOnSuccessListener(successListener);
        task.addOnProgressListener(progressListener);
        task.addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Log.e("EPISODE", "Failure downloading episode", e);
            }
        });
    }
}
