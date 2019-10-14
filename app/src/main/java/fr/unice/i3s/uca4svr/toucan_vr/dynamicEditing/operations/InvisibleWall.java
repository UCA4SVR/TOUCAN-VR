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

package fr.unice.i3s.uca4svr.toucan_vr.dynamicEditing.operations;

import com.google.android.exoplayer2.util.Clock;
import com.google.android.exoplayer2.util.SystemClock;

import com.google.android.exoplayer2.ExoPlayer;

import org.gearvrf.GVRContext;
import org.gearvrf.GVRSceneObject;
import org.gearvrf.scene_objects.GVRVideoSceneObjectPlayer;

import java.util.Arrays;
import java.util.Locale;

import fr.unice.i3s.uca4svr.toucan_vr.dynamicEditing.DynamicEditingHolder;
import fr.unice.i3s.uca4svr.toucan_vr.tracking.DynamicOperationsTracker;
import fr.unice.i3s.uca4svr.toucan_vr.utils.Angles;
import fr.unice.i3s.uca4svr.toucan_vr.utils.RotationTool;
import fr.unice.i3s.uca4svr.toucan_vr.tracking.HeadMotionTracker;

/**
 * InvisibleWall technique as described in further research documentation.
 * Implements the operation's data and behavior.
 *
 * @author Antoine Dezarnaud
 */

public class InvisibleWall extends DynamicOperation {

  //Degree snapchange
  private int roiDegrees;
  //Center of the wall
  private float centerOfWallAngle;
  //Number of degrees that the user is free to rotate on Y axis
  private int freedomYDegrees = 0;
  //Number of degrees that the user is free to rotate on X axis
  private int freedomXDegrees = 0;
  //Duration of this operation
  private int millisInvisibleWallDuration;

  private int[] foVTiles;

  private boolean roiDegreesFlag;
  private boolean foVTilesFlag;
  private boolean triggered;
  private boolean recenterView;

  private float lastRotation;
  private int NOT_INITIALIZED = -1000;
  private float degreeYbeforeBlocking = NOT_INITIALIZED;

  // Changes for proper logging
  private final Clock clock;
  private class MyRunnableLogging implements Runnable {

    private boolean doStop = false;
    private long curVideoTime;
    private float currentUserRotation;
    private boolean userLookingOutOfWallsLimits;

    public InvisibleWall invisibleWall;
    public GVRVideoSceneObjectPlayer<ExoPlayer> player;
    public GVRSceneObject videoHolder;
    public HeadMotionTracker headMotionTracker;
    public DynamicOperationsTracker tracker;

    public MyRunnableLogging(InvisibleWall invisibleWall, GVRVideoSceneObjectPlayer<ExoPlayer> player, GVRSceneObject videoHolder, HeadMotionTracker headMotionTracker, DynamicOperationsTracker tracker) {
      super();
      this.invisibleWall = invisibleWall;
      this.player = player;
      this.videoHolder = videoHolder;
      this.headMotionTracker = headMotionTracker;
      this.tracker = tracker;
    }

    public synchronized void doStop() {
      this.doStop = true;
    }

    private synchronized boolean keepRunning() {
      return this.doStop == false;
    }

    private void do_logging() {
      userLookingOutOfWallsLimits = invisibleWall.checkUserHeadRotation(videoHolder);
      curVideoTime = player.getPlayer().getCurrentPosition();
      currentUserRotation = Angles.getCurrentYAngle(videoHolder.getGVRContext());
      headMotionTracker.track(curVideoTime, videoHolder, dynamicEditingHolder);
      invisibleWall.logIn_inWall(tracker, curVideoTime, currentUserRotation, userLookingOutOfWallsLimits);
    }

    @Override
    public void run() {
      while(keepRunning()) {
        do_logging();
        try {
          Thread.sleep(100L);
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
      }
    }
  }
  /// End changes for proper logging

  public InvisibleWall(DynamicEditingHolder dynamicEditingHolder) {
    super(dynamicEditingHolder);
    this.roiDegreesFlag = this.foVTilesFlag = false;
    this.clock = new SystemClock();
  }

  public void setRoiDegrees(int roiDegrees) {
    this.roiDegrees = roiDegrees;
    this.roiDegreesFlag = true;
  }

  public void setFoVTiles(int[] foVTiles) {
    this.foVTiles = foVTiles;
    this.foVTilesFlag = true;
  }

  @Override
  public boolean isWellDefined() {
    return super.isWellDefined() && this.roiDegreesFlag && this.foVTilesFlag;
  }

  /**
   * This method check and track user's head motions in order to know if the user gaze outside the walls.
   * @param videoHolder
   */
  private boolean checkUserHeadRotation(GVRSceneObject videoHolder) {

    boolean userGazeOutOfWalls = false;

    //check Y rotation
    float currentYangle = Angles.getCurrentYAngle(videoHolder.getGVRContext());
    boolean userGazeInAngleY = Angles.isAngleInAngleField(currentYangle, centerOfWallAngle, freedomYDegrees);

    userGazeOutOfWalls = !userGazeInAngleY;

    // update last limit angle
    if((userGazeOutOfWalls && degreeYbeforeBlocking == NOT_INITIALIZED) || //first time out of the walls
      (!userGazeOutOfWalls && degreeYbeforeBlocking != NOT_INITIALIZED)){ //first time between the walls

      if(Angles.isMoreToTheRight(centerOfWallAngle,currentYangle)){
        degreeYbeforeBlocking = Angles.subtractDegrees(roiDegrees,freedomYDegrees/2);
      }else{
        degreeYbeforeBlocking = Angles.addDegrees(roiDegrees,freedomYDegrees/2);
      }
    }

    return userGazeOutOfWalls;
  }

  @Override
  public void activate(GVRVideoSceneObjectPlayer<ExoPlayer> player, GVRSceneObject videoHolder, HeadMotionTracker headMotionTracker, DynamicOperationsTracker tracker) {

    //Initial snap change
    RotationTool.alignUserGazeTo(roiDegrees,videoHolder,dynamicEditingHolder);
    //float angleBeforeSC = Angles.getCurrentYAngle(videoHolder.getGVRContext());
    //float difference = angleBeforeSC - roiDegrees;
    //if(difference > dynamicEditingHolder.angleThreshold) {
    //  videoHolder.getTransform().setRotationByAxis(difference, 0, 1, 0);
    //}
    centerOfWallAngle = Angles.getCurrentYAngle(videoHolder.getGVRContext()); //the center of walls become the current user position
    //roiDegrees;
    //apply rotation restrictions
    //while the operation duration is not finished, control user's gaze
    long startTime = player.getPlayer().getCurrentPosition();
    long curVideoTime = startTime;
    boolean userLookingOutOfWallsLimits;
    float currentUserRotation;

    // Start loging thread not to impact wall smoothness
    MyRunnableLogging myRunnableLogging = new MyRunnableLogging(this,player,videoHolder,headMotionTracker,tracker);
    Thread thread = new Thread(myRunnableLogging, "MyLogging");
    thread.start();
    ///
    while (curVideoTime - startTime < millisInvisibleWallDuration) {

      userLookingOutOfWallsLimits = checkUserHeadRotation(videoHolder);
      currentUserRotation = Angles.getCurrentYAngle(videoHolder.getGVRContext());

      if(userLookingOutOfWallsLimits){
        blockUserGaze(currentUserRotation, videoHolder);
      }else{
        deblockUserGaze(currentUserRotation, videoHolder);
      }

      // added by Lucile (no more logging than once each 100ms)
//      if (curVideoTime - lastTimeLogged > 100) {
//        headMotionTracker.track(curVideoTime, videoHolder, dynamicEditingHolder);
//        logIn_inWall(tracker, curVideoTime, currentUserRotation, userLookingOutOfWallsLimits);
//        lastTimeLogged = curVideoTime;
//      }
      curVideoTime = player.getPlayer().getCurrentPosition();
    }
    myRunnableLogging.doStop();

    dynamicEditingHolder.advance();
  }

  private void deblockUserGaze(float currentUserRotation, GVRSceneObject videoHolder) {

    if(degreeYbeforeBlocking != NOT_INITIALIZED) {
      //RotationTool.alignUserGazeTo(degreeYbeforeBlocking,videoHolder,dynamicEditingHolder);
      float difference = currentUserRotation - (recenterView? roiDegrees : degreeYbeforeBlocking);
      videoHolder.getTransform().setRotationByAxis(difference, 0, 1, 0);
      lastRotation = difference;

      // added by Lucile
      dynamicEditingHolder.lastRotation = lastRotation;

      degreeYbeforeBlocking = NOT_INITIALIZED;
    }
  }

  private void blockUserGaze(float currentUserRotation, GVRSceneObject videoHolder) {

    // calculating the rotation to apply to block the user's rotation on Y axis
    float difference = currentUserRotation - (recenterView? roiDegrees : degreeYbeforeBlocking);
    videoHolder.getTransform().setRotationByAxis(difference, 0, 1, 0);
    lastRotation = difference;

    // added by Lucile
    dynamicEditingHolder.lastRotation = lastRotation;

  }

  @Override
  public void logIn(DynamicOperationsTracker tracker, long executionTime) {
    tracker.trackInvisibleWall(getMilliseconds(), executionTime, roiDegrees, triggered);
  }

  // added by Lucile
  private void logIn_inWall(DynamicOperationsTracker tracker,long curVideoTime, float currentUserRotation, boolean userLookingOutOfWallsLimits) {

    float normalizedLastRotation = Angles.normalizeAngle(dynamicEditingHolder.lastRotation);
    float currentUserPosition_correctedwithSC = Angles.normalizeAngle(currentUserRotation-normalizedLastRotation);

    tracker.inWallLogWriter.writeLine(String.format(Locale.ENGLISH, "%1d,%2d,%3$.4f,%4$.4f,%5$.4f,%6$b",
      clock.elapsedRealtime(), curVideoTime,
      currentUserPosition_correctedwithSC, currentUserRotation, dynamicEditingHolder.lastRotation, userLookingOutOfWallsLimits));
  }

  @Override
  public int computeIdealTileIndex(int selectedIndex, int adaptationSetIndex, long nextChunkStartTimeUs) {
    int desiredIndex = Arrays.binarySearch(foVTiles, adaptationSetIndex) >= 0 ? 0 : 1;
    if (this.getMicroseconds() >= nextChunkStartTimeUs && desiredIndex == 1) {
      // The snap change involves the current chunk. Provide a smooth transition when the snap change
      // is forcing the quality to be low while the tile is still displayed to the user.
      return selectedIndex;
    } else {
      return desiredIndex;
    }
  }

  @Override
  public boolean hasToBeTriggeredInContext(GVRContext gvrContext) {
    //Always have to be triggered
    return true;
  }

  public int getSCroiDegrees() {
    return this.roiDegrees;
  }

  public int[] getSCfoVTiles() {
    return this.foVTiles;
  }

  public void setDuration(int millisDuration) {
    this.millisInvisibleWallDuration = millisDuration;
  }

  public void setFreeXDegrees(int freeXDegrees) {
    this.freedomXDegrees = freeXDegrees;
  }

  public void setFreeYDegrees(int freeYDegrees) {
    this.freedomYDegrees = freeYDegrees;
  }

  public void setRecenterView(boolean recenterView) {
    this.recenterView = recenterView;
  }
}
