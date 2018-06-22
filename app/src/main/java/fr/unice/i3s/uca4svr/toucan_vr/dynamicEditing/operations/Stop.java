package fr.unice.i3s.uca4svr.toucan_vr.dynamicEditing.operations;

import com.google.android.exoplayer2.ExoPlayer;

import org.gearvrf.GVRContext;
import org.gearvrf.GVRSceneObject;
import org.gearvrf.scene_objects.GVRVideoSceneObjectPlayer;

import fr.unice.i3s.uca4svr.toucan_vr.dynamicEditing.DynamicEditingHolder;
import fr.unice.i3s.uca4svr.toucan_vr.tracking.DynamicOperationsTracker;

public class Stop extends DynamicOperation {
  public Stop(DynamicEditingHolder dynamicEditingHolder) {
    super(dynamicEditingHolder);
  }

  @Override
  public void activate(GVRVideoSceneObjectPlayer<ExoPlayer> player, GVRSceneObject videoHolder) {

  }

  @Override
  public boolean hasToBeTriggeredInContext(GVRContext gvrContext) {
    return false;
  }

  @Override
  public void logIn(DynamicOperationsTracker tracker, long executionTime) {

  }
}
