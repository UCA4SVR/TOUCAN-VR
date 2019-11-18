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
import android.os.AsyncTask;
import android.util.Log;
import android.view.Gravity;

import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.util.Clock;
import com.google.android.exoplayer2.util.SystemClock;

import org.gearvrf.GVRAndroidResource;
import org.gearvrf.GVRContext;
import org.gearvrf.GVRFrustumPicker;
import org.gearvrf.GVRMain;
import org.gearvrf.GVRMeshCollider;
import org.gearvrf.GVRRenderData;
import org.gearvrf.GVRScene;
import org.gearvrf.GVRSceneObject;
import org.gearvrf.GVRTexture;
import org.gearvrf.GVRTransform;
import org.gearvrf.scene_objects.GVRSphereSceneObject;
import org.gearvrf.scene_objects.GVRTextViewSceneObject;
import org.gearvrf.scene_objects.GVRVideoSceneObject;
import org.gearvrf.scene_objects.GVRVideoSceneObject.GVRVideoType;
import org.gearvrf.scene_objects.GVRVideoSceneObjectPlayer;

//import org.tensorflow.TensorFlow;
//import org.tensorflow.Graph;
//import org.tensorflow.Session;
//import org.tensorflow.Tensor;
//import org.tensorflow.SavedModelBundle;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;
import java.util.Queue;
import java.util.concurrent.Future;

import fr.unice.i3s.uca4svr.toucan_vr.dynamicEditing.DynamicEditingHolder;
import fr.unice.i3s.uca4svr.toucan_vr.dynamicEditing.operations.DynamicOperation;
import fr.unice.i3s.uca4svr.toucan_vr.dynamicEditing.operations.SnapChange;
import fr.unice.i3s.uca4svr.toucan_vr.mediaplayer.TiledExoPlayer;
import fr.unice.i3s.uca4svr.toucan_vr.meshes.PartitionedSphereMeshes;
import fr.unice.i3s.uca4svr.toucan_vr.realtimeUserPosition.PushRealtimeEvents;
import fr.unice.i3s.uca4svr.toucan_vr.realtimeUserPosition.PushResponse;
import fr.unice.i3s.uca4svr.toucan_vr.realtimeUserPosition.RealtimeEvent;
import fr.unice.i3s.uca4svr.toucan_vr.tflite.Classifier;
import fr.unice.i3s.uca4svr.toucan_vr.tflite.ClassifierFloatMobileNet;
import fr.unice.i3s.uca4svr.toucan_vr.tilespicker.TilesPicker;
import fr.unice.i3s.uca4svr.toucan_vr.tracking.DynamicOperationsTracker;
import fr.unice.i3s.uca4svr.toucan_vr.tracking.FreezingEventsTracker;
import fr.unice.i3s.uca4svr.toucan_vr.tracking.HeadMotionTracker;
import fr.unice.i3s.uca4svr.toucan_vr.utils.Angles;

public class Minimal360Video extends GVRMain implements PushResponse {

  // The associated GVR context
  private GVRContext gvrContext;

  // The head motion tracker to log head motions
  private HeadMotionTracker headMotionTracker = null;

  // The tracker for the re-buffering events
  private FreezingEventsTracker freezingEventsTracker = null;

  // The tracker for the snapchange events
  private DynamicOperationsTracker dynamicOperationsTracker = null;

  // Objects used to push the tap events and the user's realtime position
  private PushRealtimeEvents realtimeEventPusher = null;
  private RealtimeEvent realtimeEvent = null;

  private long lastTransmissionTime = Long.MIN_VALUE;
  private Clock clock = new SystemClock();

  // The status code needed to always know which virtual scene to create
  private PlayerActivity.Status statusCode = PlayerActivity.Status.NULL;

  private GVRVideoSceneObjectPlayer<ExoPlayer> videoSceneObjectPlayer = null;

  private boolean videoStarted = false;

  //Info about the dynamic editing
  private DynamicEditingHolder dynamicEditingHolder;

  private float lastDynamicOpTriggered;

  private Classifier snap_trigger_classifier = null;

  //---- To hhtp send quality in FoV
  static private List<Integer> chunkIndexes_picked = new ArrayList<Integer>();
  static private List<Integer> chunkIndexes_quals = new ArrayList<Integer>();
  static private List<Boolean[]> pickedTiles = new ArrayList<Boolean[]>();
  static private List<Integer[]> qualityTiles = new ArrayList<Integer[]>();
  private int index_waitingtocomplete = 1;
  private int chunkIndex;
  //------------

  private GVRSceneObject videoHolder;

  // Info about the tiles, needed to properly build the sphere
  private int gridHeight;
  private int gridWidth;
  private String[] tiles;
  public TilesPicker tilesPicker = null;

  private float currentSnapAngle = Float.NaN;

  private int nb_snaps_triggered = 0;
  private float last_possible_snap_time;
  private float proba_trigger = 0.0f;

  Minimal360Video(PlayerActivity activity, PlayerActivity.Status statusCode, String [] tiles,
                  int gridWidth, int gridHeight, DynamicEditingHolder dynamicEditingHolder, int numThreads) {
    this.statusCode = statusCode;
    this.tiles = tiles;
    this.gridWidth = gridWidth;
    this.gridHeight = gridHeight;
    this.dynamicEditingHolder = dynamicEditingHolder;
    this.lastDynamicOpTriggered = -0.01f;
    try {
      this.snap_trigger_classifier = Classifier.create(activity, Classifier.Model.FLOAT, Classifier.Device.CPU, numThreads);
    } catch(Exception e){

    }
  }

  public void setVideoSceneObjectPlayer(GVRVideoSceneObjectPlayer<ExoPlayer> videoSceneObjectPlayer) {
    this.videoSceneObjectPlayer = videoSceneObjectPlayer;
    this.tilesPicker.videoSceneObjectPlayer = videoSceneObjectPlayer;

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
//    System.out.print("x:"+videoHolder.getTransform().getRotationPitch()+" y:"+videoHolder.getTransform().getRotationYaw()+" z:"+videoHolder.getTransform().getRotationRoll()+"\n");
//    System.exit(0);
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
      scene.getEventReceiver().addListener(TilesPicker.getPicker(dynamicEditingHolder));

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


              realtimeEventPusher = new PushRealtimeEvents(gvrContext, realtimeEventPusher.getServerIP());
              realtimeEvent = new RealtimeEvent();
              realtimeEvent.timestamp = clock.elapsedRealtime();
              realtimeEvent.videoTime = 0;
              realtimeEvent.playing = false;
              realtimeEvent.x = 0;
              realtimeEvent.y = 0;
              realtimeEvent.z = 0;
              realtimeEvent.isPlaying = 0;
              realtimeEvent.headX = 0;
              realtimeEvent.headY = 0;
              realtimeEvent.headZ = 0;
              realtimeEvent.dynamic = dynamicEditingHolder.isDynamicEdited();
              realtimeEvent.snapAngle = 0;
              realtimeEvent.start = false;
              realtimeEventPusher.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, realtimeEvent);
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

        @Override
        public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {

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
//      videoHolder.getGVRContext().getMainScene().getMainCameraRig().getHeadTransform().reset();

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

  public void initDynamicOperationsTracker(String logPrefix) {
    if (dynamicOperationsTracker == null)
      dynamicOperationsTracker = new DynamicOperationsTracker(logPrefix);
  }

  public void initRealtimeEventPusher(GVRContext context, String serverIP) {
    if (realtimeEventPusher == null)
      realtimeEventPusher = new PushRealtimeEvents(context, serverIP, this);
    if (realtimeEvent != null)
      realtimeEvent = new RealtimeEvent();
  }

  private boolean inSession() {
    return statusCode == PlayerActivity.Status.PLAYING ||
            statusCode == PlayerActivity.Status.PAUSED;

  }

  private boolean playbackEnded() {
    return statusCode == PlayerActivity.Status.PLAYBACK_ENDED;

  }

  @Override
  public void onStep() {

//    Graph graph = new Graph()
//    Session session = new Session(graph);
//    SavedModelBundle model = SavedModelBundle.load("./model");
//    Tensor<Double> tensor = model.session().runner().fetch("actor0/out/BiasAdd:0")
//      .feed("actor0/inputs1/X:0", Tensor.<Double>create(3, Double.class))
//      .feed("actor0/inputs2/X:0", Tensor.<Double>create(3, Double.class))
//      .feed("actor0/inputs3/X:0", Tensor.<Double>create(3, Double.class))
//      .feed("actor0/inputs4/X:0", Tensor.<Double>create(3, Double.class))
//      .feed("actor0/keep_per:0", Tensor.<Double>create(3, Double.class))
//      .run().get(0).expect(Double.class);
//
//    System.out.println(tensor.doubleValue());


//    this.snap_trigger_classifier.predict(null);

    // We only perform the tracking and the snapchanges if the video is playing
    if (inSession() && videoSceneObjectPlayer != null) {
      final ExoPlayer player = videoSceneObjectPlayer.getPlayer();
      if (headMotionTracker != null) {
        // BEFORE LUCILE
        // headMotionTracker.track(gvrContext, player.getCurrentPosition(), Angles.getCurrentXAngle(gvrContext), Angles.getCurrentYAngle(gvrContext));
        // AFTER LUCILE
        headMotionTracker.track(player.getCurrentPosition(), videoHolder, dynamicEditingHolder);
      }
      if (freezingEventsTracker != null) {
        freezingEventsTracker.track(player.getPlaybackState(), player.getCurrentPosition());
      }
      boolean is_snapChange = false;
      //Dynamic Editing block
      if(dynamicEditingHolder.isDynamicEdited()) {
        List<DynamicOperation> ops = (List)DynamicEditingHolder.operations;
        DynamicOperation op = dynamicEditingHolder.getCurrentOperation();
        //----------Initialization to correct randomness of tilePicker------
        if (op.getMicroseconds() < 20000){
          for (int i=0;i<ops.size();i++){
            SnapChange sc = (SnapChange)(ops.get(i));
            int[] FoVtiles = new int[gridWidth*gridHeight];
            for (int ind=0; ind<gridHeight*gridWidth; ind++){
              FoVtiles[ind]=-1;
              if (tilesPicker.isPicked(ind)) FoVtiles[ind]=ind;
            }
            sc.setFoVTiles(FoVtiles);
          }
        }
        //------------------------------------------------------------------
        if (op.isReady(player.getCurrentPosition())) {
          if (op.getDecided() && op.getTriggered()) {
            if (op.hasToBeTriggeredInContext(gvrContext)) {
              op.activate(videoSceneObjectPlayer, videoHolder, headMotionTracker, dynamicOperationsTracker); // Execute the operation
              currentSnapAngle = dynamicEditingHolder.lastRotation * 0.017453292F;//TO_RADIANS = 0.017453292F
            } else {
              dynamicEditingHolder.advance();
            }
            //nb_snaps_triggered += 1;
            nb_snaps_triggered = 1;
            lastDynamicOpTriggered = op.getMilliseconds();
          } else { // If operation doesn't have to be triggered, go to next one anyway
            dynamicEditingHolder.advance();
            nb_snaps_triggered = 0;
          }
          last_possible_snap_time = op.getInput(); // not initially here
          proba_trigger = op.getProba();
        }

        // Log
        if (dynamicOperationsTracker != null) {
          op.logIn(dynamicOperationsTracker, player.getCurrentPosition());
        }
        //last_possible_snap_time = op.getInput(); //player.getCurrentPosition();
      }

      if (realtimeEventPusher != null) {
        long currentTime = clock.elapsedRealtime();
        if (currentTime - lastTransmissionTime >= 80 || lastTransmissionTime == Long.MIN_VALUE) {
          realtimeEventPusher = new PushRealtimeEvents(gvrContext, realtimeEventPusher.getServerIP(), this);
          realtimeEvent = new RealtimeEvent();
          realtimeEvent.timestamp = clock.elapsedRealtime();
          realtimeEvent.videoTime = player.getCurrentPosition();
          realtimeEvent.playing = player.getPlayWhenReady() && player.getPlaybackState() == ExoPlayer.STATE_READY;
          GVRTransform headTransform = gvrContext.getMainScene().getMainCameraRig().getHeadTransform();
//          GVRTransform headTransform = videoHolder.getTransform();

          GVRContext context = videoHolder.getGVRContext();
          float normalizedLastRotation = Angles.normalizeAngle(dynamicEditingHolder.lastRotation);
          float currentUserPosition = Angles.getCurrentYAngle(context);
          float currentUserPosition_correctedwithSC = Angles.normalizeAngle(currentUserPosition-normalizedLastRotation); // [0;360]

//          String msg = "Y : "+headTransform.getRotationY()+" / Y snaps : "+currentUserPosition_correctedwithSC;
//          context.getMainScene().addStatMessage(msg);
//          headTransform.setRotation(headTransform.getRotationW(), headTransform.getRotationX(), currentUserPosition_correctedwithSC, headTransform.getRotationZ());

//          System.out.print("normalizedLastRotation:"+normalizedLastRotation+" / currentUserPosition:"+currentUserPosition+"\n");
          realtimeEvent.x = gvrContext.getMainScene().getMainCameraRig().getLookAt()[1];//[-1;1]
          // Lucile
          //realtimeEvent.x = headTransform.getRotationPitch();
          realtimeEvent.y = currentUserPosition_correctedwithSC;//[0;360]
          realtimeEvent.z = headTransform.getRotationRoll(); //Angles.normalizeAngle(headTransform.getRotationRoll());//not correct
          if(statusCode == PlayerActivity.Status.PLAYING) {
            realtimeEvent.isPlaying = 1;
          } else{
            realtimeEvent.isPlaying = 0;
          }

          //TODO find a way to send bandwidth tracking

          realtimeEvent.headW = headTransform.getRotationW();
          realtimeEvent.headX = headTransform.getRotationX();
          realtimeEvent.headY = headTransform.getRotationY();
          realtimeEvent.headZ = headTransform.getRotationZ();
          realtimeEvent.lastDynamicOpTriggered = lastDynamicOpTriggered;
          realtimeEvent.dynamic = dynamicEditingHolder.isDynamicEdited();
          realtimeEvent.snapAngle = currentSnapAngle;

          realtimeEvent.nb_snaps_triggered = nb_snaps_triggered; // test: it is last trigger
          realtimeEvent.last_possible_snap_time = last_possible_snap_time; // test: it is mean input
          realtimeEvent.proba_trigger = proba_trigger;

          realtimeEvent.start = lastTransmissionTime == Long.MIN_VALUE;

          setIndexAndQualFoV();
//          realtimeEvent.chunkIndex = dynamicEditingHolder.getChunkIndex();
//          realtimeEvent.qualityFoV = dynamicEditingHolder.getQualityFoV();

          realtimeEventPusher.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, realtimeEvent);
          lastTransmissionTime = currentTime;
//          System.out.print("mean input speed: "+realtimeEvent.last_possible_snap_time+" , proba_trigger: "+realtimeEvent.proba_trigger+"\n");
          System.out.print("chunk index: "+realtimeEvent.chunkIndex+" , quality in FoV: "+realtimeEvent.qualityFoV+"\n");
        }
      }
    }
    if(playbackEnded()) {
      if (tilesPicker != null) {
        tilesPicker.myRunnableTilesLogging.doStop();
      }
    }
    }

  @Override
  public void pushResponse(boolean exists) {

  }

  private void setIndexAndQualFoV() {

    chunkIndexes_picked = dynamicEditingHolder.get_chunkIndexes_picked();
    pickedTiles = dynamicEditingHolder.get_pickedTiles();
    chunkIndexes_quals = dynamicEditingHolder.get_chunkIndexes_quals();
    qualityTiles = dynamicEditingHolder.get_qualityTiles();

    int current_played_index = 0;
    if (chunkIndexes_picked.size()>0 && chunkIndexes_quals.size()>0){
      current_played_index = Math.min(chunkIndexes_picked.get(chunkIndexes_picked.size()-1), chunkIndexes_quals.get(chunkIndexes_quals.size()-1));
    }

    if (current_played_index > index_waitingtocomplete) {
      System.out.print("current_played_index, index_waitingtocomplete: "+current_played_index+", "+index_waitingtocomplete+ "\n");

      int ind_in_qualityTiles_list = chunkIndexes_quals.indexOf(index_waitingtocomplete);
      if (ind_in_qualityTiles_list>=0) {
        Integer[] quals = qualityTiles.get(ind_in_qualityTiles_list);
//        System.out.print("chunk index: "+index_waitingtocomplete+", ind in list: "+ ind_in_qualityTiles_list + ", quals: " + quals[0] + quals[1] + quals[2] + quals[3] + quals[4] + quals[5] + quals[6] + quals[7] + quals[8] + "\n");
        int indStart_in_pickedTiles_list = chunkIndexes_picked.indexOf(index_waitingtocomplete);
        int indEnd_in_pickedTiles_list = chunkIndexes_picked.lastIndexOf(index_waitingtocomplete);
        int nb_timestamps = 0;
        int nb_tilesInFoV = 0;
        float acc_tot = 0.0f;
        float acc_chunk = 0.0f;
        for (int ind = indStart_in_pickedTiles_list; ind < indEnd_in_pickedTiles_list + 1; ind = ind + 1) {
          Boolean[] picked = pickedTiles.get(ind);
          if (picked.length == gridHeight * gridWidth) {
            nb_tilesInFoV = 0;
            acc_chunk = 0.0f;
            for (int j = 0; j < picked.length; j = j + 1) {
              if (picked[j]) {
//                if (quals[j] < 0) {
//                  System.out.print("in loop, chunk index: "+index_waitingtocomplete+", ind in list: "+ ind_in_qualityTiles_list + ", quals: " + quals[0] + quals[1] + quals[2] + quals[3] + quals[4] + quals[5] + quals[6] + quals[7] + quals[8] + "\n");
//                }
                acc_chunk = acc_chunk + quals[j];
                nb_tilesInFoV = nb_tilesInFoV + 1;
              }
            }
            acc_chunk = acc_chunk / nb_tilesInFoV;
            acc_tot = acc_tot + acc_chunk;
            nb_timestamps = nb_timestamps + 1;
          }
        }
//        if (acc_tot <= 0.1 || nb_timestamps == 0) {
//          acc_tot = acc_tot - 10;
//        }
        float qualInFoV = acc_tot;
        if (nb_timestamps>0) qualInFoV = qualInFoV / nb_timestamps;
        realtimeEvent.chunkIndex = index_waitingtocomplete;
        realtimeEvent.qualityFoV = qualInFoV;
      }
      index_waitingtocomplete = index_waitingtocomplete + 1; //current_played_index;
    }
  }
}
