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

import fr.unice.i3s.uca4svr.toucan_vr.connectivity.CheckConnection;
import fr.unice.i3s.uca4svr.toucan_vr.connectivity.CheckConnectionResponse;
import fr.unice.i3s.uca4svr.toucan_vr.dashSRD.track_selection.CustomTrackSelector;
import fr.unice.i3s.uca4svr.toucan_vr.mediaplayer.TiledExoPlayer;
import fr.unice.i3s.uca4svr.toucan_vr.mediaplayer.scene_objects.ExoplayerSceneObject;
import fr.unice.i3s.uca4svr.toucan_vr.mediaplayer.upstream.TransferListenerBroadcaster;
import fr.unice.i3s.uca4svr.toucan_vr.permissions.PermissionManager;
import fr.unice.i3s.uca4svr.toucan_vr.permissions.RequestPermissionResultListener;
import fr.unice.i3s.uca4svr.toucan_vr.tracking.BandwidthConsumedTracker;
import fr.unice.i3s.uca4svr.toucan_vr.dashSRD.DashSRDMediaSource;

public class PlayerActivity extends GVRActivity implements RequestPermissionResultListener, CheckConnectionResponse {

    public static final int STATUS_OK = 0;
    public static final int NO_INTENT = 1;
    public static final int NO_INTERNET = 2;
    public static final int NO_PERMISSION = 3;

    private static DefaultBandwidthMeter BANDWIDTH_METER = new DefaultBandwidthMeter();
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
    private String logPrefix = "Log";
    private boolean loggingBandwidth = false;
    private boolean loggingHeadMotion = false;

    private String[] tiles;
    private int gridWidth = 1;
    private int gridHeight = 1;
    private int numberOfTiles = 1;

    private String userAgent;
    private DataSource.Factory mediaDataSourceFactory;
    private Handler mainHandler;

    private final boolean shouldAutoPlay = false;

    private int statusCode = STATUS_OK;

    private Intent intent;
    private boolean newIntent = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        permissionManager = new PermissionManager(this);
        userAgent = Util.getUserAgent(this, "Toucan_VR");
        mediaDataSourceFactory = buildDataSourceFactory(true);
        mainHandler = new Handler();

        intent = getIntent();
        parseIntent();

        // We avoid creating the player at first. We will create it only if the source is accessible.
        final Minimal360Video main = new Minimal360Video(/*videoSceneObjectPlayer*/ null,
                statusCode, tiles, gridWidth, gridHeight);
        setMain(main, "gvr.xml");

        // At this point we know if there is no intent, in which case
        // we don't try to create the player or ask for permissions etc.
        if (statusCode != NO_INTENT)
            checkInternetAndPermissions();
    }

    private void parseIntent() {
        if(intent!=null && intent.getStringExtra("videoLink")!=null) {
            mediaUri = intent.getStringExtra("videoLink");
            logPrefix = intent.getStringExtra("videoName");
            minBufferMs = intent.getIntExtra("minBufferSize", DefaultLoadControl.DEFAULT_MIN_BUFFER_MS);
            maxBufferMs = intent.getIntExtra("maxBufferSize", DefaultLoadControl.DEFAULT_MAX_BUFFER_MS);
            bufferForPlaybackMs = intent.getIntExtra("bufferForPlayback",
                    DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS);
            bufferForPlaybackAfterRebufferMs = intent.getIntExtra("bufferForPlaybackAR",
                    DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS);
            loggingBandwidth = intent.getBooleanExtra("bandwidthLogging", false);
            loggingHeadMotion = intent.getBooleanExtra("headMotionLogging", false);
            gridWidth = intent.getIntExtra("W", 3);
            gridHeight = intent.getIntExtra("H", 3);
            tiles = intent.getStringExtra("tilesCSV").split(",");
            numberOfTiles = tiles.length / 4;
            statusCode = STATUS_OK;
        } else {
            statusCode = NO_INTENT;
        }
    }

    /**
     * Always called before onResume (except when we first launch the application, in which
     * case onResume gets called right after onCreate).
     * The intent has extras only when it comes from the parametrizer app.
     */
    @Override
    protected void onNewIntent(Intent intent) {
        if(intent!=null && intent.getStringExtra("videoLink")!=null) {
            this.newIntent = true;
            this.intent = intent;
            if (videoSceneObjectPlayer != null) {
                // TODO: Does this really releases the surfaces ?
                videoSceneObjectPlayer.release();
                videoSceneObjectPlayer = null;
                player = null;
            }
        }
    }

    // Rebuild everything if the intent has changed.
    @Override
    protected void onResume() {
        super.onResume();
        if (newIntent) {
            newIntent = false;
            parseIntent();

            // clean the listeners list, we got to start fresh.
            MASTER_TRANSFER_LISTENER.removeAllListeners();

            // Lets reset the bandwidth meter too to avoid using legacy bandwidth estimates
            BANDWIDTH_METER = new DefaultBandwidthMeter();
            MASTER_TRANSFER_LISTENER.addListener(BANDWIDTH_METER);

            final Minimal360Video main = new Minimal360Video(/*videoSceneObjectPlayer*/ null,
                    statusCode, tiles, gridWidth, gridHeight);
            setMain(main, "gvr.xml");

            if (statusCode != NO_INTENT)
                checkInternetAndPermissions();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (videoSceneObjectPlayer != null)
            videoSceneObjectPlayer.pause();
    }

    /**
     *  Permissions for accessing the storage are now requested upfront (if necessary).
     *  Also, we check the internet connectivity and whether the link is actually accessible.
     *  Such requests lead to callbacks that are handled later in the code.
     */
    private void checkInternetAndPermissions() {
        if (Util.isLocalFileUri(Uri.parse(mediaUri)) || loggingHeadMotion || loggingBandwidth) {
            Set<String> permissions = new HashSet<>();
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            permissionManager.requestPermissions(permissions, this);
        }
        if (!Util.isLocalFileUri(Uri.parse(mediaUri))) {
            CheckConnection checkConnection = new CheckConnection(this);
            checkConnection.response = this;
            checkConnection.execute(mediaUri);
        }
    }

    /**
     * The event is triggered every time the user touches the GearVR trackpad.
     * If the touch event lasts less than 200 ms it is recognized as a "Tap".
     * The tap che be used to play/pause the playback or to trigger a change of scene.
     */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        super.onTouchEvent(event);
        if (event.getActionMasked() == MotionEvent.ACTION_UP &&
                event.getEventTime() - event.getDownTime() < 200) {
            switch (statusCode) {
                case NO_INTENT:
                    break;
                case NO_PERMISSION:
                    ((Minimal360Video) getMain()).sceneDispatcher();
                    // Permissions can be requested up to three times
                    Set<String> permissions = new HashSet<>();
                    permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);
                    permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
                    permissionManager.requestPermissions(permissions, this);
                    break;
                // in case of NO_INTERNET the video object player is null
                default:
                    ((Minimal360Video) getMain()).sceneDispatcher();
                    if (videoSceneObjectPlayer != null) {
                        final ExoPlayer exoPlayer = videoSceneObjectPlayer.getPlayer();
                        if (exoPlayer != null) {
                            if (exoPlayer.getPlayWhenReady())
                                videoSceneObjectPlayer.pause();
                            else
                                videoSceneObjectPlayer.start();
                        }
                    }
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

    // Callback for the permission requests
    @Override
    public void onPermissionRequestDone(int requestID, int result) {
        // If there is no internet, it's not safe to overwrite the status
        if (statusCode != NO_INTERNET) {
            if (result == PackageManager.PERMISSION_GRANTED) {
                videoSceneObjectPlayer = makeVideoSceneObject();
                ((Minimal360Video) getMain()).setVideoSceneObjectPlayer(videoSceneObjectPlayer);
                statusCode = STATUS_OK;
                ((Minimal360Video) getMain()).setStatusCode(STATUS_OK);
                if (loggingHeadMotion)
                    ((Minimal360Video) getMain()).initHeadMotionTracker(logPrefix);
                if(loggingBandwidth)
                    MASTER_TRANSFER_LISTENER.addListener(new BandwidthConsumedTracker(logPrefix));
            }
            if (result == PackageManager.PERMISSION_DENIED) {
                statusCode = NO_PERMISSION;
                ((Minimal360Video) getMain()).setStatusCode(NO_PERMISSION);
            }
        }
    }

    // Callback for the internet connectivity check. If okay, the remote link is reachable.
    @Override
    public void urlChecked(boolean exists) {
        if (exists) {
            videoSceneObjectPlayer = makeVideoSceneObject();
            ((Minimal360Video) getMain()).setVideoSceneObjectPlayer(videoSceneObjectPlayer);
        } else {
            statusCode = NO_INTERNET;
            videoSceneObjectPlayer = null;
            ((Minimal360Video) getMain()).setStatusCode(NO_INTERNET);
            ((Minimal360Video) getMain()).setVideoSceneObjectPlayer(null);
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
