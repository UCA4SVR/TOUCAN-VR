package fr.unice.i3s.uca4svr.toucan_vr.dynamicEditing.operations;

import com.google.android.exoplayer2.ExoPlayer;

import org.gearvrf.GVRContext;
import org.gearvrf.GVRSceneObject;
import org.gearvrf.scene_objects.GVRVideoSceneObjectPlayer;

import fr.unice.i3s.uca4svr.toucan_vr.dynamicEditing.DynamicEditingHolder;
import fr.unice.i3s.uca4svr.toucan_vr.tracking.DynamicOperationsTracker;

public class Stop extends DynamicOperation {

  private int millisPauseTime;

  public Stop(DynamicEditingHolder dynamicEditingHolder, int millisPauseTime) {
    super(dynamicEditingHolder);
    this.millisPauseTime = millisPauseTime;
  }

  @Override
  public void activate(final GVRVideoSceneObjectPlayer<ExoPlayer> player, GVRSceneObject videoHolder) {
    System.out.println("STOOOP");
    player.pause();
    new Thread(new Runnable() { //async so the user can explore the scene
      @Override
      public void run() {
        try {
          Thread.sleep(millisPauseTime);
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
        System.out.println("GO ON");
        player.start();
      }
    }).start();
  }

  @Override
  public boolean hasToBeTriggeredInContext(GVRContext gvrContext) {
    return true;
  }

  @Override
  public void logIn(DynamicOperationsTracker tracker, long executionTime) {

  }

  @Override
  public int computeIdealIndex(int selectedIndex, int adaptationSetIndex, long nextChunkStartTimeUs) {
    return selectedIndex; // nothing to do
  }
}
