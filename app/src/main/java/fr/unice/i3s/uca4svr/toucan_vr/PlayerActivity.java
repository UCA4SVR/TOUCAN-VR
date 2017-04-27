/*
 * Copyright 2017 Laboratoire I3S, CNRS, Université côte d'azur
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
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.source.ConcatenatingMediaSource;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.dash.DefaultDashChunkSource;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.source.smoothstreaming.DefaultSsChunkSource;
import com.google.android.exoplayer2.source.smoothstreaming.SsMediaSource;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelector;
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

import fr.unice.i3s.uca4svr.toucan_vr.dashSRD.track_selection.CustomTrackSelector;
import fr.unice.i3s.uca4svr.toucan_vr.mediaplayer.TiledExoPlayer;
import fr.unice.i3s.uca4svr.toucan_vr.mediaplayer.scene_objects.ExoplayerSceneObject;
import fr.unice.i3s.uca4svr.toucan_vr.mediaplayer.upstream.TransferListenerBroadcaster;
import fr.unice.i3s.uca4svr.toucan_vr.permissions.PermissionManager;
import fr.unice.i3s.uca4svr.toucan_vr.permissions.RequestPermissionResultListener;
import fr.unice.i3s.uca4svr.toucan_vr.tracking.BandwidthConsumedTracker;
import fr.unice.i3s.uca4svr.toucan_vr.dashSRD.DashSRDMediaSource;

public class PlayerActivity extends GVRActivity implements RequestPermissionResultListener {

    public static final int STATUS_OK = 0;
    public static final int NO_INTENT = 1;
    public static final int NO_INTERNET = 2;
    public static final int NO_PERMISSION = 3;

    private int statusCode = STATUS_OK;

    private static final DefaultBandwidthMeter BANDWIDTH_METER = new DefaultBandwidthMeter();
    private static final TransferListenerBroadcaster MASTER_TRANSFER_LISTENER =
            new TransferListenerBroadcaster();

    static {
        MASTER_TRANSFER_LISTENER.addListener(BANDWIDTH_METER);
    }

    private PermissionManager permissionManager = null;

    private GVRVideoSceneObjectPlayer<ExoPlayer> videoSceneObjectPlayer;
    private ExoPlayer player;

    // Player's parameters to fine tune as we need
    private int minBufferMs = DefaultLoadControl.DEFAULT_MIN_BUFFER_MS;
    private int maxBufferMs = DefaultLoadControl.DEFAULT_MAX_BUFFER_MS;
    private int bufferForPlaybackMs =
            DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS;
    private int bufferForPlaybackAfterRebufferMs =
            DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS;
    private String mediaUri = "https://bitmovin-a.akamaihd.net/content/playhouse-vr/mpds/105560.mpd";
    private String logPrefix = "bitmovin105560";
    private boolean loggingBandwidth = true;
    private boolean loggingHeadMotion = true;

    private String[] tiles;
    private int gridWidth = 3;
    private int gridHeight = 3;
    private int numberOfTiles = 1;

    private String userAgent;
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

        // Extract parameters from the intent
        if(getIntent()!=null && getIntent().getExtras()!=null) {
            Intent intent = getIntent();
            mediaUri = intent.getStringExtra("videoLink");
            logPrefix = intent.getStringExtra("videoName");
            minBufferMs = intent.getIntExtra("minBufferSize", DefaultLoadControl.DEFAULT_MIN_BUFFER_MS);
            maxBufferMs = intent.getIntExtra("maxBufferSize", DefaultLoadControl.DEFAULT_MAX_BUFFER_MS);
            bufferForPlaybackMs = intent.getIntExtra("bufferForPlayback",
                    DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS);
            bufferForPlaybackAfterRebufferMs = intent.getIntExtra("bufferForPlaybackAR",
                    DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS);
            loggingBandwidth = intent.getBooleanExtra("bandwidthLogging", true);
            loggingHeadMotion = intent.getBooleanExtra("headMotionLogging", true);
            gridWidth = intent.getIntExtra("W", 3);
            gridHeight = intent.getIntExtra("H", 3);
            tiles = intent.getStringExtra("tilesCSV").split(",");
            numberOfTiles = tiles.length / 4;
        } else {
            // TODO: handle the case when no intent exists (the app was not launched from the parametrizer)

            //statusCode = NO_INTENT;

            // Overriding some variables so that we can keep testing the application without the parametrizer
            numberOfTiles = 9;
            gridWidth = 3;
            gridHeight = 3;
            String tilesCSV = "0,0,1,1,1,0,1,1,2,0,1,1,0,1,1,1,1,1,1,1,2,1,1,1,0,2,1,1,1,2,1,1,2,2,1,1";
            tiles = tilesCSV.split(",");
            mediaUri = "http://download.tsi.telecom-paristech.fr/gpac/SRD/360/srd_360.mpd";
        }

        // We avoid creating the player at first
        videoSceneObjectPlayer = null;
        final Minimal360Video main = new Minimal360Video(videoSceneObjectPlayer, statusCode,
                permissionManager, logPrefix, tiles, gridWidth, gridHeight, loggingHeadMotion);
        setMain(main, "gvr.xml");

        // If there is no intent, we don't try to create the player or ask for permissions etc.
        if (statusCode != NO_INTENT)
            checkMediaUri();
    }

    // Dummy example (to replace with the actual method that checks the url's reachability
    private void checkMediaUri() {
        if(Util.isLocalFileUri(Uri.parse(mediaUri)) || loggingHeadMotion || loggingBandwidth) {
            Set<String> permissions = new HashSet<>();
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            //if (permissionManager.isPermissionGranted(permissions))
                permissionManager.requestPermissions(permissions, this);
        }
        if(!Util.isLocalFileUri(Uri.parse(mediaUri))) {
            /*CheckConnection checkConnection = new CheckConnection(this);
            checkConnection.response = this;
            checkConnection.execute(mediaUri);*/

            // Testing no internet situation
            //statusCode = NO_INTERNET;
            //((Minimal360Video) getMain()).setStatusCode(NO_INTERNET);

        }
    }

    /*@Override
    public void urlChecked(boolean exists) {
        if (exists) {
            // Check whether we should log the bandwidth or not
            if(loggingBandwidth)
                MASTER_TRANSFER_LISTENER.addListener(new BandwidthConsumedTracker(logPrefix));
            videoSceneObjectPlayer = makeVideoSceneObject();
            ((Minimal360Video) getMain()).setVideoSceneObjectPlayer(videoSceneObjectPlayer);
        } else {
            statusCode = NO_INTERNET;
            ((Minimal360Video) getMain()).setStatusCode(NO_INTERNET);
            ((Minimal360Video) getMain()).setVideoSceneObjectPlayer(null);
        }
    }

    /**
     * The event is triggered every time the user touches the GearVR trackpad.
     * If the touch event lasts less than 200 ms it is recognized as a "Tap"
     * and the playback is paused or restarted according to the current state.
     */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        super.onTouchEvent(event);
        if (event.getActionMasked() == MotionEvent.ACTION_UP &&
                event.getEventTime() - event.getDownTime() < 200) {
            switch (statusCode) {
                case NO_INTENT:
                    break;
                case NO_INTERNET:
                    ((Minimal360Video) getMain()).sceneDispatcher();
                    break;
                case NO_PERMISSION:
                    ((Minimal360Video) getMain()).sceneDispatcher();
                    // TODO: Ask for permission again ?
                    Set<String> permissions = new HashSet<>();
                    permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);
                    permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
                    permissionManager.requestPermissions(permissions, this);
                    break;
                case STATUS_OK:
                    if (videoSceneObjectPlayer != null) {
                        final ExoPlayer exoPlayer = videoSceneObjectPlayer.getPlayer();
                        if (exoPlayer != null) {
                            if (exoPlayer.getPlayWhenReady())
                                videoSceneObjectPlayer.pause();
                            else
                                videoSceneObjectPlayer.start();
                        }
                    }
                    ((Minimal360Video) getMain()).sceneDispatcher();
                    break;
                default:
                    ((Minimal360Video) getMain()).sceneDispatcher();
                    break;
            }
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
            TrackSelector trackSelector = new CustomTrackSelector(videoTrackSelectionFactory);

            // The LoadControl, responsible for the buffering strategy
            LoadControl loadControl = new DefaultLoadControl(
                    new DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE),
                    minBufferMs,
                    maxBufferMs,
                    bufferForPlaybackMs,
                    bufferForPlaybackAfterRebufferMs
            );

            // Instantiation of the ExoPlayer using our custom implementation.
            // The number of tiles and the other components created above are given as parameters.
            player = new TiledExoPlayer(this, numberOfTiles, trackSelector, loadControl);
            player.setPlayWhenReady(shouldAutoPlay);
        }

        boolean needPreparePlayer = videoSceneObjectPlayer == null;
        if (needPreparePlayer) {
            // Creation of the data source
            Uri[] uris;
            String[] extensions;
            uris = new Uri[] {Uri.parse(mediaUri)};
            extensions = new String[1];

            /*boolean isAUriLocal = false;
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
            }*/
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

        // Make sure that we have internet access, otherwise it is not safe to overwrite the Status
        if (statusCode != NO_INTERNET) {
            if (result == PackageManager.PERMISSION_GRANTED) {
                videoSceneObjectPlayer = makeVideoSceneObject();
                ((Minimal360Video) getMain()).setVideoSceneObjectPlayer(videoSceneObjectPlayer);
                statusCode = STATUS_OK;
                ((Minimal360Video) getMain()).setStatusCode(STATUS_OK);
            }
            if (result == PackageManager.PERMISSION_DENIED) {
                statusCode = NO_PERMISSION;
                ((Minimal360Video) getMain()).setStatusCode(NO_PERMISSION);
            }
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
