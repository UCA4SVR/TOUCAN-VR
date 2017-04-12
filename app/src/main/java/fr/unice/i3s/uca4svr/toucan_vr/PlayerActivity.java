/*
 * Copyright 2017 Laboratoire I3S, CNRS, Université côte d'azur
 *
 * Author: Romaric Pighetti
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package fr.unice.i3s.uca4svr.toucan_vr;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.util.Log;
import android.view.MotionEvent;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.LoadControl;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.source.ConcatenatingMediaSource;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.dash.DefaultDashChunkSource;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.source.smoothstreaming.DefaultSsChunkSource;
import com.google.android.exoplayer2.source.smoothstreaming.SsMediaSource;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultAllocator;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import com.google.android.exoplayer2.upstream.TransferListener;
import com.google.android.exoplayer2.util.Util;

import org.gearvrf.GVRActivity;
import org.gearvrf.scene_objects.GVRVideoSceneObjectPlayer;

import java.util.HashSet;
import java.util.Set;

import fr.unice.i3s.uca4svr.toucan_vr.mediaplayer.TiledExoPlayer;
import fr.unice.i3s.uca4svr.toucan_vr.mediaplayer.scene_objects.ExoplayerSceneObject;
import fr.unice.i3s.uca4svr.toucan_vr.mediaplayer.upstream.TransferListenerBroadcaster;
import fr.unice.i3s.uca4svr.toucan_vr.permissions.PermissionManager;
import fr.unice.i3s.uca4svr.toucan_vr.permissions.RequestPermissionResultListener;
import fr.unice.i3s.uca4svr.toucan_vr.tracking.BandwidthConsumedTracker;
import fr.unice.i3s.uca4svr.toucan_vr.dashSRD.DashSRDMediaSource;

public class PlayerActivity extends GVRActivity implements RequestPermissionResultListener {

    private static final DefaultBandwidthMeter BANDWIDTH_METER = new DefaultBandwidthMeter();
    private static final TransferListenerBroadcaster MASTER_TRANSFER_LISTENER =
            new TransferListenerBroadcaster();

    static {
        MASTER_TRANSFER_LISTENER.addListener(BANDWIDTH_METER);
    }

    private PermissionManager permissionManager = null;

    private GVRVideoSceneObjectPlayer<ExoPlayer> videoSceneObjectPlayer;
    private TiledExoPlayer player;

    // Player's parameters to fine tune as we need
    private int minBufferMs = DefaultLoadControl.DEFAULT_MIN_BUFFER_MS;
    private int maxBufferMs = DefaultLoadControl.DEFAULT_MAX_BUFFER_MS;
    private int bufferForPlaybackMs =
            DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS;
    private int bufferForPlaybackAfterRebufferMs =
            DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS;
    private String mediaUri = "https://bitmovin-a.akamaihd.net/content/playhouse-vr/mpds/105560.mpd";
    private String logPrefix = "bitmovin105560";

    private String userAgent;
    private DefaultTrackSelector trackSelector;
    private DataSource.Factory mediaDataSourceFactory;
    private Handler mainHandler;

    private final boolean shouldAutoPlay = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        permissionManager = new PermissionManager(this);

        userAgent = Util.getUserAgent(this, "Toucan_VR");
        mediaDataSourceFactory = buildDataSourceFactory(true);
        mainHandler = new Handler();

        // TODO: take parameters from the intent

        Intent i = getIntent();
        String videoLink = i.getStringExtra("videoLink");
        //mediaUri = "file:///android_asset/"+videoLink;
        //logPrefix = "ROI";
        MASTER_TRANSFER_LISTENER.addListener(new BandwidthConsumedTracker(logPrefix));

        // Overriding mediaUri just for testing.

        // the manifest here is stored locally together with the media segments and tiles
        mediaUri = "file:///android_asset/video_test/manifest.mpd";

        // Manifest with tiles and SupplementalProperties
        //mediaUri = "http://download.tsi.telecom-paristech.fr/gpac/SRD/360/srd_360.mpd";

        // Manifest with two adaptation sets
        //mediaUri = "http://www-itec.uni-klu.ac.at/ftp/datasets/DASHDataset2014/TearsOfSteel/2sec/TearsOfSteel_2s_onDemand_2014_05_09.mpd";

        videoSceneObjectPlayer = makeVideoSceneObject();
        final Minimal360Video main = new Minimal360Video(videoSceneObjectPlayer,
                permissionManager, logPrefix);
        setMain(main, "gvr.xml");
    }

    /**
     * The event is triggered every time the user touches the GearVR trackpad.
     * If the touch event lasts less than 200 ms it is recognized as a "Tap"
     * and the playback is paused or restarted according to the current state.
     */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        super.onTouchEvent(event);
        if (null != videoSceneObjectPlayer) {
            if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                if (event.getEventTime() - event.getDownTime() < 200) {
                    Minimal360Video main = (Minimal360Video) getMain();
                    main.displayVideo();
                    final ExoPlayer exoPlayer = videoSceneObjectPlayer.getPlayer();
                    if (exoPlayer != null) {
                        if (exoPlayer.getPlayWhenReady())
                            videoSceneObjectPlayer.pause();
                        else
                            videoSceneObjectPlayer.start();
                    }
                }
            }
        } else {
            videoSceneObjectPlayer = makeVideoSceneObject();
            ((Minimal360Video)getMain()).setVideoSceneObjectPlayer(videoSceneObjectPlayer);
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // Forwarding the call to the permission manager
        permissionManager.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    /**
     * Builds the video player scene object.
     * This object holds the instance of the exoplayer and can be passed to the Minimal360Video
     * to be displayed on a proper surface.
     *
     * This method mainly creates and prepares an exoplayer instance before wrapping it in an
     * ExoplayerSceneObject.
     * @return the created video scene object
     */
    private GVRVideoSceneObjectPlayer<ExoPlayer> makeVideoSceneObject() {
        boolean needNewPlayer = player == null;
        if (needNewPlayer) {
            // The video track selection factory and the track selector.
            // May be extended or replaced by custom implementations to try different adaptive strategies.
            TrackSelection.Factory videoTrackSelectionFactory =
                    new AdaptiveTrackSelection.Factory(BANDWIDTH_METER);
            trackSelector = new DefaultTrackSelector(videoTrackSelectionFactory);

            // The LoadControl, responsible for the buffering strategy
            LoadControl loadControl = new DefaultLoadControl(
                    new DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE),
                    minBufferMs,
                    maxBufferMs,
                    bufferForPlaybackMs,
                    bufferForPlaybackAfterRebufferMs
            );

            // Instantiation of the ExoPlayer using our custom implementation.
            // The number of video renderers and the other components created above are given as parameters.
            player = new TiledExoPlayer(this, /*videoRendererCount*/ 2, trackSelector, loadControl);

            // TODO: extract the number of video renderers from the manifest

            player.setPlayWhenReady(shouldAutoPlay);
        }

        boolean needPreparePlayer = videoSceneObjectPlayer == null;
        if (needPreparePlayer) {
            // Creation of the data source
            Uri[] uris;
            String[] extensions;
            uris = new Uri[] {Uri.parse(mediaUri)};
            extensions = new String[1];

            boolean isAUriLocal = false;
            for (Uri uri : uris) {
                if (Util.isLocalFileUri(uri)) {
                    isAUriLocal = true;
                }
            }
            Set<String> permissions = new HashSet<>();
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            if (isAUriLocal && !permissionManager.isPermissionGranted(permissions)) {
                // The player will be reinitialized if the permission is granted.
                permissionManager.requestPermissions(permissions, this);
                return null;
            }
            MediaSource[] mediaSources = new MediaSource[uris.length];
            for (int i = 0; i < uris.length; i++) {
                mediaSources[i] = buildMediaSource(uris[i], extensions[i]);
            }
            MediaSource mediaSource = mediaSources.length == 1 ? mediaSources[0]
                    : new ConcatenatingMediaSource(mediaSources);

            // Prepare the player with the given data source
            player.prepare(mediaSource);
        }

        return new ExoplayerSceneObject(player);
    }

    @Override
    public void onPermissionRequestDone(int requestID, int result) {
        if (result == PackageManager.PERMISSION_GRANTED) {
            videoSceneObjectPlayer = makeVideoSceneObject();
            ((Minimal360Video)getMain()).setVideoSceneObjectPlayer(videoSceneObjectPlayer);
        }
    }

    /**
     * Builds the media source, guessing the type of source using its extension (or the override
     * extension string).
     *
     * Helper method taken from the exoplayer demo app.
     *
     * @param uri the uri to the media.
     * @param overrideExtension the string to use as the extension instead of extracting the extension
     *                          from the media uri.
     * @return A media sources on which the player can prepare
     */
    private MediaSource buildMediaSource(Uri uri, String overrideExtension) {
        int type = TextUtils.isEmpty(overrideExtension) ? Util.inferContentType(uri)
                : Util.inferContentType("." + overrideExtension);
        switch (type) {
            case C.TYPE_SS:
                return new SsMediaSource(uri, buildDataSourceFactory(false),
                        new DefaultSsChunkSource.Factory(mediaDataSourceFactory), mainHandler,
                        /* eventListener */ null);
            case C.TYPE_DASH:
                return new DashSRDMediaSource(uri, buildDataSourceFactory(false),
                        new DefaultDashChunkSource.Factory(mediaDataSourceFactory), mainHandler,
                        /* eventListener */ null);
            case C.TYPE_HLS:
                return new HlsMediaSource(uri, mediaDataSourceFactory, mainHandler,
                        /* eventListener */ null);
            case C.TYPE_OTHER:
                return new ExtractorMediaSource(uri, mediaDataSourceFactory, new DefaultExtractorsFactory(),
                        mainHandler, /* eventListener */ null);
            default: {
                throw new IllegalStateException("Unsupported type: " + type);
            }
        }
    }

    /**
     * Returns a new DataSource factory.
     *
     * Helper method taken from the exoplayer demo app.
     *
     * @param useBandwidthMeter Whether to set {@link #MASTER_TRANSFER_LISTENER} as a listener to the new
     *     DataSource factory.
     * @return A new DataSource factory.
     */
    private DataSource.Factory buildDataSourceFactory(boolean useBandwidthMeter) {
        return buildDataSourceFactory(useBandwidthMeter ? MASTER_TRANSFER_LISTENER : null);
    }

    /**
     * Helper method taken from the exoplayer demo app.
     *
     * @param listener The transfert listener to be used. May be null.
     * @return A data source factory
     */
    private DataSource.Factory buildDataSourceFactory(TransferListener listener) {
        return new DefaultDataSourceFactory(this, listener,
                buildHttpDataSourceFactory(listener));
    }

    /**
     * Helper method taken from the exoplayer demo app.
     *
     * @param listener The transfer listener to be used. May be null.
     * @return An HttpDataSource factory
     */
    private HttpDataSource.Factory buildHttpDataSourceFactory(TransferListener listener) {
        return new DefaultHttpDataSourceFactory(userAgent, listener);
    }
}
