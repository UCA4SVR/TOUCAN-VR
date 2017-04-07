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
 * Package name
 * Displaying head rotations in the debug log
 * Copyright 2017 Laboratoire I3S, CNRS, Université côte d'azur
 * Author: Romaric Pighetti
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
import org.gearvrf.GVRContext;
import org.gearvrf.GVRMain;
import org.gearvrf.GVRMesh;
import org.gearvrf.GVRRenderData;
import org.gearvrf.GVRScene;
import org.gearvrf.GVRSceneObject;
import org.gearvrf.GVRTexture;
import org.gearvrf.scene_objects.GVRSphereSceneObject;
import org.gearvrf.scene_objects.GVRTextViewSceneObject;
import org.gearvrf.scene_objects.GVRVideoSceneObject;
import org.gearvrf.scene_objects.GVRVideoSceneObject.GVRVideoType;
import org.gearvrf.scene_objects.GVRVideoSceneObjectPlayer;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Future;

import fr.unice.i3s.uca4svr.toucan_vr.permissions.PermissionManager;
import fr.unice.i3s.uca4svr.toucan_vr.permissions.RequestPermissionResultListener;
import fr.unice.i3s.uca4svr.toucan_vr.tracking.HeadMotionTracker;

public class Minimal360Video extends GVRMain implements RequestPermissionResultListener {
    // The associated android context
    private final PermissionManager permissionManager;
    private final String logPrefix;

    // The GVRContext associated with the scene.
    // Needed by the headMotionTracker
    private GVRContext gvrContext;

    private GVRVideoSceneObjectPlayer<ExoPlayer> videoSceneObjectPlayer;

    // The head motion tracker to log head motions
    private HeadMotionTracker headMotionTracker;
    // stores the current number of times onStep has been called, used as the frame number for the
    // head motion logging for now.
    private float currentFrame;

    private boolean videoStarted = false;
    private boolean videoEnded = false;

    Minimal360Video(GVRVideoSceneObjectPlayer<ExoPlayer> videoSceneObjectPlayer,
                    PermissionManager permissionManager, String logPrefix) {
        this.videoSceneObjectPlayer = videoSceneObjectPlayer;
        this.permissionManager = permissionManager;
        this.logPrefix = logPrefix;
    }

    public void setVideoSceneObjectPlayer(GVRVideoSceneObjectPlayer<ExoPlayer> videoSceneObjectPlayer) {
        this.videoSceneObjectPlayer = videoSceneObjectPlayer;
    }

    /** Called when the activity is first created. */
    @Override
    public void onInit(GVRContext gvrContext) {
        this.gvrContext = gvrContext;
        currentFrame = 0;

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

            GVRScene scene = gvrContext.getMainScene();
            // Add a listener to the player to catch the end of the playback
            ExoPlayer player = videoSceneObjectPlayer.getPlayer();
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

            // create sphere / mesh
            GVRSphereSceneObject sphere = new GVRSphereSceneObject(gvrContext, 72, 144, false);
            GVRMesh mesh = sphere.getRenderData().getMesh();
            sphere.getTransform().setScale(100f, 100f, 100f);

            // create video scene
            GVRVideoSceneObject video = new GVRVideoSceneObject(gvrContext, mesh,
                    videoSceneObjectPlayer, GVRVideoType.MONO);
            video.setName("video");
            // apply video to scene
            scene.addSceneObject(video);
        }
    }

    private void initHeadMotionTracker() {
        // Give the context to the logger so that it has access to the camera
        headMotionTracker = new HeadMotionTracker(gvrContext, logPrefix);
    }

    private void createWaitForPermissionScene() {
        GVRScene scene = gvrContext.getMainScene();

        // the initial scene contains the GearVRf logo, so we clean it
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
        /* Alternative version: using image from < R.drawable.tap_text > as shown in createEndScene() */
    }

    private void createInitialScene()
    {
        GVRScene scene = gvrContext.getMainScene();

        // the initial scene contains the GearVRf logo, so we clean it
        scene.removeAllSceneObjects();

        // add a 360-photo as background, taken from resources
        Future<GVRTexture> textureSphere  = gvrContext.loadFutureTexture(
                new GVRAndroidResource(gvrContext, R.drawable.prague));
        GVRSphereSceneObject sphereObject = new GVRSphereSceneObject(gvrContext, false, textureSphere);
        sphereObject.getTransform().setScale(100, 100, 100);
        scene.addSceneObject(sphereObject);

        // add a message to the scene asking the user to tap the TrackPad to start the video
        GVRTextViewSceneObject textObject = new GVRTextViewSceneObject(gvrContext, 1.2f, 0.3f, "Tap to play!");
        textObject.setBackgroundColor(Color.TRANSPARENT);
        textObject.setTextColor(Color.RED);
        textObject.setGravity(Gravity.CENTER);
        textObject.setTextSize(5.0f);
        textObject.getTransform().setPosition(0.0f, 0.0f, -2.0f);
        textObject.getRenderData().setRenderingOrder(GVRRenderData.GVRRenderingOrder.TRANSPARENT);
        scene.getMainCameraRig().addChildObject(textObject);
        /* Alternative version: using image from < R.drawable.tap_text > as shown in createEndScene() */
    }

    public void createEndScene()
    {
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

        /* add message with request to remove the headset
         * N.B. the message here is an image remove.png taken from the resources;
         * an alternative would be creating a text object as shown above. */
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
        // logging with frame number for now
        if (headMotionTracker != null &&
                videoStarted &&
                !videoEnded &&
                videoSceneObjectPlayer != null &&
                videoSceneObjectPlayer.getPlayer() != null &&
                videoSceneObjectPlayer.getPlayer().getPlayWhenReady()) {
            //headMotionTracker.track(currentFrame);
            //Use this instead of current frame
            headMotionTracker.track(videoSceneObjectPlayer.getPlayer().getCurrentPosition());
        }
        //currentFrame++;
    }

    @Override
    public void onPermissionRequestDone(int requestID, int result) {
        if (result == PackageManager.PERMISSION_GRANTED && headMotionTracker == null) {
            initHeadMotionTracker();
        }
    }
}
