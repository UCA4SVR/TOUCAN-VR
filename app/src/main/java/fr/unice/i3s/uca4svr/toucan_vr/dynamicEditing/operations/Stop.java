package fr.unice.i3s.uca4svr.toucan_vr.dynamicEditing.operations;

import com.google.android.exoplayer2.ExoPlayer;

import org.gearvrf.GVRContext;
import org.gearvrf.GVRSceneObject;
import org.gearvrf.scene_objects.GVRVideoSceneObjectPlayer;

import java.util.Date;

import fr.unice.i3s.uca4svr.toucan_vr.dynamicEditing.DynamicEditingHolder;
import fr.unice.i3s.uca4svr.toucan_vr.tracking.DynamicOperationsTracker;

public class Stop extends DynamicOperation {

  private int millisPauseTime;
  private long actualDuration;

  public Stop(DynamicEditingHolder dynamicEditingHolder, int millisPauseTime) {
    super(dynamicEditingHolder);
    this.millisPauseTime = millisPauseTime;
  }

  public Stop(DynamicEditingHolder dynamicEditingHolder) {
    this(dynamicEditingHolder, 0);
  }

  @Override
  public void activate(final GVRVideoSceneObjectPlayer<ExoPlayer> player, GVRSceneObject videoHolder) {
    long startTime = new Date().getTime();
    while (System.currentTimeMillis() - startTime < millisPauseTime) {
      player.pause();
    }
    player.start();
    actualDuration = new Date().getTime() - startTime;
    dynamicEditingHolder.advance();
  }

  @Override
  public boolean hasToBeTriggeredInContext(GVRContext gvrContext) {
    return true; //always trigger stops
  }

  @Override
  public void logIn(DynamicOperationsTracker tracker, long executionTime) {
    tracker.trackStop(getMilliseconds(), executionTime, millisPauseTime, actualDuration);
  }

  @Override
  public int computeIdealTileIndex(int selectedIndex, int adaptationSetIndex, long nextChunkStartTimeUs) {
    return selectedIndex; // nothing to do
  }

  public void setDuration(int millisDuration) {
    this.millisPauseTime = millisDuration;
  }
}
