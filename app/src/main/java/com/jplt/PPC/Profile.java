package com.jplt.PPC;

import android.app.Activity;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.google.android.gms.auth.api.signin.internal.Storage;
import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class Profile {
    public static Profile me = null;
    public static HashMap<String, Profile> lookup = new HashMap<>();

    public String uid;
    public String username;
    public String remoteImageURL;
    public String remoteThumbURL;
    public boolean loaded = false;

    public Profile() { }

    public Profile(String uid) {
        this.uid = uid;
    }

    public static void initMe() {
        me = new Profile(FirebaseAuth.getInstance().getUid());
        lookup.put(me.uid, me);

        Log.i("PROFILE", "Loading my user profile..");
        me.load(new OnSuccessListener<Profile>() {
            @Override
            public void onSuccess(Profile profile) {
                Log.i("PROFILE", "Loaded user profile " + me.uid);
            }
        });
    }

    public static Profile getById(String uid) {
        if (!lookup.containsKey(uid)) {
            lookup.put(uid, new Profile(uid));
        }

        return lookup.get(uid);
    }

    public void setData(Map<String, Object> data) {
        if (data == null)
            return;

        if (data.containsKey("username")) {
            username = (String)data.get("username");
        }

        if (data.containsKey("remoteImageURL")) {
            remoteImageURL = (String)data.get("remoteImageURL");
        }

        if (data.containsKey("remoteThumbURL")) {
            remoteThumbURL = (String)data.get("remoteThumbURL");
        }
    }

    public Map<String, Object> getData() {
        HashMap<String,Object> result = new HashMap<String, Object>();

        result.put("uid", this.uid);

        if (username != null)
            result.put("username", username);

        if (remoteImageURL != null)
            result.put("remoteImageURL", remoteImageURL);

        if (remoteThumbURL != null)
            result.put("remoteThumbURL", remoteThumbURL);

        return result;
    }

    public DocumentReference getDocumentReference() {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        return db.collection("profiles")
                .document(this.uid);

    }

    public void load(final OnSuccessListener<Profile> callback) {
        if (uid == null)
            return;

        if (loaded) {
            callback.onSuccess(this);
            return;
        }

        DocumentReference docRef = getDocumentReference();
        Task<DocumentSnapshot> task = docRef.get();
        task.addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
            @Override
            public void onSuccess(DocumentSnapshot documentSnapshot) {
                Profile.this.setData(documentSnapshot.getData());
                loaded = true;
                callback.onSuccess(Profile.this);
            }
        });
        task.addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Log.e("PROFILE", "Exception loading profile", e);
            }
        });
    }

    public void save() {
        if (uid == null)
            return;

        Map<String, Object> doc = this.getData();
        DocumentReference docRef = getDocumentReference();
        docRef.set(doc)
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.e("PROFILE", "Exception saving", e);
                    }
                });
    }


    public String getRemotePath(String filename) {
        if (uid == null)
            return null;

        return "profiles/" + uid + "/" + filename;
    }

    public void upload(Uri uri, final OnSuccessListener<String> handler) {

        FirebaseStorage storage = FirebaseStorage.getInstance();
        final StorageReference ref = storage.getReference().child(getRemotePath(uri.getLastPathSegment()));
        final UploadTask uploadTask = ref.putFile(uri);

        Log.i("PROFILE", "Uploading " + uri);
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
                    Log.i("PROFILE", "Download url is " + downloadUri);
                    handler.onSuccess(downloadUri.toString());

                } else {
                    Log.e("PROFILE", "Upload failed.");
                }
            }
        });
    }

    public void uploadProfileImage(Uri uri) {
        upload(uri, new OnSuccessListener<String>() {
            @Override
            public void onSuccess(String downloadURL) {
                Profile.this.remoteImageURL = downloadURL;
                Profile.this.save();
            }
        });
    }

    public void uploadProfileThumb(Uri uri) {
        upload(uri, new OnSuccessListener<String>() {
            @Override
            public void onSuccess(String downloadURL) {
                Profile.this.remoteThumbURL = downloadURL;
                Profile.this.save();
            }
        });
    }
}
