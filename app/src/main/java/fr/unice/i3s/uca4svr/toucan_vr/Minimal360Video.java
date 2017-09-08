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

import android.graphics.Color;
import android.view.Gravity;

import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;

import org.gearvrf.GVRAndroidResource;
import org.gearvrf.GVRContext;
import org.gearvrf.GVRFrustumPicker;
import org.gearvrf.GVRMain;
import org.gearvrf.GVRMeshCollider;
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
import java.util.concurrent.Future;

import fr.unice.i3s.uca4svr.toucan_vr.dynamicEditing.DynamicEditingHolder;
import fr.unice.i3s.uca4svr.toucan_vr.mediaplayer.TiledExoPlayer;
import fr.unice.i3s.uca4svr.toucan_vr.meshes.PartitionedSphereMeshes;
import fr.unice.i3s.uca4svr.toucan_vr.realtimeUserPosition.PushRealtimeEvents;
import fr.unice.i3s.uca4svr.toucan_vr.realtimeUserPosition.PushResponse;
import fr.unice.i3s.uca4svr.toucan_vr.realtimeUserPosition.RealtimeEvent;
import fr.unice.i3s.uca4svr.toucan_vr.tilespicker.TilesPicker;
import fr.unice.i3s.uca4svr.toucan_vr.tracking.FreezingEventsTracker;
import fr.unice.i3s.uca4svr.toucan_vr.tracking.HeadMotionTracker;
import fr.unice.i3s.uca4svr.toucan_vr.tracking.SnapchangeEventsTracker;

import static java.lang.Math.abs;

public class Minimal360Video extends GVRMain implements PushResponse {

    // The associated GVR context
    private GVRContext gvrContext;

    // The head motion tracker to log head motions
    private HeadMotionTracker headMotionTracker = null;

    // The tracker for the re-buffering events
    private FreezingEventsTracker freezingEventsTracker = null;

    // The tracker for the snapchange events
    private SnapchangeEventsTracker snapchangeEventsTracker = null;

    // Objects used to push the tap events and the user's realtime position
    private PushRealtimeEvents realtimeEventPusher = null;
    private RealtimeEvent realtimeEvent = null;

    // The status code needed to always know which virtual scene to create
    private PlayerActivity.Status statusCode = PlayerActivity.Status.NULL;

    private GVRVideoSceneObjectPlayer<ExoPlayer> videoSceneObjectPlayer = null;

    private boolean videoStarted = false;

    //Info about the dynamic editing
    private DynamicEditingHolder dynamicEditingHolder;


    private GVRSceneObject videoHolder;

    // Info about the tiles, needed to properly build the sphere
    private int gridHeight;
    private int gridWidth;
    private String[] tiles;

    Minimal360Video(PlayerActivity.Status statusCode, String [] tiles,
                    int gridWidth, int gridHeight, DynamicEditingHolder dynamicEditingHolder) {
        this.statusCode = statusCode;
        this.tiles = tiles;
        this.gridWidth = gridWidth;
        this.gridHeight = gridHeight;
        this.dynamicEditingHolder = dynamicEditingHolder;
    }

    public void setVideoSceneObjectPlayer(GVRVideoSceneObjectPlayer<ExoPlayer> videoSceneObjectPlayer) {
        this.videoSceneObjectPlayer = videoSceneObjectPlayer;
    }

    public void setStatusCode(PlayerActivity.Status statusCode) {
        this.statusCode = statusCode;
        if (gvrContext != null) {
            sceneDispatcher();
        }
    }

    /** Called when the activity is first created. */
    @Override
    public void onInit(GVRContext gvrContext) {
        this.gvrContext = gvrContext;
        // We need to create the initial scene
        sceneDispatcher();
    }

    private void displayVideo() {
        // start building the scene for 360 playback
        if (!videoStarted) {
            videoStarted = true;

            // Get the main VR scene
            final GVRScene scene = gvrContext.getMainScene();

            // Start with a clean scene to add only the video
            scene.removeAllSceneObjects();

            // The frustum picker needed to always know which tiles are within the FoV
            GVRFrustumPicker frustumPicker = new GVRFrustumPicker(gvrContext,scene);

            // The frustum in which the picker is able to pick tiles
            frustumPicker.setFrustum(40,1,49,50);

            // Attaching the picker
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
                            // Display the end scene
                            videoSceneObjectPlayer = null;
                            setStatusCode(PlayerActivity.Status.PLAYBACK_ENDED);
                            break;
                        default:
                            break;
                    }
                }

                @Override
                public void onPlayerError(ExoPlaybackException error) {
                    // Display the end scene on error
                    videoSceneObjectPlayer = null;
                    setStatusCode(PlayerActivity.Status.PLAYBACK_ERROR);
                }

                @Override
                public void onPositionDiscontinuity() {
                    // Does Nothing
                }
            });

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

            // Create an holder objects for the video objects, attach it to the scene and rotate the object according the first snapchange
            this.videoHolder = new GVRSceneObject(this.gvrContext);
            scene.addSceneObject(videoHolder);

            //Initial Rotation
            if(dynamicEditingHolder.isDynamicEdited()) {
                float currentAngle = getCurrentYAngle();
                float difference = currentAngle - dynamicEditingHolder.nextSCroiDegrees;
                videoHolder.getTransform().setRotationByAxis(difference, 0, 1, 0);
                dynamicEditingHolder.advance(difference);
            }

            // need a final handle on the object for the thread
            final GVRSceneObject videoHolderFinal = videoHolder;

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
                        videoHolderFinal.addChildObject( videos[id] );
                    }
                }).start();
            }
        }
    }

    private void sceneDispatcher() {
        gvrContext.runOnGlThread(
                new Runnable() {
                     @Override
                     public void run() {
                         // According to the state of the application we can understand what scene to build
                         GVRTextViewSceneObject textObject;
                         switch (statusCode) {
                             case NO_INTENT:
                                 textObject = new GVRTextViewSceneObject(gvrContext, 1.7f, 4f,
                                         gvrContext.getActivity().getString(R.string.no_intent));
                                 createScene(textObject);
                                 break;
                             case NO_INTERNET:
                                 textObject = new GVRTextViewSceneObject(gvrContext, 1.2f, 2f,
                                         gvrContext.getActivity().getString(R.string.source_unreachable));
                                 createScene(textObject);
                                 break;
                             case NO_PERMISSION:
                                 textObject = new GVRTextViewSceneObject(gvrContext, 1.2f, 2f,
                                         gvrContext.getActivity().getString(R.string.no_permission));
                                 createScene(textObject);
                                 break;
                             case READY_TO_PLAY:
                                 textObject = new GVRTextViewSceneObject(gvrContext, 1.2f, 2f,
                                         gvrContext.getActivity().getString(R.string.tap_to_play));
                                 createScene(textObject);
                                 break;
                             case PLAYING:
                                 displayVideo();
                                 break;
                             case PLAYBACK_ENDED:
                                 textObject = new GVRTextViewSceneObject(gvrContext, 1.2f, 2f,
                                         gvrContext.getActivity().getString(R.string.remove_headset));
                                 createScene(textObject);
                                 break;
                             case PLAYBACK_ERROR:
                                 textObject = new GVRTextViewSceneObject(gvrContext, 1.2f, 2f,
                                         gvrContext.getActivity().getString(R.string.playback_error));
                                 createScene(textObject);
                                 break;
                             case WRONGDYNED:
                                 textObject = new GVRTextViewSceneObject(gvrContext, 1.2f, 2f,
                                         gvrContext.getActivity().getString(R.string.dynamicEd_error));
                                 createScene(textObject);
                                 break;
                             case CHECKING_INTERNET:
                                 textObject = new GVRTextViewSceneObject(gvrContext, 1.2f, 2f,
                                         gvrContext.getActivity().getString(R.string.checking_url));
                                 createScene(textObject);
                                 break;
                             case CHECKING_PERMISSION:
                                 textObject = new GVRTextViewSceneObject(gvrContext, 1.2f, 2f,
                                         gvrContext.getActivity().getString(R.string.checking_permissions));
                                 createScene(textObject);
                                 break;
                             case CHECKING_INTERNET_AND_PERMISSION:
                                 textObject = new GVRTextViewSceneObject(gvrContext, 1.2f, 2f,
                                         gvrContext.getActivity().getString(R.string.checking_url_permissions));
                                 createScene(textObject);
                                 break;
                             case NULL:
                                 textObject = new GVRTextViewSceneObject(gvrContext, 1.2f, 2f,
                                         gvrContext.getActivity().getString(R.string.initializing));
                                 createScene(textObject);
                                 break;
                         }
                     }
                });
    }

    private void createScene(GVRSceneObject message) {

        // clean the old scene before building the new one
        GVRScene scene = gvrContext.getMainScene();
        scene.removeAllSceneObjects();

        // add a 360-photo as background, taken from resources
        Future<GVRTexture> textureSphere  = gvrContext.loadFutureTexture(
                new GVRAndroidResource(gvrContext, R.drawable.prague));
        GVRSphereSceneObject sphereObject = new GVRSphereSceneObject(gvrContext, false, textureSphere);
        sphereObject.getTransform().setScale(100, 100, 100);
        scene.addSceneObject(sphereObject);

        // add the message to the scene (using Text Objects for now)
        GVRTextViewSceneObject textObject = (GVRTextViewSceneObject)message;
        textObject.setBackgroundColor(Color.TRANSPARENT);
        textObject.setTextColor(Color.RED);
        textObject.setGravity(Gravity.CENTER);
        textObject.setTextSize(4.0f);
        textObject.getTransform().setPosition(0.0f, 0.0f, -2.0f);
        textObject.getRenderData().setRenderingOrder(GVRRenderData.GVRRenderingOrder.TRANSPARENT);
        scene.getMainCameraRig().addChildObject(textObject);
    }

    public void initHeadMotionTracker(String logPrefix) {
        // We cannot provide the GVRContext because it is not yet available when the method is called
        if (headMotionTracker == null)
            headMotionTracker = new HeadMotionTracker(logPrefix);
    }

    public void initFreezingEventsTracker(String logPrefix) {
        if (freezingEventsTracker == null)
            freezingEventsTracker = new FreezingEventsTracker(logPrefix);
    }

    public void initSnapchangeEventsTracker(String logPrefix) {
        if (snapchangeEventsTracker == null)
            snapchangeEventsTracker = new SnapchangeEventsTracker(logPrefix);
    }

    public void initRealtimeEventPusher(GVRContext context, String serverIP) {
        if (realtimeEventPusher == null)
            realtimeEventPusher = new PushRealtimeEvents(context, serverIP, this);
        if (realtimeEvent != null)
            realtimeEvent = new RealtimeEvent();
    }

    public void pushTapEvent(Long timestamp) {
        if (realtimeEventPusher != null) {
            realtimeEvent.eventType = false;
            realtimeEvent.timestamp = timestamp;
            realtimeEventPusher.execute(realtimeEvent);
        }
    }

    @Override
    public void onStep() {
        // We only perform the tracking and the snapchanges if the video is playing
        if (statusCode == PlayerActivity.Status.PLAYING && videoSceneObjectPlayer != null) {
            final ExoPlayer player = videoSceneObjectPlayer.getPlayer();
            if (headMotionTracker != null)
                headMotionTracker.track(gvrContext, player.getCurrentPosition(), getCurrentXAngle(), getCurrentYAngle());
            if (freezingEventsTracker != null)
                freezingEventsTracker.track(player.getPlaybackState(), player.getCurrentPosition());
            if (realtimeEventPusher != null) {
                realtimeEventPusher = new PushRealtimeEvents(gvrContext, realtimeEventPusher.getServerIP(), this);
                realtimeEvent = new RealtimeEvent();
                realtimeEvent.eventType = true;//TODO realtimeEvent was null here
                realtimeEvent.timestamp = player.getCurrentPosition();
                realtimeEventPusher.execute(realtimeEvent);
            }
            //Dynamic Editing block
            if(dynamicEditingHolder.isDynamicEdited()) {
                if (abs(dynamicEditingHolder.nextSCMilliseconds - player.getCurrentPosition()) < dynamicEditingHolder.timeThreshold) {
                    boolean triggered = false;
                    //Check if a SnapChange is needed
                    float currentAngle = getCurrentYAngle();
                    float difference = currentAngle-dynamicEditingHolder.nextSCroiDegrees;
                    float trigger = computeTrigger(dynamicEditingHolder.lastRotation, currentAngle, dynamicEditingHolder.nextSCroiDegrees);
                    if(trigger > dynamicEditingHolder.angleThreshold) {
                        //Perform the SnapChange
                        videoHolder.getTransform().setRotationByAxis(difference,0,1,0);
                        triggered = true;
                    }
                    //Tracking
                    if (snapchangeEventsTracker != null)
                        snapchangeEventsTracker.track(dynamicEditingHolder.nextSCMilliseconds, player.getCurrentPosition(), dynamicEditingHolder.nextSCroiDegrees, currentAngle, triggered);
                    //Update for the next SnapChange
                    dynamicEditingHolder.advance(difference);

                }
            }

        }
    }

    /**
     * Gets the current user position Y angle (in degrees). It ranges between -180 and 180
     * @return User position
     */

    private float getCurrentYAngle() {
        double angle = 0;
        float[] lookAt = gvrContext.getMainScene().getMainCameraRig().getLookAt();
        // cos = [0], sin = [2]
        double norm = Math.sqrt(lookAt[0] * lookAt[0] + lookAt[2] * lookAt[2]);
        double cos = lookAt[0] / norm;
        cos = abs(cos) > 1 ? Math.signum(cos) : cos;
        if (lookAt[2] == 0) {
            angle = Math.acos(cos);
        } else {
            angle = Math.signum(lookAt[2]) * Math.acos(cos);
        }
        //From radiant to degree + orientation
        return (float)(angle * 180 / Math.PI * -1);
    }

    /**
     * Gets the current user position X angle (in degrees). It ranges between -90 and 90
     * @return User position
     */

    private float getCurrentXAngle() {
        double angle = 0;
        float[] lookAt = gvrContext.getMainScene().getMainCameraRig().getLookAt();
        // cos = [0], sin = [2]
        double norm = Math.sqrt(lookAt[0] * lookAt[0] + lookAt[2] * lookAt[2]);
        angle = Math.atan2(lookAt[1],norm);

        //From radiant to degree + orientation
        return (float)(angle * 180 / Math.PI * -1);
    }

    /**
     * Normalizes a given angle from a space between -360 and 360 to a space between -180 and 180
     * @param angle Angle to be normalized
     * @return Normalized angle
     */
    private float normalizeAngle(float angle) {
        if((angle>=-180)&&(angle<=180))
            return angle;
        else
            return -1*Math.signum(angle)*(360-abs(angle));
    }

    private float computeTrigger(float lastRotation, float currentUserPosition, float nextSCroiDegrees) {
        float normalizedLastRotation = normalizeAngle(lastRotation);
        float trigger = normalizeAngle(currentUserPosition-normalizedLastRotation);
        trigger = normalizeAngle(trigger-nextSCroiDegrees);
        return abs(trigger);
    }

    @Override
    public void pushResponse(boolean exists) {

    }
}
