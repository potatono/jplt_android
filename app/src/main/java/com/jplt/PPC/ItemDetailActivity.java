package com.jplt.PPC;

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.view.ActionMode;
import android.support.v7.widget.AppCompatImageButton;
import android.support.v7.widget.Toolbar;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.app.ActionBar;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.jplt.PPC.podcast.Episodes;

import java.io.IOException;

/**
 * An activity representing a single Item detail screen. This
 * activity is only used on narrow width devices. On tablet-size devices,
 * item details are presented side-by-side with a list of items
 * in a {@link ItemListActivity}.
 */
public class ItemDetailActivity extends AppCompatActivity {

    enum State { EMPTY, RECORDING, STOPPED, PLAYING, PAUSED }

    State mState = State.EMPTY;
    Episodes.Episode mEpisode = null;

    Button mMediaButton = null;
    SeekBar mSeekBar = null;
    TextView mTimeElapsed = null;
    TextView mTimeRemain = null;
    MediaPlayer mPlayer = null;
    Runnable mPlayerTimer = null;
    Handler mPlayerHandler = null;
    boolean mPlayerIsPrepared = false;
    boolean mClosing = false;
    boolean mSeeking = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.item_detail);

        setupControls();
        setupCover();
        setupPlayer();
        setEpisode();
        initializeControls();
    }

    @Override
    protected void onDestroy() {
        mClosing = true;
        mPlayer.release();

        super.onDestroy();
    }

    void setupControls() {
        mMediaButton = findViewById(R.id.media_button);
        mMediaButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) { ItemDetailActivity.this.toggleState(); }
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
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                ItemDetailActivity.this.stopSeek();
            }
        });

        mTimeElapsed = findViewById(R.id.time_elapsed);
        mTimeRemain = findViewById(R.id.time_remain);
    }

    void setupCover() {
        // Set cover to 1:1 aspect w/screen width
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);

        ImageView cover = findViewById(R.id.detail_cover);
        cover.getLayoutParams().height = metrics.widthPixels;
        cover.requestLayout();
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
            }
        });
        mPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mediaPlayer) {
                setState(State.STOPPED);
                mTimeElapsed.setText("0:00");
                mTimeRemain.setText(formatTime(mPlayer.getDuration() / 1000));
                mSeekBar.setProgress(0);
            }
        });

        mPlayerHandler = new Handler();
        mPlayerTimer = new Runnable() {
            @Override
            public void run() {
                if (mClosing || mState == State.STOPPED)
                    return;

                long total = mPlayer.getDuration();
                long current = mPlayer.getCurrentPosition();
                int remain = (int)((total - current) / 1000);

                if (!mSeeking)
                    mSeekBar.setProgress((int)(100 * current / total));

                mTimeElapsed.setText("-" + formatTime((int)current/1000));
                mTimeRemain.setText(formatTime(remain));

                if (mPlayer.isPlaying())
                    mPlayerHandler.postDelayed(this, 100);
            }
        };
    }

    void setEpisode() {
        String id = getIntent().getStringExtra("item_id");

        if (id != null) {
            Episodes episodes = Episodes.getInstance(this);
            mEpisode = episodes.episode_map.get(id);
        }
        else {
            mEpisode = null;
        }
    }

    void initializeControls() {
        if (mEpisode != null) {
            TextView titleView = findViewById(R.id.detail_title);
            titleView.setText(mEpisode.title);

            if (mEpisode.remoteCoverURL != null) {
                ImageView cover = findViewById(R.id.detail_cover);
                Glide.with(this).load(mEpisode.remoteCoverURL).into(cover);
            }

            if (mEpisode.remoteURL != null) {
                setState(State.STOPPED);

                try {
                    mPlayer.setDataSource(mEpisode.remoteURL);
                    mPlayer.prepare();
                }
                catch (IOException e) {
                    Log.e("MediaPlayer", "Exception while setting data source", e);
                }
            }
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
        button.setCompoundDrawablesWithIntrinsicBounds(imageId, 0,0,0);
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
    }

    void stopRecording() {
        Log.d("MediaPlayer", "Stopping Recording.");
        setState(State.STOPPED);

    }

    void startPlayback() {
        Log.d("MediaPlayer", "Starting playback..");
        setState(State.PLAYING);

        if (mEpisode != null && mEpisode.remoteURL != null) {
            mPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);

            if (mPlayerIsPrepared) {
                resumePlayback();
            }
        }
    }

    void pausePlayback() {
        Log.d("MediaPlayer", "Pausing playback..");
        setState(State.PAUSED);
        mPlayer.pause();

    }

    void resumePlayback() {
        Log.d("MediaPlayer", "Resuming playback..");
        setState(State.PLAYING);
        mPlayer.start();
        mPlayerHandler.postDelayed(mPlayerTimer, 100);
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

            int seekTo = (int) (mSeekBar.getProgress() / 100.0 * mPlayer.getDuration());
            mPlayer.seekTo(seekTo);
            mPlayerHandler.postDelayed(mPlayerTimer, 100);
        }
    }
}
