/* Copyright 2015 Samsung Electronics Co., LTD
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Modifications:
 * Several adaptations to use Exoplayer2 to display tiles videos.
 * Copyright 2017 Laboratoire I3S, CNRS, Université côte d'azur
 */

package fr.unice.i3s.uca4svr.toucan_vr;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.view.Gravity;

import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;

import org.gearvrf.GVRAndroidResource;
import org.gearvrf.GVRAssetLoader;
import org.gearvrf.GVRBoxCollider;
import org.gearvrf.GVRContext;
import org.gearvrf.GVRFrustumPicker;
import org.gearvrf.GVRMain;
import org.gearvrf.GVRMeshCollider;
import org.gearvrf.GVRPicker;
import org.gearvrf.GVRRenderData;
import org.gearvrf.GVRScene;
import org.gearvrf.GVRSceneObject;
import org.gearvrf.GVRTexture;
import org.gearvrf.scene_objects.GVRSphereSceneObject;
import org.gearvrf.scene_objects.GVRTextViewSceneObject;
import org.gearvrf.scene_objects.GVRVideoSceneObject;
import org.gearvrf.scene_objects.GVRVideoSceneObject.GVRVideoType;
import org.gearvrf.scene_objects.GVRVideoSceneObjectPlayer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Future;

import fr.unice.i3s.uca4svr.toucan_vr.mediaplayer.TiledExoPlayer;
import fr.unice.i3s.uca4svr.toucan_vr.meshes.PartitionedSphereMeshes;
import fr.unice.i3s.uca4svr.toucan_vr.permissions.PermissionManager;
import fr.unice.i3s.uca4svr.toucan_vr.permissions.RequestPermissionResultListener;
import fr.unice.i3s.uca4svr.toucan_vr.tilespicker.TilesPicker;
import fr.unice.i3s.uca4svr.toucan_vr.tracking.HeadMotionTracker;

public class Minimal360Video extends GVRMain implements RequestPermissionResultListener {

    private TilesPicker tilesPicker;

    // The associated GVR context
    private GVRContext gvrContext;

    // The head motion tracker to log head motions
    private HeadMotionTracker headMotionTracker;

    // Whether we should track the head motions or not
    private boolean loggingHeadMotion;

    // The prefix to give to the log file
    private final String logPrefix;

    private final PermissionManager permissionManager;

    private GVRVideoSceneObjectPlayer<ExoPlayer> videoSceneObjectPlayer;

    private boolean videoStarted = false;
    private boolean videoEnded = false;

    // Info about the tiles, needed to properly build the sphere
    private int gridHeight;
    private int gridWidth;
    private String[] tiles;

    Minimal360Video(GVRVideoSceneObjectPlayer<ExoPlayer> videoSceneObjectPlayer,
                    PermissionManager permissionManager, String logPrefix,
                    String [] tiles, int gridWidth, int gridHeight,
                    boolean loggingHeadMotion) {
        this.videoSceneObjectPlayer = videoSceneObjectPlayer;
        this.permissionManager = permissionManager;
        this.logPrefix = logPrefix;
        this.tiles = tiles;
        this.gridWidth = gridWidth;
        this.gridHeight = gridHeight;
        this.loggingHeadMotion = loggingHeadMotion;
    }

    public void setVideoSceneObjectPlayer(GVRVideoSceneObjectPlayer<ExoPlayer> videoSceneObjectPlayer) {
        this.videoSceneObjectPlayer = videoSceneObjectPlayer;
    }

    /** Called when the activity is first created. */
    @Override
    public void onInit(GVRContext gvrContext) {
        this.gvrContext = gvrContext;

        // The HeadMotionTracker needs permission to write to external storage.
        // Lets check that now or ask for it if necessary
        Set<String> permissions = new HashSet<>();
        permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);

        // Only one permission request is done in this component. No need to save the callID to
        // discriminate different permission requests, the callback will be called for these
        // particular permissions only.
        permissionManager.requestPermissions(permissions, this);

        // N.B. permissions need to be granted before playing the video
        if (videoSceneObjectPlayer == null) {
            createWaitForPermissionScene();
        } else {

            // create the initial scene as waiting screen
            createInitialScene();

            // pause the player from automatically starting
            videoSceneObjectPlayer.pause();
        }
    }

    public void displayVideo() {
        // start building the scene for 360 playback
        if (!videoStarted) {
            videoStarted = true;

            final GVRScene scene = gvrContext.getMainScene();

            GVRFrustumPicker frustumPicker = new GVRFrustumPicker(gvrContext,scene);
            frustumPicker.setFrustum(60,1,49,50);

            //Attaching the tiles picker
            scene.getEventReceiver().addListener(TilesPicker.getPicker());



            // Add a listener to the player to catch the end of the playback
            final ExoPlayer player = videoSceneObjectPlayer.getPlayer();
            player.addListener(new ExoPlayer.EventListener() {
                @Override
                public void onTimelineChanged(Timeline timeline, Object manifest) {
                    // Does Nothing
                }

                @Override
                public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {
                    // Does Nothing
                }

                @Override
                public void onLoadingChanged(boolean isLoading) {
                    // Does Nothing
                }

                @Override
                public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
                    switch (playbackState) {
                        case ExoPlayer.STATE_ENDED:
                            videoEnded = true;
                            // handle the event in order to rebuild the scene
                            createEndScene();
                            break;
                        default:
                            break;
                    }
                }

                @Override
                public void onPlayerError(ExoPlaybackException error) {
                    // Display the end scene on error
                    videoEnded = true;
                    createEndScene();
                }

                @Override
                public void onPositionDiscontinuity() {
                    // Does Nothing
                }
            });

            // Start with a clean scene to add only the video
            scene.removeAllSceneObjects();

            // Create a list of tiles to provide to the sphere constructor
            ArrayList<int[]> listOfTiles = new ArrayList<>();
            for (int i=0; i < tiles.length-3; i=i+4) {
                listOfTiles.add(new int[]{Integer.parseInt(tiles[i]),
                        Integer.parseInt(tiles[i + 1]),
                        Integer.parseInt(tiles[i + 2]),
                        Integer.parseInt(tiles[i + 3])});
            }

            // Create the meshes on which video tiles are rendered (portions of sphere right now)
            final PartitionedSphereMeshes sphereMeshes = new PartitionedSphereMeshes(gvrContext,
                    72, 144, gridHeight, gridWidth, listOfTiles, false);

            final GVRVideoSceneObject videos[] = new GVRVideoSceneObject[tiles.length/4];

            final TiledExoPlayer tiledPlayer = (TiledExoPlayer) videoSceneObjectPlayer.getPlayer();
            for (int i = 0; i < sphereMeshes.getNumberOfTiles(); i++) {
                final int id = i;
                /* Using Threads here to ensure that the UI thread is not blocked while
                 * waiting in the setNextSurfaceTileId method for earlier initialization to
                 * end.
                 */
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        tiledPlayer.setNextSurfaceTileId(id);
                        videos[id] = new GVRVideoSceneObject(gvrContext, sphereMeshes.getMeshById(id),
                                videoSceneObjectPlayer, GVRVideoType.MONO);
                        // FIXME: Is this really necessary ?
                        videos[id].getTransform().setScale(50f, 50f, 50f);
                        videos[id].setName( "video_" + id );
                        videos[id].setTag("vd"+(id));
                        videos[id].attachCollider(new GVRMeshCollider(gvrContext, true));
                        scene.addSceneObject( videos[id] );
                    }
                }).start();
            }
        }
    }

    private void initHeadMotionTracker() {
        // Check whether we should log the head motions or not.
        if (loggingHeadMotion)
            // Give the context to the logger so that it has access to the camera
            headMotionTracker = new HeadMotionTracker(gvrContext, logPrefix);
    }

    private void createWaitForPermissionScene() {
        GVRScene scene = gvrContext.getMainScene();

        // clean the scene before adding objects
        scene.removeAllSceneObjects();

        // add a 360-photo as background, taken from resources
        Future<GVRTexture> textureSphere  = gvrContext.loadFutureTexture(
                new GVRAndroidResource(gvrContext, R.drawable.prague));
        GVRSphereSceneObject sphereObject = new GVRSphereSceneObject(gvrContext, false, textureSphere);
        sphereObject.getTransform().setScale(100, 100, 100);
        scene.addSceneObject(sphereObject);

        // add a message to the scene asking the user to tap the TrackPad to start the video
        GVRTextViewSceneObject textObject = new GVRTextViewSceneObject(gvrContext, 1.2f, 2f,
                "Tap to start after accepting the permissions.");
        textObject.setBackgroundColor(Color.TRANSPARENT);
        textObject.setTextColor(Color.RED);
        textObject.setGravity(Gravity.CENTER);
        textObject.setTextSize(5.0f);
        textObject.getTransform().setPosition(0.0f, 0.0f, -2.0f);
        textObject.getRenderData().setRenderingOrder(GVRRenderData.GVRRenderingOrder.TRANSPARENT);
        scene.getMainCameraRig().addChildObject(textObject);
    }

    private void createInitialScene()
    {
        GVRScene scene = gvrContext.getMainScene();
        scene.removeAllSceneObjects();

        // add a 360-photo as background, taken from resources
        Future<GVRTexture> textureSphere  = gvrContext.loadFutureTexture(
                new GVRAndroidResource(gvrContext, R.drawable.prague));
        GVRSphereSceneObject sphereObject = new GVRSphereSceneObject(gvrContext, false, textureSphere);
        sphereObject.getTransform().setScale(100, 100, 100);
        scene.addSceneObject(sphereObject);

        // add a message to the scene asking the user to tap the TrackPad to start the video
        GVRAssetLoader gvrAssetLoader = new GVRAssetLoader(gvrContext);
        GVRTexture texture = gvrAssetLoader.loadTexture(new GVRAndroidResource(
                gvrContext, R.drawable.tap_text));
        GVRSceneObject messageObject = new GVRSceneObject(gvrContext, 1.2f, 0.3f, texture);
        messageObject.getTransform().setPosition(0.0f, 0.0f, -3.0f);
        messageObject.getRenderData().setRenderingOrder(GVRRenderData.GVRRenderingOrder.TRANSPARENT);
        scene.getMainCameraRig().addChildObject(messageObject);
        //*/
    }

    private void createEndScene()
    {
        // the player has been released at this point, we can put it to null
        videoSceneObjectPlayer = null;

        // clean the old scene to build the new one
        // N.B. getNextMainScene() can be used but it's going to become deprecated soon
        GVRScene scene = gvrContext.getMainScene();
        scene.removeAllSceneObjects();

        // add a 360-photo as background
        Future<GVRTexture> textureSphere  = gvrContext.loadFutureTexture(
                new GVRAndroidResource(gvrContext, R.drawable.prague));
        GVRSphereSceneObject sphereObject = new GVRSphereSceneObject(gvrContext, false, textureSphere);
        sphereObject.getTransform().setScale(100, 100, 100);
        scene.addSceneObject(sphereObject);

        // add message with request to remove the headset
        GVRAssetLoader gvrAssetLoader = new GVRAssetLoader(gvrContext);
        GVRTexture texture = gvrAssetLoader.loadTexture(new GVRAndroidResource(
                gvrContext, R.drawable.remove));
        GVRSceneObject messageObject = new GVRSceneObject(gvrContext, 1.8f, 0.3f, texture);
        messageObject.getTransform().setPosition(0.0f, 0.0f, -3.0f);
        messageObject.getRenderData().setRenderingOrder(GVRRenderData.GVRRenderingOrder.TRANSPARENT);
        scene.getMainCameraRig().addChildObject(messageObject);
    }

    @Override
    public void onStep() {
        if (headMotionTracker != null &&
                videoStarted &&
                !videoEnded &&
                videoSceneObjectPlayer != null &&
                videoSceneObjectPlayer.getPlayer() != null &&
                videoSceneObjectPlayer.getPlayer().getPlayWhenReady()) {
            headMotionTracker.track(videoSceneObjectPlayer.getPlayer().getCurrentPosition());
        }
    }

    @Override
    public void onPermissionRequestDone(int requestID, int result) {
        if (result == PackageManager.PERMISSION_GRANTED && headMotionTracker == null) {
            initHeadMotionTracker();
        }
    }
}
