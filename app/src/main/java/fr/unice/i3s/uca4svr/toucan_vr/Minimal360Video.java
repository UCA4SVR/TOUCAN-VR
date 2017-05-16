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

import fr.unice.i3s.uca4svr.toucan_vr.mediaplayer.TiledExoPlayer;
import fr.unice.i3s.uca4svr.toucan_vr.meshes.PartitionedSphereMeshes;
import fr.unice.i3s.uca4svr.toucan_vr.tilespicker.TilesPicker;
import fr.unice.i3s.uca4svr.toucan_vr.tracking.HeadMotionTracker;

public class Minimal360Video extends GVRMain {

    // The associated GVR context
    private GVRContext gvrContext;

    // The head motion tracker to log head motions
    private HeadMotionTracker headMotionTracker = null;

    // The status code needed to always know which virtual scene to create
    private PlayerActivity.Status statusCode = PlayerActivity.Status.NULL;

    private GVRVideoSceneObjectPlayer<ExoPlayer> videoSceneObjectPlayer = null;

    private boolean videoStarted = false;

    // Info about the tiles, needed to properly build the sphere
    private int gridHeight;
    private int gridWidth;
    private String[] tiles;

    Minimal360Video(PlayerActivity.Status statusCode, String [] tiles,
                    int gridWidth, int gridHeight) {
        this.statusCode = statusCode;
        this.tiles = tiles;
        this.gridWidth = gridWidth;
        this.gridHeight = gridHeight;
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

            final GVRScene scene = gvrContext.getMainScene();

            GVRFrustumPicker frustumPicker = new GVRFrustumPicker(gvrContext,scene);
            frustumPicker.setFrustum(40,1,49,50);


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

    @Override
    public void onStep() {
        if (headMotionTracker != null &&
                statusCode == PlayerActivity.Status.PLAYING) {
            headMotionTracker.track(gvrContext, videoSceneObjectPlayer.getPlayer().getCurrentPosition());
        }
    }
}
