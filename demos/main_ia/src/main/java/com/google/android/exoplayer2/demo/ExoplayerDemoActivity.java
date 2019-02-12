package com.google.android.exoplayer2.demo;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.ext.mpegh.MpeghAudioRenderer;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.dash.DashMediaSource;
import com.google.android.exoplayer2.source.dash.DefaultDashChunkSource;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.source.smoothstreaming.DefaultSsChunkSource;
import com.google.android.exoplayer2.source.smoothstreaming.SsMediaSource;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.util.Util;
import android.view.View.OnClickListener;

import com.google.android.exoplayer2.upstream.BandwidthMeter;

import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.LoadControl;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.ExoPlayer;

import com.google.android.exoplayer2.Renderer;

public class ExoplayerDemoActivity extends Activity implements OnClickListener {

    // UIs
    private Button play_button;
    private static final String TAG = "ExoplayerDemoActivity";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // build UI
        setContentView(R.layout.exoplayer_test_activity);
        play_button = findViewById(R.id.play_button);
        play_button.setOnClickListener(this);
    }

    // data sources
    private DataSource.Factory mediaDataSourceFactory;
    private MediaSource mediaSource;

    //private SimpleExoPlayer player; // --> SimpleExoPlayer cannot be passed renderer classes.
    private ExoPlayer player;

    // band width estimator
    private static final DefaultBandwidthMeter BANDWIDTH_METER = new DefaultBandwidthMeter();

    // renderer
    private Renderer[] createRenderers() {
        MpeghAudioRenderer iaAudioRenderer = new MpeghAudioRenderer();
        //FfmpegAudioRenderer ffmpegAudioRenderer = new FfmpegAudioRenderer();
        Renderer[] renderers = new Renderer[1];
        //MediaCodecAudioRenderer audioRenderer = new MediaCodecAudioRenderer(this, MediaCodecSelector.DEFAULT);
        //renderers[0] = audioRenderer;
        renderers[0] = iaAudioRenderer;
        //renderers[0] = ffmpegAudioRenderer;
        return renderers;
    }

    // media player
    private ExoPlayer createMediaPlayer() {

        BandwidthMeter bandwidthMeter = new DefaultBandwidthMeter();
        TrackSelection.Factory videoTrackSelectionFactory = new AdaptiveTrackSelection.Factory(bandwidthMeter);
        TrackSelector trackSelector = new DefaultTrackSelector(videoTrackSelectionFactory);
        //TrackSelector trackSelector = new DefaultTrackSelector();
        LoadControl loadControl = new DefaultLoadControl();

        // prepare renderer
        Renderer[] renderers = createRenderers();

        // create player
        ExoPlayer player = ExoPlayerFactory.newInstance(renderers, trackSelector, loadControl);
        return player;
    }

    // DataSource factory
    private DataSource.Factory buildDataSourceFactory(boolean useBandwidthMeter) {
        return ((DemoApplication) getApplication())
                .buildDataSourceFactory(useBandwidthMeter ? BANDWIDTH_METER : null);
    }

    // media source
    private MediaSource buildMediaSource(Uri uri) {
        return buildMediaSource(uri, null);
    }

    @SuppressWarnings("unchecked")
    private MediaSource buildMediaSource(Uri uri, @Nullable String overrideExtension) {
        @C.ContentType int type = Util.inferContentType(uri, overrideExtension);
        switch (type) {
            case C.TYPE_DASH:
                return new DashMediaSource.Factory(
                        new DefaultDashChunkSource.Factory(mediaDataSourceFactory),
                        buildDataSourceFactory(false))
                        //.setManifestParser(
                        //        new FilteringManifestParser<>(
                        //                new DashManifestParser(), (List<RepresentationKey>) getOfflineStreamKeys(uri)))
                        .createMediaSource(uri);
            case C.TYPE_SS:
                return new SsMediaSource.Factory(
                        new DefaultSsChunkSource.Factory(mediaDataSourceFactory),
                        buildDataSourceFactory(false))
                        //.setManifestParser(
                        //        new FilteringManifestParser<>(
                        //                new SsManifestParser(), (List<StreamKey>) getOfflineStreamKeys(uri)))
                        .createMediaSource(uri);
            case C.TYPE_HLS:
                return new HlsMediaSource.Factory(mediaDataSourceFactory)
                        //.setPlaylistParser(
                        //        new FilteringManifestParser<>(
                        //                new HlsPlaylistParser(), (List<RenditionKey>) getOfflineStreamKeys(uri)))
                        .createMediaSource(uri);
            case C.TYPE_OTHER:
                return new ExtractorMediaSource.Factory(mediaDataSourceFactory).createMediaSource(uri);
            default: {
                throw new IllegalStateException("Unsupported type: " + type);
            }
        }
    }

    // on play button pressed
    @Override
    public void onClick(View v) {
        // create player
        player = this.createMediaPlayer();

        // contents URIs: check media.exolist.json.
        Uri mediaUri = Uri.parse("https://html5demos.com/assets/dizzy.mp4");

        mediaDataSourceFactory = buildDataSourceFactory(true);
        MediaSource mediaSource = buildMediaSource(mediaUri, null);

        // play audio
        player.prepare(mediaSource);
        player.setPlayWhenReady(true);

    }

    // activity life cycles
    @Override
    public void onStart() {
        super.onStart();

    }

    @Override
    public void onResume() {
        super.onResume();

    }

    @Override
    public void onPause() {
        super.onPause();

    }

    @Override
    public void onStop() {
        super.onStop();

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        player.release();
    }

    /*@Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        // See whether the player view wants to handle media or DPAD keys events.
        return rootView.dispatchKeyEvent(event) || super.dispatchKeyEvent(event);
    }*/


    // utilities
    private void showToast(String message) {
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
    }



}
