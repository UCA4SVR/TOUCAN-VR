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
import com.google.android.exoplayer2.source.dash.DefaultDashSRDChunkSource;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.source.smoothstreaming.DefaultSsChunkSource;
import com.google.android.exoplayer2.source.smoothstreaming.SsMediaSource;
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
import fr.unice.i3s.uca4svr.toucan_vr.dashSRD.track_selection.PyramidalTrackSelection;
import fr.unice.i3s.uca4svr.toucan_vr.dynamicEditing.DynamicEditingHolder;
import fr.unice.i3s.uca4svr.toucan_vr.dynamicEditing.DynamicEditingParser;
import fr.unice.i3s.uca4svr.toucan_vr.mediaplayer.TiledExoPlayer;
import fr.unice.i3s.uca4svr.toucan_vr.mediaplayer.scene_objects.ExoplayerSceneObject;
import fr.unice.i3s.uca4svr.toucan_vr.mediaplayer.upstream.TransferListenerBroadcaster;
import fr.unice.i3s.uca4svr.toucan_vr.permissions.PermissionManager;
import fr.unice.i3s.uca4svr.toucan_vr.permissions.RequestPermissionResultListener;
import fr.unice.i3s.uca4svr.toucan_vr.tracking.BandwidthConsumedTracker;
import fr.unice.i3s.uca4svr.toucan_vr.dashSRD.DashSRDMediaSource;

public class PlayerActivity extends GVRActivity implements RequestPermissionResultListener, CheckConnectionResponse {

    enum Status {
        NO_INTENT, NO_INTERNET, NO_PERMISSION, CHECKING_INTERNET, CHECKING_PERMISSION,
        CHECKING_INTERNET_AND_PERMISSION, READY_TO_PLAY, PLAYING, PAUSED, PLAYBACK_ENDED,
        PLAYBACK_ERROR, WRONGDYNED, NULL
    }

    private static TransferListenerBroadcaster MASTER_TRANSFER_LISTENER =
            new TransferListenerBroadcaster();

    private PermissionManager permissionManager = null;

    private GVRVideoSceneObjectPlayer<ExoPlayer> videoSceneObjectPlayer;
    private ExoPlayer player;
    private boolean needPreparePlayer = true;
    private DefaultBandwidthMeter bandwidthMeter;

    // Player's parameters to fine tune as we need
    //First section
    private int minBufferMs;
    private int maxBufferMs;
    private int bufferForPlaybackMs;
    private int bufferForPlaybackAfterRebufferMs;
    //Second section
    private int maxInitialBitrate = PyramidalTrackSelection.DEFAULT_MAX_INITIAL_BITRATE;
    private int minDurationForQualityIncreaseUs = PyramidalTrackSelection.DEFAULT_MIN_DURATION_FOR_QUALITY_INCREASE_MS;
    private int maxDurationForQualityDecreaseUs = PyramidalTrackSelection.DEFAULT_MAX_DURATION_FOR_QUALITY_DECREASE_MS;
    private int minDurationToRetainAfterDiscardUs = PyramidalTrackSelection.DEFAULT_MIN_DURATION_TO_RETAIN_AFTER_DISCARD_MS;
    private float bandwidthFraction = PyramidalTrackSelection.DEFAULT_BANDWIDTH_FRACTION;

    private String mediaUri = "https://bitmovin-a.akamaihd.net/content/playhouse-vr/mpds/105560.mpd";
    private String logPrefix = "Log";
    private boolean loggingBandwidth = false;
    private boolean loggingHeadMotion = false;
    private boolean loggingFreezes = false;

    private String[] tiles;
    private int gridWidth = 1;
    private int gridHeight = 1;
    private int numberOfTiles = 1;

    private String dynamicEditingFN;
    private DynamicEditingHolder dynamicEditingHolder;

    private String userAgent;
    private DataSource.Factory mediaDataSourceFactory;
    private Handler mainHandler;

    private Status statusCode = Status.NULL;

    private Intent intent;
    private boolean newIntent = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        permissionManager = new PermissionManager(this);
        userAgent = Util.getUserAgent(this, "Toucan_VR");
        mediaDataSourceFactory = buildDataSourceFactory(true);
        mainHandler = new Handler();

        // Retrieve the intent and extract the parameters
        intent = getIntent();
        if (isIntentValid(intent)) {
            parseIntent();
        } else {
            synchronized (this) {
                changeStatus(Status.NO_INTENT);
            }
        }

        // We can avoid providing the videoSceneObject at first. We will create it only if the
        // intent exists and every parameter specified in it can be activated.
        final Minimal360Video main = new Minimal360Video(statusCode, tiles, gridWidth, gridHeight, dynamicEditingHolder);
        setMain(main, "gvr.xml");

        // The intent is required to run the app, if it has not been provided we can stop there.
        if (statusCode != Status.NO_INTENT) {
            bandwidthMeter = new DefaultBandwidthMeter();
            MASTER_TRANSFER_LISTENER.addListener(bandwidthMeter);
            checkInternetAndPermissions();
        }
    }

    // Rebuild everything if the intent has changed after launching a new video from the parametrizer.
    @Override
    protected void onResume() {
        super.onResume();
        if (newIntent) {
            newIntent = false;
            statusCode = Status.NULL;
            if (isIntentValid(intent)) {
                parseIntent();
            } else {
                synchronized (this) {
                    changeStatus(Status.NO_INTENT);
                }
            }

            final Minimal360Video main = new Minimal360Video(statusCode, tiles, gridWidth, gridHeight, dynamicEditingHolder);
            setMain(main, "gvr.xml");

            if (statusCode != Status.NO_INTENT) {
                // Clean the listeners list, we got to start fresh.
                MASTER_TRANSFER_LISTENER.removeAllListeners();

                // Lets reset the bandwidth meter too to avoid using legacy bandwidth estimates
                bandwidthMeter = new DefaultBandwidthMeter();
                MASTER_TRANSFER_LISTENER.addListener(bandwidthMeter);

                // Check the availability of connection and permissions
                checkInternetAndPermissions();
            }
        }
    }

    /**
     * Checks if the intent is valid for our app (and thus can be parsed)
     *
     * @param intent
     *      The intent to check
     * @return
     *      True if the intent is valid, false if it is not
     */
    private boolean isIntentValid(Intent intent) {
        return intent!=null && intent.getStringExtra("videoLink")!=null;
    }

    /**
     * Retrieves all the necessary values from the intent currently held by this instance.
     */
    private void parseIntent() {
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
        loggingFreezes = intent.getBooleanExtra("freezingEventsLogging", false);
        gridWidth = intent.getIntExtra("W", 3);
        gridHeight = intent.getIntExtra("H", 3);
        tiles = intent.getStringExtra("tilesCSV").split(",");
        numberOfTiles = tiles.length / 4;
        //Dynamic editing check
        dynamicEditingFN = intent.getStringExtra("dynamicEditingFN");
        if(dynamicEditingFN != null && dynamicEditingFN.length() > 0)
            dynamicEditingHolder = new DynamicEditingHolder(true);
        else
            dynamicEditingHolder = new DynamicEditingHolder(false);
    }

    /**
     * Always called before onResume (except when we first launch the application, in which
     * case onResume gets called right after onCreate).
     * The intent has extras only when it comes from the parametrizer app.
     */
    @Override
    protected void onNewIntent(Intent intent) {
        if(isIntentValid(intent)) {
            this.newIntent = true;
            this.intent = intent;
            if (videoSceneObjectPlayer != null) {
                // TODO: Does this really releases the surfaces?
                videoSceneObjectPlayer.release();
                videoSceneObjectPlayer = null;
                player = null;
                needPreparePlayer = true;
            }
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
        synchronized (this) {
            if (statusCode == Status.NO_INTENT) {
                // If there is no intent there is no need to check anything else
                return;
            }
            changeStatus(Status.CHECKING_INTERNET_AND_PERMISSION);

            if (Util.isLocalFileUri(Uri.parse(mediaUri)) ||
                    loggingHeadMotion || loggingBandwidth || loggingFreezes
                    || dynamicEditingHolder.isDynamicEdited()) {
                Set<String> permissions = new HashSet<>();
                permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);
                permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
                permissionManager.requestPermissions(permissions, this);
            } else {
                changeStatus(Status.CHECKING_INTERNET);
            }
            if (!Util.isLocalFileUri(Uri.parse(mediaUri))) {
                CheckConnection checkConnection = new CheckConnection(this);
                checkConnection.response = this;
                checkConnection.execute(mediaUri);
            } else {
                if (statusCode == Status.CHECKING_INTERNET_AND_PERMISSION) {
                    changeStatus(Status.CHECKING_PERMISSION);
                } else {
                    videoSceneObjectPlayer = makeVideoSceneObject();
                    ((Minimal360Video) getMain()).setVideoSceneObjectPlayer(videoSceneObjectPlayer);
                    changeStatus(Status.READY_TO_PLAY);
                }
            }
        }
    }

    private void changeStatus(Status newStatus) {
        this.statusCode = newStatus;
        if (getMain() != null) {
            ((Minimal360Video) getMain()).setStatusCode(newStatus);
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
        synchronized (this) {
            if (event.getActionMasked() == MotionEvent.ACTION_UP &&
                    event.getEventTime() - event.getDownTime() < 200) {
                switch (statusCode) {
                    case NO_INTENT:
                        break;
                    case NO_PERMISSION:
                        //((Minimal360Video) getMain()).sceneDispatcher();
                        // Permissions can be requested up to three times
                        synchronized (this) {
                            changeStatus(Status.CHECKING_PERMISSION);
                        }
                        Set<String> permissions = new HashSet<>();
                        permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);
                        permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
                        permissionManager.requestPermissions(permissions, this);
                        break;
                    // In case of NO_INTERNET the video object player is null
                    case READY_TO_PLAY:
                        startPlaying(true);
                        synchronized (this) {
                            changeStatus(Status.PLAYING);
                        }
                        break;
                    case PLAYING:
                        startPlaying(false);
                        synchronized (this) {
                            changeStatus(Status.PAUSED);
                        }
                        break;
                    case PAUSED:
                        startPlaying(true);
                        synchronized (this) {
                            changeStatus(Status.PLAYING);
                        }
                        break;
                }
            }
        }
        return true;
    }

    private void startPlaying(boolean play) {
        if (videoSceneObjectPlayer != null) {
            final ExoPlayer exoPlayer = videoSceneObjectPlayer.getPlayer();
            if (exoPlayer != null) {
                if (needPreparePlayer)
                    preparePlayer();
                else if (!play) {
                    videoSceneObjectPlayer.pause();
                } else {
                    videoSceneObjectPlayer.start();
                }
            }
        }
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
                    new PyramidalTrackSelection.Factory(
                            bandwidthMeter, maxInitialBitrate, minDurationForQualityIncreaseUs,
                            maxDurationForQualityDecreaseUs, minDurationToRetainAfterDiscardUs,
                            bandwidthFraction, dynamicEditingHolder);
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
        }

        return new ExoplayerSceneObject(player);
    }

    // Starts downloading chunks of video from the source
    private void preparePlayer() {
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

        needPreparePlayer = false;
        player.setPlayWhenReady(true);

        // Prepare the player with the given data source
        player.prepare(mediaSource);
    }

    // Callback from the permission requests.
    @Override
    public void onPermissionRequestDone(int requestID, int result) {
        synchronized (this) {
            // If we know that there is no internet, we can stop there
            if (statusCode != Status.NO_INTERNET && statusCode != Status.NO_INTENT) {
                if (result == PackageManager.PERMISSION_GRANTED) {
                    // Initialize the logging to file
                    if (loggingHeadMotion)
                        ((Minimal360Video) getMain()).initHeadMotionTracker(logPrefix);
                    if (loggingBandwidth)
                        MASTER_TRANSFER_LISTENER.addListener(new BandwidthConsumedTracker(logPrefix));
                    if (loggingFreezes)
                        ((Minimal360Video) getMain()).initFreezingEventsTracker(logPrefix);
                    if (dynamicEditingHolder.isDynamicEdited()) {
                        parseDynamicEditing();
                    }
                    switch (statusCode) {
                        case CHECKING_PERMISSION:
                            videoSceneObjectPlayer = makeVideoSceneObject();
                            ((Minimal360Video) getMain()).setVideoSceneObjectPlayer(videoSceneObjectPlayer);
                            changeStatus(Status.READY_TO_PLAY);
                            break;
                        case CHECKING_INTERNET_AND_PERMISSION:
                            changeStatus(Status.CHECKING_INTERNET);
                            break;
                        case READY_TO_PLAY:
                        case PLAYING:
                        case CHECKING_INTERNET:
                        // case NO_INTENT: Can't happen
                        // case NO_INTERNET: Can't happen
                        case NO_PERMISSION:
                        case NULL:
                        default:
                            break;
                    }
                }
                if (result == PackageManager.PERMISSION_DENIED) {
                    switch(statusCode) {
                        case READY_TO_PLAY:
                        case PLAYING:
                        case CHECKING_INTERNET:
                        case CHECKING_PERMISSION:
                        case CHECKING_INTERNET_AND_PERMISSION:
                        case NO_PERMISSION:
                            changeStatus(Status.NO_PERMISSION);
                            break;
                        // case NO_INTENT: can't happen
                        // case NO_INTERNET: can't happen
                        case NULL:
                        default:
                            break;
                    }
                }
            }
        }
    }

    /**
     * Callback for the internet connectivity check. If okay, the remote link is reachable.
     * @param exists
     *      True if the URL has been reached, false if it has not been reached
     */
    @Override
    public void urlChecked(boolean exists) {
        synchronized (this) {
            if (exists) {
                switch (statusCode) {
                    case CHECKING_INTERNET:
                        videoSceneObjectPlayer = makeVideoSceneObject();
                        ((Minimal360Video) getMain()).setVideoSceneObjectPlayer(videoSceneObjectPlayer);
                        changeStatus(Status.READY_TO_PLAY);
                        break;
                    case CHECKING_INTERNET_AND_PERMISSION:
                        changeStatus(Status.CHECKING_PERMISSION);
                        break;
                    case READY_TO_PLAY:
                    case PLAYING:
                    case CHECKING_PERMISSION:
                    case NO_INTENT:
                    case NO_INTERNET:
                    case NO_PERMISSION:
                    case NULL:
                    default:
                        break;
                }
            } else {
                // No internet is stronger than no permissions, but weaker than no intent.
                // Although we shouldn't be there if there is no intent, lets do a proper check
                switch(statusCode) {
                    case READY_TO_PLAY:
                    case PLAYING:
                    case CHECKING_INTERNET:
                    case CHECKING_PERMISSION:
                    case CHECKING_INTERNET_AND_PERMISSION:
                    case NO_PERMISSION:
                        changeStatus(Status.NO_INTERNET);
                        break;
                    case NO_INTENT:
                    case NO_INTERNET:
                    case NULL:
                    default:
                        break;
                }
                videoSceneObjectPlayer = null;
                ((Minimal360Video) getMain()).setVideoSceneObjectPlayer(null);
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
                DashSRDMediaSource mediaSource = new DashSRDMediaSource(uri, buildDataSourceFactory(false),
                        new DefaultDashSRDChunkSource.Factory(mediaDataSourceFactory), mainHandler,
                        /* eventListener */ null);
                mediaSource.setDynamicEditingHolder(dynamicEditingHolder);
                mediaSource.setBuffers(minBufferMs, maxBufferMs);
                return mediaSource;
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
     * @param listener The transfer listener to be used. May be null.
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

    /**
     * Parse the dynamic editing XML file to retrieve snapchanges
     */
    private void parseDynamicEditing() {
        DynamicEditingParser parser = new DynamicEditingParser(dynamicEditingFN);
        try {
            parser.parse(dynamicEditingHolder);
        } catch (Exception e) {
            changeStatus(Status.WRONGDYNED);
        }
    }
}
