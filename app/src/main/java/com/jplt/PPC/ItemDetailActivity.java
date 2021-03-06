package com.jplt.PPC;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AlertDialog;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.support.v7.app.AppCompatActivity;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.storage.FileDownloadTask;
import com.google.firebase.storage.OnProgressListener;
import com.yalantis.ucrop.UCrop;

import java.io.File;
import java.io.IOException;

/**
 * An activity representing a single Item detail screen. This
 * activity is only used on narrow width devices. On tablet-size devices,
 * item details are presented side-by-side with a list of items
 * in a {@link ItemListActivity}.
 */
public class ItemDetailActivity extends AppCompatActivity implements ActivityCompat.OnRequestPermissionsResultCallback {

    public static final int INTENT_PICK_IMAGE = 1;
    public static final int PERM_RECORD_AUDIO = 0;

    public static String COVER_FILENAME = "cover.jpg";
    public static String AUDIO_FILENAME = "sound.aac";

    enum State {EMPTY, RECORDING, STOPPED, PLAYING, PAUSED}

    State mState = State.EMPTY;
    Episode mEpisode = new Episode();

    EditText mTitle = null;
    ImageView mCover = null;
    Button mMediaButton = null;
    Button mDeleteButton = null;
    SeekBar mSeekBar = null;
    TextView mTimeElapsed = null;
    TextView mTimeRemain = null;
    MediaPlayer mPlayer = null;
    Runnable mPlayerTimer = null;
    Handler mPlayerHandler = null;
    boolean mPlayerIsPrepared = false;
    boolean mClosing = false;
    boolean mSeeking = false;
    MediaRecorder mRecorder = null;
    Runnable mRecorderTimer = null;
    Handler mRecorderHandler = null;
    long mRecorderStartTime = 0;
    ImageView mProfileView = null;
    TextView mUsernameView = null;
    TextView mDateView = null;
    ProgressBar mDownloadProgress = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.item_detail);

        setupControls();
        setupCover();
        setupPlayer();
        setEpisode();
        initializeControls();

        if (mEpisode.isNew())
            setupRecorder();
    }

    @Override
    protected void onDestroy() {
        mClosing = true;
        mPlayer.release();

        super.onDestroy();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        finishEditingTitle();

        return super.onTouchEvent(event);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode != RESULT_OK)
            return;

        if (requestCode == INTENT_PICK_IMAGE) {
            startCroppingCover(data.getData());
        } else if (requestCode == UCrop.REQUEST_CROP) {
            Uri resultUri = UCrop.getOutput(data);
            finishEditingCover(resultUri);
        }
    }

    File getLocalFile() {
        File path = new File(getCacheDir(), mEpisode.id);
        path.mkdirs();

        return new File(path, AUDIO_FILENAME);
    }

    boolean isOwner() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();

        return (user != null && mEpisode.owner.equals(user.getUid()));
    }

    void setupControls() {

        mTitle = findViewById(R.id.detail_title);
        mTitle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startEditingTitle();
            }
        });
        mTitle.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
                finishEditingTitle();
                return false;
            }
        });

        mMediaButton = findViewById(R.id.media_button);
        mMediaButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finishEditingTitle();
                ItemDetailActivity.this.toggleState();
            }
        });

        mDeleteButton = findViewById(R.id.delete_button);
        mDeleteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startDelete();
            }
        });

        FloatingActionButton back_button = findViewById(R.id.back_button);
        back_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ItemDetailActivity.this.finish();
            }
        });

        mSeekBar = findViewById(R.id.seek);
        mSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                ItemDetailActivity.this.seekChanged();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                ItemDetailActivity.this.startSeek();
                finishEditingTitle();
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                ItemDetailActivity.this.stopSeek();
            }
        });

        mTimeElapsed = findViewById(R.id.time_elapsed);
        mTimeRemain = findViewById(R.id.time_remain);

        mUsernameView = findViewById(R.id.username);
        mProfileView = findViewById(R.id.profile_image);
        mDateView = findViewById(R.id.date);

        mDownloadProgress = findViewById(R.id.download_progress);
    }

    void setupCover() {
        // Set cover to 1:1 aspect w/screen width
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);

        mCover = findViewById(R.id.detail_cover);
        mCover.getLayoutParams().height = metrics.widthPixels;
        mCover.requestLayout();

        mCover.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startEditingCover();
            }
        });
    }

    String formatTime(long totalSeconds) {
        return formatTime((int) totalSeconds);
    }

    String formatTime(int totalSeconds) {
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;

        return String.format("%d:%02d", minutes, seconds);
    }

    void setupPlayer() {
        mPlayer = new MediaPlayer();
        mPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mediaPlayer) {
                mPlayerIsPrepared = true;

                mTimeRemain.setText(formatTime(mPlayer.getDuration() / 1000));
                Log.d("MediaPlayer", "Prepared.");
            }
        });
        mPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mediaPlayer) {
                setState(State.STOPPED);
                mTimeElapsed.setText("0:00");
                mTimeRemain.setText(formatTime(mPlayer.getDuration() / 1000));
                mSeekBar.setProgress(0);
                Log.d("MediaPlayer", "Playback complete.");
            }
        });

        AudioAttributes.Builder aaBuilder = new AudioAttributes.Builder();
        aaBuilder.setContentType(AudioAttributes.CONTENT_TYPE_MUSIC);
        aaBuilder.setUsage(AudioAttributes.USAGE_MEDIA);
        aaBuilder.setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED);
        mPlayer.setAudioAttributes(aaBuilder.build());

        mPlayerHandler = new Handler();
        mPlayerTimer = new Runnable() {
            @Override
            public void run() {
                if (mClosing || mState == State.STOPPED)
                    return;

                long total = mPlayer.getDuration();
                long current = mPlayer.getCurrentPosition();
                int remain = (int) ((total - current) / 1000);

                if (!mSeeking)
                    mSeekBar.setProgress((int) (100 * current / total));

                mTimeElapsed.setText("-" + formatTime((int) current / 1000));
                mTimeRemain.setText(formatTime(remain));

                if (mPlayer.isPlaying())
                    mPlayerHandler.postDelayed(this, 100);
            }
        };
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERM_RECORD_AUDIO) {
            setupRecorder();
        }
    }

    void setupRecorder() {
        Log.d("MediaRecorder", "Setting up.");

        int permission = ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO);
        if (permission != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO}, PERM_RECORD_AUDIO);

            return;
        }

        //new File(getCacheDir(), AUDIO_FILENAME).getAbsolutePath();
        String localPath = getLocalFile().getAbsolutePath();

        mRecorder = new MediaRecorder();
        mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mRecorder.setOutputFormat(MediaRecorder.OutputFormat.AAC_ADTS);
        mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        mRecorder.setAudioSamplingRate(44100);
        mRecorder.setAudioEncodingBitRate(96000);
        mRecorder.setAudioChannels(1);
        mRecorder.setOutputFile(localPath);

        try {
            mRecorder.prepare();
        } catch (IOException e) {
            Log.e("MediaRecorder", "Caught IOException", e);
            setState(State.STOPPED);
        }

        mRecorderHandler = new Handler();
        mRecorderTimer = new Runnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                mTimeRemain.setText(formatTime((int) ((now - mRecorderStartTime) / 1000)));

                if (mState == State.RECORDING)
                    mRecorderHandler.postDelayed(this, 100);
            }
        };
    }

    void setEpisode() {
        String id = getIntent().getStringExtra("item_id");

        if (id != null) {
            Episodes episodes = Episodes.getInstance();
            mEpisode = episodes.episode_map.get(id);
        }
    }

    void initializeControls() {
        mTitle.setText(mEpisode.title);
        mDateView.setText(mEpisode.getCreatedAgo());

        if (mEpisode.remoteCoverURL != null) {
            ImageView cover = findViewById(R.id.detail_cover);
            Glide.with(this).load(mEpisode.remoteCoverURL).into(cover);
        }

        if (mEpisode.remoteURL != null) {
            setPlayerMedia();
        }

        if (mEpisode.profile.username != null) {
            mUsernameView.setText(mEpisode.profile.username);
        }

        if (mEpisode.profile.remoteThumbURL != null) {
            Glide.with(this).load(mEpisode.profile.remoteThumbURL).into(mProfileView);
        }


        if (!isOwner()) {
            mTitle.setEnabled(false);
            mCover.setEnabled(false);
            mDeleteButton.setEnabled(false);
        }
    }

    void preparePlayer(Uri uri) {
        try {
            mPlayer.setDataSource(this, uri);
            mPlayer.prepare();
        } catch (IOException e) {
            Log.e("PLAYER", "Exception preparing player", e);
        }
    }

    void setPlayerMedia() {
        setState(State.STOPPED);

        final File localFile = getLocalFile();

        if (localFile.exists()) {
            preparePlayer(Uri.fromFile(localFile));
        }
        else {
            mMediaButton.setVisibility(View.INVISIBLE);
            mDownloadProgress.setVisibility(View.VISIBLE);

            OnSuccessListener<FileDownloadTask.TaskSnapshot> successHandler = new OnSuccessListener<FileDownloadTask.TaskSnapshot>() {
                @Override
                public void onSuccess(FileDownloadTask.TaskSnapshot taskSnapshot) {
                    preparePlayer(Uri.fromFile(localFile));
                    mMediaButton.setVisibility(View.VISIBLE);
                    mDownloadProgress.setVisibility(View.INVISIBLE);
                }
            };
            OnProgressListener<FileDownloadTask.TaskSnapshot> progressHandler = new OnProgressListener<FileDownloadTask.TaskSnapshot>() {
                @Override
                public void onProgress(FileDownloadTask.TaskSnapshot taskSnapshot) {
                    long total = taskSnapshot.getTotalByteCount();
                    long xfer = taskSnapshot.getBytesTransferred();

                    if (total > 0) {
                        mDownloadProgress.setProgress((int)(xfer / total * 100));
                    }
                }
            };

            mEpisode.downloadRecording(Uri.fromFile(localFile), successHandler, progressHandler);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            // This ID represents the Home or Up button. In the case of this
            // activity, the Up button is shown. For
            // more details, see the Navigation pattern on Android Design:
            //
            // http://developer.android.com/design/patterns/navigation.html#up-vs-back
            //
            navigateUpTo(new Intent(this, ItemListActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    void setState(State state) {
        mState = state;
        int imageId = 0;

        switch (state) {
            case EMPTY:
                imageId = R.drawable.record;
                break;
            case RECORDING:
                imageId = R.drawable.stop;
                break;
            case STOPPED:
                imageId = R.drawable.play;
                break;
            case PLAYING:
                imageId = R.drawable.pause;
                break;
            case PAUSED:
                imageId = R.drawable.play;
                break;
        }

        Button button = findViewById(R.id.media_button);
        button.setCompoundDrawablesWithIntrinsicBounds(imageId, 0, 0, 0);
    }

    void toggleState() {
        switch (mState) {
            case EMPTY:
                startRecording();
                break;
            case RECORDING:
                stopRecording();
                break;
            case STOPPED:
                startPlayback();
                break;
            case PLAYING:
                pausePlayback();
                break;
            case PAUSED:
                resumePlayback();
                break;
        }
    }

    void startRecording() {
        Log.d("MediaPlayer", "Starting recording.");
        setState(State.RECORDING);

        if (mRecorder != null) {
            mRecorder.start();
            mRecorderStartTime = System.currentTimeMillis();
            mRecorderHandler.postDelayed(mRecorderTimer, 100);
        }
    }

    void stopRecording() {
        Log.d("MediaPlayer", "Stopping Recording.");
        setState(State.STOPPED);

        if (mRecorder != null) {
            //Uri localUri = Uri.fromFile(new File(getCacheDir(), AUDIO_FILENAME));
            Uri localUri = Uri.fromFile(getLocalFile());
            mRecorder.stop();
            mEpisode.duration = new Long(System.currentTimeMillis() - mRecorderStartTime);

            try {
                mPlayer.setDataSource(this, localUri);
                mPlayer.prepare();
            } catch (IOException e) {
                Log.e("MediaPlayer", "Caught IOException", e);
            }

            mEpisode.uploadRecording(localUri);
        }
    }

    void startPlayback() {
        Log.d("MediaPlayer", "Starting playback..");
        setState(State.PLAYING);

        if (mPlayerIsPrepared) {
            resumePlayback();
        }
    }

    void pausePlayback() {
        Log.d("MediaPlayer", "Pausing playback..");
        setState(State.PAUSED);

        if (mPlayer.isPlaying())
            mPlayer.pause();
    }

    void resumePlayback() {
        Log.d("MediaPlayer", "Resuming playback..");
        setState(State.PLAYING);

        if (mPlayerIsPrepared) {
            mPlayer.start();
            mPlayerHandler.postDelayed(mPlayerTimer, 100);
        }
    }

    void startSeek() {
        if (mPlayerIsPrepared) {
            Log.d("MediaPlayer", "Start seek..");
            if (mPlayer.isPlaying())
                mPlayer.pause();

            mSeeking = true;
        }
    }

    void stopSeek() {
        if (mPlayerIsPrepared) {
            Log.d("MediaPlayer", "Stop seek..");
            if (mState == State.PLAYING)
                mPlayer.start();

            mSeeking = false;
        }
    }

    void seekChanged() {
        if (mPlayerIsPrepared && mSeeking) {
            Log.d("MediaPlayer", "Seek changed..");

            int duration = mPlayer.getDuration();
            int seekTo = (int) (mSeekBar.getProgress() / 100.0 * duration);
            Log.d("MediaPlayer", "Seeking to " + seekTo + "/" + duration);
            mPlayer.seekTo(seekTo);
            mPlayerHandler.postDelayed(mPlayerTimer, 100);
        }
    }

    void startEditingTitle() {
        Log.d("ItemDetail", "Requested edit title.");
        mTitle.setCursorVisible(true);

        if (mTitle.getText().toString().equals("New Episode")) {
            Log.i("Title", "New Episode");
            mTitle.selectAll();
        }
    }

    void finishEditingTitle() {
        if (mTitle.isCursorVisible()) {
            mTitle.setCursorVisible(false);
            mEpisode.title = mTitle.getText().toString();
            mEpisode.save();
        }
    }

    void startEditingCover() {
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(Intent.createChooser(intent, "Select Cover"),
                INTENT_PICK_IMAGE);
    }

    void startCroppingCover(Uri sourceUri) {
        Uri destinationUri = Uri.fromFile(new File(getCacheDir(), COVER_FILENAME));

        Log.d("CROP", sourceUri.toString());
        Log.d("CROP", destinationUri.toString());

        UCrop.of(sourceUri, destinationUri)
                .withAspectRatio(1, 1)
                .withMaxResultSize(1024, 1024)
                .start(this);
    }

    void finishEditingCover(Uri resultUri) {
        Log.d("CROP", resultUri.toString());
        mCover.setImageURI(resultUri);
        mEpisode.uploadCover(resultUri);
    }

    void startDelete() {
        if (!isOwner())
            return;

        AlertDialog.Builder dialog = new AlertDialog.Builder(this);
        dialog.setTitle("Delete Episode?");
        dialog.setMessage("Are you sure you want to delete this episode?");
        dialog.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                finishDelete();
                dialogInterface.dismiss();
            }
        });

        dialog.setNegativeButton("No", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                dialogInterface.dismiss();
            }
        });

        dialog.show();
    }

    void finishDelete() {
        mEpisode.delete(new OnCompleteListener<Void>() {
            @Override
            public void onComplete(@NonNull Task<Void> task) {
                ItemDetailActivity.this.finish();
            }
        });

    }
}
