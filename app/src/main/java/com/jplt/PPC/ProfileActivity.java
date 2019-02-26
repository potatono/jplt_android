package com.jplt.PPC;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.MediaStore;
import android.support.design.widget.AppBarLayout;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.yalantis.ucrop.UCrop;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class ProfileActivity extends AppCompatActivity {
    public static final int INTENT_PICK_IMAGE = 1;
    public static final String PROFILE_FILENAME = "profile.jpg";

    Profile mProfile;
    ImageButton mProfileButton;
    EditText mUsernameEdit;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        mProfile = Profile.me;
        Log.i("PROFILE", "Profile is " + mProfile.uid + " " + mProfile.username);
        mProfileButton = findViewById(R.id.profile_image);
        mUsernameEdit = findViewById(R.id.username);

        setupToolbar();
        resizeControls();
        setupEditActions();
        loadProfile();

        setResult(RESULT_CANCELED);
    }

    void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);

        AppBarLayout appbar = findViewById(R.id.app_bar);
        Drawable drawable = ContextCompat.getDrawable(this, R.drawable.appbar);
        CenterCropDrawable cropped = new CenterCropDrawable(drawable);
        cropped.setBounds(0,0,appbar.getMeasuredWidth(),50);
        appbar.setBackground(cropped);

        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);
        getSupportActionBar().setDisplayShowTitleEnabled(false);
        toolbar.setTitle("");
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Have to set this up so that hitting top back button will result in onActivityResult update.
                finish();
            }
        });
    }

    void resizeControls() {
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);


        RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                metrics.widthPixels - 40);
        lp.setMargins(20, metrics.widthPixels / 4, 20, 0);
        mProfileButton.setLayoutParams(lp);

        mProfileButton.requestLayout();
        mUsernameEdit.requestLayout();
    }

    void setupEditActions() {
        mProfileButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startEditingProfileImage();
            }
        });

        mUsernameEdit.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
                mProfile.username = mUsernameEdit.getText().toString();
                mProfile.save();
                ProfileActivity.this.setResult(RESULT_OK);
                return false;
            }
        });
    }

    void loadProfile() {
        mProfile.load(new OnSuccessListener<Profile>() {
            @Override
            public void onSuccess(Profile profile) {
                if (profile.remoteImageURL != null) {
                    Glide.with(ProfileActivity.this)
                            .load(mProfile.remoteImageURL)
                            .into(mProfileButton);
                }

                if (profile.username != null) {
                    mUsernameEdit.setText(profile.username);
                }
            }
        });
    }

    @Override
    public void onBackPressed() {
        Log.e("PROFILE", "BACK PRESSED");
        super.onBackPressed();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode != RESULT_OK)
            return;

        if (requestCode == INTENT_PICK_IMAGE) {
            startCroppingProfileImage(data.getData());
        }
        else if (requestCode == UCrop.REQUEST_CROP) {
            Uri resultUri = UCrop.getOutput(data);
            finishEditingProfileImage(resultUri);
        }
    }

    void startEditingProfileImage() {
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(Intent.createChooser(intent, "Select Cover"),
                INTENT_PICK_IMAGE);
    }

    void startCroppingProfileImage(Uri sourceUri) {
        Uri destinationUri = Uri.fromFile(new File(getCacheDir(), PROFILE_FILENAME));

        Log.d("CROP", sourceUri.toString());
        Log.d("CROP", destinationUri.toString());

        UCrop.of(sourceUri, destinationUri)
                .withAspectRatio(1, 1)
                .withMaxResultSize(640 / 2, 640 / 2)
                .start(this);
    }

    void finishEditingProfileImage(Uri resultUri) {
        Log.d("CROP", resultUri.toString());

        Bitmap bitmap = cropToPath(resultUri);
        mProfileButton.setImageBitmap(bitmap);

        File image = saveProfileImage(bitmap);
        mProfile.uploadProfileImage(Uri.fromFile(image));

        File thumb = saveProfileThumb(bitmap);
        mProfile.uploadProfileThumb(Uri.fromFile(thumb));

        setResult(RESULT_OK);
    }

    Bitmap cropToPath(Uri imageUri) {
        Bitmap src;

        try {
            src = MediaStore.Images.Media.getBitmap(this.getContentResolver(), imageUri);
        } catch (IOException e) {
            Log.e("PROFILE", "Exception reading image", e);
            return null;
        }

        // Create a mutable bitmap and use canvas to copy the original in.
        // Bitmap.copy is a liar and will not include alpha channel on JPEGs
        Bitmap bitmap = Bitmap.createBitmap(320, 320, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawBitmap(src, (float) 0, (float) 0, null);

        // Create a path for the triangle
        Path path = new Path();
        path.moveTo(12 / 2, 20 / 2);
        path.lineTo(628 / 2, 88 / 2);
        path.lineTo(328 / 2, 619 / 2);
        path.close();

        // Create the inverse path for blanking out the pixels we're cropping out
        Path clipPath = new Path();
        clipPath.addRect((float) 0, (float) 0, (float) 320, (float) 320, Path.Direction.CW);
        clipPath.op(path, Path.Op.DIFFERENCE);

        // Fill the inverse path with transparent pixels
        Paint paint = new Paint();
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC));
        paint.setColor(Color.TRANSPARENT);
        canvas.drawPath(clipPath, paint);

        // Stroke the original triangle
        paint = new Paint();
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(Color.argb(255, 89, 81, 190));
        paint.setStrokeWidth(3);
        paint.setAntiAlias(true);
        canvas.drawPath(path, paint);

        return bitmap;
    }

    File saveProfileImage(Bitmap bitmap) {
        try {
            // Save as JPEG
            File sd = getFilesDir();
            File dest = new File(sd, "profile.jpg");

            Bitmap jpegOutput = Bitmap.createBitmap(320, 320, Bitmap.Config.ARGB_8888);
            jpegOutput.eraseColor(Color.WHITE);
            Canvas jpegCanvas = new Canvas(jpegOutput);
            jpegCanvas.drawBitmap(bitmap, 0, 0, null);
            jpegOutput.compress(Bitmap.CompressFormat.JPEG, 80, new FileOutputStream(dest));

            return dest;
        } catch (IOException ex) {
            Log.e("PROFILE", "Exception writing profile image", ex);
            return null;
        }
    }

    File saveProfileThumb(Bitmap bitmap) {
        try {
            // Shrink and save as PNG
            File sd = getFilesDir();
            File dest = new File(sd, "profile-thumb.png");

            int size = 128;
            Bitmap pngOutput = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas pngCanvas = new Canvas(pngOutput);

            float ratioX = size / (float) bitmap.getWidth();
            float ratioY = size / (float) bitmap.getHeight();
            float middleX = size / 2.0f;
            float middleY = size / 2.0f;

            Matrix scaleMatrix = new Matrix();
            scaleMatrix.setScale(ratioX, ratioY, middleX, middleY);

            pngCanvas.setMatrix(scaleMatrix);
            Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
            paint.setAntiAlias(true);
            paint.setDither(true);
            paint.setFilterBitmap(true);
            pngCanvas.drawBitmap(bitmap,
                    middleX - bitmap.getWidth() / 2, middleY - bitmap.getHeight() / 2,
                    paint);

//            pngCanvas.drawBitmap(bitmap, null, new Rect(0, 0, 64, 64), null);
            //Bitmap pngOutput = Bitmap.createScaledBitmap(bitmap, 64, 64, false);

            pngOutput.compress(Bitmap.CompressFormat.PNG, 100, new FileOutputStream(dest));

            return dest;
        } catch (IOException ex) {
            Log.e("PROFILE", "Exception writing profile thumb", ex);
            return null;
        }
    }
}
