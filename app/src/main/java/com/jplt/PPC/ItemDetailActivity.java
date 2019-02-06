package com.jplt.PPC;

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
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
    MediaPlayer mPlayer = null;
    Runnable mPlayerTimer = null;
    Handler mPlayerHandler = null;

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
        mPlayer.release();
        mPlayer = null;

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
    }

    void setupCover() {
        // Set cover to 1:1 aspect w/screen width
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);

        ImageView cover = findViewById(R.id.detail_cover);
        cover.getLayoutParams().height = metrics.widthPixels;
        cover.requestLayout();
    }

    void setupPlayer() {
        mPlayer = new MediaPlayer();
        mPlayerHandler = new Handler();
        mPlayerTimer = new Runnable() {
            @Override
            public void run() {
                long totalDuration = mPlayer.getDuration();
                long currentDuration = mPlayer.getCurrentPosition();
                boolean isPlaying = mPlayer.isPlaying();

                if (!isPlaying) {
                    mSeekBar.setProgress(0);
                    setState(State.STOPPED);
                    return;
                }

                mSeekBar.setProgress((int)(100 * currentDuration / totalDuration));
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
        setState(State.RECORDING);
    }

    void stopRecording() {
        setState(State.STOPPED);

    }

    void startPlayback() {
        setState(State.PLAYING);

        if (mEpisode != null && mEpisode.remoteURL != null) {
            mPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);

            try {
                mPlayer.setDataSource(mEpisode.remoteURL);
                mPlayer.prepare();
                mPlayer.start();
                mPlayerHandler.postDelayed(mPlayerTimer, 100);
            }
            catch (IOException e) {
                Log.e("MediaPlayer", e.getLocalizedMessage());
                setState(State.STOPPED);
            }
        }
    }

    void pausePlayback() {
        setState(State.PAUSED);

    }

    void resumePlayback() {
        setState(State.PLAYING);

    }
}
